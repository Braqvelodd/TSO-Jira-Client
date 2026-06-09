package tso.usmc.jira.ui;

import org.json.JSONArray;
import org.json.JSONObject;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JiraConfig;
import tso.usmc.jira.util.JiraUtils;
import tso.usmc.jira.util.ExecutionService;
import tso.usmc.jira.ui.AssigneeOption;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * A self-contained panel to automate the 5-step issue processing workflow.
 * It utilizes JavaFX controls and displays log reports in a WebView.
 */
public class WorkflowPanel extends BorderPane implements tso.usmc.jira.util.ConfigChangeListener {

    private final JiraApiClientGui mainFrame;
    private final JiraConfig jiraConfig;
    private boolean isUpdating = false;

    // --- UI Components ---
    private final Button refreshButton = new Button("Refresh Issue List");
    private final TableView<WorkflowIssueRow> resultsTable = new TableView<>();
    private final Label statusLabel = new Label("Enter a JQL query and click Execute.");

    // Options Panel Components
    private final ComboBox<String> issueTypeComboBox = new ComboBox<>();
    private final ComboBox<AssigneeOption> assigneeComboBox = new ComboBox<>();
    private final ComboBox<String> maintenanceTypeComboBox = new ComboBox<>();
    private final TextField fySummaryIssueField = new TextField();
    private final RadioButton useOriginalDueDateRadio = new RadioButton("Use Original Due Date");
    private final RadioButton useManualDueDateRadio = new RadioButton("Manual Due Date:");
    private final TextField manualDueDateField = new TextField();
    private final Button processButton = new Button("Process Selected Issue");

    // Local response pane for this panel only
    private final WebView localResponsePane = new WebView();

    public static class WorkflowIssueRow {
        private final SimpleStringProperty key;
        private final SimpleStringProperty summary;
        private final SimpleStringProperty status;

        public WorkflowIssueRow(String key, String summary, String status) {
            this.key = new SimpleStringProperty(key);
            this.summary = new SimpleStringProperty(summary);
            this.status = new SimpleStringProperty(status);
        }

        public String getKey() { return key.get(); }
        public SimpleStringProperty keyProperty() { return key; }

        public String getSummary() { return summary.get(); }
        public SimpleStringProperty summaryProperty() { return summary; }

        public String getStatus() { return status.get(); }
        public SimpleStringProperty statusProperty() { return status; }
    }

    public WorkflowPanel(JiraApiClientGui mainFrame, JiraConfig jiraConfig) {
        this.mainFrame = mainFrame;
        this.jiraConfig = jiraConfig;
        this.jiraConfig.addConfigChangeListener(this);

        // Configure ComboBoxes and Fields
        issueTypeComboBox.getItems().addAll("Utility/Extract", "FCR", "PTR", "Table Update");
        issueTypeComboBox.getSelectionModel().select(0);

        maintenanceTypeComboBox.getItems().addAll("Maintenance", "Enhancement", "Fallout");
        maintenanceTypeComboBox.getSelectionModel().select(0);

        fySummaryIssueField.setText(jiraConfig.getWorkflowFySummaryIssue());

        populateAssigneeOptions();
        
        assigneeComboBox.setCellFactory(lv -> new ListCell<AssigneeOption>() {
            @Override
            protected void updateItem(AssigneeOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getDisplayName());
                }
            }
        });
        assigneeComboBox.setButtonCell(new ListCell<AssigneeOption>() {
            @Override
            protected void updateItem(AssigneeOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getDisplayName());
                }
            }
        });

        // Set up table columns
        TableColumn<WorkflowIssueRow, String> keyCol = new TableColumn<>("Key");
        keyCol.setCellValueFactory(cellData -> cellData.getValue().keyProperty());
        keyCol.setPrefWidth(120);

        TableColumn<WorkflowIssueRow, String> summaryCol = new TableColumn<>("Summary");
        summaryCol.setCellValueFactory(cellData -> cellData.getValue().summaryProperty());
        summaryCol.setPrefWidth(400);

        TableColumn<WorkflowIssueRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
        statusCol.setPrefWidth(120);

        resultsTable.getColumns().addAll(keyCol, summaryCol, statusCol);
        resultsTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        
        resultsTable.setRowFactory(tv -> {
            TableRow<WorkflowIssueRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    WorkflowIssueRow rowData = row.getItem();
                    JiraUtils.browseIssue(mainFrame.getBaseUrl(), rowData.getKey());
                }
            });
            return row;
        });

        // Due date toggles
        ToggleGroup dueDateGroup = new ToggleGroup();
        useOriginalDueDateRadio.setToggleGroup(dueDateGroup);
        useOriginalDueDateRadio.setSelected(true);
        useManualDueDateRadio.setToggleGroup(dueDateGroup);

        manualDueDateField.setDisable(true);
        manualDueDateField.setPrefWidth(100);

        useManualDueDateRadio.setOnAction(e -> manualDueDateField.setDisable(false));
        useOriginalDueDateRadio.setOnAction(e -> manualDueDateField.setDisable(true));

        // Layout creation
        setPadding(new Insets(10));

        BorderPane topContentPanel = new BorderPane();
        HBox topButtonPanel = new HBox();
        topButtonPanel.getChildren().add(refreshButton);
        topContentPanel.setTop(topButtonPanel);
        BorderPane.setMargin(topButtonPanel, new Insets(0, 0, 10, 0));

        resultsTable.setPrefHeight(200);
        topContentPanel.setCenter(resultsTable);
        BorderPane.setMargin(resultsTable, new Insets(0, 0, 10, 0));

        // Options Grid
        GridPane optionsGrid = new GridPane();
        optionsGrid.setHgap(10);
        optionsGrid.setVgap(10);
        optionsGrid.setPadding(new Insets(10));

        optionsGrid.add(new Label("New Issue Type:"), 0, 0);
        optionsGrid.add(issueTypeComboBox, 1, 0);
        issueTypeComboBox.setMaxWidth(Double.MAX_VALUE);

        optionsGrid.add(new Label("Assign To:"), 0, 1);
        optionsGrid.add(assigneeComboBox, 1, 1);
        assigneeComboBox.setMaxWidth(Double.MAX_VALUE);

        optionsGrid.add(new Label("Maintenance Type:"), 0, 2);
        optionsGrid.add(maintenanceTypeComboBox, 1, 2);
        maintenanceTypeComboBox.setMaxWidth(Double.MAX_VALUE);

        optionsGrid.add(new Label("FY Summary Issue:"), 0, 3);
        optionsGrid.add(fySummaryIssueField, 1, 3);
        fySummaryIssueField.setMaxWidth(Double.MAX_VALUE);

        HBox dueDatePanel = new HBox(10);
        dueDatePanel.setAlignment(Pos.CENTER_LEFT);
        dueDatePanel.getChildren().addAll(useOriginalDueDateRadio, useManualDueDateRadio, manualDueDateField);
        optionsGrid.add(new Label("Extended Due Date:"), 0, 4);
        optionsGrid.add(dueDatePanel, 1, 4);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(150);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        optionsGrid.getColumnConstraints().addAll(col1, col2);

        TitledPane processPanel = new TitledPane("Processing Options", optionsGrid);
        processPanel.setCollapsible(false);

        VBox processBox = new VBox(10);
        processBox.getChildren().addAll(processPanel, processButton);
        processButton.setMaxWidth(Double.MAX_VALUE);
        processButton.setStyle("-fx-font-weight: bold;");

        topContentPanel.setBottom(processBox);

        // Split Pane
        localResponsePane.setMinHeight(250);
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(topContentPanel, localResponsePane);
        splitPane.setDividerPositions(0.5);

        // Bottom Status Bar
        HBox statusPanel = new HBox(10);
        statusPanel.getStyleClass().add("status-bar");
        statusLabel.getStyleClass().add("status-text");
        statusPanel.getChildren().add(statusLabel);

        setCenter(splitPane);
        setBottom(statusPanel);
        BorderPane.setMargin(splitPane, new Insets(0, 0, 10, 0));

        // Actions
        refreshButton.setOnAction(e -> fetchIssues());
        processButton.setOnAction(e -> startWorkflow());
    }

    private void populateAssigneeOptions() {
        assigneeComboBox.getItems().clear();

        String[] teamKeys = jiraConfig.getWorkflowTeamKeys();
        for (String key : teamKeys) {
            String name = jiraConfig.getTeamProperty(key, "name");
            String lead = jiraConfig.getTeamProperty(key, "lead");
            String component = jiraConfig.getTeamProperty(key, "component");
            String id = jiraConfig.getTeamProperty(key, "id");

            if (name != null && lead != null) {
                assigneeComboBox.getItems().add(new AssigneeOption(name, lead, component, id));
            } else {
                String details = jiraConfig.getTeamDetails(key);
                if (details != null) {
                    String[] parts = details.split("\\|");
                    if (parts.length == 4) {
                        assigneeComboBox.getItems().add(new AssigneeOption(parts[0], parts[1], parts[2], parts[3]));
                    }
                }
            }
        }
        if (!assigneeComboBox.getItems().isEmpty()) {
            assigneeComboBox.getSelectionModel().select(0);
        }
    }

    private void fetchIssues() {
        if (isUpdating)
            return;

        isUpdating = true;
        refreshButton.setDisable(true);
        processButton.setDisable(true);
        statusLabel.setText("Fetching issues from Jira...");
        resultsTable.getItems().clear();

        ExecutionService.submit(() -> {
            try {
                String jql = jiraConfig.getWorkflowJql();
                String encodedJql = URLEncoder.encode(jql, "UTF-8");
                String url = mainFrame.getBaseUrl() + "/rest/api/2/search?jql=" + encodedJql
                        + "&fields=summary,status,duedate";
                String response = mainFrame.getService().executeRequest(url, "GET", null);

                JSONObject result = new JSONObject(response);
                JSONArray issues = result.getJSONArray("issues");
                List<WorkflowIssueRow> rows = new ArrayList<>();
                for (int i = 0; i < issues.length(); i++) {
                    JSONObject issue = issues.getJSONObject(i);
                    String key = issue.getString("key");
                    String summary = issue.getJSONObject("fields").getString("summary");
                    String status = issue.getJSONObject("fields").getJSONObject("status").getString("name");
                    rows.add(new WorkflowIssueRow(key, summary, status));
                }

                Platform.runLater(() -> {
                    resultsTable.getItems().addAll(rows);
                    statusLabel.setText("Found " + issues.length() + " issues.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error fetching issues.");
                    localResponsePane.getEngine().loadContent("<html><font color='red'><b>Failed to fetch issues:</b><br>"
                            + e.getMessage() + "</font></html>");
                });
                e.printStackTrace();
            } finally {
                Platform.runLater(() -> {
                    isUpdating = false;
                    refreshButton.setDisable(false);
                    processButton.setDisable(false);
                });
            }
        });
    }

    @Override
    public void onConfigChanged() {
        Platform.runLater(() -> {
            fySummaryIssueField.setText(jiraConfig.getWorkflowFySummaryIssue());
            populateAssigneeOptions();
        });
    }

    private void startWorkflow() {
        if (isUpdating)
            return;

        WorkflowIssueRow selectedRow = resultsTable.getSelectionModel().getSelectedItem();
        if (selectedRow == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Issue Selected");
            alert.setHeaderText(null);
            alert.setContentText("Please select an issue from the table to process.");
            alert.showAndWait();
            return;
        }

        final String originalIssueKey = selectedRow.getKey();
        final String originalSummary = selectedRow.getSummary();
        final String newIssueType = issueTypeComboBox.getSelectionModel().getSelectedItem();
        final AssigneeOption selectedAssignment = assigneeComboBox.getSelectionModel().getSelectedItem();
        final String maintenanceType = maintenanceTypeComboBox.getSelectionModel().getSelectedItem();
        final String fySummaryIssue = fySummaryIssueField.getText().trim();
        final boolean useOriginalDueDate = useOriginalDueDateRadio.isSelected();
        final String manualDueDateValue = manualDueDateField.getText().trim();

        if (!useOriginalDueDate && manualDueDateValue.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Manual Due Date Missing");
            alert.setHeaderText(null);
            alert.setContentText("Please enter a manual due date or select 'Use Original Due Date'.");
            alert.showAndWait();
            return;
        }

        isUpdating = true;
        processButton.setDisable(true);
        refreshButton.setDisable(true);
        localResponsePane.getEngine().loadContent("");
        statusLabel.setText("Processing " + originalIssueKey + "...");

        ExecutionService.submit(() -> {
            ProcessIssueTask task = new ProcessIssueTask(
                    originalIssueKey, originalSummary, newIssueType,
                    selectedAssignment,
                    maintenanceType, fySummaryIssue,
                    useOriginalDueDate, manualDueDateValue);
            task.run();
        });
    }

    private class ProcessIssueTask {
        private final String originalIssueKey, originalSummary, newIssueType, maintenanceType, fySummaryIssue,
                manualDueDate;
        private final AssigneeOption selectedAssignment;
        private final boolean useOriginalDueDate;
        private final StringBuilder report;
        private boolean workflowSucceeded = true;
        private String reporterNameToUpdate;

        ProcessIssueTask(String originalIssueKey, String originalSummary, String newIssueType,
                AssigneeOption selectedAssignment, String maintenanceType, String fySummaryIssue,
                boolean useOriginalDueDate, String manualDueDate) {
            this.originalIssueKey = originalIssueKey;
            this.originalSummary = originalSummary;
            this.newIssueType = newIssueType;
            this.selectedAssignment = selectedAssignment;
            this.maintenanceType = maintenanceType;
            this.fySummaryIssue = fySummaryIssue;
            this.useOriginalDueDate = useOriginalDueDate;
            this.manualDueDate = manualDueDate;
            this.report = new StringBuilder("<html><h2>Workflow Report for " + originalIssueKey
                    + "</h2><table border='1' style='width:100%'><tr><th>Step</th><th>Action</th><th>Result</th></tr>");
        }

        public void run() {
            try {
                String originalIssueJson = mainFrame.getService().executeRequest(
                        mainFrame.getBaseUrl() + "/rest/api/2/issue/" + this.originalIssueKey
                                + "?fields=summary,status,duedate,description,reporter,attachment,issuelinks",
                        "GET", null);

                JSONObject sourceIssue = new JSONObject(originalIssueJson);

                step1_UpdateOriginalIssue();
                String newIssueKey = step2_3_5_CreateMovedClone(sourceIssue);

                if (newIssueKey != null) {
                    step3a_UpdateClonedIssue(newIssueKey, sourceIssue);
                    cloneAttachments(newIssueKey, sourceIssue);
                    cloneLinks(newIssueKey, sourceIssue);
                    step4_LinkIssues(newIssueKey);
                }

            } catch (Exception e) {
                this.workflowSucceeded = false;
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                e.printStackTrace(pw);
                String stackTrace = sw.toString();
                addReportRow("FATAL ERROR", e.getClass().getSimpleName(),
                        "<font color='red'>" + e.getMessage() + "</font>");
                report.append("</table><hr><h3>Stack Trace</h3><pre style='font-size:10px; color: #555555;'>")
                        .append(stackTrace).append("</pre></body></html>");
                e.printStackTrace();
            } finally {
                if (workflowSucceeded) {
                    report.append("</table></html>");
                }
                final String finalReport = report.toString();
                Platform.runLater(() -> {
                    localResponsePane.getEngine().loadContent(finalReport);
                    if (workflowSucceeded) {
                        statusLabel.setText("Workflow for " + originalIssueKey + " completed.");
                    } else {
                        statusLabel.setText("Workflow for " + originalIssueKey + " failed. See report for details.");
                    }
                    isUpdating = false;
                    processButton.setDisable(false);
                    refreshButton.setDisable(false);
                });
            }
        }

        private void cleanJsonForCreation(JSONObject fields) {
            final String[] readOnlyFields = {
                    "id", "self", "key", "status", "creator", "created", "updated", "duedate",
                    "resolutiondate", "workratio", "timespent", "aggregatetimespent",
                    "issuelinks", "attachment", "subtasks", "votes", "watches", "thumbnail"
            };
            for (String field : readOnlyFields)
                fields.remove(field);
        }

        private String step2_3_5_CreateMovedClone(JSONObject sourceIssue) throws Exception {
            JSONObject fieldsForCreation = new JSONObject(sourceIssue.getJSONObject("fields").toString());
            String originalDescription = fieldsForCreation.optString("description", "");
            this.reporterNameToUpdate = null;
            if (fieldsForCreation.has("reporter") && !fieldsForCreation.isNull("reporter")) {
                this.reporterNameToUpdate = fieldsForCreation.getJSONObject("reporter").getString("name");
            } else {
                addReportRow("2.2", "Capture reporter for update",
                        "<font color='orange'>Warning: Reporter field not found on original issue.</font>");
            }
            cleanJsonForCreation(fieldsForCreation);

            String projectKey = mainFrame.getJiraConfig().getCloneProjectKey();
            fieldsForCreation.put("project", new JSONObject().put("key", projectKey));
            fieldsForCreation.put("summary", originalIssueKey + " - " + originalSummary);
            fieldsForCreation.put("issuetype", new JSONObject().put("name", newIssueType));
            fieldsForCreation.put("description", originalDescription);

            String srcIssueKeyField = mainFrame.getJiraConfig().getCustomFieldId("source_issue_key", "customfield_10400");
            fieldsForCreation.put(srcIssueKeyField, originalIssueKey);

            String maintTypeField = mainFrame.getJiraConfig().getCustomFieldId("maintenance_type", "customfield_10522");
            fieldsForCreation.put(maintTypeField, new JSONObject().put("value", maintenanceType));

            String epicLinkField = mainFrame.getJiraConfig().getCustomFieldId("epic_link", "customfield_13056");
            if (fySummaryIssue != null && !fySummaryIssue.isEmpty()) {
                fieldsForCreation.put(epicLinkField, fySummaryIssue);
            }
            String createJsonBody = new JSONObject().put("fields", fieldsForCreation).toString();
            String response = mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue",
                    "POST", createJsonBody);
            String newKey = new JSONObject(response).getString("key");
            addReportRow("2, 5", "Create new issue " + newKey, "<font color='green'>Success</font>");
            return newKey;
        }

        private void step3a_UpdateClonedIssue(String newIssueKey, JSONObject sourceIssue) throws Exception {
            JSONObject fieldsToUpdate = new JSONObject();
            String designPhaseField = mainFrame.getJiraConfig().getCustomFieldId("design_phase", "customfield_10523");
            String analysisPhaseField = mainFrame.getJiraConfig().getCustomFieldId("analysis_phase", "customfield_10512");
            fieldsToUpdate.put(designPhaseField, new JSONArray().put(new JSONObject().put("value", "Design")));
            fieldsToUpdate.put(analysisPhaseField, new JSONArray().put(new JSONObject().put("value", "Analysis")));

            String assigneeId = selectedAssignment.getAssigneeJiraId();
            fieldsToUpdate.put("assignee", new JSONObject().put("name", assigneeId));
            addReportRow("3.1", "Set Assignee", "Assigning to: " + assigneeId);

            if (selectedAssignment.getComponentName() != null) {
                String componentName = selectedAssignment.getComponentName();
                String teamId = selectedAssignment.getTeamId();

                JSONArray componentsArray = new JSONArray().put(new JSONObject().put("name", componentName));
                fieldsToUpdate.put("components", componentsArray);
                String teamField = mainFrame.getJiraConfig().getCustomFieldId("team_id", "customfield_15350");
                fieldsToUpdate.put(teamField, teamId);

                addReportRow("3.2", "Set Team & Component",
                        "Component: '" + componentName + "', Team: '" + componentName + "'");
            }

            if (this.reporterNameToUpdate != null) {
                String repNameField = mainFrame.getJiraConfig().getCustomFieldId("reporter_name", "customfield_10540");
                fieldsToUpdate.put("reporter", new JSONObject().put("name", this.reporterNameToUpdate));
                fieldsToUpdate.put(repNameField, new JSONObject().put("name", this.reporterNameToUpdate));
                addReportRow("3.3", "Set Reporter Fields", "Original Reporter: " + this.reporterNameToUpdate);
            }

            String finalDueDate = null;
            if (this.useOriginalDueDate) {
                finalDueDate = sourceIssue.getJSONObject("fields").optString("duedate", null);
                addReportRow("3.4", "Due Date Choice",
                        "Using original due date: " + (finalDueDate != null ? finalDueDate : "None found"));
            } else {
                finalDueDate = this.manualDueDate;
                addReportRow("3.4", "Due Date Choice", "Using manual due date: " + finalDueDate);
            }
            if (finalDueDate != null && !finalDueDate.isEmpty()) {
                String dueDateField = mainFrame.getJiraConfig().getCustomFieldId("due_date", "customfield_10517");
                fieldsToUpdate.put("duedate", finalDueDate);
                fieldsToUpdate.put(dueDateField, finalDueDate);
            }

            String updateJsonBody = new JSONObject().put("fields", fieldsToUpdate).toString();

            try {
                mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + newIssueKey,
                        "PUT", updateJsonBody);
                addReportRow("3.5", "Update All Fields", "<font color='green'>Success</font>");
            } catch (Exception e) {
                addReportRow("3.5", "Update All Fields", "<font color='red'>Failed: " + e.getMessage() + "</font>");
            }

            if (selectedAssignment.getComponentName() == null) {
                try {
                    String transitionsJson = mainFrame.getService().executeRequest(
                            mainFrame.getBaseUrl() + "/rest/api/2/issue/" + newIssueKey + "/transitions", "GET", null);
                    String transitionId = JiraUtils.findTransitionIdByName(transitionsJson, "Unassigned Backlog");

                    if (transitionId != null) {
                        String transitionPayload = new JSONObject()
                                .put("transition", new JSONObject().put("id", transitionId)).toString();
                        mainFrame.getService().executeRequest(
                                mainFrame.getBaseUrl() + "/rest/api/2/issue/" + newIssueKey + "/transitions", "POST",
                                transitionPayload);
                        addReportRow("3.6", "Change Status",
                                "<font color='green'>Transitioned to 'Unassigned Backlog'</font>");
                    } else {
                        addReportRow("3.6", "Change Status",
                                "<font color='red'>Could not find transition named 'Unassigned Backlog'!</font>");
                    }
                } catch (Exception e) {
                    addReportRow("3.6", "Change Status",
                            "<font color='red'>Failed to transition status: " + e.getMessage() + "</font>");
                }
            }
        }

        private void step1_UpdateOriginalIssue() throws Exception {
            String fullUrl = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + this.originalIssueKey;
            String transitionsJson = mainFrame.getService().executeRequest(fullUrl + "/transitions", "GET", null);
            String transitionId = JiraUtils.findTransitionIdByName(transitionsJson, "TO: In Progress");
            if (transitionId == null)
                throw new RuntimeException("'In Progress' transition not found for " + this.originalIssueKey);
            String transitionJsonBody = new JSONObject().put("transition", new JSONObject().put("id", transitionId))
                    .toString();
            mainFrame.getService().executeRequest(fullUrl + "/transitions", "POST", transitionJsonBody);

            String resetField = mainFrame.getJiraConfig().getCustomFieldId("reset_field", "customfield_10519");
            String fieldUpdateJsonBody = new JSONObject()
                    .put("fields", new JSONObject().put(resetField, JSONObject.NULL)).toString();
            mainFrame.getService().executeRequest(fullUrl, "PUT", fieldUpdateJsonBody);
            addReportRow("1", "Update Orginal ticket", "<font color='green'>Success</font>");
        }

        private void cloneAttachments(String newIssueKey, JSONObject sourceIssue) throws Exception {
            if (!sourceIssue.getJSONObject("fields").has("attachment")
                    || sourceIssue.getJSONObject("fields").isNull("attachment")) {
                addReportRow("3.4", "Clone Attachments", "No attachments found to clone.");
                return;
            }

            JSONArray attachments = sourceIssue.getJSONObject("fields").getJSONArray("attachment");
            if (attachments.length() == 0) {
                addReportRow("3.4", "Clone Attachments", "No attachments found to clone.");
                return;
            }
            addReportRow("3.1A", "Found " + attachments.length() + " attachment(s) to clone.", "In Progress...");
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.getJSONObject(i);
                String filename = attachment.getString("filename");
                String contentUrl = attachment.getString("content");
                File tempFile = null;
                try {
                    tempFile = mainFrame.getService().downloadAttachmentToTempFile(contentUrl, filename);
                    String uploadUrl = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + newIssueKey + "/attachments";
                    mainFrame.getService().uploadAttachment(uploadUrl, tempFile, filename);
                } catch (Exception e) {
                    addReportRow("Attachment", "Cloning failed for: " + filename,
                            "<font color='red'>" + e.getMessage() + "</font>");
                    throw e;
                } finally {
                    if (tempFile != null)
                        tempFile.delete();
                }
            }
            addReportRow("3.1A", "Cloned " + attachments.length() + " attachment(s).",
                    "<font color='green'>Success</font>");
        }

        private void cloneLinks(String newIssueKey, JSONObject sourceIssue) throws Exception {
            if (!sourceIssue.getJSONObject("fields").has("issuelinks")
                    || sourceIssue.getJSONObject("fields").isNull("issuelinks"))
                return;
            JSONArray links = sourceIssue.getJSONObject("fields").getJSONArray("issuelinks");
            if (links.length() == 0)
                return;
            addReportRow("3.1B", "Found " + links.length() + " link(s) to clone.", "In Progress...");
            for (int i = 0; i < links.length(); i++) {
                JSONObject link = links.getJSONObject(i);
                String linkTypeName = link.getJSONObject("type").getString("name");
                String inwardKey = link.has("inwardIssue") ? link.getJSONObject("inwardIssue").getString("key") : null;
                String outwardKey = link.has("outwardIssue") ? link.getJSONObject("outwardIssue").getString("key")
                        : null;
                if (originalIssueKey.equals(inwardKey) || originalIssueKey.equals(outwardKey))
                    continue;
                String otherIssueKey = (outwardKey != null) ? outwardKey : inwardKey;
                if (otherIssueKey == null)
                    continue;
                try {
                    JSONObject linkPayload = new JSONObject().put("type", new JSONObject().put("name", linkTypeName));
                    if (outwardKey != null) {
                        linkPayload.put("inwardIssue", new JSONObject().put("key", newIssueKey));
                        linkPayload.put("outwardIssue", new JSONObject().put("key", otherIssueKey));
                    } else {
                        linkPayload.put("inwardIssue", new JSONObject().put("key", otherIssueKey));
                        linkPayload.put("outwardIssue", new JSONObject().put("key", newIssueKey));
                    }
                    mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issueLink", "POST",
                            linkPayload.toString());
                    addReportRow("Link", "Clone link to " + otherIssueKey, "<font color='green'>Success</font>");
                } catch (Exception e) {
                    addReportRow("Link", "Linking to " + otherIssueKey + " failed",
                            "<font color='red'>" + e.getMessage() + "</font>");
                    throw e;
                }
            }
        }

        private void step4_LinkIssues(String newIssueKey) throws Exception {
            String linkJsonBody = new JSONObject().put("type", new JSONObject().put("name", "SMARTS Link"))
                    .put("inwardIssue", new JSONObject().put("key", this.originalIssueKey))
                    .put("outwardIssue", new JSONObject().put("key", newIssueKey)).toString();
            mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issueLink", "POST",
                    linkJsonBody);
            addReportRow("4", "Link " + newIssueKey + " back to " + this.originalIssueKey,
                    "<font color='green'>Success</font>");
        }

        private void addReportRow(String step, String action, String result) {
            report.append("<tr><td>").append(step).append("</td><td>").append(action).append("</td><td>")
                    .append(result).append("</td></tr>");
            final String currentReport = report.toString() + "</table></html>";
            Platform.runLater(() -> localResponsePane.getEngine().loadContent(currentReport));
        }
    }
}
