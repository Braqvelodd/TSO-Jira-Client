package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraIssueService;
import tso.usmc.jira.util.ExecutionService;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BulkActionPanel extends BorderPane {

    private final JiraApiClientGui mainFrame;
    private final TextArea issueKeysArea = new TextArea();
    private final ComboBox<String> actionCombo = new ComboBox<>();
    private final TextField actionValueField = new TextField();
    private final Button executeBtn = new Button("Run Bulk Action");
    
    private final TableView<BulkResult> resultsTable = new TableView<>();
    private final Label statusLabel = new Label("Enter issue keys (one per line) and configure the action.");

    public static class BulkResult {
        public final SimpleStringProperty key;
        public final SimpleStringProperty action;
        public final SimpleStringProperty result;

        public BulkResult(String key, String action, String result) {
            this.key = new SimpleStringProperty(key);
            this.action = new SimpleStringProperty(action);
            this.result = new SimpleStringProperty(result);
        }
    }

    public BulkActionPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setPadding(new Insets(10));

        // --- Left: Input Area ---
        VBox leftPanel = new VBox(5);
        leftPanel.getStyleClass().add("card");
        leftPanel.setPadding(new Insets(10));
        leftPanel.setPrefWidth(250);
        
        Label leftTitle = new Label("1. Issue Keys");
        leftTitle.getStyleClass().add("card-title");
        issueKeysArea.setPrefRowCount(25);
        issueKeysArea.setMinHeight(350);
        VBox.setVgrow(issueKeysArea, Priority.ALWAYS);
        leftPanel.getChildren().addAll(leftTitle, issueKeysArea);

        // --- Center: Configuration & Results ---
        BorderPane centerPanel = new BorderPane();
        BorderPane.setMargin(centerPanel, new Insets(0, 0, 0, 10));
        
        HBox configPanel = new HBox(10);
        configPanel.getStyleClass().add("card");
        configPanel.setPadding(new Insets(10));
        
        actionCombo.getItems().addAll(
            "Transition", 
            "Change Assignee", 
            "Add Comment", 
            "Update Fix Version/s",
            "Link Issues",
            "Add Label/s",
            "Remove Label/s",
            "Edit Custom Field",
            "Log Work"
        );
        actionCombo.getSelectionModel().select(0);
        actionValueField.setPrefWidth(200);

        // Bind prompt text dynamically based on selected action
        actionCombo.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updatePromptText(newVal);
        });
        updatePromptText(actionCombo.getSelectionModel().getSelectedItem());

        configPanel.getChildren().addAll(
            new Label("Action:"),
            actionCombo,
            new Label("Value:"),
            actionValueField,
            executeBtn
        );
        
        // TableView setup
        TableColumn<BulkResult, String> colKey = new TableColumn<>("Issue Key");
        colKey.setCellValueFactory(cellData -> cellData.getValue().key);
        colKey.setPrefWidth(120);

        TableColumn<BulkResult, String> colAction = new TableColumn<>("Action");
        colAction.setCellValueFactory(cellData -> cellData.getValue().action);
        colAction.setPrefWidth(180);

        TableColumn<BulkResult, String> colResult = new TableColumn<>("Result");
        colResult.setCellValueFactory(cellData -> cellData.getValue().result);
        colResult.setPrefWidth(300);

        resultsTable.getColumns().addAll(colKey, colAction, colResult);

        VBox resultsWrapper = new VBox(5);
        resultsWrapper.getStyleClass().add("card");
        resultsWrapper.setPadding(new Insets(10));
        Label resultsTitle = new Label("Results");
        resultsTitle.getStyleClass().add("card-title");
        resultsWrapper.getChildren().addAll(resultsTitle, resultsTable);
        VBox.setVgrow(resultsTable, Priority.ALWAYS);
        BorderPane.setMargin(resultsWrapper, new Insets(10, 0, 0, 0));

        centerPanel.setTop(configPanel);
        centerPanel.setCenter(resultsWrapper);

        // --- Bottom: Status ---
        HBox statusPanel = new HBox();
        statusPanel.getStyleClass().add("status-bar");
        statusLabel.getStyleClass().add("status-text");
        statusPanel.getChildren().add(statusLabel);

        setLeft(leftPanel);
        setCenter(centerPanel);
        setBottom(statusPanel);

        executeBtn.setOnAction(e -> executeBulkAction());
    }

    private void executeBulkAction() {
        String[] keys = issueKeysArea.getText().trim().toUpperCase().split("\\s+");
        if (keys.length == 0 || (keys.length == 1 && keys[0].isEmpty())) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please enter at least one issue key.");
            return;
        }

        final String actionType = actionCombo.getSelectionModel().getSelectedItem();
        final String actionValue = actionValueField.getText().trim();

        if (actionValue.isEmpty() && !"Change Assignee".equals(actionType) && !"Update Fix Version/s".equals(actionType)) {
            showAlert(Alert.AlertType.WARNING, "Warning", "Please enter a value for the action.");
            return;
        }

        resultsTable.getItems().clear();
        setButtonsEnabled(false);
        statusLabel.setText("Starting bulk execution...");

        final int total = keys.length;
        final AtomicInteger completedCount = new AtomicInteger(0);
        final int threads = mainFrame.getJiraConfig().getParallelThreads();

        ExecutionService.submit(() -> {
            JiraIssueService issueService = null;
            try {
                issueService = mainFrame.getIssueService();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.ERROR, "Authentication Error", "Authentication error: " + e.getMessage());
                    setButtonsEnabled(true);
                });
                return;
            }

            final JiraIssueService finalService = issueService;
            ExecutorService executor = Executors.newFixedThreadPool(threads);

            for (String key : keys) {
                executor.submit(() -> {
                    try {
                        String actionDesc = "";
                        if ("Transition".equals(actionType)) {
                            finalService.transitionIssue(key, actionValue, null);
                            actionDesc = "Transition to " + actionValue;
                        } else if ("Change Assignee".equals(actionType)) {
                            String assignee = actionValue.isEmpty() ? null : actionValue;
                            if (assignee != null && (assignee.equalsIgnoreCase("CLEAR") || assignee.equalsIgnoreCase("UNASSIGN") || assignee.equalsIgnoreCase("NONE"))) {
                                assignee = null;
                            }
                            finalService.assignIssue(key, assignee);
                            actionDesc = "Assign to " + (assignee == null ? "Unassigned" : assignee);
                        } else if ("Add Comment".equals(actionType)) {
                            finalService.addComment(key, actionValue);
                            actionDesc = "Comment added";
                        } else if ("Update Fix Version/s".equals(actionType)) {
                            org.json.JSONArray fixVersionsArr = new org.json.JSONArray();
                            if (!actionValue.isEmpty() && !actionValue.equalsIgnoreCase("CLEAR") && !actionValue.equalsIgnoreCase("EMPTY") && !actionValue.equalsIgnoreCase("NONE")) {
                                String[] versionNames = actionValue.split(",");
                                for (String name : versionNames) {
                                    if (!name.trim().isEmpty()) {
                                        fixVersionsArr.put(new org.json.JSONObject().put("name", name.trim()));
                                    }
                                }
                            }
                            org.json.JSONObject fields = new org.json.JSONObject();
                            fields.put("fixVersions", fixVersionsArr);
                            finalService.updateIssue(key, fields);
                            actionDesc = "Update Fix Version/s to " + (fixVersionsArr.length() == 0 ? "None" : actionValue);
                        } else if ("Link Issues".equals(actionType)) {
                            String[] parts = actionValue.split("\\|");
                            if (parts.length != 2) {
                                throw new Exception("Value must be in format 'Link Type | Target Key'");
                            }
                            String linkType = parts[0].trim();
                            String targetKey = parts[1].trim();
                            finalService.linkIssues(key, targetKey, linkType);
                            actionDesc = "Link (" + linkType + ") to " + targetKey;
                        } else if ("Add Label/s".equals(actionType)) {
                            org.json.JSONArray updateArr = new org.json.JSONArray();
                            String[] labelNames = actionValue.split(",");
                            for (String label : labelNames) {
                                if (!label.trim().isEmpty()) {
                                    updateArr.put(new org.json.JSONObject().put("add", label.trim()));
                                }
                            }
                            org.json.JSONObject updateObj = new org.json.JSONObject().put("labels", updateArr);
                            org.json.JSONObject body = new org.json.JSONObject().put("update", updateObj);
                            finalService.updateIssueRaw(key, body);
                            actionDesc = "Added labels: " + actionValue;
                        } else if ("Remove Label/s".equals(actionType)) {
                            org.json.JSONArray updateArr = new org.json.JSONArray();
                            String[] labelNames = actionValue.split(",");
                            for (String label : labelNames) {
                                if (!label.trim().isEmpty()) {
                                    updateArr.put(new org.json.JSONObject().put("remove", label.trim()));
                                }
                            }
                            org.json.JSONObject updateObj = new org.json.JSONObject().put("labels", updateArr);
                            org.json.JSONObject body = new org.json.JSONObject().put("update", updateObj);
                            finalService.updateIssueRaw(key, body);
                            actionDesc = "Removed labels: " + actionValue;
                        } else if ("Edit Custom Field".equals(actionType)) {
                            String[] parts = actionValue.split("\\|", 2);
                            if (parts.length != 2) {
                                throw new Exception("Value must be in format 'Field ID | Value'");
                            }
                            String fieldId = parts[0].trim();
                            String rawVal = parts[1].trim();
                            Object valObj;
                            if (rawVal.equalsIgnoreCase("CLEAR") || rawVal.equalsIgnoreCase("NONE") || rawVal.equalsIgnoreCase("EMPTY")) {
                                valObj = org.json.JSONObject.NULL;
                            } else if (rawVal.startsWith("{") && rawVal.endsWith("}")) {
                                valObj = new org.json.JSONObject(rawVal);
                            } else if (rawVal.startsWith("[") && rawVal.endsWith("]")) {
                                valObj = new org.json.JSONArray(rawVal);
                            } else {
                                valObj = rawVal;
                            }
                            org.json.JSONObject fields = new org.json.JSONObject();
                            fields.put(fieldId, valObj);
                            finalService.updateIssue(key, fields);
                            actionDesc = "Updated field " + fieldId + " to " + rawVal;
                        } else if ("Log Work".equals(actionType)) {
                            String timeSpent;
                            String comment = null;
                            if (actionValue.contains("|")) {
                                String[] parts = actionValue.split("\\|", 2);
                                timeSpent = parts[0].trim();
                                comment = parts[1].trim();
                            } else {
                                timeSpent = actionValue.trim();
                            }
                            finalService.logWork(key, timeSpent, comment);
                            actionDesc = "Log work: " + timeSpent + (comment != null ? " (" + comment + ")" : "");
                        }
                        
                        addResultRow(key, actionDesc, "SUCCESS");
                    } catch (Exception e) {
                        addResultRow(key, actionType, "ERROR: " + e.getMessage());
                    } finally {
                        int current = completedCount.incrementAndGet();
                        Platform.runLater(() -> statusLabel.setText("Parallel Processing: " + current + " of " + total + " complete..."));
                    }
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException ignored) {}

            Platform.runLater(() -> {
                statusLabel.setText("Bulk execution complete. Processed " + total + " issues in parallel.");
                setButtonsEnabled(true);
            });
        });
    }

    private void addResultRow(String key, String action, String result) {
        Platform.runLater(() -> resultsTable.getItems().add(new BulkResult(key, action, result)));
    }

    private void setButtonsEnabled(boolean enabled) {
        executeBtn.setDisable(!enabled);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void updatePromptText(String action) {
        if ("Transition".equals(action)) {
            actionValueField.setPromptText("e.g., Done, In Progress");
        } else if ("Change Assignee".equals(action)) {
            actionValueField.setPromptText("e.g., username (or leave empty to unassign)");
        } else if ("Add Comment".equals(action)) {
            actionValueField.setPromptText("e.g., Comment text here");
        } else if ("Update Fix Version/s".equals(action)) {
            actionValueField.setPromptText("e.g., Rel-1.0, Rel-2.0 (or CLEAR)");
        } else if ("Link Issues".equals(action)) {
            actionValueField.setPromptText("e.g., Relates | TFS-12345 (or Blocks | TFS-54321)");
        } else if ("Add Label/s".equals(action)) {
            actionValueField.setPromptText("e.g., label1, label2");
        } else if ("Remove Label/s".equals(action)) {
            actionValueField.setPromptText("e.g., label1, label2");
        } else if ("Edit Custom Field".equals(action)) {
            actionValueField.setPromptText("e.g., customfield_10522 | Value (or JSON / CLEAR)");
        } else if ("Log Work".equals(action)) {
            actionValueField.setPromptText("e.g., 2h | Description (or just 45m)");
        }
    }
}
