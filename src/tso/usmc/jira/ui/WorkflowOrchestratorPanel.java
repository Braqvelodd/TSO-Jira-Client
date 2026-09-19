package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.ui.workflow.RecipeVariablePanel;
import tso.usmc.jira.ui.workflow.StepEditorPanel;
import tso.usmc.jira.workflow.*;
import tso.usmc.jira.service.MetadataCacheService;
import tso.usmc.jira.service.JiraIssueService;
import tso.usmc.jira.service.JqlAutocompleteService;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final VBox variablesContainer = new VBox(3);
    private final TitledPane variablesPane = new TitledPane();
    private final ListView<String> tokenList = new ListView<>();
    private final TextField tokenSearchField = new TextField();
    
    // UI Elements - Runner
    private final ComboBox<String> runnerRecipeCombo = new ComboBox<>();
    private final TextField runnerJqlField = new TextField() {
        @Override
        public void paste() {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            if (clipboard.hasString()) {
                String text = clipboard.getString();
                if (isIssueKeyList(text)) {
                    String[] parts = text.split("[\\r\\n,;\\s]+");
                    List<String> keys = new ArrayList<>();
                    for (String p : parts) {
                        String trimmed = p.trim();
                        if (!trimmed.isEmpty()) {
                            keys.add(trimmed);
                        }
                    }
                    String formatted = String.join(", ", keys);
                    replaceSelection(formatted);
                    return;
                }
            }
            super.paste();
        }
    };
    private final GridPane runnerInputsPanel = new GridPane();
    private final Map<String, Node> promptFields = new HashMap<>();
    private final TextArea runnerLog = new TextArea();
    private JqlAutocompleteService jqlAutocompleteService;
    private final Button runBtn = new Button("Run Workflow");
    private final Button exportReportBtn = new Button("Export Report (CSV)");
    private final CheckBox verboseLogCheck = new CheckBox("Verbose API Logs");
    private final CheckBox dryRunCheck = new CheckBox("Dry Run (Validate only)");
    private final Label statusLabel = new Label("Ready.");
    private TabPane mainTabs;
    private boolean isUpdatingRunnerInputs = false;
    private boolean isUpdatingBindings = false;
    private final List<DynamicPromptBinding> dynamicPromptBindings = new ArrayList<>();

    // Results data
    private List<WorkflowEngine.ExecutionResult> lastResults = new ArrayList<>();

    private final Map<String, JSONObject> cachedFullMeta = new HashMap<>();
    private final List<String> allTokens = new ArrayList<>();

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

        mainTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == runnerTab) {
                String currentDesignerRecipe = recipeNameField.getText().trim();
                if (!currentDesignerRecipe.isEmpty() && runnerRecipeCombo.getItems().contains(currentDesignerRecipe)) {
                    if (!currentDesignerRecipe.equals(runnerRecipeCombo.getSelectionModel().getSelectedItem())) {
                        runnerRecipeCombo.getSelectionModel().select(currentDesignerRecipe);
                    } else {
                        updateRunnerInputs();
                    }
                } else {
                    updateRunnerInputs();
                }
            }
        });
        
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
        
        header.add(new Label("Default Target Issues:"), 0, 1);
        header.add(jqlField, 1, 1);
        GridPane.setHgrow(jqlField, Priority.ALWAYS);
        jqlField.setPromptText("Optional: Saved issue keys (e.g. ABC-101, ABC-102). If empty, run from JQL Runner.");
        jqlField.setTooltip(new Tooltip("Optional default issue keys to execute against. Leave blank if this recipe is initiated from the JQL Runner."));
        
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

        // Recipe Variables Panel
        variablesPane.setText("Recipe Variables (0)");
        variablesPane.setExpanded(false);

        VBox varsBox = new VBox(5);
        varsBox.setPadding(new Insets(5));

        HBox varsHeader = new HBox(6);
        varsHeader.setPadding(new Insets(2, 5, 2, 5));
        Label hDel = new Label(""); hDel.setPrefWidth(22);
        Label hToken = new Label("Token Placeholder"); hToken.setPrefWidth(145); hToken.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        Label hPrompt = new Label("Prompt?"); hPrompt.setPrefWidth(65); hPrompt.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        Label hLabel = new Label("Prompt Question / Label"); hLabel.setPrefWidth(155); hLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        Label hDefault = new Label("Default / Static Value"); hDefault.setPrefWidth(155); hDefault.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        Label hOptions = new Label("Dropdown Choices (CSV)"); hOptions.setPrefWidth(140); hOptions.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");
        varsHeader.getChildren().addAll(hDel, hToken, hPrompt, hLabel, hDefault, hOptions);

        Button addVarBtn = new Button("+ Add Variable");
        addVarBtn.setOnAction(e -> {
            addVariableUI(new RecipeVariable("var" + (variablesContainer.getChildren().size() + 1), "Custom Variable", "", true, ""));
            variablesPane.setExpanded(true);
        });

        varsBox.getChildren().addAll(varsHeader, variablesContainer, addVarBtn);
        variablesPane.setContent(varsBox);

        VBox editorTop = new VBox(5);
        editorTop.getChildren().addAll(header, variablesPane);
        center.setTop(editorTop);
        
        // Editor Steps
        HBox stepsHeader = new HBox(10);
        stepsHeader.setAlignment(Pos.CENTER_LEFT);
        stepsHeader.setPadding(new Insets(5, 0, 5, 0));
        
        Label stepsTitleLabel = new Label("Recipe Steps");
        stepsTitleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        
        Region stepsSpacer = new Region();
        HBox.setHgrow(stepsSpacer, Priority.ALWAYS);
        
        Button expandAllBtn = new Button("Expand All");
        Button collapseAllBtn = new Button("Collapse All");
        expandAllBtn.setMinWidth(Region.USE_PREF_SIZE);
        collapseAllBtn.setMinWidth(Region.USE_PREF_SIZE);
        expandAllBtn.setOnAction(e -> setAllStepsCollapsed(false));
        collapseAllBtn.setOnAction(e -> setAllStepsCollapsed(true));
        
        stepsHeader.getChildren().addAll(stepsTitleLabel, stepsSpacer, expandAllBtn, collapseAllBtn);
        
        ScrollPane stepsScroll = new ScrollPane();
        stepsScroll.setContent(stepsContainer);
        stepsScroll.setFitToWidth(true);
        stepsContainer.setMinWidth(Region.USE_PREF_SIZE);
        
        VBox stepsWrapper = new VBox(5);
        stepsWrapper.getChildren().addAll(stepsHeader, stepsScroll);
        VBox.setVgrow(stepsScroll, Priority.ALWAYS);
        
        center.setCenter(stepsWrapper);
        BorderPane.setMargin(stepsWrapper, new Insets(10, 0, 10, 0));
        
        // Editor Footer
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_LEFT);
        
        MenuButton addStepMenu = new MenuButton("+ Add Step");
        addStepMenu.getStyleClass().add("primary-button");
        addStepMenu.setMinWidth(Region.USE_PREF_SIZE);

        MenuItem addTransItem = new MenuItem("Transition");
        MenuItem addUpdateItem = new MenuItem("Update");
        MenuItem addCreateItem = new MenuItem("Create");
        MenuItem addLinkItem = new MenuItem("Link");
        MenuItem addAssetItem = new MenuItem("Copy Assets (Links/Att/Sub)");
        MenuItem addWorklogItem = new MenuItem("Worklog");
        MenuItem addAttachmentItem = new MenuItem("Attachment");
        MenuItem addCommentItem = new MenuItem("Comment");
        MenuItem addNotifyItem = new MenuItem("Notify");
        
        addStepMenu.getItems().addAll(
            addTransItem, addUpdateItem, addCreateItem, addLinkItem, addAssetItem,
            addWorklogItem, addAttachmentItem, addCommentItem, addNotifyItem
        );
        
        footer.getChildren().add(addStepMenu);
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
        
        addTransItem.setOnAction(e -> addStep(new TransitionStep()));
        addUpdateItem.setOnAction(e -> addStep(new UpdateStep()));
        addCreateItem.setOnAction(e -> addStep(new CreateStep()));
        addLinkItem.setOnAction(e -> addStep(new LinkStep()));
        addAssetItem.setOnAction(e -> addStep(new AssetStep()));
        addWorklogItem.setOnAction(e -> addStep(new WorklogStep()));
        addAttachmentItem.setOnAction(e -> addStep(new AttachmentStep()));
        addCommentItem.setOnAction(e -> addStep(new CommentStep()));
        addNotifyItem.setOnAction(e -> addStep(new NotifyStep()));

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
            mainFrame.showPanel("Workflow Orchestrator");
            mainTabs.getSelectionModel().select(1);
            if (recipeName != null && !recipeName.isEmpty()) {
                runnerRecipeCombo.getSelectionModel().select(recipeName);
            }
            if (key != null && !key.isEmpty()) {
                runnerJqlField.setText(key);
            }
            updateRunnerInputs();
        });
    }

    public void runWorkflowDirectly(String recipeName, String issueKeys) {
        Platform.runLater(() -> {
            mainFrame.showPanel("Workflow Orchestrator");
            mainTabs.getSelectionModel().select(1);
            if (recipeName != null && !recipeName.isEmpty()) {
                runnerRecipeCombo.getSelectionModel().select(recipeName);
            }
            if (issueKeys != null && !issueKeys.isEmpty()) {
                runnerJqlField.setText(issueKeys);
            }
            runWorkflow();
        });
    }

    private Node createRunnerPanel() {
        BorderPane panel = new BorderPane();
        
        VBox top = new VBox(10);
        top.setPadding(new Insets(10, 10, 5, 10));
        
        GridPane topGrid = new GridPane();
        topGrid.setHgap(10);
        topGrid.setVgap(10);

        Label selectRecipeLabel = new Label("Select Recipe:");
        selectRecipeLabel.setStyle("-fx-text-fill: -fx-text-base-color; -fx-font-weight: bold;");
        topGrid.add(selectRecipeLabel, 0, 0);
        topGrid.add(runnerRecipeCombo, 1, 0);
        GridPane.setHgrow(runnerRecipeCombo, Priority.ALWAYS);
        runnerRecipeCombo.setMaxWidth(Double.MAX_VALUE);
        
        HBox checkPanel = new HBox(10);
        checkPanel.setAlignment(Pos.CENTER_LEFT);
        verboseLogCheck.setStyle("-fx-text-fill: -fx-text-base-color;");
        dryRunCheck.setStyle("-fx-text-fill: -fx-text-base-color;");
        checkPanel.getChildren().addAll(verboseLogCheck, dryRunCheck);
        topGrid.add(checkPanel, 2, 0);

        Label targetIssuesLabel = new Label("Target Issues:");
        targetIssuesLabel.setStyle("-fx-text-fill: -fx-text-base-color; -fx-font-weight: bold;");
        topGrid.add(targetIssuesLabel, 0, 1);
        topGrid.add(runnerJqlField, 1, 1);
        GridPane.setHgrow(runnerJqlField, Priority.ALWAYS);
        runnerJqlField.setPromptText("Enter issue key(s) e.g. TSO-101, TSO-102 or transfer from JQL Runner");
        UiUtils.setupExpandedView(runnerJqlField);

        HBox issueActionBtns = new HBox(5);
        Button clearIssuesBtn = new Button("Clear");
        clearIssuesBtn.setOnAction(e -> runnerJqlField.setText(""));
        Button loadSavedBtn = new Button("Load Saved");
        loadSavedBtn.setTooltip(new Tooltip("Load default target issues saved with this recipe"));
        loadSavedBtn.setOnAction(e -> {
            String rName = runnerRecipeCombo.getSelectionModel().getSelectedItem();
            if (rName != null) {
                try {
                    WorkflowRecipe r = workflowManager.loadWorkflow(rName);
                    if (r == null) {
                        String v = mainFrame.getJiraConfig().getProperty("workflow." + rName);
                        if (v != null) r = WorkflowRecipe.fromJson(v);
                    }
                    if (r != null && r.getTargetIssues() != null) {
                        runnerJqlField.setText(r.getTargetIssues());
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });
        issueActionBtns.getChildren().addAll(clearIssuesBtn, loadSavedBtn);
        topGrid.add(issueActionBtns, 2, 1);

        top.getChildren().add(topGrid);
        panel.setTop(top);

        // Center SplitPane: Prompts (Upper) & Execution Log (Lower)
        runnerInputsPanel.setHgap(10);
        runnerInputsPanel.setVgap(8);
        runnerInputsPanel.setPadding(new Insets(5, 5, 5, 5));

        ScrollPane inputsScroll = new ScrollPane(runnerInputsPanel);
        inputsScroll.setFitToWidth(true);
        inputsScroll.setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        VBox.setVgrow(inputsScroll, Priority.ALWAYS);

        VBox promptsBox = new VBox(5);
        promptsBox.setPadding(new Insets(0, 10, 5, 10));
        Label promptsHeader = new Label("Recipe Variables & Prompts:");
        promptsHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: -fx-text-base-color;");
        promptsBox.getChildren().addAll(promptsHeader, inputsScroll);
        VBox.setVgrow(promptsBox, Priority.ALWAYS);

        // Execution Log
        VBox logBox = new VBox(5);
        logBox.setPadding(new Insets(5, 10, 0, 10));
        Label logLabel = new Label("Execution Log:");
        logLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: -fx-text-base-color;");
        runnerLog.setEditable(false);
        runnerLog.setWrapText(true);
        runnerLog.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        VBox.setVgrow(runnerLog, Priority.ALWAYS);
        logBox.getChildren().addAll(logLabel, runnerLog);

        SplitPane runnerSplit = new SplitPane();
        runnerSplit.setOrientation(Orientation.VERTICAL);
        runnerSplit.getItems().addAll(promptsBox, logBox);
        runnerSplit.setDividerPositions(0.68);
        SplitPane.setResizableWithParent(promptsBox, true);
        SplitPane.setResizableWithParent(logBox, false);

        panel.setCenter(runnerSplit);

        HBox bottom = new HBox(10);
        bottom.setPadding(new Insets(10));
        bottom.setAlignment(Pos.CENTER_RIGHT);
        
        runBtn.setText("▶ Run Workflow");
        runBtn.getStyleClass().add("primary-button");
        exportReportBtn.setDisable(true);
        Button clearLogBtn = new Button("Clear Log");
        
        bottom.getChildren().addAll(clearLogBtn, exportReportBtn, runBtn);
        panel.setBottom(bottom);

        runBtn.setOnAction(e -> runWorkflow());
        exportReportBtn.setOnAction(e -> exportToCsv());
        clearLogBtn.setOnAction(e -> runnerLog.setText(""));

        // Auto-load saved issues when selecting recipe
        runnerRecipeCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                try {
                    WorkflowRecipe r = workflowManager.loadWorkflow(newVal);
                    if (r == null) {
                        String v = mainFrame.getJiraConfig().getProperty("workflow." + newVal);
                        if (v != null) r = WorkflowRecipe.fromJson(v);
                    }
                    if (r != null && r.getTargetIssues() != null && !r.getTargetIssues().trim().isEmpty()) {
                        runnerJqlField.setText(r.getTargetIssues());
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            updateRunnerInputs();
        });

        runnerJqlField.textProperty().addListener((obs, oldV, newV) -> updateRunnerInputs());

        return panel;
    }

    private void runWorkflow() {
        onRunnerVariableChanged();
        String recipeName = runnerRecipeCombo.getSelectionModel().getSelectedItem();
        if (recipeName == null) {
            showAlert(Alert.AlertType.WARNING, "No Recipe", "Please select a recipe to run.");
            return;
        }

        String rawText = runnerJqlField.getText();
        if (rawText == null || rawText.trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Target Issues", "Please enter at least one target issue key, or select issues in JQL Runner.");
            return;
        }

        List<String> cleanKeys = new ArrayList<>();
        for (String p : rawText.split("[\\r\\n,;\\s]+")) {
            String ck = JiraUtils.cleanIssueKey(p);
            if (!ck.isEmpty() && !cleanKeys.contains(ck)) {
                cleanKeys.add(ck);
            }
        }

        if (cleanKeys.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Invalid Target Issues", "No valid issue keys found in Target Issues.");
            return;
        }

        Map<String, String> promptValues = new HashMap<>();
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
            } else if (comp instanceof FilePromptPanel) {
                val = ((FilePromptPanel) comp).getValue();
            } else if (comp instanceof TextArea) {
                val = ((TextArea) comp).getText();
            }
            promptValues.put(label, val);
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

                // If currently editing this recipe in Designer, sync variables in case they were modified
                if (recipeName.equals(recipeNameField.getText().trim())) {
                    List<RecipeVariable> memoryVars = new ArrayList<>();
                    for (Node c : variablesContainer.getChildren()) {
                        if (c instanceof RecipeVariablePanel) {
                            RecipeVariable var = ((RecipeVariablePanel) c).saveToVariable();
                            if (var != null && var.getName() != null && !var.getName().trim().isEmpty()) {
                                memoryVars.add(var);
                            }
                        }
                    }
                    if (!memoryVars.isEmpty()) {
                        recipe.setVariables(memoryVars);
                    }
                }

                // Fetch issues
                onLog("Fetching issue data for " + cleanKeys.size() + " issue(s)...");
                List<JSONObject> issuesToProcess = new ArrayList<>();
                try {
                    String jql = "key in (" + String.join(",", cleanKeys) + ")";
                    String searchUrl = mainFrame.getBaseUrl() + "/rest/api/2/search?jql=" + java.net.URLEncoder.encode(jql, "UTF-8") + "&expand=names,renderedFields&fields=*all,attachment,issuelinks&maxResults=" + cleanKeys.size();
                    String searchResp = mainFrame.getService().executeRequest(searchUrl, "GET", null);
                    JSONArray issuesArr = new JSONObject(searchResp).getJSONArray("issues");
                    for (int i = 0; i < issuesArr.length(); i++) {
                        issuesToProcess.add(issuesArr.getJSONObject(i));
                    }
                } catch (Exception ex) {
                    for (String key : cleanKeys) {
                        try {
                            String url = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "?expand=names,renderedFields&fields=*all,attachment,issuelinks";
                            String resp = mainFrame.getService().executeRequest(url, "GET", null);
                            issuesToProcess.add(new JSONObject(resp));
                        } catch (Exception indEx) {
                            onLog("ERROR: Failed to fetch issue " + key + ": " + indEx.getMessage());
                        }
                    }
                }

                if (issuesToProcess.isEmpty()) {
                    onLog("ERROR: Could not fetch data for any of the target issues.");
                    onComplete();
                    return;
                }

                final int threads = mainFrame.getJiraConfig().getParallelThreads();
                String mode = dryRunCheck.isSelected() ? "[DRY RUN - VALIDATE ONLY]" : "[LIVE EXECUTION]";
                onLog(mode + " Starting workflow: " + recipe.getRecipeName() + " on " + issuesToProcess.size() + " issue(s) using " + threads + " thread(s).");

                List<WorkflowEngine.ExecutionResult> results = Collections.synchronizedList(new ArrayList<>());

                if (threads > 1 && issuesToProcess.size() > 1) {
                    ExecutorService executor = Executors.newFixedThreadPool(threads);
                    List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

                    for (JSONObject issue : issuesToProcess) {
                        final WorkflowRecipe finalRecipe = recipe;
                        futures.add(executor.submit(() -> {
                            try {
                                WorkflowEngine engine = new WorkflowEngine(mainFrame.getService(), mainFrame.getIssueService(), mainFrame.getMetadataService(), mainFrame.getBaseUrl(), this);
                                engine.setVerboseLogging(verboseLogCheck.isSelected());
                                engine.setDryRun(dryRunCheck.isSelected());
                                engine.setSuppressSummaryLogging(true);
                                List<WorkflowEngine.ExecutionResult> res = engine.execute(finalRecipe, Collections.singletonList(issue), promptValues);
                                results.addAll(res);
                            } catch (Exception ex) {
                                onError("Error processing issue " + issue.optString("key"), ex);
                            }
                        }));
                    }

                    for (java.util.concurrent.Future<?> f : futures) {
                        try {
                            f.get();
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                    executor.shutdown();
                } else {
                    WorkflowEngine engine = new WorkflowEngine(mainFrame.getService(), mainFrame.getIssueService(), mainFrame.getMetadataService(), mainFrame.getBaseUrl(), this);
                    engine.setVerboseLogging(verboseLogCheck.isSelected());
                    engine.setDryRun(dryRunCheck.isSelected());
                    results.addAll(engine.execute(recipe, issuesToProcess, promptValues));
                }

                lastResults = results;
                onLog(mode + " Workflow Execution Complete.");
                onComplete();

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

    private List<RecipeVariable> getActiveRecipeVariables(String recipeName) {
        List<RecipeVariable> activeVars = new ArrayList<>();
        if (recipeName == null) return activeVars;
        if (recipeName.equals(recipeNameField.getText().trim())) {
            for (Node c : variablesContainer.getChildren()) {
                if (c instanceof RecipeVariablePanel) {
                    RecipeVariable var = ((RecipeVariablePanel) c).saveToVariable();
                    if (var != null && var.getName() != null && !var.getName().trim().isEmpty()) {
                        activeVars.add(var);
                    }
                }
            }
            return activeVars;
        }

        try {
            WorkflowRecipe recipe = workflowManager.loadWorkflow(recipeName);
            if (recipe == null) {
                String val = mainFrame.getJiraConfig().getProperty("workflow." + recipeName);
                if (val != null) recipe = WorkflowRecipe.fromJson(val);
            }
            if (recipe != null && recipe.getVariables() != null) {
                activeVars.addAll(recipe.getVariables());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return activeVars;
    }

    private Map<String, String> getCurrentRunnerVariables() {
        Map<String, String> varMap = new HashMap<>();
        String recipeName = runnerRecipeCombo.getSelectionModel().getSelectedItem();
        if (recipeName == null) return varMap;

        List<RecipeVariable> activeVars = getActiveRecipeVariables(recipeName);
        for (RecipeVariable var : activeVars) {
            if (var.getName() == null || var.getName().trim().isEmpty()) continue;
            String vName = var.getName().trim();
            String val = null;

            Node comp = promptFields.get(vName);
            if (comp instanceof TextField) {
                val = ((TextField) comp).getText();
            } else if (comp instanceof TextArea) {
                val = ((TextArea) comp).getText();
            } else if (comp instanceof ComboBox) {
                Object sel = ((ComboBox<?>) comp).getSelectionModel().getSelectedItem();
                if (sel != null) val = sel.toString();
            }

            if (val == null) {
                val = var.getDefaultValue() != null ? var.getDefaultValue() : "";
            }

            varMap.put(vName, val);
            varMap.put(vName + ".value", val);
        }

        // Also incorporate any other prompt field values or teams
        for (Map.Entry<String, Node> entry : promptFields.entrySet()) {
            String key = entry.getKey();
            Node comp = entry.getValue();
            String val = null;
            if (comp instanceof TextField) {
                val = ((TextField) comp).getText();
            } else if (comp instanceof TextArea) {
                val = ((TextArea) comp).getText();
            } else if (comp instanceof AutocompleteTextField) {
                val = ((AutocompleteTextField) comp).getText();
            } else if (comp instanceof ComboBox) {
                Object sel = ((ComboBox<?>) comp).getSelectionModel().getSelectedItem();
                if (sel instanceof ConfigOption) {
                    ConfigOption co = (ConfigOption) sel;
                    val = co.value;
                    if (co.teamKey != null) {
                        String name = mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "name");
                        String lead = mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "lead");
                        String component = mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "component");
                        String id = mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "id");
                        varMap.put("team.name", name != null ? name : "");
                        varMap.put("team.lead", lead != null ? lead : "");
                        varMap.put("team.component", component != null ? component : "");
                        varMap.put("team.id", id != null ? id : "");
                    }
                } else if (sel != null) {
                    val = sel.toString();
                }
            } else if (comp instanceof PromptChoicePanel) {
                val = ((PromptChoicePanel) comp).getValue();
            } else if (comp instanceof FilePromptPanel) {
                val = ((FilePromptPanel) comp).getValue();
            }
            if (val != null) {
                varMap.put(key, val);
                varMap.put(key + ".value", val);
            }
        }

        return varMap;
    }

    private void registerDynamicPromptBinding(Node control, String rawTemplate, JSONObject contextIssue, String initialEvaluatedValue) {
        if (control == null || rawTemplate == null || !rawTemplate.contains("{{")) return;
        dynamicPromptBindings.add(new DynamicPromptBinding(control, rawTemplate, initialEvaluatedValue, contextIssue));
    }

    private void onRunnerVariableChanged() {
        if (isUpdatingRunnerInputs || isUpdatingBindings) return;
        isUpdatingBindings = true;
        try {
            for (int iteration = 0; iteration < 3; iteration++) {
                Map<String, String> currentVars = getCurrentRunnerVariables();
                boolean anyChanged = false;
                for (DynamicPromptBinding binding : dynamicPromptBindings) {
                    if (binding.updateValue(currentVars)) {
                        anyChanged = true;
                    }
                }
                if (!anyChanged) break;
            }
        } finally {
            isUpdatingBindings = false;
        }
    }

    private void updateRunnerInputs() {
        if (isUpdatingRunnerInputs) return;
        isUpdatingRunnerInputs = true;
        try {
            String recipeName = runnerRecipeCombo.getSelectionModel().getSelectedItem();
            if (recipeName == null) return;
            
            // Preserve user inputs if runner inputs are being reloaded
            Map<String, String> existingUserInputs = new HashMap<>();
            for (String k : promptFields.keySet()) {
                Node comp = promptFields.get(k);
                if (comp instanceof TextField) {
                    existingUserInputs.put(k, ((TextField) comp).getText());
                } else if (comp instanceof TextArea) {
                    existingUserInputs.put(k, ((TextArea) comp).getText());
                } else if (comp instanceof ComboBox) {
                    Object sel = ((ComboBox<?>) comp).getSelectionModel().getSelectedItem();
                    if (sel != null) existingUserInputs.put(k, sel.toString());
                }
            }

            runnerInputsPanel.getChildren().clear();
            promptFields.clear();
            dynamicPromptBindings.clear();
            
            WorkflowRecipe recipe = workflowManager.loadWorkflow(recipeName);
            if (recipe == null) {
                String val = mainFrame.getJiraConfig().getProperty("workflow." + recipeName);
                if (val != null) recipe = WorkflowRecipe.fromJson(val);
            }
            
            if (recipe != null) {
                if (runnerJqlField.getText().isEmpty() && recipe.getTargetIssues() != null) {
                    runnerJqlField.setText(recipe.getTargetIssues());
                }
                
                List<String> currentKeys = new ArrayList<>();
                String rawText = runnerJqlField.getText();
                if (rawText != null && !rawText.trim().isEmpty()) {
                    for (String p : rawText.split("[\\r\\n,;\\s]+")) {
                        String ck = JiraUtils.cleanIssueKey(p);
                        if (!ck.isEmpty() && !currentKeys.contains(ck)) {
                            currentKeys.add(ck);
                        }
                    }
                }

                JSONObject contextIssue = null;
                if (!currentKeys.isEmpty()) {
                    contextIssue = new JSONObject().put("key", currentKeys.get(0));
                }

                Set<String> labels = new HashSet<>();

                List<RecipeVariable> activeVars = getActiveRecipeVariables(recipeName);

                // Build variable lookup map for prompt resolution
                Map<String, String> varMap = new HashMap<>();
                for (RecipeVariable var : activeVars) {
                    if (var.getName() != null && !var.getName().trim().isEmpty()) {
                        String vName = var.getName().trim();
                        String curVal = existingUserInputs.get(vName);
                        if (curVal == null || curVal.isEmpty()) {
                            curVal = var.getDefaultValue() != null ? var.getDefaultValue() : "";
                        }
                        varMap.put(vName, curVal);
                        varMap.put(vName + ".value", curVal);
                    }
                }

                // Recipe Variables Prompts
                for (RecipeVariable var : activeVars) {
                    if (var.isPrompt() && var.getName() != null && !var.getName().trim().isEmpty()) {
                        String vName = var.getName().trim();
                        String displayLabel = (var.getLabel() != null && !var.getLabel().trim().isEmpty()) ? var.getLabel() : vName;
                        String promptTitle = displayLabel + " (" + vName + ")";

                        // Resolve default value if it has tokens
                        String defaultVal = existingUserInputs.containsKey(vName) ? existingUserInputs.get(vName) : var.getDefaultValue();
                        String rawTemplate = var.getDefaultValue();
                        if (defaultVal != null && defaultVal.contains("{{")) {
                            defaultVal = TokenEngine.replaceTokens(defaultVal, contextIssue, varMap, true);
                        }
                        if (defaultVal == null) defaultVal = "";

                        if (var.getOptions() != null && !var.getOptions().trim().isEmpty()) {
                            ComboBox<String> combo = new ComboBox<>();
                            for (String opt : var.getOptions().split(",")) {
                                String trimmed = opt.trim();
                                if (!trimmed.isEmpty()) combo.getItems().add(trimmed);
                            }
                            if (!defaultVal.isEmpty() && combo.getItems().contains(defaultVal)) {
                                combo.getSelectionModel().select(defaultVal);
                            } else if (!combo.getItems().isEmpty()) {
                                combo.getSelectionModel().select(0);
                            }
                            addInputRow(promptTitle, combo, labels);
                            promptFields.put(vName, combo);
                            promptFields.put(promptTitle, combo);

                            combo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
                        } else {
                            TextField inputField = new TextField(defaultVal);
                            UiUtils.setupExpandedView(inputField);
                            addInputRow(promptTitle, inputField, labels);
                            promptFields.put(vName, inputField);
                            promptFields.put(promptTitle, inputField);

                            inputField.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());

                            if (rawTemplate != null && rawTemplate.contains("{{")) {
                                registerDynamicPromptBinding(inputField, rawTemplate, contextIssue, defaultVal);
                            }
                        }
                    }
                }

                for (WorkflowStep step : recipe.getSteps()) {
                    if (step instanceof CreateStep) {
                        CreateStep cs = (CreateStep) step;
                        addCreateStepPrompts(labels, cs, contextIssue, varMap);
                    }
                    
                    if (step instanceof AssetStep) {
                        AssetStep as = (AssetStep) step;
                        if (as.isPromptOptions()) {
                            String label = "Asset Options (" + step.getLabel() + ")";
                            AssetOptionsPromptPanel panel = new AssetOptionsPromptPanel(as.isCopyAttachments(), as.isCopyLinks(), as.isCopySubTasks(), as.getSubTaskFields());
                            addInputRow(label, panel, labels);
                            promptFields.put(label.replaceAll("\\[.*?\\]", "").trim(), panel);
                        }
                    }
                    
                    if (step instanceof AttachmentStep) {
                        AttachmentStep as = (AttachmentStep) step;
                        if (as.isPromptAtRuntime()) {
                            String label = "Attachment File (" + step.getLabel() + ")";
                            String defaultPath = as.getFilePath();
                            String rawPath = defaultPath;
                            if (defaultPath != null && !defaultPath.trim().isEmpty()) {
                                defaultPath = TokenEngine.replaceTokens(defaultPath, contextIssue, varMap, true);
                            }
                            FilePromptPanel panel = new FilePromptPanel(mainFrame.getPrimaryStage(), defaultPath);
                            addInputRow(label, panel, labels);
                            promptFields.put(label.replaceAll("\\[.*?\\]", "").trim(), panel);
                            if (rawPath != null && rawPath.contains("{{")) {
                                registerDynamicPromptBinding(panel, rawPath, contextIssue, defaultPath);
                            }
                        }
                    }
                    
                    if (step instanceof CommentStep) {
                        CommentStep cs = (CommentStep) step;
                        if (cs.isPromptAtRuntime()) {
                            String rawComment = cs.getCommentBody();
                            if (cs.isPromptPerIssue()) {
                                if (currentKeys.isEmpty()) {
                                    String label = "Comment (" + step.getLabel() + ")";
                                    TextArea area = new TextArea();
                                    area.setPrefRowCount(3);
                                    area.setWrapText(true);
                                    UiUtils.setupExpandedView(area);
                                    String initialVal = "";
                                    if (rawComment != null) {
                                        initialVal = TokenEngine.replaceTokens(rawComment, (JSONObject) null, varMap, true);
                                        area.setText(initialVal != null ? initialVal.replace("\\n", "\n").replace("\\r", "\r") : "");
                                    }
                                    area.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
                                    addInputRow(label, area, labels);
                                    promptFields.put(label, area);
                                    if (rawComment != null && rawComment.contains("{{")) {
                                        registerDynamicPromptBinding(area, rawComment, null, initialVal);
                                    }
                                } else {
                                    for (String key : currentKeys) {
                                        String label = "Comment (" + step.getLabel() + ") for " + key;
                                        TextArea area = new TextArea();
                                        area.setPrefRowCount(3);
                                        area.setWrapText(true);
                                        UiUtils.setupExpandedView(area);
                                        String initialVal = "";
                                        JSONObject issueContext = new JSONObject().put("key", key);
                                        if (rawComment != null) {
                                            initialVal = TokenEngine.replaceTokens(rawComment, issueContext, varMap, true);
                                            area.setText(initialVal != null ? initialVal.replace("\\n", "\n").replace("\\r", "\r") : "");
                                        }
                                        area.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
                                        addInputRow(label, area, labels);
                                        promptFields.put(label, area);
                                        if (rawComment != null && rawComment.contains("{{")) {
                                            registerDynamicPromptBinding(area, rawComment, issueContext, initialVal);
                                        }
                                    }
                                }
                            } else {
                                String label = "Comment (" + step.getLabel() + ")";
                                TextArea area = new TextArea();
                                area.setPrefRowCount(3);
                                area.setWrapText(true);
                                UiUtils.setupExpandedView(area);
                                String initialVal = "";
                                if (rawComment != null && !rawComment.trim().isEmpty()) {
                                    initialVal = TokenEngine.replaceTokens(rawComment, contextIssue, varMap, true);
                                    area.setText(initialVal != null ? initialVal.replace("\\n", "\n").replace("\\r", "\r") : "");
                                }
                                area.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
                                addInputRow(label, area, labels);
                                promptFields.put(label, area);
                                if (rawComment != null && rawComment.contains("{{")) {
                                    registerDynamicPromptBinding(area, rawComment, contextIssue, initialVal);
                                }
                            }
                        }
                    }

                    if (step instanceof WorklogStep) {
                        WorklogStep ws = (WorklogStep) step;
                        if (ws.isPromptAtRuntime()) {
                            if (ws.isPromptPerIssue()) {
                                if (currentKeys.isEmpty()) {
                                    addWorklogPromptsOnce(labels, ws, contextIssue, varMap);
                                } else {
                                    for (String key : currentKeys) {
                                        JSONObject issueContext = new JSONObject().put("key", key);
                                        addWorklogPromptsForIssue(labels, ws, key, issueContext, varMap);
                                    }
                                }
                            } else {
                                addWorklogPromptsOnce(labels, ws, contextIssue, varMap);
                            }
                        }
                    }

                    if (step instanceof TransitionStep) {
                        TransitionStep ts = (TransitionStep) step;
                        String status = ts.getTargetStatus();
                        if (status != null && status.contains(",")) {
                            String label = "Transition (" + step.getLabel() + ")";
                            String[] options = status.split("\\s*,\\s*");
                            ComboBox<String> combo = new ComboBox<>();
                            for (String opt : options) {
                                String trimmed = opt.trim();
                                if (!trimmed.isEmpty()) combo.getItems().add(trimmed);
                            }
                            if (!combo.getItems().isEmpty()) {
                                combo.getSelectionModel().select(0);
                            }
                            addInputRow(label, combo, labels);
                            promptFields.put(label, combo);
                        }
                    }
                    
                    if (step instanceof NotifyStep) {
                        NotifyStep ns = (NotifyStep) step;
                        if (ns.isPromptAtRuntime()) {
                            if (ns.isPromptPerIssue()) {
                                if (currentKeys.isEmpty()) {
                                    addNotifyPromptsOnce(labels, ns, contextIssue, varMap);
                                } else {
                                    for (String key : currentKeys) {
                                        JSONObject issueContext = new JSONObject().put("key", key);
                                        addNotifyPromptsForIssue(labels, ns, key, issueContext, varMap);
                                    }
                                }
                            } else {
                                addNotifyPromptsOnce(labels, ns, contextIssue, varMap);
                            }
                        }
                    }
                    
                    for (FieldAction fa : step.getFieldActions().values()) {
                        if (fa.getMode() == FieldAction.MappingMode.PROMPT) {
                            addDynamicPrompt(labels, fa.getPromptLabel(), fa.getValue() != null ? fa.getValue().toString() : null, fa.getFieldId(), contextIssue, varMap);
                        }
                    }
                }
            }

            if (promptFields.isEmpty()) {
                Label emptyLabel = new Label("No runtime prompts or variables required for this recipe.");
                emptyLabel.setStyle("-fx-text-fill: -fx-text-base-color; -fx-opacity: 0.7; -fx-font-style: italic;");
                runnerInputsPanel.add(emptyLabel, 0, 0, 2, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isUpdatingRunnerInputs = false;
        }
    }

    private void addInputRow(String label, Node input, Set<String> labels) {
        String cleanLabel = label.replaceAll("\\[.*?\\]", "").trim();
        if (labels.contains(cleanLabel)) return;
        labels.add(cleanLabel);

        int rowCount = runnerInputsPanel.getChildren().size() / 2;
        
        Label labelNode = new Label(cleanLabel + ":");
        labelNode.setAlignment(Pos.CENTER_RIGHT);
        labelNode.getStyleClass().add("prompt-label");
        labelNode.setStyle("-fx-text-fill: -fx-text-base-color; -fx-font-weight: bold;");
        
        input.getStyleClass().add("prompt-input");
        if (input instanceof AutocompleteTextField) {
            ((AutocompleteTextField) input).getTextField().getStyleClass().add("prompt-input");
        }
        
        runnerInputsPanel.add(labelNode, 0, rowCount);
        runnerInputsPanel.add(input, 1, rowCount);
        GridPane.setHgrow(input, Priority.ALWAYS);
    }

    private void addDynamicPrompt(Set<String> labels, String label, String value, String fieldId, JSONObject contextIssue, Map<String, String> variables) {
        if (label == null || label.trim().isEmpty()) return;
        String cleanLabel = label.replaceAll("\\[.*?\\]", "").trim();
        if (labels.contains(cleanLabel)) return;

        Node input = createPromptInput(label, value, fieldId, contextIssue, variables);
        
        addInputRow(label, input, labels);
        promptFields.put(cleanLabel, input);
    }

    private void addWorklogPromptsForIssue(Set<String> labels, WorklogStep ws, String key, JSONObject issueContext, Map<String, String> variables) {
        String timeSpentLabel = "Time Spent (" + ws.getLabel() + ") for " + key;
        String commentLabel = "Comment (" + ws.getLabel() + ") for " + key;
        
        TextField tsField = new TextField();
        tsField.setPrefWidth(200);
        UiUtils.setupExpandedView(tsField);
        if (ws.getTimeSpent() != null) {
            tsField.setText(TokenEngine.replaceTokens(ws.getTimeSpent(), issueContext, variables, true));
        }
        tsField.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
        addInputRow(timeSpentLabel, tsField, labels);
        promptFields.put(timeSpentLabel, tsField);
        if (ws.getTimeSpent() != null && ws.getTimeSpent().contains("{{")) {
            registerDynamicPromptBinding(tsField, ws.getTimeSpent(), issueContext, tsField.getText());
        }
        
        TextArea commentArea = new TextArea();
        commentArea.setPrefRowCount(3);
        commentArea.setWrapText(true);
        UiUtils.setupExpandedView(commentArea);
        if (ws.getComment() != null) {
            String initialComment = TokenEngine.replaceTokens(ws.getComment(), issueContext, variables, true);
            commentArea.setText(initialComment != null ? initialComment.replace("\\n", "\n").replace("\\r", "\r") : "");
        }
        commentArea.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
        addInputRow(commentLabel, commentArea, labels);
        promptFields.put(commentLabel, commentArea);
        if (ws.getComment() != null && ws.getComment().contains("{{")) {
            registerDynamicPromptBinding(commentArea, ws.getComment(), issueContext, commentArea.getText());
        }
    }

    private void addWorklogPromptsOnce(Set<String> labels, WorklogStep ws, JSONObject contextIssue, Map<String, String> variables) {
        String timeSpentLabel = "Time Spent (" + ws.getLabel() + ")";
        String commentLabel = "Comment (" + ws.getLabel() + ")";
        
        TextField tsField = new TextField();
        tsField.setPrefWidth(200);
        UiUtils.setupExpandedView(tsField);
        if (ws.getTimeSpent() != null) {
            String defTs = ws.getTimeSpent();
            if (contextIssue != null) defTs = TokenEngine.replaceTokens(defTs, contextIssue, variables, true);
            else if (defTs.contains("{{")) defTs = TokenEngine.replaceTokens(defTs, (JSONObject) null, variables, true);
            tsField.setText(defTs);
        }
        tsField.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
        addInputRow(timeSpentLabel, tsField, labels);
        promptFields.put(timeSpentLabel, tsField);
        if (ws.getTimeSpent() != null && ws.getTimeSpent().contains("{{")) {
            registerDynamicPromptBinding(tsField, ws.getTimeSpent(), contextIssue, tsField.getText());
        }
        
        TextArea commentArea = new TextArea();
        commentArea.setPrefRowCount(3);
        commentArea.setWrapText(true);
        UiUtils.setupExpandedView(commentArea);
        if (ws.getComment() != null) {
            String defComment = ws.getComment();
            if (contextIssue != null) defComment = TokenEngine.replaceTokens(defComment, contextIssue, variables, true);
            else if (defComment.contains("{{")) defComment = TokenEngine.replaceTokens(defComment, (JSONObject) null, variables, true);
            commentArea.setText(defComment != null ? defComment.replace("\\n", "\n").replace("\\r", "\r") : "");
        }
        commentArea.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
        addInputRow(commentLabel, commentArea, labels);
        promptFields.put(commentLabel, commentArea);
        if (ws.getComment() != null && ws.getComment().contains("{{")) {
            registerDynamicPromptBinding(commentArea, ws.getComment(), contextIssue, commentArea.getText());
        }
    }

    private void addNotifyPromptsForIssue(Set<String> labels, NotifyStep ns, String key, JSONObject issueContext, Map<String, String> variables) {
        String subLabel = "Subject (" + ns.getLabel() + ") for " + key;
        String bodyLabel = "Body (" + ns.getLabel() + ") for " + key;
        String usersLabel = "To Users (" + ns.getLabel() + ") for " + key;
        String groupsLabel = "To Groups (" + ns.getLabel() + ") for " + key;

        boolean s = ns.isPromptSubject();
        boolean b = ns.isPromptBody();
        boolean u = ns.isPromptUsers();
        boolean g = ns.isPromptGroups();
        if (!s && !b && !u && !g) {
            s = true; b = true; u = true; g = true; // Backwards compatibility fallback
        }

        if (s) {
            TextField subField = new TextField();
            subField.setPrefWidth(200);
            UiUtils.setupExpandedView(subField);
            if (ns.getSubject() != null) {
                subField.setText(TokenEngine.replaceTokens(ns.getSubject(), issueContext, variables, true));
            }
            subField.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            addInputRow(subLabel, subField, labels);
            promptFields.put(subLabel, subField);
            if (ns.getSubject() != null && ns.getSubject().contains("{{")) {
                registerDynamicPromptBinding(subField, ns.getSubject(), issueContext, subField.getText());
            }
        }

        if (b) {
            TextArea bodyArea = new TextArea();
            bodyArea.setPrefRowCount(3);
            bodyArea.setWrapText(true);
            UiUtils.setupExpandedView(bodyArea);
            if (ns.getTextBody() != null) {
                String bodyText = TokenEngine.replaceTokens(ns.getTextBody(), issueContext, variables, true);
                bodyArea.setText(bodyText != null ? bodyText.replace("\\n", "\n").replace("\\r", "\r") : "");
            }
            bodyArea.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            addInputRow(bodyLabel, bodyArea, labels);
            promptFields.put(bodyLabel, bodyArea);
            if (ns.getTextBody() != null && ns.getTextBody().contains("{{")) {
                registerDynamicPromptBinding(bodyArea, ns.getTextBody(), issueContext, bodyArea.getText());
            }
        }

        if (u) {
            JiraUserAutocompleteTextField usersField = new JiraUserAutocompleteTextField(20);
            usersField.setService(getAutocompleteService());
            usersField.setAutocompleteEnabled(mainFrame.getJiraConfig().isAutocompleteEnabled());
            usersField.setPrefWidth(200);
            UiUtils.setupExpandedView(usersField);
            if (ns.getToUsers() != null) {
                usersField.setText(TokenEngine.replaceTokens(ns.getToUsers(), issueContext, variables, true));
            }
            usersField.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            addInputRow(usersLabel, usersField, labels);
            promptFields.put(usersLabel, usersField);
            if (ns.getToUsers() != null && ns.getToUsers().contains("{{")) {
                registerDynamicPromptBinding(usersField, ns.getToUsers(), issueContext, usersField.getText());
            }
        }

        if (g) {
            TextField groupsField = new TextField();
            groupsField.setPrefWidth(200);
            UiUtils.setupExpandedView(groupsField);
            if (ns.getToGroups() != null) {
                groupsField.setText(TokenEngine.replaceTokens(ns.getToGroups(), issueContext, variables, true));
            }
            groupsField.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            addInputRow(groupsLabel, groupsField, labels);
            promptFields.put(groupsLabel, groupsField);
            if (ns.getToGroups() != null && ns.getToGroups().contains("{{")) {
                registerDynamicPromptBinding(groupsField, ns.getToGroups(), issueContext, groupsField.getText());
            }
        }
    }

    private void addNotifyPromptsOnce(Set<String> labels, NotifyStep ns, JSONObject contextIssue, Map<String, String> variables) {
        String subLabel = "Subject (" + ns.getLabel() + ")";
        String bodyLabel = "Body (" + ns.getLabel() + ")";
        String usersLabel = "To Users (" + ns.getLabel() + ")";
        String groupsLabel = "To Groups (" + ns.getLabel() + ")";

        boolean s = ns.isPromptSubject();
        boolean b = ns.isPromptBody();
        boolean u = ns.isPromptUsers();
        boolean g = ns.isPromptGroups();
        if (!s && !b && !u && !g) {
            s = true; b = true; u = true; g = true; // Backwards compatibility fallback
        }

        if (s) {
            TextField subField = new TextField();
            subField.setPrefWidth(200);
            UiUtils.setupExpandedView(subField);
            if (ns.getSubject() != null) {
                String def = ns.getSubject();
                if (contextIssue != null) def = TokenEngine.replaceTokens(def, contextIssue, variables, true);
                else if (def.contains("{{")) def = TokenEngine.replaceTokens(def, (JSONObject) null, variables, true);
                subField.setText(def);
            }
            subField.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            addInputRow(subLabel, subField, labels);
            promptFields.put(subLabel, subField);
            if (ns.getSubject() != null && ns.getSubject().contains("{{")) {
                registerDynamicPromptBinding(subField, ns.getSubject(), contextIssue, subField.getText());
            }
        }

        if (b) {
            TextArea bodyArea = new TextArea();
            bodyArea.setPrefRowCount(3);
            bodyArea.setWrapText(true);
            UiUtils.setupExpandedView(bodyArea);
            if (ns.getTextBody() != null) {
                String def = ns.getTextBody();
                if (contextIssue != null) def = TokenEngine.replaceTokens(def, contextIssue, variables, true);
                else if (def.contains("{{")) def = TokenEngine.replaceTokens(def, (JSONObject) null, variables, true);
                bodyArea.setText(def != null ? def.replace("\\n", "\n").replace("\\r", "\r") : "");
            }
            bodyArea.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            addInputRow(bodyLabel, bodyArea, labels);
            promptFields.put(bodyLabel, bodyArea);
            if (ns.getTextBody() != null && ns.getTextBody().contains("{{")) {
                registerDynamicPromptBinding(bodyArea, ns.getTextBody(), contextIssue, bodyArea.getText());
            }
        }

        if (u) {
            JiraUserAutocompleteTextField usersField = new JiraUserAutocompleteTextField(20);
            usersField.setService(getAutocompleteService());
            usersField.setAutocompleteEnabled(mainFrame.getJiraConfig().isAutocompleteEnabled());
            usersField.setPrefWidth(200);
            UiUtils.setupExpandedView(usersField);
            if (ns.getToUsers() != null) {
                String def = ns.getToUsers();
                if (contextIssue != null) def = TokenEngine.replaceTokens(def, contextIssue, variables, true);
                else if (def.contains("{{")) def = TokenEngine.replaceTokens(def, (JSONObject) null, variables, true);
                usersField.setText(def);
            }
            usersField.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            addInputRow(usersLabel, usersField, labels);
            promptFields.put(usersLabel, usersField);
            if (ns.getToUsers() != null && ns.getToUsers().contains("{{")) {
                registerDynamicPromptBinding(usersField, ns.getToUsers(), contextIssue, usersField.getText());
            }
        }

        if (g) {
            TextField groupsField = new TextField();
            groupsField.setPrefWidth(200);
            UiUtils.setupExpandedView(groupsField);
            if (ns.getToGroups() != null) {
                String def = ns.getToGroups();
                if (contextIssue != null) def = TokenEngine.replaceTokens(def, contextIssue, variables, true);
                else if (def.contains("{{")) def = TokenEngine.replaceTokens(def, (JSONObject) null, variables, true);
                groupsField.setText(def);
            }
            groupsField.textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            addInputRow(groupsLabel, groupsField, labels);
            promptFields.put(groupsLabel, groupsField);
            if (ns.getToGroups() != null && ns.getToGroups().contains("{{")) {
                registerDynamicPromptBinding(groupsField, ns.getToGroups(), contextIssue, groupsField.getText());
            }
        }
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

    private Node createPromptInput(String label, String staticOptions, String fieldId, JSONObject contextIssue, Map<String, String> variables) {
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

        List<String> customOpts = resolveAndSplitOptions(staticOptions, contextIssue, variables);
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
                UiUtils.setupExpandedView(atf.getTextField());
                String resolvedValue = staticOptions;
                if (staticOptions != null && staticOptions.contains("{{")) {
                    resolvedValue = TokenEngine.replaceTokens(staticOptions, contextIssue, variables, true);
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
                                    UiUtils.setupExpandedView(atf.getTextField());
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
                            if (tokenExpr.contains("{{")) {
                                resolved = TokenEngine.replaceTokens(tokenExpr, contextIssue, variables, true);
                            }
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
                                combo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
                                result = combo;
                            } else if (key.equals("fy_summary")) {
                                ComboBox<String> combo = new ComboBox<>();
                                combo.getItems().add(mainFrame.getJiraConfig().getWorkflowFySummaryIssue());
                                combo.getSelectionModel().select(0);
                                combo.setPrefWidth(200);
                                combo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
                                result = combo;
                            } else {
                                String val = mainFrame.getJiraConfig().getProperty(key);
                                if (val != null) {
                                    if (val.contains(",")) {
                                        String[] opts = smartSplit(val);
                                        for (int i = 0; i < opts.length; i++) {
                                            if (opts[i].contains("{{")) {
                                                opts[i] = TokenEngine.replaceTokens(opts[i], contextIssue, variables, true);
                                            }
                                        }
                                        ComboBox<String> combo = new ComboBox<>();
                                        combo.getItems().addAll(opts);
                                        combo.getSelectionModel().select(0);
                                        combo.setPrefWidth(200);
                                        combo.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
                                        result = combo;
                                    } else {
                                        String resolved = val;
                                        if (val.contains("{{")) {
                                            resolved = TokenEngine.replaceTokens(val, contextIssue, variables, true);
                                        }
                                        TextField tf = new TextField(resolved);
                                        tf.setPrefWidth(200);
                                        UiUtils.setupExpandedView(tf);
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
            if (staticOptions != null && staticOptions.contains("{{")) {
                resolvedValue = TokenEngine.replaceTokens(staticOptions, contextIssue, variables, true);
            }
            
            boolean isUser = isUserField(effectiveFieldId, contextIssue);
            if (!isUser && label != null) {
                String labelLower = label.toLowerCase();
                if (labelLower.contains("assignee") || labelLower.contains("reporter") || labelLower.contains("owner")) {
                    isUser = true;
                }
            }
            
            if (isUser) {
                JiraUserAutocompleteTextField tf = new JiraUserAutocompleteTextField(20);
                tf.setService(getAutocompleteService());
                tf.setAutocompleteEnabled(mainFrame.getJiraConfig().isAutocompleteEnabled());
                if (resolvedValue != null) {
                    tf.setText(resolvedValue);
                }
                tf.setPrefWidth(200);
                UiUtils.setupExpandedView(tf);
                result = tf;
            } else {
                String jqlField = null;
                if (effectiveFieldId != null) {
                    String cleanId = effectiveFieldId.toLowerCase();
                    if (cleanId.equals("status") || cleanId.equals("project") || cleanId.equals("issuetype") || cleanId.equals("resolution") || cleanId.equals("priority") || cleanId.equals("components")) {
                        jqlField = cleanId;
                        if (jqlField.equals("components")) jqlField = "component";
                    }
                }
                if (jqlField == null && label != null) {
                    String labelLower = label.toLowerCase();
                    if (labelLower.contains("status") || labelLower.contains("transition")) jqlField = "status";
                    else if (labelLower.contains("project")) jqlField = "project";
                    else if (labelLower.contains("type")) jqlField = "issuetype";
                    else if (labelLower.contains("resolution")) jqlField = "resolution";
                    else if (labelLower.contains("priority")) jqlField = "priority";
                }
                
                if (jqlField != null) {
                    AutocompleteTextField atf = new AutocompleteTextField();
                    atf.setPrefWidth(200);
                    atf.setUserAutocompleteService(getAutocompleteService());
                    atf.setJqlFieldName(jqlField);
                    atf.setAutocompleteEnabled(mainFrame.getJiraConfig().isAutocompleteEnabled());
                    if (resolvedValue != null) {
                        atf.setText(resolvedValue);
                    }
                    UiUtils.setupExpandedView(atf.getTextField());
                    result = atf;
                } else {
                    TextField tf = new TextField(resolvedValue != null ? resolvedValue : "");
                    tf.setPrefWidth(200);
                    UiUtils.setupExpandedView(tf);
                    result = tf;
                }
            }
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
        debug.append("\n(Double-click to expand)");
        
        Tooltip tooltip = new Tooltip(debug.toString());
        if (result instanceof Control) {
            ((Control) result).setTooltip(tooltip);
        }
        if (result instanceof AutocompleteTextField) {
            ((AutocompleteTextField) result).getTextField().setTooltip(tooltip);
        }

        if (result != null) {
            String templateToBind = null;
            if (staticOptions != null && staticOptions.contains("{{")) {
                templateToBind = staticOptions;
            } else if (result instanceof PromptChoicePanel) {
                templateToBind = ((PromptChoicePanel) result).getTokenName();
            }
            if (templateToBind != null && templateToBind.contains("{{")) {
                String initVal = null;
                if (result instanceof TextField) initVal = ((TextField) result).getText();
                else if (result instanceof TextArea) initVal = ((TextArea) result).getText();
                else if (result instanceof AutocompleteTextField) initVal = ((AutocompleteTextField) result).getText();
                else if (result instanceof PromptChoicePanel) initVal = ((PromptChoicePanel) result).getTokenValue();
                else if (result instanceof ComboBox) {
                    Object sel = ((ComboBox<?>) result).getSelectionModel().getSelectedItem();
                    initVal = sel != null ? sel.toString() : "";
                }
                registerDynamicPromptBinding(result, templateToBind, contextIssue, initVal);
            }

            if (result instanceof TextField) {
                ((TextField) result).textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            } else if (result instanceof TextArea) {
                ((TextArea) result).textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            } else if (result instanceof AutocompleteTextField) {
                ((AutocompleteTextField) result).getTextField().textProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            } else if (result instanceof ComboBox) {
                ((ComboBox<?>) result).getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> onRunnerVariableChanged());
            }
        }

        return result;
    }

    private static String[] smartSplit(String input) {
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

    private void addCreateStepPrompts(Set<String> labels, CreateStep cs, JSONObject contextIssue, Map<String, String> variables) {
        String projVal = cs.getProjectKey();
        List<String> projOpts = resolveAndSplitOptions(projVal, contextIssue, variables);
        if (projOpts.size() > 1) {
            addDynamicPrompt(labels, "Project (" + cs.getLabel() + ")", projVal, "project", contextIssue, variables);
        }
        
        String typeVal = cs.getIssueType();
        List<String> typeOpts = resolveAndSplitOptions(typeVal, contextIssue, variables);
        if (typeOpts.size() > 1) {
            addDynamicPrompt(labels, "Issue Type (" + cs.getLabel() + ")", typeVal, "issuetype", contextIssue, variables);
        }

        String parentVal = cs.getParentIssueKey();
        if (parentVal != null) {
            List<String> parentOpts = resolveAndSplitOptions(parentVal, contextIssue, variables);
            if (parentOpts.size() > 1) {
                addDynamicPrompt(labels, "Parent Issue (" + cs.getLabel() + ")", parentVal, "parent", contextIssue, variables);
            }
        }
    }

    private static List<String> resolveAndSplitOptions(String staticOptions, JSONObject contextIssue, Map<String, String> variables) {
        if (staticOptions == null || staticOptions.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String resolved = staticOptions;
        if (contextIssue != null && staticOptions.contains("{{")) {
            resolved = TokenEngine.replaceTokens(staticOptions, contextIssue, variables, true);
        } else if (staticOptions.contains("{{")) {
            resolved = TokenEngine.replaceTokens(staticOptions, (JSONObject) null, variables, true);
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
        private final String tokenName;
        private String tokenValue;

        PromptChoicePanel(String tokenName, String resolvedValue) {
            setSpacing(10);
            setAlignment(Pos.CENTER_LEFT);
            this.tokenName = tokenName;
            this.tokenValue = resolvedValue != null ? resolvedValue : "";
            
            String displayText = this.tokenValue;
            if (this.tokenValue.equals(tokenName)) {
                displayText = tokenName;
            } else {
                displayText = this.tokenValue + " (" + tokenName + ")";
            }
            
            tokenRadio = new RadioButton(displayText);
            tokenRadio.setSelected(true);
            tokenRadio.setStyle("-fx-text-fill: -fx-text-base-color;");
            manualRadio = new RadioButton("Manual:");
            manualRadio.setStyle("-fx-text-fill: -fx-text-base-color;");
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

        public void setResolvedValue(String newResolvedValue) {
            this.tokenValue = newResolvedValue != null ? newResolvedValue : "";
            String displayText = this.tokenValue;
            if (this.tokenValue.equals(tokenName)) {
                displayText = tokenName;
            } else {
                displayText = this.tokenValue + " (" + tokenName + ")";
            }
            tokenRadio.setText(displayText);
        }

        public String getTokenName() {
            return tokenName;
        }

        public String getTokenValue() {
            return tokenValue;
        }

        public boolean isManualSelected() {
            return manualRadio.isSelected();
        }

        public String getValue() {
            return tokenRadio.isSelected() ? tokenValue : manualField.getText();
        }
    }

    private static class AssetOptionsPromptPanel extends HBox {
        private final CheckBox att, links, sub;
        private final TextField fieldsField;
        AssetOptionsPromptPanel(boolean a, boolean l, boolean s, String defaultFields) {
            setSpacing(10);
            setAlignment(Pos.CENTER_LEFT);
            att = new CheckBox("Attachments");
            att.setSelected(a);
            att.setStyle("-fx-text-fill: -fx-text-base-color;");
            links = new CheckBox("Links");
            links.setSelected(l);
            links.setStyle("-fx-text-fill: -fx-text-base-color;");
            sub = new CheckBox("Sub-tasks");
            sub.setSelected(s);
            sub.setStyle("-fx-text-fill: -fx-text-base-color;");
            
            fieldsField = new TextField(defaultFields != null ? defaultFields : "");
            fieldsField.setPrefWidth(150);
            fieldsField.setDisable(!s);
            sub.setOnAction(e -> fieldsField.setDisable(!sub.isSelected()));
            
            Label fieldsLabel = new Label("Fields:");
            fieldsLabel.setStyle("-fx-text-fill: -fx-text-base-color;");
            getChildren().addAll(att, links, sub, fieldsLabel, fieldsField);
        }
        public String getValue() {
            String fieldsText = fieldsField.getText().trim();
            return att.isSelected() + "," + links.isSelected() + "," + sub.isSelected() + "," + fieldsText;
        }
    }

    private static class FilePromptPanel extends HBox {
        private final TextField pathField = new TextField();
        private final Button browseBtn = new Button("Browse...");
        
        FilePromptPanel(javafx.stage.Window ownerWindow, String defaultPath) {
            setSpacing(5);
            setAlignment(Pos.CENTER_LEFT);
            pathField.setEditable(false);
            pathField.setPrefWidth(250);
            if (defaultPath != null) {
                pathField.setText(defaultPath);
            }
            
            browseBtn.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                fc.setTitle("Select Attachment File");
                File file = fc.showOpenDialog(ownerWindow);
                if (file != null) {
                    pathField.setText(file.getAbsolutePath());
                }
            });

            // Drag-and-drop file support in runtime prompt
            setOnDragOver(e -> {
                if (e.getDragboard().hasFiles()) {
                    e.acceptTransferModes(TransferMode.COPY);
                }
                e.consume();
            });
            setOnDragDropped(e -> {
                Dragboard db = e.getDragboard();
                boolean success = false;
                if (db.hasFiles()) {
                    List<File> files = db.getFiles();
                    if (!files.isEmpty()) {
                        pathField.setText(files.get(0).getAbsolutePath());
                        success = true;
                    }
                }
                e.setDropCompleted(success);
                e.consume();
            });
            
            getChildren().addAll(pathField, browseBtn);
        }

        public void setPath(String path) {
            pathField.setText(path != null ? path : "");
        }
        
        public String getValue() {
            return pathField.getText();
        }
    }

    private static class DynamicPromptBinding {
        final Node control;
        String rawTemplate;
        String lastEvaluatedValue;
        final JSONObject contextIssue;

        DynamicPromptBinding(Node control, String rawTemplate, String initialEvaluatedValue, JSONObject contextIssue) {
            this.control = control;
            this.rawTemplate = rawTemplate;
            this.lastEvaluatedValue = initialEvaluatedValue != null ? initialEvaluatedValue : "";
            this.contextIssue = contextIssue;
        }

        boolean updateValue(Map<String, String> currentVariables) {
            if (rawTemplate == null || !rawTemplate.contains("{{")) return false;

            if (control instanceof PromptChoicePanel) {
                PromptChoicePanel pcp = (PromptChoicePanel) control;
                if (pcp.isManualSelected()) {
                    return false;
                }
                String newEvaluatedValue = TokenEngine.replaceTokens(rawTemplate, contextIssue, currentVariables, true);
                if (newEvaluatedValue == null) newEvaluatedValue = "";
                if (!newEvaluatedValue.equals(pcp.getTokenValue())) {
                    pcp.setResolvedValue(newEvaluatedValue);
                    this.lastEvaluatedValue = newEvaluatedValue;
                    return true;
                }
                return false;
            }

            String currentControlText = getControlText();

            // If user typed a token directly into the field, adopt it as the new template
            if (currentControlText != null && currentControlText.contains("{{") && !currentControlText.equals(rawTemplate)) {
                rawTemplate = currentControlText;
            }

            // Check if user has unmodified text (still matching lastEvaluatedValue or blank)
            boolean shouldUpdate = false;
            if (currentControlText == null || currentControlText.isEmpty()) {
                shouldUpdate = true;
            } else if (lastEvaluatedValue == null || lastEvaluatedValue.isEmpty()) {
                shouldUpdate = true;
            } else if (currentControlText.equals(lastEvaluatedValue) || currentControlText.trim().equals(lastEvaluatedValue.trim())) {
                shouldUpdate = true;
            }

            if (shouldUpdate) {
                if (control instanceof ComboBox) {
                    @SuppressWarnings("unchecked")
                    ComboBox<String> cb = (ComboBox<String>) control;
                    String selected = cb.getSelectionModel().getSelectedItem();
                    List<String> newOpts = resolveAndSplitOptions(rawTemplate, contextIssue, currentVariables);
                    if (!newOpts.isEmpty() && !newOpts.equals(cb.getItems())) {
                        cb.getItems().setAll(newOpts);
                        if (selected != null && newOpts.contains(selected)) {
                            cb.getSelectionModel().select(selected);
                        } else {
                            cb.getSelectionModel().select(0);
                        }
                        return true;
                    }
                    return false;
                }

                String newEvaluatedValue = TokenEngine.replaceTokens(rawTemplate, contextIssue, currentVariables, true);
                if (newEvaluatedValue == null) newEvaluatedValue = "";

                if (!newEvaluatedValue.equals(currentControlText)) {
                    setControlText(newEvaluatedValue);
                    this.lastEvaluatedValue = newEvaluatedValue;
                    return true;
                }
            }
            return false;
        }

        private String getControlText() {
            if (control instanceof TextField) {
                return ((TextField) control).getText();
            } else if (control instanceof TextArea) {
                return ((TextArea) control).getText();
            } else if (control instanceof AutocompleteTextField) {
                return ((AutocompleteTextField) control).getText();
            } else if (control instanceof FilePromptPanel) {
                return ((FilePromptPanel) control).getValue();
            } else if (control instanceof PromptChoicePanel) {
                return ((PromptChoicePanel) control).getValue();
            } else if (control instanceof ComboBox) {
                Object sel = ((ComboBox<?>) control).getSelectionModel().getSelectedItem();
                return sel != null ? sel.toString() : null;
            }
            return null;
        }

        private void setControlText(String text) {
            if (control instanceof TextField) {
                ((TextField) control).setText(text);
            } else if (control instanceof TextArea) {
                ((TextArea) control).setText(text);
            } else if (control instanceof AutocompleteTextField) {
                ((AutocompleteTextField) control).setText(text);
            } else if (control instanceof FilePromptPanel) {
                ((FilePromptPanel) control).setPath(text);
            } else if (control instanceof PromptChoicePanel) {
                ((PromptChoicePanel) control).setResolvedValue(text);
            }
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
        UiUtils.configureWindowOwner(alert, mainFrame != null ? mainFrame.getPrimaryStage() : null);
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
        StepEditorPanel panel = new StepEditorPanel(step, cachedFieldOptions, cachedFullMeta, getAutocompleteService(), () -> {
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

    public JqlAutocompleteService getAutocompleteService() {
        if (jqlAutocompleteService == null) {
            try {
                jqlAutocompleteService = new JqlAutocompleteService(mainFrame.getService(), mainFrame.getBaseUrl(), mainFrame.getJiraConfig());
            } catch (Exception e) {
                // ignore
            }
        }
        return jqlAutocompleteService;
    }

    private boolean isUserField(String fieldId, JSONObject contextIssue) {
        if (fieldId == null) return false;
        if ("assignee".equals(fieldId) || "reporter".equals(fieldId)) {
            return true;
        }
        JSONObject meta = findFieldMeta(fieldId, contextIssue);
        if (meta != null && meta.has("schema")) {
            JSONObject schema = meta.optJSONObject("schema");
            if (schema != null) {
                String type = schema.optString("type");
                String system = schema.optString("system");
                if ("user".equals(type) || "user".equals(system)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isIssueKeyList(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String[] parts = text.split("[\\r\\n,;\\s]+");
        if (parts.length == 0) return false;
        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            if (!trimmed.matches("(?i)[a-z0-9]+-\\d+")) {
                return false;
            }
        }
        return true;
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
                jqlField.setText(recipe.getTargetIssues() != null ? recipe.getTargetIssues() : "");
                stepsContainer.getChildren().clear();
                variablesContainer.getChildren().clear();

                if (recipe.getVariables() != null) {
                    for (RecipeVariable var : recipe.getVariables()) {
                        addVariableUI(var);
                    }
                }
                updateVariablesPaneTitle();

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
        refreshRecipeList(null);
    }

    private void refreshRecipeList(String preferredSelection) {
        String currentRunnerSel = runnerRecipeCombo.getSelectionModel().getSelectedItem();
        String currentDesignerSel = recipeList.getSelectionModel().getSelectedItem();
        String targetSel = preferredSelection != null ? preferredSelection 
                : (currentRunnerSel != null ? currentRunnerSel : currentDesignerSel);

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
        if (targetSel != null && runnerRecipeCombo.getItems().contains(targetSel)) {
            runnerRecipeCombo.getSelectionModel().select(targetSel);
        } else if (!names.isEmpty()) {
            runnerRecipeCombo.getSelectionModel().select(0);
        }
        if (targetSel != null && recipeList.getItems().contains(targetSel)) {
            recipeList.getSelectionModel().select(targetSel);
        }
        updateRunnerInputs();
    }

    private void saveRecipe() {
        String name = recipeNameField.getText().trim();
        if (name.isEmpty()) return;
        WorkflowRecipe recipe = new WorkflowRecipe();
        recipe.setRecipeName(name);
        recipe.setTargetIssues(jqlField.getText());

        for (Node c : variablesContainer.getChildren()) {
            if (c instanceof RecipeVariablePanel) {
                RecipeVariable var = ((RecipeVariablePanel) c).saveToVariable();
                if (var != null && var.getName() != null && !var.getName().trim().isEmpty()) {
                    recipe.addVariable(var);
                }
            }
        }

        for (Node c : stepsContainer.getChildren()) {
            if (c instanceof StepEditorPanel) {
                ((StepEditorPanel) c).saveToStep();
                recipe.addStep(((StepEditorPanel) c).getStep());
            }
        }

        try {
            workflowManager.saveWorkflow(recipe);
            refreshRecipeList(name);
            showAlert(Alert.AlertType.INFORMATION, "Saved", "Recipe saved!");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Save Error", "Error saving: " + e.getMessage());
        }
    }

    private void clearEditor() {
        recipeNameField.setText("");
        jqlField.setText("");
        stepsContainer.getChildren().clear();
        variablesContainer.getChildren().clear();
        updateVariablesPaneTitle();
        recipeList.getSelectionModel().clearSelection();
    }

    private void addVariableUI(RecipeVariable var) {
        RecipeVariablePanel panel = new RecipeVariablePanel(var, new RecipeVariablePanel.RecipeVariableListener() {
            @Override
            public void onRemove(RecipeVariablePanel p) {
                variablesContainer.getChildren().remove(p);
                updateVariablesPaneTitle();
                updateTokensFromCache();
            }

            @Override
            public void onChange(RecipeVariablePanel p) {
                updateTokensFromCache();
            }
        });
        variablesContainer.getChildren().add(panel);
        updateVariablesPaneTitle();
        updateTokensFromCache();
    }

    private void updateVariablesPaneTitle() {
        int count = variablesContainer.getChildren().size();
        variablesPane.setText("Recipe Variables (" + count + ")");
    }

    private void setAllStepsCollapsed(boolean collapse) {
        for (Node c : stepsContainer.getChildren()) {
            if (c instanceof StepEditorPanel) {
                ((StepEditorPanel) c).setCollapsed(collapse);
            }
        }
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

        // Add custom recipe variables as tokens
        for (Node c : variablesContainer.getChildren()) {
            if (c instanceof RecipeVariablePanel) {
                RecipeVariable var = ((RecipeVariablePanel) c).getVariable();
                if (var != null && var.getName() != null && !var.getName().trim().isEmpty()) {
                    String clean = var.getName().replace("{{", "").replace("}}", "").trim();
                    String lbl = (var.getLabel() != null && !var.getLabel().trim().isEmpty()) ? var.getLabel() : "Custom Variable";
                    tokens.add(lbl + " ({{" + clean + "}})");
                }
            }
        }

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
            UiUtils.showAlert(mainFrame != null ? mainFrame.getPrimaryStage() : null, type, title, content);
        });
    }
}
