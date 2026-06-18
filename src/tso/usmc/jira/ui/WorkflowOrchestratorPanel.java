package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.ui.workflow.StepEditorPanel;
import tso.usmc.jira.workflow.*;
import tso.usmc.jira.service.MetadataCacheService;
import tso.usmc.jira.service.JiraIssueService;
import tso.usmc.jira.ui.UiUtils;
import tso.usmc.jira.util.JiraUtils;
import tso.usmc.jira.util.ExecutionService;
import org.json.JSONObject;
import org.json.JSONArray;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;

public class WorkflowOrchestratorPanel extends BorderPane implements WorkflowProgressListener {

    private final JiraApiClientGui mainFrame;
    private final WorkflowManager workflowManager;
    private final Map<String, String> cachedFieldOptions = new HashMap<>();
    private final List<String> cachedLinkTypes = new ArrayList<>();
    private StepEditorPanel activeDraggedPanel = null;
    
    // UI Elements - Designer
    private final ListView<String> recipeList = new ListView<>();
    private final TextField recipeNameField = new TextField();
    private final TextField jqlField = new TextField();
    private final TextField contextIssueField = new TextField(); 
    private final Button fetchMetaBtn = new Button("Fetch Metadata");
    private final ProgressBar syncProgress = new ProgressBar();
    private final VBox stepsContainer = new VBox(10);
    private final ListView<String> tokenList = new ListView<>();
    private final TextField tokenSearchField = new TextField();
    
    // UI Elements - Runner
    private final ComboBox<String> runnerRecipeCombo = new ComboBox<>();
    private final TextField runnerJqlField = new TextField();
    private final Button searchBtn = new Button("Search Issues");
    private final TableView<RunnerIssueRow> runnerTable = new TableView<>();
    private final GridPane runnerInputsPanel = new GridPane();
    private final Map<String, Node> promptFields = new HashMap<>();
    private final TextArea runnerLog = new TextArea();
    private final Button runBtn = new Button("Run Workflow on Selected");
    private final Button exportReportBtn = new Button("Export Report (CSV)");
    private final CheckBox verboseLogCheck = new CheckBox("Verbose API Logs");
    private final CheckBox dryRunCheck = new CheckBox("Dry Run (Validate only)");
    private final Label statusLabel = new Label("Ready.");
    private TabPane mainTabs;

    // Results data
    private final List<JSONObject> currentSearchIssues = new ArrayList<>();
    private List<WorkflowEngine.ExecutionResult> lastResults = new ArrayList<>();

    private final Map<String, JSONObject> cachedFullMeta = new HashMap<>();
    private final List<String> allTokens = new ArrayList<>();

    public static class RunnerIssueRow {
        private final SimpleStringProperty key;
        private final SimpleStringProperty summary;
        private final SimpleStringProperty status;
        private final SimpleStringProperty assignee;

        public RunnerIssueRow(String key, String summary, String status, String assignee) {
            this.key = new SimpleStringProperty(key);
            this.summary = new SimpleStringProperty(summary);
            this.status = new SimpleStringProperty(status);
            this.assignee = new SimpleStringProperty(assignee);
        }

        public String getKey() { return key.get(); }
        public SimpleStringProperty keyProperty() { return key; }

        public String getSummary() { return summary.get(); }
        public SimpleStringProperty summaryProperty() { return summary; }

        public String getStatus() { return status.get(); }
        public SimpleStringProperty statusProperty() { return status; }

        public String getAssignee() { return assignee.get(); }
        public SimpleStringProperty assigneeProperty() { return assignee; }
    }

    public WorkflowOrchestratorPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        this.workflowManager = new WorkflowManager();
        try {
            unpackMetadataFromCache(mainFrame.getMetadataService().getDiskCache());
        } catch (Exception e) {
            System.err.println("Could not load initial metadata: " + e.getMessage());
        }
        
        UiUtils.setupExpandedView(recipeNameField);
        UiUtils.setupExpandedView(jqlField);
        UiUtils.setupExpandedView(contextIssueField);
        UiUtils.setupExpandedView(runnerJqlField);

        mainTabs = new TabPane();
        Tab designerTab = new Tab("Designer", createDesignerPanel());
        designerTab.setClosable(false);
        Tab runnerTab = new Tab("Runner", createRunnerPanel());
        runnerTab.setClosable(false);
        
        mainTabs.getTabs().addAll(designerTab, runnerTab);
        setCenter(mainTabs);
        
        refreshRecipeList();
        updateTokensFromCache();

        runnerRecipeCombo.setOnAction(e -> updateRunnerInputs());
    }

    // --- WorkflowProgressListener Implementation ---

    @Override
    public void onLog(String message) {
        Platform.runLater(() -> {
            runnerLog.appendText(message + "\n");
        });
    }

    @Override
    public void onError(String message, Exception ex) {
        onLog("ERROR: " + message + (ex != null ? " (" + ex.getMessage() + ")" : ""));
        if (ex != null) ex.printStackTrace();
    }

    @Override
    public void onComplete() {
        Platform.runLater(() -> {
            runBtn.setDisable(false);
            exportReportBtn.setDisable(false);
            statusLabel.setText("Workflow Execution Complete.");
        });
    }

    private Node createDesignerPanel() {
        BorderPane panel = new BorderPane();
        
        // Left: List
        BorderPane left = new BorderPane();
        left.setPadding(new Insets(10));
        left.setPrefWidth(200);
        Label recipesTitle = new Label("Recipes");
        recipesTitle.setStyle("-fx-font-weight: bold;");
        left.setTop(recipesTitle);
        BorderPane.setMargin(recipesTitle, new Insets(0, 0, 5, 0));
        
        left.setCenter(recipeList);
        BorderPane.setMargin(recipeList, new Insets(0, 0, 5, 0));
        
        HBox leftButtons = new HBox(10);
        leftButtons.setAlignment(Pos.CENTER);
        Button newBtn = new Button("New");
        Button delBtn = new Button("Delete");
        leftButtons.getChildren().addAll(newBtn, delBtn);
        left.setBottom(leftButtons);
        
        panel.setLeft(left);
        
        // Right: Tokens
        BorderPane right = new BorderPane();
        right.setPadding(new Insets(10));
        Label tokenTitle = new Label("Token Browser");
        tokenTitle.setStyle("-fx-font-weight: bold;");
        right.setTop(tokenTitle);
        BorderPane.setMargin(tokenTitle, new Insets(0, 0, 5, 0));
        
        HBox tokenSearchPanel = new HBox(5);
        tokenSearchPanel.setAlignment(Pos.CENTER_LEFT);
        tokenSearchPanel.getChildren().addAll(new Label(" Search: "), tokenSearchField);
        HBox.setHgrow(tokenSearchField, Priority.ALWAYS);
        
        VBox rightCenter = new VBox(5);
        rightCenter.getChildren().addAll(tokenSearchPanel, tokenList);
        VBox.setVgrow(tokenList, Priority.ALWAYS);
        right.setCenter(rightCenter);
        
        tokenList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        tokenList.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        tokenList.setTooltip(new Tooltip("Double-click to copy token"));
        
        // Center: Editor
        BorderPane center = new BorderPane();
        center.setPadding(new Insets(10));
        
        // Editor Header
        GridPane header = new GridPane();
        header.setHgap(10);
        header.setVgap(10);
        header.setPadding(new Insets(10));
        
        header.add(new Label("Recipe Name:"), 0, 0);
        header.add(recipeNameField, 1, 0);
        GridPane.setHgrow(recipeNameField, Priority.ALWAYS);
        
        header.add(new Label("JQL Query:"), 0, 1);
        header.add(jqlField, 1, 1);
        GridPane.setHgrow(jqlField, Priority.ALWAYS);
        
        header.add(new Label("Project Filter / Context Issue:"), 0, 2);
        header.add(contextIssueField, 1, 2);
        GridPane.setHgrow(contextIssueField, Priority.ALWAYS);
        header.add(fetchMetaBtn, 2, 2);
        contextIssueField.setTooltip(new Tooltip("Deep Sync: PROJ1, PROJ2 (Rebuild Filtered) or +PROJ1 (Incremental Add). Transition Meta: Issue Key."));

        syncProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        syncProgress.setVisible(false);
        header.add(syncProgress, 0, 3, 3, 1);

        Button saveBtn = new Button("Save Recipe");
        header.add(saveBtn, 2, 0);

        Button toggleTokensBtn = new Button("Toggle Tokens");
        header.add(toggleTokensBtn, 2, 1);
        
        center.setTop(header);
        
        // Editor Steps
        ScrollPane stepsScroll = new ScrollPane();
        stepsScroll.setContent(stepsContainer);
        stepsScroll.setFitToWidth(true);
        stepsContainer.setMinWidth(Region.USE_PREF_SIZE);
        center.setCenter(stepsScroll);
        BorderPane.setMargin(stepsScroll, new Insets(10, 0, 10, 0));
        
        // Editor Footer
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        Button addTransBtn = new Button("Add Transition");
        Button addUpdateBtn = new Button("Add Update");
        Button addCreateBtn = new Button("Add Create");
        Button addLinkBtn = new Button("Add Link");
        Button addAssetBtn = new Button("Add Asset (Links/Att/Sub)");
        Button addWorklogBtn = new Button("Add Worklog");
        footer.getChildren().addAll(addTransBtn, addUpdateBtn, addCreateBtn, addLinkBtn, addAssetBtn, addWorklogBtn);
        center.setBottom(footer);

        // Split Editor and Tokens
        SplitPane split = new SplitPane();
        split.getItems().addAll(center, right);
        split.setDividerPositions(0.75);
        
        panel.setCenter(split);
        
        toggleTokensBtn.setOnAction(e -> {
            if (split.getDividerPositions()[0] > 0.95) {
                split.setDividerPositions(0.75);
            } else {
                split.setDividerPositions(1.0);
            }
        });
        
        // Listeners
        recipeList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) loadRecipe(newVal);
        });
        
        newBtn.setOnAction(e -> clearEditor());
        delBtn.setOnAction(e -> deleteRecipe());
        saveBtn.setOnAction(e -> saveRecipe());
        fetchMetaBtn.setOnAction(e -> fetchLiveMetadata());
        
        addTransBtn.setOnAction(e -> addStep(new TransitionStep()));
        addUpdateBtn.setOnAction(e -> addStep(new UpdateStep()));
        addCreateBtn.setOnAction(e -> addStep(new CreateStep()));
        addLinkBtn.setOnAction(e -> addStep(new LinkStep()));
        addAssetBtn.setOnAction(e -> addStep(new AssetStep()));
        addWorklogBtn.setOnAction(e -> addStep(new WorklogStep()));

        tokenSearchField.textProperty().addListener((obs, oldVal, newVal) -> filterTokens());

        tokenList.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                String selected = tokenList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    int start = selected.indexOf("{{");
                    int end = selected.lastIndexOf("}}");
                    if (start >= 0 && end > start) {
                        String token = selected.substring(start, end + 2);
                        ClipboardContent content = new ClipboardContent();
                        content.putString(token);
                        Clipboard.getSystemClipboard().setContent(content);
                    }
                }
            }
        });
        
        return panel;
    }

    public void setRunnerIssueKey(String recipeName, String key) {
        Platform.runLater(() -> {
            mainTabs.getSelectionModel().select(1);
            runnerRecipeCombo.getSelectionModel().select(recipeName);
            runnerJqlField.setText(key);
            executeRunnerSearch();
        });
    }

    public void runWorkflowDirectly(String recipeName, String issueKeys) {
        Platform.runLater(() -> {
            mainFrame.showPanel("Workflow Orchestrator");
            mainTabs.getSelectionModel().select(1);
            runnerRecipeCombo.getSelectionModel().select(recipeName);
            runnerJqlField.setText(issueKeys);
            runnerLog.setText("");
            executeRunnerSearch();
        });

        ExecutionService.submit(() -> {
            try {
                WorkflowRecipe recipe = workflowManager.loadWorkflow(recipeName);
                if (recipe == null) {
                    String val = mainFrame.getJiraConfig().getProperty("workflow." + recipeName);
                    if (val != null) recipe = WorkflowRecipe.fromJson(val);
                }
                if (recipe == null) {
                    onLog("ERROR: Recipe not found: " + recipeName);
                    return;
                }

                String[] keys = issueKeys.split(",");
                List<JSONObject> issues = new ArrayList<>();
                for (String key : keys) {
                    String cleanKey = JiraUtils.cleanIssueKey(key.trim());
                    if (cleanKey.isEmpty()) continue;
                    
                    onLog("Fetching data for " + cleanKey + "...");
                    String searchUrl = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + cleanKey + "?expand=names,renderedFields&fields=*all,attachment,issuelinks";
                    String resp = mainFrame.getService().executeRequest(searchUrl, "GET", null);
                    issues.add(new JSONObject(resp));
                }

                WorkflowEngine engine = new WorkflowEngine(mainFrame.getService(), mainFrame.getIssueService(), mainFrame.getMetadataService(), mainFrame.getBaseUrl(), this);
                engine.setVerboseLogging(verboseLogCheck.isSelected());
                lastResults = engine.execute(recipe, issues, new HashMap<>());

            } catch (Exception e) {
                onError("Execution Error", e);
            }
        });
    }

    private Node createRunnerPanel() {
        BorderPane panel = new BorderPane();
        
        VBox top = new VBox(10);
        top.setPadding(new Insets(10));
        
        GridPane topGrid = new GridPane();
        topGrid.setHgap(10);
        topGrid.setVgap(10);

        topGrid.add(new Label("Select Recipe:"), 0, 0);
        topGrid.add(runnerRecipeCombo, 1, 0);
        GridPane.setHgrow(runnerRecipeCombo, Priority.ALWAYS);
        runnerRecipeCombo.setMaxWidth(Double.MAX_VALUE);
        
        HBox checkPanel = new HBox(10);
        checkPanel.setAlignment(Pos.CENTER_LEFT);
        checkPanel.getChildren().addAll(verboseLogCheck, dryRunCheck);
        topGrid.add(checkPanel, 2, 0);

        topGrid.add(new Label("JQL Query / Issue Key:"), 0, 1);
        topGrid.add(runnerJqlField, 1, 1);
        GridPane.setHgrow(runnerJqlField, Priority.ALWAYS);
        topGrid.add(searchBtn, 2, 1);

        runnerInputsPanel.setHgap(10);
        runnerInputsPanel.setVgap(5);
        
        top.getChildren().addAll(topGrid, runnerInputsPanel);
        panel.setTop(top);

        // Runner table setup
        TableColumn<RunnerIssueRow, String> keyCol = new TableColumn<>("Key");
        keyCol.setCellValueFactory(cellData -> cellData.getValue().keyProperty());
        keyCol.setPrefWidth(120);

        TableColumn<RunnerIssueRow, String> summaryCol = new TableColumn<>("Summary");
        summaryCol.setCellValueFactory(cellData -> cellData.getValue().summaryProperty());
        summaryCol.setPrefWidth(350);

        TableColumn<RunnerIssueRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusCol.setPrefWidth(120);

        TableColumn<RunnerIssueRow, String> assigneeCol = new TableColumn<>("Assignee");
        assigneeCol.setCellValueFactory(cellData -> cellData.getValue().assigneeProperty());
        assigneeCol.setPrefWidth(150);

        runnerTable.getColumns().addAll(keyCol, summaryCol, statusCol, assigneeCol);
        runnerTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        runnerTable.setRowFactory(tv -> {
            TableRow<RunnerIssueRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    RunnerIssueRow rowData = row.getItem();
                    JiraUtils.browseIssue(mainFrame.getBaseUrl(), rowData.getKey());
                }
            });
            return row;
        });

        SplitPane split = new SplitPane();
        split.setOrientation(Orientation.VERTICAL);
        split.getItems().addAll(runnerTable, runnerLog);
        split.setDividerPositions(0.4);
        
        runnerLog.setEditable(false);
        runnerLog.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        
        panel.setCenter(split);

        HBox bottom = new HBox(10);
        bottom.setPadding(new Insets(10));
        bottom.setAlignment(Pos.CENTER_RIGHT);
        
        runBtn.getStyleClass().add("primary-button");
        exportReportBtn.setDisable(true); // Enable after run
        Button clearLogBtn = new Button("Clear Log");
        
        bottom.getChildren().addAll(clearLogBtn, exportReportBtn, runBtn);
        panel.setBottom(bottom);

        searchBtn.setOnAction(e -> executeRunnerSearch());
        runBtn.setOnAction(e -> runWorkflowOnSelected());
        exportReportBtn.setOnAction(e -> exportToCsv());
        clearLogBtn.setOnAction(e -> runnerLog.setText(""));

        return panel;
    }

    private void executeRunnerSearch() {
        String finalJql = runnerJqlField.getText().trim();
        if (finalJql.isEmpty()) return;

        if (!finalJql.contains(" ") && !finalJql.contains("=") && !finalJql.contains("(")) {
            if (finalJql.contains(",")) {
                finalJql = "key in (" + finalJql + ")";
            } else {
                finalJql = "key = " + finalJql;
            }
        }

        final String jql = finalJql;
        onLog("Searching: " + jql);
        runnerTable.getItems().clear();
        currentSearchIssues.clear();

        ExecutionService.submit(() -> {
            try {
                String encodedJql = java.net.URLEncoder.encode(jql, "UTF-8");
                String searchUrl = mainFrame.getBaseUrl() + "/rest/api/2/search?jql=" + encodedJql + "&expand=names,renderedFields&fields=*all,attachment,issuelinks";
                String searchResp = mainFrame.getService().executeRequest(searchUrl, "GET", null);
                JSONArray issues = new JSONObject(searchResp).getJSONArray("issues");
                
                List<RunnerIssueRow> rows = new ArrayList<>();
                for (int i = 0; i < issues.length(); i++) {
                    JSONObject issue = issues.getJSONObject(i);
                    currentSearchIssues.add(issue);
                    JSONObject fields = issue.getJSONObject("fields");
                    String key = issue.getString("key");
                    String summary = fields.optString("summary", "N/A");
                    String status = fields.optJSONObject("status") != null ? fields.getJSONObject("status").getString("name") : "N/A";
                    String assignee = fields.optJSONObject("assignee") != null ? fields.getJSONObject("assignee").getString("displayName") : "Unassigned";
                    rows.add(new RunnerIssueRow(key, summary, status, assignee));
                }

                Platform.runLater(() -> {
                    runnerTable.getItems().setAll(rows);
                    onLog("Found " + issues.length() + " issues.");
                    
                    if (!rows.isEmpty()) {
                        runnerTable.getSelectionModel().selectAll();
                    }
                    
                    updateRunnerInputs();
                });
            } catch (Exception e) {
                onLog("Search Error: " + e.getMessage());
            }
        });
    }

    private void runWorkflowOnSelected() {
        List<RunnerIssueRow> selectedRows = runnerTable.getSelectionModel().getSelectedItems();
        if (selectedRows.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select one or more issues from the table first.");
            return;
        }

        String recipeName = runnerRecipeCombo.getSelectionModel().getSelectedItem();
        if (recipeName == null) return;

        Map<String, String> promptValues = new HashMap<>();
        // Initialize team variables to prevent holdover and handle missing fields
        promptValues.put("team.name", "");
        promptValues.put("team.lead", "");
        promptValues.put("team.component", "");
        promptValues.put("team.id", "");

        for (String label : promptFields.keySet()) {
            Node comp = promptFields.get(label);
            String val = "";
            if (comp instanceof TextField) {
                val = ((TextField) comp).getText();
            } else if (comp instanceof AutocompleteTextField) {
                val = ((AutocompleteTextField) comp).getText();
            } else if (comp instanceof ComboBox) {
                Object selected = ((ComboBox<?>) comp).getSelectionModel().getSelectedItem();
                if (selected instanceof ConfigOption) {
                    ConfigOption co = (ConfigOption) selected;
                    val = co.value;
                    if (co.teamKey != null) {
                        String name = mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "name");
                        String lead = mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "lead");
                        String component = mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "component");
                        String id = mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "id");
                        
                        promptValues.put("team.name", name != null ? name : "");
                        promptValues.put("team.lead", lead != null ? lead : "");
                        promptValues.put("team.component", component != null ? component : "");
                        promptValues.put("team.id", id != null ? id : "");
                    }
                } else if (selected != null) {
                    val = selected.toString();
                }
            } else if (comp instanceof ListView) {
                List<String> selectedValues = ((ListView<String>) comp).getSelectionModel().getSelectedItems();
                val = String.join(",", selectedValues);
            } else if (comp instanceof PromptChoicePanel) {
                val = ((PromptChoicePanel) comp).getValue();
            } else if (comp instanceof AssetOptionsPromptPanel) {
                val = ((AssetOptionsPromptPanel) comp).getValue();
            }
            promptValues.put(label, val);
        }

        List<JSONObject> issuesToProcess = new ArrayList<>();
        for (RunnerIssueRow row : selectedRows) {
            int idx = runnerTable.getItems().indexOf(row);
            if (idx >= 0 && idx < currentSearchIssues.size()) {
                issuesToProcess.add(currentSearchIssues.get(idx));
            }
        }

        runnerLog.setText("");
        runBtn.setDisable(true);
        exportReportBtn.setDisable(true);
        
        ExecutionService.submit(() -> {
            try {
                WorkflowRecipe recipe = workflowManager.loadWorkflow(recipeName);
                if (recipe == null) {
                    String val = mainFrame.getJiraConfig().getProperty("workflow." + recipeName);
                    if (val != null) recipe = WorkflowRecipe.fromJson(val);
                }
                if (recipe == null) {
                    onLog("ERROR: Recipe not found.");
                    onComplete();
                    return;
                }
                
                WorkflowEngine engine = new WorkflowEngine(mainFrame.getService(), mainFrame.getIssueService(), mainFrame.getMetadataService(), mainFrame.getBaseUrl(), this);
                engine.setVerboseLogging(verboseLogCheck.isSelected());
                engine.setDryRun(dryRunCheck.isSelected());
                lastResults = engine.execute(recipe, issuesToProcess, promptValues);

            } catch (Exception e) {
                onError("FATAL ERROR", e);
                onComplete();
            }
        });
    }

    private void exportToCsv() {
        if (lastResults == null || lastResults.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Results", "No results to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialFileName("workflow_report_" + System.currentTimeMillis() + ".csv");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files (*.csv)", "*.csv"));
        File file = fileChooser.showSaveDialog(mainFrame.getPrimaryStage());
        if (file != null) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("Issue Key,Status,Duration (ms),Log,Errors");
                for (WorkflowEngine.ExecutionResult res : lastResults) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(res.issueKey).append(",");
                    sb.append(res.status).append(",");
                    sb.append(res.durationMs).append(",");
                    sb.append("\"").append(String.join("; ", res.logEntries).replace("\"", "'")).append("\",");
                    sb.append("\"").append(String.join("; ", res.errors).replace("\"", "'")).append("\"");
                    pw.println(sb.toString());
                }
                showAlert(Alert.AlertType.INFORMATION, "Export Success", "Report exported to " + file.getAbsolutePath());
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Export Error", "Export Error: " + e.getMessage());
            }
        }
    }

    private void updateRunnerInputs() {
        String recipeName = runnerRecipeCombo.getSelectionModel().getSelectedItem();
        if (recipeName == null) return;
        
        runnerInputsPanel.getChildren().clear();
        promptFields.clear();
        
        try {
            WorkflowRecipe recipe = workflowManager.loadWorkflow(recipeName);
            if (recipe == null) {
                String val = mainFrame.getJiraConfig().getProperty("workflow." + recipeName);
                if (val != null) recipe = WorkflowRecipe.fromJson(val);
            }
            
            if (recipe != null) {
                if (runnerJqlField.getText().isEmpty()) runnerJqlField.setText(recipe.getJqlQuery());
                
                JSONObject contextIssue = null;
                RunnerIssueRow selected = runnerTable.getSelectionModel().getSelectedItem();
                int idx = selected != null ? runnerTable.getItems().indexOf(selected) : -1;
                if (idx >= 0 && idx < currentSearchIssues.size()) {
                    contextIssue = currentSearchIssues.get(idx);
                } else if (!currentSearchIssues.isEmpty()) {
                    contextIssue = currentSearchIssues.get(0);
                }

                Set<String> labels = new HashSet<>();
                for (WorkflowStep step : recipe.getSteps()) {
                    if (step instanceof CreateStep) {
                        CreateStep cs = (CreateStep) step;
                        addCreateStepPrompts(labels, cs, contextIssue);
                    }
                    
                    if (step instanceof AssetStep) {
                        AssetStep as = (AssetStep) step;
                        if (as.isPromptOptions()) {
                            String label = "Asset Options (" + step.getLabel() + ")";
                            AssetOptionsPromptPanel panel = new AssetOptionsPromptPanel(as.isCopyAttachments(), as.isCopyLinks(), as.isCopySubTasks());
                            addInputRow(label, panel, labels);
                            promptFields.put(label.replaceAll("\\[.*?\\]", "").trim(), panel);
                        }
                    }
                    
                    for (FieldAction fa : step.getFieldActions().values()) {
                        if (fa.getMode() == FieldAction.MappingMode.PROMPT) {
                            addDynamicPrompt(labels, fa.getPromptLabel(), fa.getValue() != null ? fa.getValue().toString() : null, fa.getFieldId(), contextIssue);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addInputRow(String label, Node input, Set<String> labels) {
        String cleanLabel = label.replaceAll("\\[.*?\\]", "").trim();
        if (labels.contains(cleanLabel)) return;
        labels.add(cleanLabel);

        int rowCount = runnerInputsPanel.getChildren().size() / 2;
        
        Label labelNode = new Label(cleanLabel + ":");
        labelNode.setAlignment(Pos.CENTER_RIGHT);
        
        runnerInputsPanel.add(labelNode, 0, rowCount);
        runnerInputsPanel.add(input, 1, rowCount);
        GridPane.setHgrow(input, Priority.ALWAYS);
    }

    private void addDynamicPrompt(Set<String> labels, String label, String value, String fieldId, JSONObject contextIssue) {
        if (label == null || label.trim().isEmpty()) return;
        String cleanLabel = label.replaceAll("\\[.*?\\]", "").trim();
        if (labels.contains(cleanLabel)) return;

        Node input = createPromptInput(label, value, fieldId, contextIssue);
        
        addInputRow(label, input, labels);
        promptFields.put(cleanLabel, input);
    }

    private JSONObject findFieldMeta(String fieldId, JSONObject contextIssue) {
        if (fieldId == null) return null;

        if (contextIssue != null && contextIssue.has("fields")) {
            JSONObject fields = contextIssue.getJSONObject("fields");
            JSONObject project = fields.optJSONObject("project");
            JSONObject issueType = fields.optJSONObject("issuetype");
            if (project != null && issueType != null) {
                String pKey = project.optString("key");
                String tName = issueType.optString("name");
                String scopedKey = "createmeta:" + pKey + ":" + tName;
                
                if (cachedFullMeta.containsKey(scopedKey)) {
                    JSONObject scopedMeta = cachedFullMeta.get(scopedKey);
                    if (scopedMeta.has("values")) {
                        JSONArray values = scopedMeta.getJSONArray("values");
                        for (int i = 0; i < values.length(); i++) {
                            JSONObject f = values.getJSONObject(i);
                            if (fieldId.equals(f.optString("fieldId"))) {
                                return f;
                            }
                        }
                    }
                }
            }
        }

        return cachedFullMeta.get(fieldId);
    }

    private Node createPromptInput(String label, String staticOptions, String fieldId, JSONObject contextIssue) {
        Node result = null;
        String effectiveFieldId = fieldId;

        Set<String> mergedOptions = new TreeSet<>();
        boolean isArray = false;
        
        if (fieldId != null) {
            for (String key : cachedFullMeta.keySet()) {
                if (key.startsWith("createmeta:")) {
                    JSONObject scopedMeta = cachedFullMeta.get(key);
                    if (scopedMeta.has("values")) {
                        JSONArray values = scopedMeta.getJSONArray("values");
                        for (int i = 0; i < values.length(); i++) {
                            JSONObject f = values.getJSONObject(i);
                            if (fieldId.equals(f.optString("fieldId"))) {
                                if (f.has("allowedValues")) {
                                    JSONArray allowed = f.getJSONArray("allowedValues");
                                    for (int j = 0; j < allowed.length(); j++) {
                                        JSONObject av = allowed.getJSONObject(j);
                                        mergedOptions.add(av.optString("name", av.optString("value", "")));
                                    }
                                }
                                if (f.has("schema") && "array".equals(f.getJSONObject("schema").optString("type"))) {
                                    isArray = true;
                                }
                            }
                        }
                    }
                }
            }

            JSONObject globalMeta = cachedFullMeta.get(fieldId);
            if (globalMeta != null) {
                if (globalMeta.has("allowedValues")) {
                    JSONArray allowed = globalMeta.getJSONArray("allowedValues");
                    for (int i = 0; i < allowed.length(); i++) {
                        JSONObject av = allowed.getJSONObject(i);
                        mergedOptions.add(av.optString("name", av.optString("value", "")));
                    }
                }
                if (globalMeta.has("schema") && "array".equals(globalMeta.getJSONObject("schema").optString("type"))) {
                    isArray = true;
                }
            }
        }

        List<String> customOpts = resolveAndSplitOptions(staticOptions, contextIssue);
        if (customOpts.size() > 1) {
            if (isArray) {
                ListView<String> list = new ListView<>();
                list.getItems().addAll(customOpts);
                list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                list.setPrefHeight(Math.min(customOpts.size() * 24 + 4, 100));
                result = list;
            } else {
                ComboBox<String> combo = new ComboBox<>();
                combo.getItems().addAll(customOpts);
                combo.getSelectionModel().select(0);
                combo.setPrefWidth(200);
                result = combo;
            }
        }

        if (result == null && !mergedOptions.isEmpty()) {
            List<String> options = new ArrayList<>(mergedOptions);
            if (isArray) {
                ListView<String> list = new ListView<>();
                list.getItems().addAll(options);
                list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                list.setPrefHeight(Math.min(options.size() * 24 + 4, 100));
                result = list;
            } else {
                AutocompleteTextField atf = new AutocompleteTextField();
                atf.setPrefWidth(200);
                atf.setSuggestions(options);
                atf.setAutocompleteEnabled(true);
                String resolvedValue = staticOptions;
                if (contextIssue != null && staticOptions != null && staticOptions.contains("{{")) {
                    resolvedValue = TokenEngine.replaceTokens(staticOptions, contextIssue);
                }
                if (resolvedValue != null) atf.setText(resolvedValue);
                result = atf;
            }
        }
        
        if (result == null) {
            String tagSource = null;
            if (staticOptions != null && (staticOptions.contains("[config:") || staticOptions.contains("[choice:") || staticOptions.contains("[allowed:"))) tagSource = staticOptions;
            else if (label != null && (label.contains("[config:") || label.contains("[choice:") || label.contains("[allowed:"))) tagSource = label;

            if (tagSource != null) {
                try {
                    if (tagSource.contains("[allowed:")) {
                        int start = tagSource.indexOf("[allowed:") + 9;
                        int end = tagSource.indexOf("]", start);
                        if (end > start) {
                            String taggedFieldId = tagSource.substring(start, end).trim();
                            effectiveFieldId = taggedFieldId;
                            
                            Set<String> taggedOptions = new TreeSet<>();
                            boolean tIsArray = false;
                            for (String key : cachedFullMeta.keySet()) {
                                if (key.startsWith("createmeta:")) {
                                    JSONObject scopedMeta = cachedFullMeta.get(key);
                                    if (scopedMeta.has("values")) {
                                        JSONArray values = scopedMeta.getJSONArray("values");
                                        for (int i = 0; i < values.length(); i++) {
                                            JSONObject f = values.getJSONObject(i);
                                            if (taggedFieldId.equals(f.optString("fieldId"))) {
                                                if (f.has("allowedValues")) {
                                                    JSONArray allowed = f.getJSONArray("allowedValues");
                                                    for (int j = 0; j < allowed.length(); j++) {
                                                        JSONObject av = allowed.getJSONObject(j);
                                                        taggedOptions.add(av.optString("name", av.optString("value", "")));
                                                    }
                                                }
                                                if (f.has("schema") && "array".equals(f.getJSONObject("schema").optString("type"))) tIsArray = true;
                                            }
                                        }
                                    }
                                }
                            }
                            JSONObject tGlobal = cachedFullMeta.get(taggedFieldId);
                            if (tGlobal != null) {
                                if (tGlobal.has("allowedValues")) {
                                    JSONArray allowed = tGlobal.getJSONArray("allowedValues");
                                    for (int i = 0; i < allowed.length(); i++) {
                                        JSONObject av = allowed.getJSONObject(i);
                                        taggedOptions.add(av.optString("name", av.optString("value", "")));
                                    }
                                }
                                if (tGlobal.has("schema") && "array".equals(tGlobal.getJSONObject("schema").optString("type"))) tIsArray = true;
                            }

                            if (!taggedOptions.isEmpty()) {
                                List<String> options = new ArrayList<>(taggedOptions);
                                if (tIsArray) {
                                    ListView<String> list = new ListView<>();
                                    list.getItems().addAll(options);
                                    list.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                                    list.setPrefHeight(Math.min(options.size() * 24 + 4, 100));
                                    result = list;
                                } else {
                                    AutocompleteTextField atf = new AutocompleteTextField();
                                    atf.setPrefWidth(200);
                                    atf.setSuggestions(options);
                                    atf.setAutocompleteEnabled(mainFrame.getJiraConfig().isAutocompleteEnabled());
                                    result = atf;
                                }
                            }
                        }
                    }

                    if (result == null && tagSource.contains("[choice:")) {
                        int start = tagSource.indexOf("[choice:") + 8;
                        int end = tagSource.lastIndexOf("]");
                        if (end > start) {
                            String tokenExpr = tagSource.substring(start, end);
                            String resolved = tokenExpr;
                            if (contextIssue != null) resolved = TokenEngine.replaceTokens(tokenExpr, contextIssue);
                            result = new PromptChoicePanel(tokenExpr, resolved);
                        }
                    }

                    if (result == null && tagSource.contains("[config:")) {
                        int start = tagSource.indexOf("[config:") + 8;
                        int end = tagSource.lastIndexOf("]");
                        if (end > start) {
                            String tag = tagSource.substring(start, end);
                            String[] parts = tag.split(":");
                            String key = parts[0];
                            
                            if (key.equals("teams")) {
                                String subKey = parts.length > 1 ? parts[1] : "lead";
                                List<ConfigOption> options = new ArrayList<>();
                                String[] teamKeys = mainFrame.getJiraConfig().getWorkflowTeamKeys();
                                for (String tKey : teamKeys) {
                                    String name = mainFrame.getJiraConfig().getTeamProperty(tKey, "name");
                                    String val = mainFrame.getJiraConfig().getTeamProperty(tKey, subKey);
                                    if (name != null && val != null) options.add(new ConfigOption(name, val, tKey));
                                }
                                ComboBox<ConfigOption> combo = new ComboBox<>();
                                combo.getItems().addAll(options);
                                combo.setPrefWidth(200);
                                if (!options.isEmpty()) combo.getSelectionModel().select(0);
                                result = combo;
                            } else if (key.equals("fy_summary")) {
                                ComboBox<String> combo = new ComboBox<>();
                                combo.getItems().add(mainFrame.getJiraConfig().getWorkflowFySummaryIssue());
                                combo.getSelectionModel().select(0);
                                combo.setPrefWidth(200);
                                result = combo;
                            } else {
                                String val = mainFrame.getJiraConfig().getProperty(key);
                                if (val != null) {
                                    if (val.contains(",")) {
                                        String[] opts = smartSplit(val);
                                        if (contextIssue != null) {
                                            for (int i = 0; i < opts.length; i++) {
                                                opts[i] = TokenEngine.replaceTokens(opts[i], contextIssue);
                                            }
                                        }
                                        ComboBox<String> combo = new ComboBox<>();
                                        combo.getItems().addAll(opts);
                                        combo.getSelectionModel().select(0);
                                        combo.setPrefWidth(200);
                                        result = combo;
                                    } else {
                                        String resolved = val;
                                        if (contextIssue != null) resolved = TokenEngine.replaceTokens(val, contextIssue);
                                        TextField tf = new TextField(resolved);
                                        tf.setPrefWidth(200);
                                        result = tf;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        
        
        
        if (result == null) {
            String resolvedValue = staticOptions;
            if (contextIssue != null && staticOptions != null && staticOptions.contains("{{")) {
                resolvedValue = TokenEngine.replaceTokens(staticOptions, contextIssue);
            }
            TextField tf = new TextField(resolvedValue != null ? resolvedValue : "");
            tf.setPrefWidth(200);
            result = tf;
        }

        // Apply Debug Tooltip
        StringBuilder debug = new StringBuilder("Runner Debug Info:\n");
        debug.append("Field ID: ").append(effectiveFieldId != null ? effectiveFieldId : "None").append("\n");
        JSONObject dMeta = findFieldMeta(effectiveFieldId, contextIssue);
        if (effectiveFieldId != null && dMeta != null) {
            debug.append("Name: ").append(dMeta.optString("name", "N/A")).append("\n");
            debug.append("Has allowedValues: ").append(dMeta.has("allowedValues")).append("\n");
            if (dMeta.has("allowedValues")) {
                debug.append("Count: ").append(dMeta.getJSONArray("allowedValues").length()).append("\n");
            }
            debug.append("Schema Type: ").append(dMeta.has("schema") ? dMeta.getJSONObject("schema").optString("type") : "N/A").append("\n");
        } else if (effectiveFieldId != null) {
            debug.append("Not found in metadata cache.");
        }
        
        Tooltip tooltip = new Tooltip(debug.toString());
        if (result instanceof Control) {
            ((Control) result).setTooltip(tooltip);
        }
        if (result instanceof AutocompleteTextField) {
            ((AutocompleteTextField) result).getTextField().setTooltip(tooltip);
        }

        return result;
    }

    private String[] smartSplit(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '{' && i + 1 < input.length() && input.charAt(i + 1) == '{') {
                braceDepth++;
                current.append("{{");
                i++;
            } else if (c == '}' && i + 1 < input.length() && input.charAt(i + 1) == '}') {
                braceDepth--;
                current.append("}}");
                i++;
            } else if (c == ',' && braceDepth == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    private void addCreateStepPrompts(Set<String> labels, CreateStep cs, JSONObject contextIssue) {
        String projVal = cs.getProjectKey();
        List<String> projOpts = resolveAndSplitOptions(projVal, contextIssue);
        if (projOpts.size() > 1) {
            addDynamicPrompt(labels, "Project (" + cs.getLabel() + ")", projVal, "project", contextIssue);
        }
        
        String typeVal = cs.getIssueType();
        List<String> typeOpts = resolveAndSplitOptions(typeVal, contextIssue);
        if (typeOpts.size() > 1) {
            addDynamicPrompt(labels, "Issue Type (" + cs.getLabel() + ")", typeVal, "issuetype", contextIssue);
        }
    }

    private List<String> resolveAndSplitOptions(String staticOptions, JSONObject contextIssue) {
        if (staticOptions == null || staticOptions.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String resolved = staticOptions;
        if (contextIssue != null && staticOptions.contains("{{")) {
            resolved = TokenEngine.replaceTokens(staticOptions, contextIssue);
        }
        
        String[] parts = smartSplit(resolved);
        Set<String> uniqueOpts = new LinkedHashSet<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                uniqueOpts.add(trimmed);
            }
        }
        return new ArrayList<>(uniqueOpts);
    }

    private static class ConfigOption {
        String label, value, teamKey;
        ConfigOption(String l, String v, String tk) { this.label = l; this.value = v; this.teamKey = tk; }
        @Override public String toString() { return label; }
    }

    private static class PromptChoicePanel extends HBox {
        private final RadioButton tokenRadio;
        private final RadioButton manualRadio;
        private final TextField manualField;
        private final String tokenValue;

        PromptChoicePanel(String tokenName, String resolvedValue) {
            setSpacing(10);
            setAlignment(Pos.CENTER_LEFT);
            this.tokenValue = resolvedValue;
            
            String displayText = resolvedValue;
            if (resolvedValue.equals(tokenName)) {
                displayText = tokenName;
            } else {
                displayText = resolvedValue + " (" + tokenName + ")";
            }
            
            tokenRadio = new RadioButton(displayText);
            tokenRadio.setSelected(true);
            manualRadio = new RadioButton("Manual:");
            manualField = new TextField();
            manualField.setPrefWidth(150);
            manualField.setDisable(true);

            ToggleGroup group = new ToggleGroup();
            tokenRadio.setToggleGroup(group);
            manualRadio.setToggleGroup(group);

            getChildren().addAll(tokenRadio, manualRadio, manualField);

            manualRadio.setOnAction(e -> { manualField.setDisable(false); manualField.requestFocus(); });
            tokenRadio.setOnAction(e -> manualField.setDisable(true));
            
            manualField.setOnMouseClicked(e -> {
                manualRadio.setSelected(true);
                manualField.setDisable(false);
            });
        }

        public String getValue() {
            return tokenRadio.isSelected() ? tokenValue : manualField.getText();
        }
    }

    private static class AssetOptionsPromptPanel extends HBox {
        private final CheckBox att, links, sub;
        AssetOptionsPromptPanel(boolean a, boolean l, boolean s) {
            setSpacing(10);
            setAlignment(Pos.CENTER_LEFT);
            att = new CheckBox("Attachments");
            att.setSelected(a);
            links = new CheckBox("Links");
            links.setSelected(l);
            sub = new CheckBox("Sub-tasks");
            sub.setSelected(s);
            getChildren().addAll(att, links, sub);
        }
        public String getValue() {
            return att.isSelected() + "," + links.isSelected() + "," + sub.isSelected();
        }
    }

    private void filterTokens() {
        String filter = tokenSearchField.getText().toLowerCase();
        tokenList.getItems().clear();
        for (String t : allTokens) {
            if (t.toLowerCase().contains(filter)) tokenList.getItems().add(t);
        }
    }

    private void fetchLiveMetadata() {
        String filterText = contextIssueField.getText().trim();
        boolean isIncremental = filterText.startsWith("+");
        boolean isFiltered = !filterText.isEmpty();
        
        final List<String> targetProjects = new ArrayList<>();
        if (isFiltered) {
            String cleanFilter = isIncremental ? filterText.substring(1) : filterText;
            String[] parts = cleanFilter.split("\\s*,\\s*");
            for (String p : parts) {
                String pKey = p.trim();
                if (pKey.startsWith("+")) pKey = pKey.substring(1);
                if (!pKey.isEmpty()) targetProjects.add(pKey);
            }
        }

        String msg;
        if (!isFiltered) {
            msg = "Deep Sync will CLEAN and REBUILD the metadata cache for ALL projects.";
        } else if (isIncremental) {
            msg = "Deep Sync will ADD/UPDATE metadata for these projects: " + targetProjects + "\n(Existing cache will be preserved)";
        } else {
            msg = "Deep Sync will CLEAN and REBUILD metadata for ONLY these projects: " + targetProjects;
        }
            
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Rebuild Global Metadata Cache");
        alert.setHeaderText(null);
        alert.setContentText(msg + "\n\nContinue?");
        Optional<ButtonType> result = alert.showAndWait();
        
        if (!result.isPresent() || result.get() != ButtonType.OK) return;

        fetchMetaBtn.setDisable(true);
        fetchMetaBtn.setText("Syncing...");
        syncProgress.setVisible(true);

        ExecutionService.submit(() -> {
            try {
                MetadataCacheService helper = mainFrame.getMetadataService();
                
                if (!isIncremental) {
                    helper.clearCache(); // FORCE fresh API calls
                    cachedFullMeta.clear(); 
                    cachedFieldOptions.clear();
                    onLog("--- Starting Fresh " + (isFiltered ? "Filtered" : "Global") + " Sync ---");
                } else {
                    onLog("--- Starting Incremental Sync for: " + targetProjects + " ---");
                }

                List<String> projects;
                if (isFiltered) {
                    projects = targetProjects;
                } else {
                    projects = helper.getProjectKeys();
                    onLog("Found " + projects.size() + " total projects.");
                }

                for (String pKey : projects) {
                    if (pKey.isEmpty()) continue;
                    try {
                        onLog("Syncing Project: " + pKey);
                        List<JSONObject> types = helper.getIssueTypesForProject(pKey);
                        onLog("  > Found " + types.size() + " issue types in " + pKey);
                        
                        for (JSONObject type : types) {
                            String typeName = type.getString("name");
                            onLog("    > Fetching metadata for type: " + typeName);
                            Map<String, JSONObject> typeMeta = helper.getCreateMetadata(pKey, typeName);
                            cachedFullMeta.putAll(typeMeta);
                        }
                    } catch (Exception e) {
                        onLog("  ! Project Sync Error (" + pKey + "): " + e.getMessage());
                    }
                }

                try {
                    onLog("Syncing Global Link Types...");
                    List<JSONObject> links = helper.getIssueLinkTypes();
                    for (JSONObject lt : links) {
                        cachedFullMeta.put("linktype:" + lt.getString("name"), lt);
                    }
                } catch (Exception e) { onLog("  ! Link Type Sync Error: " + e.getMessage()); }

                try {
                    onLog("Syncing Global Field Definitions...");
                    List<JSONObject> fields = helper.getAllFields();
                    for (JSONObject f : fields) {
                        String fId = f.getString("id");
                        if (!cachedFullMeta.containsKey(fId)) {
                            cachedFullMeta.put(fId, f);
                        }
                    }
                } catch (Exception e) { onLog("  ! Global Field Sync Error: " + e.getMessage()); }

                Platform.runLater(() -> {
                    updateTokensFromCache();
                    for (Node c : stepsContainer.getChildren()) {
                        if (c instanceof StepEditorPanel) {
                            StepEditorPanel sep = (StepEditorPanel) c;
                            sep.refreshMetadata(cachedFieldOptions, cachedFullMeta);
                            sep.updateLinkTypes(cachedLinkTypes);
                        }
                    }
                    onLog("--- Deep Sync Complete ---");
                    fetchMetaBtn.setDisable(false);
                    fetchMetaBtn.setText("Fetch Metadata");
                    syncProgress.setVisible(false);
                    showAlert(Alert.AlertType.INFORMATION, "Sync Complete", "Metadata Sync Complete!\nTotal Fields: " + cachedFullMeta.size());
                });
            } catch (Exception ex) {
                onLog("CRITICAL METADATA ERROR: " + ex.getMessage());
                Platform.runLater(() -> {
                    fetchMetaBtn.setDisable(false);
                    fetchMetaBtn.setText("Fetch Metadata");
                    syncProgress.setVisible(false);
                    showAlert(Alert.AlertType.ERROR, "Sync Error", "Metadata error: " + ex.getMessage());
                });
            }
        });
    }

    private void addStep(WorkflowStep step) {
        step.setLabel("New " + step.getType() + " Step");
        addStepUI(step);
    }

    private void addStepUI(WorkflowStep step) {
        StepEditorPanel panel = new StepEditorPanel(step, cachedFieldOptions, cachedFullMeta, () -> {
            stepsContainer.getChildren().remove(getStepPanel(step));
        }, new StepEditorPanel.StepActionListener() {
            @Override
            public void onMoveUp(StepEditorPanel p) {
                int idx = stepsContainer.getChildren().indexOf(p);
                if (idx > 0) {
                    stepsContainer.getChildren().remove(p);
                    stepsContainer.getChildren().add(idx - 1, p);
                }
            }

            @Override
            public void onMoveDown(StepEditorPanel p) {
                int idx = stepsContainer.getChildren().indexOf(p);
                if (idx >= 0 && idx < stepsContainer.getChildren().size() - 1) {
                    stepsContainer.getChildren().remove(p);
                    stepsContainer.getChildren().add(idx + 1, p);
                }
            }
        }, new StepEditorPanel.StepMetadataListener() {
            @Override
            public void onFetchTransitionFields(TransitionStep step) {
                fetchTransitionMetadata(step);
            }

            @Override
            public void onFetchCreateFields(CreateStep step) {
                fetchCreateMetadata(step);
            }
        });
        panel.updateLinkTypes(cachedLinkTypes);

        // Drag-and-drop to rearrange steps
        panel.getHeader().setOnMouseEntered(e -> {
            panel.getHeader().setCursor(javafx.scene.Cursor.MOVE);
        });

        panel.getHeader().setOnDragDetected(e -> {
            // Ignore drags initiated on interactive input controls
            Node target = (Node) e.getTarget();
            while (target != null && target != panel.getHeader()) {
                if (target instanceof Button || target instanceof TextInputControl || target instanceof ComboBoxBase || target instanceof CheckBox) {
                    return;
                }
                target = target.getParent();
            }
            
            Dragboard db = panel.getHeader().startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("dragged_step");
            db.setContent(content);
            activeDraggedPanel = panel;
            panel.setOpacity(0.5);
            panel.setStyle("-fx-border-color: #3b82f6; -fx-border-style: dashed; -fx-border-width: 2px; -fx-background-color: rgba(59, 130, 246, 0.08); -fx-padding: 5px; -fx-background-radius: 4px; -fx-border-radius: 4px;");
            e.consume();
        });

        panel.setOnDragOver(e -> {
            if (e.getDragboard().hasString() && "dragged_step".equals(e.getDragboard().getString())) {
                if (activeDraggedPanel != null && activeDraggedPanel != panel) {
                    int activeIdx = stepsContainer.getChildren().indexOf(activeDraggedPanel);
                    int targetIdx = stepsContainer.getChildren().indexOf(panel);
                    if (activeIdx >= 0 && targetIdx >= 0) {
                        stepsContainer.getChildren().remove(activeDraggedPanel);
                        stepsContainer.getChildren().add(targetIdx, activeDraggedPanel);
                    }
                }
                e.acceptTransferModes(TransferMode.MOVE);
            }
            e.consume();
        });

        panel.getHeader().setOnDragDone(e -> {
            if (activeDraggedPanel != null) {
                activeDraggedPanel.setOpacity(1.0);
                activeDraggedPanel.setStyle("-fx-border-color: gray; -fx-border-width: 1px; -fx-padding: 5px;");
                activeDraggedPanel = null;
            }
            e.consume();
        });

        stepsContainer.getChildren().add(panel);
    }

    private void fetchCreateMetadata(CreateStep step) {
        String pKey = step.getProjectKey();
        String iType = step.getIssueType();
        if (pKey == null || pKey.isEmpty() || iType == null || iType.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Data", "Please provide both Project Key and Issue Type.");
            return;
        }

        String cacheKey = "createmeta:" + pKey + ":" + iType;
        if (cachedFullMeta.containsKey(cacheKey)) {
            JSONObject cached = cachedFullMeta.get(cacheKey);
            if (cached.has("values")) {
                JSONArray values = cached.getJSONArray("values");
                Map<String, JSONObject> meta = new HashMap<>();
                for (int i = 0; i < values.length(); i++) {
                    JSONObject f = values.getJSONObject(i);
                    meta.put(f.getString("fieldId"), f);
                }
                applyCreateMetadata(step, meta);
                return;
            }
        }

        ExecutionService.submit(() -> {
            try {
                MetadataCacheService helper = mainFrame.getMetadataService();
                Map<String, JSONObject> meta = helper.getCreateMetadata(pKey, iType);
                if (meta.isEmpty()) {
                    Platform.runLater(() -> showAlert(Alert.AlertType.INFORMATION, "No Metadata", "No metadata found for " + pKey + " / " + iType));
                    return;
                }

                for (String fId : meta.keySet()) {
                    cachedFullMeta.put(fId, meta.get(fId));
                }
                mainFrame.getMetadataService().updateDiskCache(meta);

                Platform.runLater(() -> applyCreateMetadata(step, meta));
            } catch (Exception ex) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Metadata Error", "Create Meta Error: " + ex.getMessage()));
            }
        });
    }

    private void applyCreateMetadata(CreateStep step, Map<String, JSONObject> meta) {
        updateTokensFromCache();
        Node cp = getStepPanel(step);
        if (cp instanceof StepEditorPanel) {
            StepEditorPanel sep = (StepEditorPanel) cp;
            sep.refreshMetadata(cachedFieldOptions, cachedFullMeta);
            
            int addedCount = 0;
            for (String fId : meta.keySet()) {
                JSONObject fMeta = meta.get(fId);
                if (fMeta.optBoolean("required", false)) {
                    if (fId.equals("project") || fId.equals("issuetype")) continue;
                    if (!step.getFieldActions().containsKey(fId)) {
                        sep.addField(new FieldAction(fId, FieldAction.MappingMode.SET, "", ""));
                        addedCount++;
                    }
                }
            }
            showAlert(Alert.AlertType.INFORMATION, "Metadata Applied", "Fetched " + meta.size() + " fields. Added " + addedCount + " required fields.");
        }
    }

    private void fetchTransitionMetadata(TransitionStep step) {
        String filterText = contextIssueField.getText().trim();
        String targetStatus = step.getTargetStatus();
        
        if (filterText.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Context Missing", "Please provide a Context Issue Key (for live API) or Project Key (for cache) in the Filter field.");
            return;
        }

        if (filterText.contains("-")) {
            fetchLiveTransitionMetadata(step, filterText);
        } else {
            String[] projects = filterText.split("\\s*,\\s*");
            for (String p : projects) {
                for (String cKey : cachedFullMeta.keySet()) {
                    if (cKey.startsWith("trans:" + p + ":") && cKey.endsWith(":" + targetStatus)) {
                        JSONObject transMeta = cachedFullMeta.get(cKey);
                        if (transMeta.has("fields")) {
                            JSONObject fieldsJson = transMeta.getJSONObject("fields");
                            Map<String, JSONObject> meta = new HashMap<>();
                            for (String fId : fieldsJson.keySet()) {
                                meta.put(fId, fieldsJson.getJSONObject(fId));
                            }
                            applyTransitionMetadata(step, meta);
                            return;
                        }
                    }
                }
            }
            showAlert(Alert.AlertType.WARNING, "No Metadata", "No cached transition metadata found for '" + targetStatus + "' in projects: " + filterText);
        }
    }

    private void fetchLiveTransitionMetadata(TransitionStep step, String issueKey) {
        ExecutionService.submit(() -> {
            try {
                MetadataCacheService helper = mainFrame.getMetadataService();
                List<JSONObject> trans = helper.getTransitions(issueKey);
                JSONObject match = null;
                for (JSONObject t : trans) {
                    if (t.getString("name").equalsIgnoreCase(step.getTargetStatus())) {
                        match = t; break;
                    }
                }
                
                if (match == null) {
                    Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Not Found", "Transition '" + step.getTargetStatus() + "' not found on issue " + issueKey));
                    return;
                }
                
                Map<String, JSONObject> meta = new HashMap<>();
                if (match.has("fields")) {
                    JSONObject fieldsJson = match.getJSONObject("fields");
                    for (String fId : fieldsJson.keySet()) {
                        meta.put(fId, fieldsJson.getJSONObject(fId));
                    }
                }

                for (String fId : meta.keySet()) {
                    cachedFullMeta.put(fId, meta.get(fId));
                }
                mainFrame.getMetadataService().updateDiskCache(meta);
                
                Platform.runLater(() -> applyTransitionMetadata(step, meta));
            } catch (Exception ex) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Transition Error", "Transition Meta Error: " + ex.getMessage()));
            }
        });
    }

    private void applyTransitionMetadata(TransitionStep step, Map<String, JSONObject> meta) {
        Node cp = getStepPanel(step);
        if (cp instanceof StepEditorPanel) {
            ((StepEditorPanel) cp).refreshMetadata(cachedFieldOptions, cachedFullMeta);
        }
        showAlert(Alert.AlertType.INFORMATION, "Metadata Applied", "Fetched " + meta.size() + " fields for transition '" + step.getTargetStatus() + "'");
    }

    private Node getStepPanel(WorkflowStep step) {
        for (Node c : stepsContainer.getChildren()) {
            if (c instanceof StepEditorPanel && ((StepEditorPanel)c).getStep() == step) return c;
        }
        return null;
    }

    private void loadRecipe(String name) {
        if (name == null) return;
        try {
            WorkflowRecipe recipe = workflowManager.loadWorkflow(name);
            if (recipe == null) {
                String key = "workflow." + name;
                String val = mainFrame.getJiraConfig().getProperty(key);
                if (val != null) recipe = WorkflowRecipe.fromJson(val);
            }

            if (recipe != null) {
                recipeNameField.setText(recipe.getRecipeName());
                jqlField.setText(recipe.getJqlQuery());
                stepsContainer.getChildren().clear();
                
                if (recipe.getMetadataSnapshot() != null) {
                    JSONObject snap = recipe.getMetadataSnapshot();
                    for (String key : snap.keySet()) {
                        cachedFullMeta.put(key, snap.getJSONObject(key));
                    }
                    updateTokensFromCache();
                }

                for (WorkflowStep step : recipe.getSteps()) {
                    addStepUI(step);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshRecipeList() {
        Set<String> names = new TreeSet<>(workflowManager.listWorkflows());
        String[] configRecipes = mainFrame.getJiraConfig().getWorkflowRecipeKeys();
        if (configRecipes != null) {
            names.addAll(Arrays.asList(configRecipes));
        }

        recipeList.getItems().clear();
        runnerRecipeCombo.getItems().clear();
        for (String name : names) {
            recipeList.getItems().add(name);
            runnerRecipeCombo.getItems().add(name);
        }
        if (!names.isEmpty()) {
            runnerRecipeCombo.getSelectionModel().select(0);
        }
        updateRunnerInputs();
    }

    private void saveRecipe() {
        String name = recipeNameField.getText().trim();
        if (name.isEmpty()) return;
        WorkflowRecipe recipe = new WorkflowRecipe();
        recipe.setRecipeName(name);
        recipe.setJqlQuery(jqlField.getText());
        for (Node c : stepsContainer.getChildren()) {
            if (c instanceof StepEditorPanel) {
                ((StepEditorPanel) c).saveToStep();
                recipe.addStep(((StepEditorPanel) c).getStep());
            }
        }

        try {
            workflowManager.saveWorkflow(recipe);
            refreshRecipeList();
            showAlert(Alert.AlertType.INFORMATION, "Saved", "Recipe saved!");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Save Error", "Error saving: " + e.getMessage());
        }
    }

    private void clearEditor() {
        recipeNameField.setText("");
        jqlField.setText("");
        stepsContainer.getChildren().clear();
        recipeList.getSelectionModel().clearSelection();
    }

    private void deleteRecipe() {
        String selected = recipeList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        workflowManager.deleteWorkflow(selected);
        refreshRecipeList();
        clearEditor();
    }

    private void updateTokensFromCache() {
        cachedFieldOptions.clear();
        cachedLinkTypes.clear();
        List<String> tokens = new ArrayList<>();
        tokens.add("Current Issue Key ({{issue.key}})"); tokens.add("Current Summary ({{issue.fields.summary}})");
        tokens.add("Current Parent Key ({{issue.fields.parent.key}})"); tokens.add("Current Timestamp ({{now}})");
        tokens.add("Current Date ({{today}})"); tokens.add("Last Created/Mod Key ({{last.key}})");
        tokens.add("Smart Key Fallback ({{COALESCE(last.key, issue.key)}})"); tokens.add("Last Created/Mod ID ({{last.id}})");
        tokens.add("Selected Team Name ({{team.name}})"); tokens.add("Selected Team Lead ({{team.lead}})");
        tokens.add("Selected Team Component ({{team.component}})"); tokens.add("Selected Team ID ({{team.id}})");
        
        for (String key : cachedFullMeta.keySet()) {
            if (key.startsWith("linktype:")) { cachedLinkTypes.add(key.substring(9)); continue; }
            if (key.startsWith("trans:") || key.startsWith("createmeta:") || key.startsWith("editmeta:") ||
                key.equals("fields:all") || key.equals("linktypes:all") || key.equals("projects:all")) continue;
            JSONObject fieldObj = cachedFullMeta.get(key);
            if (fieldObj == null) continue;
            if (fieldObj.has("inward") && fieldObj.has("outward") && fieldObj.has("name")) { cachedLinkTypes.add(fieldObj.getString("name")); continue; }
            String name = fieldObj.optString("name", key);
            cachedFieldOptions.put(name + " (" + key + ")", key);
            tokens.add(name + " ({{issue.fields." + key + "}})");
        }
        Collections.sort(tokens);
        Collections.sort(cachedLinkTypes);
        allTokens.clear();
        allTokens.addAll(tokens);
        filterTokens();
        cachedFieldOptions.put("teams_selection (Virtual)", "teams_selection");
    }

    private void unpackMetadataFromCache(Map<String, JSONObject> diskCache) {
        if (diskCache == null) return;
        cachedFullMeta.clear();
        
        // 1. Put raw cached elements in cachedFullMeta so containsKey(cacheKey) works
        cachedFullMeta.putAll(diskCache);
        
        // 2. Unpack fields:all
        if (diskCache.containsKey("fields:all")) {
            JSONObject fieldsAll = diskCache.get("fields:all");
            if (fieldsAll.has("fields")) {
                JSONArray arr = fieldsAll.getJSONArray("fields");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject f = arr.getJSONObject(i);
                    String fId = f.optString("id");
                    if (fId != null && !fId.isEmpty()) {
                        cachedFullMeta.put(fId, f);
                    }
                }
            }
        }
        
        // 3. Unpack linktypes:all
        if (diskCache.containsKey("linktypes:all")) {
            JSONObject linkTypesAll = diskCache.get("linktypes:all");
            if (linkTypesAll.has("issueLinkTypes")) {
                JSONArray arr = linkTypesAll.getJSONArray("issueLinkTypes");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject lt = arr.getJSONObject(i);
                    String name = lt.optString("name");
                    if (name != null && !name.isEmpty()) {
                        cachedFullMeta.put("linktype:" + name, lt);
                    }
                }
            }
        }
        
        // 4. Unpack all createmeta:PROJECT:TYPE entries
        for (String key : diskCache.keySet()) {
            if (key.startsWith("createmeta:")) {
                JSONObject blob = diskCache.get(key);
                if (blob.has("values")) {
                    JSONArray values = blob.getJSONArray("values");
                    for (int i = 0; i < values.length(); i++) {
                        JSONObject f = values.getJSONObject(i);
                        String fId = f.optString("fieldId");
                        if (fId != null && !fId.isEmpty()) {
                            cachedFullMeta.put(fId, f);
                        }
                    }
                }
            }
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}
