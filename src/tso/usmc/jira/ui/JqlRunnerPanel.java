package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JqlAutocompleteService;
import tso.usmc.jira.service.JqlExecutionEngine;
import tso.usmc.jira.util.JsonUtils;
import tso.usmc.jira.util.ExecutionService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.property.SimpleStringProperty;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;

public class JqlRunnerPanel extends BorderPane implements tso.usmc.jira.util.ConfigChangeListener {

    private final JiraApiClientGui mainFrame;
    private final tso.usmc.jira.util.JiraConfig jiraConfig;
    private JqlAutocompleteService jqlAutocompleteService;

    // UI Components
    private final JqlAutocompleteTextArea jqlArea;
    private final JiraFieldAutocompleteTextField fieldsField;
    private final Button executeBtn = new Button("Execute JQL");
    private final MenuButton workflowsBtn = new MenuButton("Workflows");
    private final MenuButton actionsBtn = new MenuButton("Actions");
    private final ComboBox<String> filterCombo = new ComboBox<>();
    private final Button saveFilterBtn = new Button("Save Filter");
    private final Label statusLabel = new Label("Enter a JQL query and click Execute.");

    private final TableView<ObservableList<String>> resultsTable = new TableView<>();
    private boolean isRefreshingFilters = false;

    // Excel-like Drag Selection State
    private int dragStartRow = -1;
    private int dragStartCol = -1;
    private boolean isDragSelecting = false;
    private List<TablePosition> dragInitialSelection = new ArrayList<>();

    public JqlRunnerPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        this.jiraConfig = mainFrame.getJiraConfig();
        this.jiraConfig.addConfigChangeListener(this);
        
        this.fieldsField = new JiraFieldAutocompleteTextField(this.jiraConfig.getJqlDisplayFields());
        this.jqlArea = new JqlAutocompleteTextArea(null);
        this.jqlArea.setText(this.jiraConfig.getJqlDefaultQuery());
        this.jqlArea.setPrefRowCount(4);
        
        // Listeners for focus initialization of autocomplete
        this.jqlArea.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV) ensureAutocompleteServiceInitialized();
        });
        this.fieldsField.focusedProperty().addListener((obs, oldV, newV) -> {
            if (newV) ensureAutocompleteServiceInitialized();
        });

        ensureAutocompleteServiceInitialized();

        setPadding(new Insets(10));

        // --- TOP: Input Configuration Panel ---
        VBox configPanel = new VBox(10);
        
        HBox filterPanel = new HBox(10);
        filterPanel.getChildren().addAll(new Label("Saved Filters:"), filterCombo, saveFilterBtn);
        filterCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(filterCombo, Priority.ALWAYS);
        
        VBox jqlWrapper = new VBox(5);
        jqlWrapper.getStyleClass().add("card");
        Label jqlTitle = new Label("JQL Query");
        jqlTitle.getStyleClass().add("card-title");
        jqlWrapper.getChildren().addAll(jqlTitle, jqlArea);

        HBox fieldsPanel = new HBox(10);
        fieldsPanel.getChildren().addAll(new Label("Fields to display:"), fieldsField);
        HBox.setHgrow(fieldsField, Priority.ALWAYS);
        
        HBox buttonPanel = new HBox(5);
        buttonPanel.getChildren().addAll(workflowsBtn, actionsBtn, executeBtn);
        fieldsPanel.getChildren().add(buttonPanel);
        
        configPanel.getChildren().addAll(filterPanel, jqlWrapper, fieldsPanel);
        setTop(configPanel);
        
        // --- CENTER: Results Table ---
        resultsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        resultsTable.getSelectionModel().setCellSelectionEnabled(true);
        
        VBox tableWrapper = new VBox(5);
        tableWrapper.getStyleClass().add("card");
        Label tableTitle = new Label("Results");
        tableTitle.getStyleClass().add("card-title");
        tableWrapper.getChildren().addAll(tableTitle, resultsTable);
        VBox.setVgrow(resultsTable, Priority.ALWAYS);
        VBox.setVgrow(tableWrapper, Priority.ALWAYS);
        
        setCenter(tableWrapper);

        // --- BOTTOM: Status Bar ---
        HBox statusPanel = new HBox();
        statusPanel.getStyleClass().add("status-bar");
        statusLabel.getStyleClass().add("status-text");
        statusPanel.getChildren().add(statusLabel);
        setBottom(statusPanel);

        // --- Action Listeners ---
        executeBtn.setOnAction(e -> executeJql());
        saveFilterBtn.setOnAction(e -> saveCurrentFilter());
        filterCombo.setOnAction(e -> applySelectedFilter());

        setupContextMenu();
        setupRowDoubleClick();
        refreshFilters();
        
        // Populate workflows menu on click/showing
        workflowsBtn.showingProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                populateWorkflowsMenu();
            }
        });

        // Populate actions menu on click/showing
        actionsBtn.showingProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                populateActionsMenu();
            }
        });

        // Key pressed listener for clipboard copy (Ctrl+C)
        resultsTable.setOnKeyPressed(event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.C) {
                copySelectionToClipboard();
                event.consume();
            }
        });

        // Ensure drag ends if mouse released on table structure
        resultsTable.setOnMouseReleased(e -> {
            isDragSelecting = false;
        });
    }

    @Override
    public void onConfigChanged() {
        Platform.runLater(this::refreshFilters);
    }

    private void refreshFilters() {
        isRefreshingFilters = true;
        String currentSelection = filterCombo.getSelectionModel().getSelectedItem();
        filterCombo.getItems().clear();
        filterCombo.getItems().add("-- Select a saved filter --");
        
        String[] filterKeys = jiraConfig.getJqlFilterKeys();
        for (String key : filterKeys) {
            filterCombo.getItems().add(key);
        }
        
        if (currentSelection != null) {
            filterCombo.getSelectionModel().select(currentSelection);
        } else {
            filterCombo.getSelectionModel().select(0);
        }
        isRefreshingFilters = false;
    }

    private void ensureAutocompleteServiceInitialized() {
        if (jqlAutocompleteService != null) return;
        
        try {
            JqlAutocompleteService service = new JqlAutocompleteService(mainFrame.getService(), mainFrame.getBaseUrl(), mainFrame.getJiraConfig());
            boolean enabled = mainFrame.getJiraConfig().isAutocompleteEnabled();
            this.jqlAutocompleteService = service;
            this.jqlArea.setService(service);
            this.jqlArea.setAutocompleteEnabled(enabled);
            this.fieldsField.setService(service);
            this.fieldsField.setAutocompleteEnabled(enabled);
        } catch (Exception e) {
            // Silently fail if cert not selected yet
        }
    }

    private void applySelectedFilter() {
        if (isRefreshingFilters) return;
        
        String selected = filterCombo.getSelectionModel().getSelectedItem();
        if (selected == null || selected.startsWith("--")) return;
        
        String filterData = jiraConfig.getJqlFilter(selected);
        if (filterData != null && filterData.contains("|")) {
            String[] parts = filterData.split("\\|", 2);
            fieldsField.setText(parts[0]);
            jqlArea.setText(parts[1]);
        }
    }

    private void saveCurrentFilter() {
        TextInputDialog dialog = new TextInputDialog("");
        dialog.setTitle("Save JQL Filter");
        dialog.setHeaderText("Enter a name for this filter:");
        dialog.setContentText("Name:");

        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent() || result.get().trim().isEmpty()) return;
        String name = result.get().trim();
        
        String fields = fieldsField.getText().trim();
        String jql = jqlArea.getText().trim();
        
        if (jql.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "JQL query cannot be empty.");
            return;
        }
        
        jiraConfig.saveJqlFilter(name, fields, jql);
        showAlert(Alert.AlertType.INFORMATION, "Success", "Filter '" + name + "' saved successfully.");
    }

    private void setupRowDoubleClick() {
        resultsTable.setRowFactory(tv -> {
            TableRow<ObservableList<String>> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    ObservableList<String> rowData = row.getItem();
                    int keyCol = findKeyColumnIndex();
                    if (keyCol != -1 && keyCol < rowData.size()) {
                        String key = rowData.get(keyCol);
                        if (key != null && !key.isEmpty()) {
                            mainFrame.loadAndShowIssue(key);
                        }
                    }
                }
            });
            return row;
        });
    }

    private int findKeyColumnIndex() {
        for (int i = 0; i < resultsTable.getColumns().size(); i++) {
            if ("key".equalsIgnoreCase(resultsTable.getColumns().get(i).getText())) {
                return i;
            }
        }
        return -1;
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        resultsTable.setContextMenu(contextMenu);

        resultsTable.setOnContextMenuRequested(event -> {
            contextMenu.getItems().clear();
            ObservableList<ObservableList<String>> selectedItems = resultsTable.getSelectionModel().getSelectedItems();
            if (selectedItems.isEmpty()) return;

            int keyCol = findKeyColumnIndex();
            if (keyCol == -1) return;

            List<String> keys = new ArrayList<>();
            for (ObservableList<String> row : selectedItems) {
                if (row != null && keyCol < row.size()) {
                    keys.add(row.get(keyCol));
                }
            }
            if (keys.isEmpty()) return;
            String selectedIssueKey = String.join(",", keys);

            if (jiraConfig.isTabEnabled("TaskBuilder")) {
                MenuItem openInTB = new MenuItem("Create Sub-Task in TaskBuilder");
                openInTB.setOnAction(al -> {
                    mainFrame.showPanel("Task Builder");
                    if (mainFrame.getTaskBuilderPanel() != null) {
                        mainFrame.getTaskBuilderPanel().setParentTicket(selectedIssueKey);
                    }
                });
                contextMenu.getItems().add(openInTB);
                contextMenu.getItems().add(new SeparatorMenuItem());

                // Add templates from config
                String[] templateKeys = jiraConfig.getTemplateKeys();
                for (String tKey : templateKeys) {
                    String label = jiraConfig.getTemplateLabel(tKey);
                    String text = jiraConfig.getTemplateText(tKey);
                    if (label != null && text != null) {
                        if ("release_mgmt".equals(tKey)) {
                            MenuItem item = new MenuItem(label);
                            item.setOnAction(al -> {
                                mainFrame.showPanel("Task Builder");
                                TaskBuilderPanel tbp = mainFrame.getTaskBuilderPanel();
                                if (tbp != null) {
                                    tbp.setParentTicket(""); // Clear default parent field
                                    
                                    String[] lines = text.split("\n");
                                    StringBuilder defs = new StringBuilder();
                                    StringBuilder content = new StringBuilder();
                                    String sep = "******************************************************************";
                                    
                                    for (String l : lines) {
                                        if (l.startsWith("DEFAULT_")) {
                                            defs.append(l).append("\n");
                                        } else if (l.startsWith("******")) {
                                            sep = l;
                                        } else {
                                            content.append(l).append("\n");
                                        }
                                    }
                                    
                                    StringBuilder finalSb = new StringBuilder(defs);
                                    if (defs.length() > 0) finalSb.append("\n");
                                    
                                    for (String key : keys) {
                                        finalSb.append(content);
                                        finalSb.append("parent: ").append(key).append("\n");
                                        finalSb.append(sep).append("\n\n");
                                    }
                                    tbp.setInputAreaText(finalSb.toString().trim());
                                }
                            });
                            contextMenu.getItems().add(item);

                            MenuItem itemWithCIs = new MenuItem(label + " with CIs");
                            itemWithCIs.setOnAction(al -> {
                                Alert alert = new Alert(Alert.AlertType.CONFIRMATION, 
                                    "Do you want to include the issue keys of the sub-tasks (e.g., JRS-1234) on the right of the CI and DLT lines?", 
                                    ButtonType.YES, ButtonType.NO);
                                alert.setTitle("Include Issue Keys");
                                alert.setHeaderText(null);
                                java.util.Optional<ButtonType> result = alert.showAndWait();
                                boolean includeKeys = result.isPresent() && result.get() == ButtonType.YES;
                                buildReleaseMgmtWithCIs(keys, includeKeys);
                            });
                            contextMenu.getItems().add(itemWithCIs);
                        } else {
                            MenuItem item = new MenuItem(label);
                            item.setOnAction(al -> {
                                mainFrame.showPanel("Task Builder");
                                TaskBuilderPanel tbp = mainFrame.getTaskBuilderPanel();
                                if (tbp != null) {
                                    tbp.setInputAreaText("PARENT_TICKET:" + selectedIssueKey + "\n" + text);
                                }
                            });
                            contextMenu.getItems().add(item);
                        }
                    }
                }
            }

            // ORCHESTRATOR WORKFLOWS
            if (jiraConfig.isTabEnabled("WorkflowOrchestrator")) {
                contextMenu.getItems().add(new SeparatorMenuItem());
                Menu workflowMenu = new Menu("Run Workflow");
                tso.usmc.jira.workflow.WorkflowManager wm = new tso.usmc.jira.workflow.WorkflowManager();
                List<String> recipes = wm.listWorkflows();
                
                for (String rName : recipes) {
                    try {
                        tso.usmc.jira.workflow.WorkflowRecipe recipe = wm.loadWorkflow(rName);
                        if (recipe != null && (recipe.getJqlQuery() == null || recipe.getJqlQuery().trim().isEmpty())) {
                            MenuItem item = new MenuItem(rName);
                            item.setOnAction(al -> {
                                if (hasPrompts(recipe)) {
                                    mainFrame.showPanel("Workflow Orchestrator");
                                    WorkflowOrchestratorPanel wop = mainFrame.getWorkflowOrchestratorPanel();
                                    if (wop != null) {
                                        wop.setRunnerIssueKey(rName, selectedIssueKey);
                                    }
                                } else {
                                    WorkflowOrchestratorPanel wop = mainFrame.getWorkflowOrchestratorPanel();
                                    if (wop != null) {
                                        wop.runWorkflowDirectly(rName, selectedIssueKey);
                                    }
                                }
                            });
                            workflowMenu.getItems().add(item);
                        }
                    } catch (Exception ignored) {}
                }
                if (!workflowMenu.getItems().isEmpty()) {
                    contextMenu.getItems().add(workflowMenu);
                }
            }
        });
    }

    private void executeJql() {
        String jql = jqlArea.getText().trim();
        if (jql.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Input Error", "JQL query cannot be empty.");
            return;
        }

        statusLabel.setText("Executing query...");
        resultsTable.getColumns().clear();
        resultsTable.getItems().clear();

        ExecutionService.submit(() -> {
            try {
                List<String> fields = new ArrayList<>();
                String fieldsText = fieldsField.getText().trim();
                if (!fieldsText.isEmpty()) {
                    for (String f : fieldsText.split("\\s*,\\s*")) {
                        fields.add(f);
                    }
                }

                JqlExecutionEngine engine = new JqlExecutionEngine(mainFrame.getService());
                String rawResponse = engine.executeJql(
                    mainFrame.getBaseUrl(),
                    jql,
                    fields.isEmpty() ? null : fields,
                    500
                );

                JSONObject responseJson = new JSONObject(rawResponse);
                JSONArray issues = responseJson.getJSONArray("issues");
                
                if (issues.length() == 0) {
                    Platform.runLater(() -> statusLabel.setText("Query executed successfully. No issues found."));
                    return;
                }

                String[] columns = fieldsText.isEmpty() 
                    ? JSONObject.getNames(issues.getJSONObject(0).getJSONObject("fields")) 
                    : fieldsText.split("\\s*,\\s*");
                
                Platform.runLater(() -> {
                    // Rebuild columns
                    for (int i = 0; i < columns.length; i++) {
                        final int colIndex = i;
                        TableColumn<ObservableList<String>, String> column = new TableColumn<>(columns[i]);
                        column.setCellValueFactory(cellData -> {
                            ObservableList<String> row = cellData.getValue();
                            if (row != null && colIndex < row.size()) {
                                return new SimpleStringProperty(row.get(colIndex));
                            }
                            return new SimpleStringProperty("");
                        });

                        column.setCellFactory(tc -> {
                            TableCell<ObservableList<String>, String> cell = new TableCell<ObservableList<String>, String>() {
                                @Override
                                protected void updateItem(String item, boolean empty) {
                                    super.updateItem(item, empty);
                                    if (empty || item == null) {
                                        setText(null);
                                        setGraphic(null);
                                    } else {
                                        setText(item);
                                    }
                                }
                            };

                            cell.setOnMousePressed(e -> {
                                if (e.isPrimaryButtonDown() && cell.getTableRow() != null) {
                                    resultsTable.requestFocus();
                                    dragStartRow = cell.getTableRow().getIndex();
                                    dragStartCol = resultsTable.getColumns().indexOf(cell.getTableColumn());
                                    isDragSelecting = false;
                                }
                            });

                            cell.setOnDragDetected(e -> {
                                if (e.isPrimaryButtonDown() && cell.getTableRow() != null) {
                                    isDragSelecting = true;
                                    dragInitialSelection = new ArrayList<>(resultsTable.getSelectionModel().getSelectedCells());
                                    cell.startFullDrag();
                                    e.consume();
                                }
                            });

                            cell.setOnMouseDragEntered(e -> {
                                if (isDragSelecting && cell.getTableRow() != null) {
                                    int currentRow = cell.getTableRow().getIndex();
                                    int currentCol = resultsTable.getColumns().indexOf(cell.getTableColumn());
                                    if (currentRow >= 0 && currentCol >= 0) {
                                        updateDragSelection(currentRow, currentCol);
                                        e.consume();
                                    }
                                }
                            });

                            cell.setOnMouseReleased(e -> {
                                if (isDragSelecting) {
                                    isDragSelecting = false;
                                    e.consume();
                                }
                            });

                            return cell;
                        });

                        resultsTable.getColumns().add(column);
                    }

                    // Populate rows
                    for (int i = 0; i < issues.length(); i++) {
                        JSONObject issue = issues.getJSONObject(i);
                        ObservableList<String> rowValues = FXCollections.observableArrayList();

                        for (int j = 0; j < columns.length; j++) {
                            rowValues.add(getFieldValue(issue, columns[j]));
                        }
                        resultsTable.getItems().add(rowValues);
                    }
                    statusLabel.setText("Success! Found " + issues.length() + " issues. (Max 500 displayed)");
                });

            } catch (Exception ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Execution Error", "API Error:\n" + ex.getMessage() + "\n\n" + sw.toString());
                    statusLabel.setText("Error executing JQL.");
                });
            }
        });
    }

    private String getFieldValue(JSONObject issue, String fieldName) {
        if ("key".equalsIgnoreCase(fieldName)) {
            return issue.optString("key", "N/A");
        }

        if (!issue.has("fields")) return "N/A";
        JSONObject fields = issue.getJSONObject("fields");

        if ("issuelinks".equalsIgnoreCase(fieldName)) {
            if (!fields.has("issuelinks") || fields.isNull("issuelinks") || fields.getJSONArray("issuelinks").length() == 0) {
                return "---";
            }
            
            JSONArray links = fields.getJSONArray("issuelinks");
            StringBuilder formattedLinks = new StringBuilder();
            
            for (int i = 0; i < links.length(); i++) {
                JSONObject link = links.getJSONObject(i);
                JSONObject linkType = link.getJSONObject("type");
                
                String linkKey = "N/A";
                String linkDescription = "";
                
                if (link.has("inwardIssue")) {
                    linkDescription = linkType.getString("inward");
                    linkKey = link.getJSONObject("inwardIssue").getString("key");
                } else if (link.has("outwardIssue")) {
                    linkDescription = linkType.getString("outward");
                    linkKey = link.getJSONObject("outwardIssue").getString("key");
                }
                
                if (i > 0) {
                    formattedLinks.append(", ");
                }
                formattedLinks.append(linkDescription).append(" ").append(linkKey);
            }
            return formattedLinks.toString();
        }

        if (!fields.has(fieldName) || fields.isNull(fieldName)) {
            return "---";
        }

        Object field = fields.get(fieldName);

        if (field instanceof JSONArray) {
            JSONArray array = (JSONArray) field;
            if (array.length() == 0) return "---";
            java.util.List<String> values = new java.util.ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.get(i);
                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    if (obj.has("name")) values.add(obj.getString("name"));
                    else if (obj.has("value")) values.add(obj.getString("value"));
                    else if (obj.has("displayName")) values.add(obj.getString("displayName"));
                    else values.add("[Object]");
                } else {
                    values.add(item.toString());
                }
            }
            return String.join(", ", values);
        }

        if (field instanceof JSONObject) {
            JSONObject nestedObj = (JSONObject) field;
            if (nestedObj.has("name")) {
                return nestedObj.getString("name");
            } else if (nestedObj.has("displayName")) {
                return nestedObj.getString("displayName");
            } else {
                return "[Object]";
            }
        }
        return field.toString();
    }

    private static class ReleaseMgmtData {
        java.util.Set<String> cis = new java.util.TreeSet<>();
        java.util.List<String> dlts = new java.util.ArrayList<>();
    }

    private void buildReleaseMgmtWithCIs(java.util.List<String> keys, boolean includeKeys) {
        statusLabel.setText("Building release management with CIs...");
        
        ExecutionService.submit(() -> {
            try {
                String text = jiraConfig.getTemplateText("release_mgmt");
                if (text == null) {
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Release management template not found."));
                    return;
                }
                
                String[] lines = text.split("\n");
                StringBuilder defs = new StringBuilder();
                StringBuilder content = new StringBuilder();
                String sep = "******************************************************************";
                
                for (String l : lines) {
                    if (l.startsWith("DEFAULT_")) {
                        defs.append(l).append("\n");
                    } else if (l.startsWith("******")) {
                        sep = l;
                    } else {
                        content.append(l).append("\n");
                    }
                }
                
                StringBuilder finalSb = new StringBuilder(defs);
                if (defs.length() > 0) finalSb.append("\n");
                
                for (String key : keys) {
                    ReleaseMgmtData data = fetchReleaseMgmtData(key, includeKeys);
                    String ciLines = String.join("\n", data.cis);
                    String dltLines = String.join("\n", data.dlts);
                    if (!dltLines.isEmpty()) {
                        dltLines += "\n";
                    }
                    
                    String issueContent = content.toString();
                    if (issueContent.contains("{{CI}}")) {
                        issueContent = issueContent.replace("{{CI}}", ciLines);
                    } else if (issueContent.contains("CIs:")) {
                        issueContent = issueContent.replace("CIs:", "CIs:\n" + ciLines);
                    } else {
                        issueContent = issueContent + "\nCIs:\n" + ciLines;
                    }

                    if (issueContent.contains("{{DLT}}")) {
                        issueContent = issueContent.replace("{{DLT}}", dltLines);
                    } else if (issueContent.contains("DLT:")) {
                        issueContent = issueContent.replace("DLT:", "DLT:\n" + dltLines);
                    } else {
                        issueContent = issueContent + "\nDLT:\n" + dltLines;
                    }
                    
                    finalSb.append(issueContent);
                    finalSb.append("parent: ").append(key).append("\n");
                    finalSb.append(sep).append("\n\n");
                }
                
                Platform.runLater(() -> {
                    mainFrame.showPanel("Task Builder");
                    TaskBuilderPanel tbp = mainFrame.getTaskBuilderPanel();
                    if (tbp != null) {
                        tbp.setParentTicket(""); // Clear default parent field
                        tbp.setInputAreaText(finalSb.toString().trim());
                    }
                    statusLabel.setText("Release management with CIs built successfully.");
                });
                
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Error", "Error building release management with CIs: " + ex.getMessage());
                    statusLabel.setText("Error building release management.");
                });
            }
        });
    }

    private ReleaseMgmtData fetchReleaseMgmtData(String key, boolean includeKeys) throws Exception {
        ReleaseMgmtData data = new ReleaseMgmtData();
        
        String url = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "?expand=names";
        String response = mainFrame.getService().executeRequest(url, "GET", null);
        JSONObject issueJson = new JSONObject(response);
        JSONObject fields = issueJson.optJSONObject("fields");
        if (fields == null) return data;
        
        String epicKey = null;
        JSONObject issueTypeObj = fields.optJSONObject("issuetype");
        if (issueTypeObj != null && "Epic".equalsIgnoreCase(issueTypeObj.optString("name"))) {
            epicKey = key;
        } else {
            JSONObject namesObj = issueJson.optJSONObject("names");
            if (namesObj != null) {
                for (String fieldId : namesObj.keySet()) {
                    if ("Epic Link".equalsIgnoreCase(namesObj.getString(fieldId))) {
                        epicKey = fields.optString(fieldId);
                        if (epicKey != null && epicKey.trim().isEmpty()) {
                            epicKey = null;
                        }
                        break;
                    }
                }
            }
        }
        
        java.util.List<String> keysToQuery = new java.util.ArrayList<>();
        if (epicKey != null) {
            epicKey = epicKey.trim();
            String searchUrl = mainFrame.getBaseUrl() + "/rest/api/2/search?jql=" + 
                              java.net.URLEncoder.encode("\"Epic Link\" = " + epicKey, "UTF-8") + 
                              "&fields=key&maxResults=500";
            String searchResp = mainFrame.getService().executeRequest(searchUrl, "GET", null);
            JSONObject searchJson = new JSONObject(searchResp);
            JSONArray issuesArray = searchJson.optJSONArray("issues");
            if (issuesArray != null) {
                for (int i = 0; i < issuesArray.length(); i++) {
                    JSONObject issue = issuesArray.getJSONObject(i);
                    String k = issue.optString("key");
                    if (k != null && !k.isEmpty()) {
                        keysToQuery.add(k);
                    }
                }
            }
            keysToQuery.add(epicKey);
        } else {
            keysToQuery.add(key);
        }
        
        if (!keysToQuery.isEmpty()) {
            String jql = "parent in (" + String.join(",", keysToQuery) + ")";
            String searchUrl = mainFrame.getBaseUrl() + "/rest/api/2/search?jql=" + 
                              java.net.URLEncoder.encode(jql, "UTF-8") + 
                              "&fields=summary,issuetype,comment&maxResults=500";
            String searchResp = mainFrame.getService().executeRequest(searchUrl, "GET", null);
            JSONObject searchJson = new JSONObject(searchResp);
            JSONArray subTasksArray = searchJson.optJSONArray("issues");
            
            if (subTasksArray != null) {
                String ciPattern = String.join("|", jiraConfig.getCiTypes());
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(" + ciPattern + ")[ \\t]+(.+)$", java.util.regex.Pattern.CASE_INSENSITIVE);
                for (int i = 0; i < subTasksArray.length(); i++) {
                    JSONObject subTask = subTasksArray.getJSONObject(i);
                    String subTaskKey = subTask.optString("key");
                    JSONObject subTaskFields = subTask.optJSONObject("fields");
                    if (subTaskFields != null) {
                        String summary = subTaskFields.optString("summary", "").trim();
                        java.util.regex.Matcher m = pattern.matcher(summary);
                        if (m.find()) {
                            String type = m.group(1).toUpperCase();
                            String ciName = m.group(2).trim();
                            String entry = type + " " + ciName;
                            if (includeKeys && subTaskKey != null && !subTaskKey.isEmpty()) {
                                entry += " (" + subTaskKey + ")";
                            }
                            data.cis.add(entry);
                        }
                        
                        if (summary.toUpperCase().startsWith("DLT")) {
                            JSONArray commentsArray = null;
                            JSONObject commentObj = subTaskFields.optJSONObject("comment");
                            if (commentObj != null) {
                                commentsArray = commentObj.optJSONArray("comments");
                            }
                            
                            if ((commentsArray == null || commentsArray.length() == 0) && subTaskKey != null && !subTaskKey.isEmpty()) {
                                try {
                                    String commentUrl = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + subTaskKey + "/comment";
                                    String commentResp = mainFrame.getService().executeRequest(commentUrl, "GET", null);
                                    JSONObject commentJson = new JSONObject(commentResp);
                                    commentsArray = commentJson.optJSONArray("comments");
                                } catch (Exception ex) {
                                    System.err.println("Failed to fetch comments for " + subTaskKey + ": " + ex.getMessage());
                                }
                            }
                            
                            String changeNum = extractChangeNumberFromComments(commentsArray);
                            String entry;
                            if (changeNum != null && !changeNum.isEmpty()) {
                                entry = summary + " change " + changeNum;
                            } else {
                                entry = summary;
                            }
                            if (includeKeys && subTaskKey != null && !subTaskKey.isEmpty()) {
                                entry += " (" + subTaskKey + ")";
                            }
                            data.dlts.add(entry);
                        }
                    }
                }
            }
        }
        
        return data;
    }

    private String extractChangeNumberFromComments(JSONArray comments) {
        if (comments == null) return null;
        java.util.List<String> changeNumbers = new java.util.ArrayList<>();
        for (int i = 0; i < comments.length(); i++) {
            JSONObject c = comments.getJSONObject(i);
            String body = c.optString("body", "");
            if (body.toLowerCase().contains("has been released")) {
                java.util.List<String> nums = parseChangeNumbers(body);
                for (String num : nums) {
                    if (!changeNumbers.contains(num)) {
                        changeNumbers.add(num);
                    }
                }
            }
        }
        if (changeNumbers.isEmpty()) {
            return null;
        }
        return String.join(", ", changeNumbers);
    }

    private java.util.List<String> parseChangeNumbers(String body) {
        java.util.List<String> results = new java.util.ArrayList<>();
        java.util.regex.Pattern dottedPattern = java.util.regex.Pattern.compile("\\b\\d+(?:\\.\\d+)+\\b");
        
        int changeIdx = body.toLowerCase().indexOf("change");
        int hashIdx = body.indexOf("#");
        
        int startIndex = -1;
        if (changeIdx != -1 && hashIdx != -1) {
            startIndex = Math.min(changeIdx, hashIdx);
        } else if (changeIdx != -1) {
            startIndex = changeIdx;
        } else if (hashIdx != -1) {
            startIndex = hashIdx;
        }
        
        if (startIndex != -1) {
            String sub = body.substring(startIndex);
            java.util.regex.Matcher m = dottedPattern.matcher(sub);
            while (m.find()) {
                String val = m.group();
                if (!results.contains(val)) {
                    results.add(val);
                }
            }
        }
        
        if (results.isEmpty()) {
            java.util.regex.Matcher m = dottedPattern.matcher(body);
            while (m.find()) {
                String val = m.group();
                if (!results.contains(val)) {
                    results.add(val);
                }
            }
        }
        return results;
    }

    private void populateWorkflowsMenu() {
        workflowsBtn.getItems().clear();

        int keyColumnIndex = findKeyColumnIndex();
        if (keyColumnIndex == -1) {
            MenuItem item = new MenuItem("Error: No 'key' column");
            item.setDisable(true);
            workflowsBtn.getItems().add(item);
            return;
        }

        ObservableList<ObservableList<String>> selectedRows = resultsTable.getSelectionModel().getSelectedItems();
        if (selectedRows.isEmpty()) {
            MenuItem item = new MenuItem("Select issues in table first");
            item.setDisable(true);
            workflowsBtn.getItems().add(item);
            return;
        }

        java.util.List<String> keys = new java.util.ArrayList<>();
        for (ObservableList<String> r : selectedRows) {
            if (r != null && keyColumnIndex < r.size()) {
                keys.add(r.get(keyColumnIndex));
            }
        }
        String issueKeys = String.join(",", keys);

        tso.usmc.jira.workflow.WorkflowManager wm = new tso.usmc.jira.workflow.WorkflowManager();
        java.util.List<String> recipes = wm.listWorkflows();

        for (String rName : recipes) {
            try {
                tso.usmc.jira.workflow.WorkflowRecipe recipe = wm.loadWorkflow(rName);
                if (recipe != null && (recipe.getJqlQuery() == null || recipe.getJqlQuery().trim().isEmpty())) {
                    MenuItem item = new MenuItem(rName);
                    item.setOnAction(al -> {
                        if (hasPrompts(recipe)) {
                            mainFrame.showPanel("Workflow Orchestrator");
                            WorkflowOrchestratorPanel wop = mainFrame.getWorkflowOrchestratorPanel();
                            if (wop != null) {
                                wop.setRunnerIssueKey(rName, issueKeys);
                            }
                        } else {
                            WorkflowOrchestratorPanel wop = mainFrame.getWorkflowOrchestratorPanel();
                            if (wop != null) {
                                wop.runWorkflowDirectly(rName, issueKeys);
                            }
                        }
                    });
                    workflowsBtn.getItems().add(item);
                }
            } catch (Exception ignored) {}
        }

        if (workflowsBtn.getItems().isEmpty()) {
            MenuItem empty = new MenuItem("No workflows available");
            empty.setDisable(true);
            workflowsBtn.getItems().add(empty);
        }
    }

    private boolean hasPrompts(tso.usmc.jira.workflow.WorkflowRecipe recipe) {
        for (tso.usmc.jira.workflow.WorkflowStep step : recipe.getSteps()) {
            if (step instanceof tso.usmc.jira.workflow.CreateStep) {
                tso.usmc.jira.workflow.CreateStep cs = (tso.usmc.jira.workflow.CreateStep) step;
                String pk = cs.getProjectKey();
                String it = cs.getIssueType();
                if ((pk != null && (pk.contains(",") || pk.contains("[config:") || pk.contains("[choice:"))) ||
                    (it != null && (it.contains(",") || it.contains("[config:") || it.contains("[choice:")))) {
                    return true;
                }
            }
            if (step instanceof tso.usmc.jira.workflow.WorklogStep) {
                tso.usmc.jira.workflow.WorklogStep ws = (tso.usmc.jira.workflow.WorklogStep) step;
                if (ws.isPromptAtRuntime()) return true;
                String ts = ws.getTimeSpent();
                String c = ws.getComment();
                String s = ws.getStarted();
                if ((ts != null && (ts.contains(",") || ts.contains("[config:") || ts.contains("[choice:"))) ||
                    (c != null && (c.contains(",") || c.contains("[config:") || c.contains("[choice:"))) ||
                    (s != null && (s.contains(",") || s.contains("[config:") || s.contains("[choice:")))) {
                    return true;
                }
            }
            if (step instanceof tso.usmc.jira.workflow.AttachmentStep) {
                if (((tso.usmc.jira.workflow.AttachmentStep) step).isPromptAtRuntime()) return true;
            }
            if (step instanceof tso.usmc.jira.workflow.TransitionStep) {
                tso.usmc.jira.workflow.TransitionStep ts = (tso.usmc.jira.workflow.TransitionStep) step;
                String status = ts.getTargetStatus();
                if (status != null && status.contains(",")) return true;
            }
            if (step instanceof tso.usmc.jira.workflow.CommentStep) {
                if (((tso.usmc.jira.workflow.CommentStep) step).isPromptAtRuntime()) return true;
            }
            if (step instanceof tso.usmc.jira.workflow.AssetStep) {
                if (((tso.usmc.jira.workflow.AssetStep) step).isPromptOptions()) return true;
            }
            for (tso.usmc.jira.workflow.FieldAction fa : step.getFieldActions().values()) {
                if (fa.getMode() == tso.usmc.jira.workflow.FieldAction.MappingMode.PROMPT) {
                    return true;
                }
            }
        }
        return false;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void updateDragSelection(int currentRow, int currentCol) {
        if (dragStartRow < 0 || dragStartCol < 0) return;
        
        resultsTable.getSelectionModel().clearSelection();
        
        // Restore initial selection if Ctrl was held
        for (TablePosition pos : dragInitialSelection) {
            resultsTable.getSelectionModel().select(pos.getRow(), pos.getTableColumn());
        }
        
        int minRow = Math.min(dragStartRow, currentRow);
        int maxRow = Math.max(dragStartRow, currentRow);
        int minCol = Math.min(dragStartCol, currentCol);
        int maxCol = Math.max(dragStartCol, currentCol);
        
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                TableColumn<ObservableList<String>, ?> col = resultsTable.getColumns().get(c);
                resultsTable.getSelectionModel().select(r, col);
            }
        }
    }

    private void copySelectionToClipboard() {
        List<TablePosition> selected = new ArrayList<>(resultsTable.getSelectionModel().getSelectedCells());
        if (selected.isEmpty()) return;

        // Sort by row index, then by column index
        selected.sort((a, b) -> {
            if (a.getRow() != b.getRow()) {
                return Integer.compare(a.getRow(), b.getRow());
            }
            int colA = resultsTable.getColumns().indexOf(a.getTableColumn());
            int colB = resultsTable.getColumns().indexOf(b.getTableColumn());
            return Integer.compare(colA, colB);
        });

        StringBuilder sb = new StringBuilder();
        int lastRow = -1;
        
        for (TablePosition pos : selected) {
            int row = pos.getRow();
            if (lastRow == -1) {
                lastRow = row;
            } else if (row != lastRow) {
                sb.append("\n");
                lastRow = row;
            } else {
                sb.append("\t");
            }
            
            // Get the value of the cell
            ObservableList<String> rowData = resultsTable.getItems().get(row);
            int colIndex = resultsTable.getColumns().indexOf(pos.getTableColumn());
            String val = "";
            if (rowData != null && colIndex >= 0 && colIndex < rowData.size()) {
                val = rowData.get(colIndex);
            }
            sb.append(val != null ? val : "");
        }

        // Put onto system clipboard
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(sb.toString());
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    }

    private void populateActionsMenu() {
        actionsBtn.getItems().clear();

        MenuItem manageFuncsItem = new MenuItem("Manage Custom JQL Functions...");
        manageFuncsItem.setOnAction(e -> showManageCustomFunctionsDialog());
        actionsBtn.getItems().add(manageFuncsItem);
        actionsBtn.getItems().add(new SeparatorMenuItem());

        int keyColumnIndex = findKeyColumnIndex();
        if (keyColumnIndex == -1) {
            MenuItem item = new MenuItem("Error: No 'key' column");
            item.setDisable(true);
            actionsBtn.getItems().add(item);
            return;
        }

        ObservableList<ObservableList<String>> selectedRows = resultsTable.getSelectionModel().getSelectedItems();
        if (selectedRows.isEmpty()) {
            MenuItem item = new MenuItem("Select issues in table first");
            item.setDisable(true);
            actionsBtn.getItems().add(item);
            return;
        }

        java.util.List<String> keys = new java.util.ArrayList<>();
        for (ObservableList<String> row : selectedRows) {
            if (row != null && keyColumnIndex < row.size()) {
                keys.add(row.get(keyColumnIndex));
            }
        }
        if (keys.isEmpty()) {
            MenuItem item = new MenuItem("Select issues in table first");
            item.setDisable(true);
            actionsBtn.getItems().add(item);
            return;
        }

        String selectedIssueKey = String.join(",", keys);

        // 1. Build Release Management options if TaskBuilder tab is enabled
        if (jiraConfig.isTabEnabled("TaskBuilder")) {
            String[] templateKeys = jiraConfig.getTemplateKeys();
            for (String tKey : templateKeys) {
                String label = jiraConfig.getTemplateLabel(tKey);
                String text = jiraConfig.getTemplateText(tKey);
                if (label != null && text != null) {
                    if ("release_mgmt".equals(tKey)) {
                        MenuItem item = new MenuItem(label);
                        item.setOnAction(al -> {
                            mainFrame.showPanel("Task Builder");
                            TaskBuilderPanel tbp = mainFrame.getTaskBuilderPanel();
                            if (tbp != null) {
                                tbp.setParentTicket(""); // Clear default parent field
                                
                                String[] lines = text.split("\n");
                                StringBuilder defs = new StringBuilder();
                                StringBuilder content = new StringBuilder();
                                String sep = "******************************************************************";
                                
                                for (String l : lines) {
                                    if (l.startsWith("DEFAULT_")) {
                                        defs.append(l).append("\n");
                                    } else if (l.startsWith("******")) {
                                        sep = l;
                                    } else {
                                        content.append(l).append("\n");
                                    }
                                }
                                
                                StringBuilder finalSb = new StringBuilder(defs);
                                if (defs.length() > 0) finalSb.append("\n");
                                
                                for (String key : keys) {
                                    finalSb.append(content);
                                    finalSb.append("parent: ").append(key).append("\n");
                                    finalSb.append(sep).append("\n\n");
                                }
                                tbp.setInputAreaText(finalSb.toString().trim());
                            }
                        });
                        actionsBtn.getItems().add(item);

                        MenuItem itemWithCIs = new MenuItem(label + " with CIs");
                        itemWithCIs.setOnAction(al -> {
                            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, 
                                "Do you want to include the issue keys of the sub-tasks (e.g., JRS-1234) on the right of the CI and DLT lines?", 
                                ButtonType.YES, ButtonType.NO);
                            alert.setTitle("Include Issue Keys");
                            alert.setHeaderText(null);
                            java.util.Optional<ButtonType> result = alert.showAndWait();
                            boolean includeKeys = result.isPresent() && result.get() == ButtonType.YES;
                            buildReleaseMgmtWithCIs(keys, includeKeys);
                        });
                        actionsBtn.getItems().add(itemWithCIs);
                    } else {
                        MenuItem item = new MenuItem(label);
                        item.setOnAction(al -> {
                            mainFrame.showPanel("Task Builder");
                            TaskBuilderPanel tbp = mainFrame.getTaskBuilderPanel();
                            if (tbp != null) {
                                tbp.setInputAreaText("PARENT_TICKET:" + selectedIssueKey + "\n" + text);
                            }
                        });
                        actionsBtn.getItems().add(item);
                    }
                }
            }
        }

        // 2. Bulk Actions option if BulkActions tab is enabled
        if (jiraConfig.isTabEnabled("BulkActions")) {
            if (!actionsBtn.getItems().isEmpty()) {
                actionsBtn.getItems().add(new SeparatorMenuItem());
            }
            MenuItem bulkActionItem = new MenuItem("Bulk Actions");
            bulkActionItem.setOnAction(al -> {
                mainFrame.showPanel("Bulk Actions");
                BulkActionPanel bap = mainFrame.getBulkActionPanel();
                if (bap != null) {
                    bap.setIssueKeys(keys);
                }
            });
            actionsBtn.getItems().add(bulkActionItem);
        }
    }

    private void showManageCustomFunctionsDialog() {
        try {
            JqlExecutionEngine engine = new JqlExecutionEngine(mainFrame.getService());
            JqlCustomFunctionsDialog dialog = new JqlCustomFunctionsDialog(mainFrame.getPrimaryStage(), engine);
            dialog.showAndWait();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Initialization Error", "Failed to initialize JQL Execution Engine: " + e.getMessage());
        }
    }
}
