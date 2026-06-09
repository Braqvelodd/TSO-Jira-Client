package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.util.ExecutionService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

public class TemplateExtractorPanel extends BorderPane {

    private final JiraApiClientGui mainFrame;
    private final String epicLinkFieldId;

    // UI Components
    private final TextField parentIssueField = new TextField();
    private final Button generateBtn = new Button("Generate Template from Sub-tasks");
    private final Button generateFromEpicBtn = new Button("Generate from Epic's Issues");
    private final TextArea templateArea = new TextArea();
    private final Button copyBtn = new Button("Copy to Clipboard");
    private final Label statusLabel = new Label("Enter a parent issue key (e.g., a Story) and click Generate.");

    public TemplateExtractorPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        this.epicLinkFieldId = mainFrame.getJiraConfig().getCustomFieldId("epic_link", "customfield_13056");
        setPadding(new Insets(10));

        // --- UI Setup ---
        HBox topPanel = new HBox(10);
        topPanel.getStyleClass().add("card");
        topPanel.setPadding(new Insets(10));
        
        parentIssueField.setPrefWidth(150);
        topPanel.getChildren().addAll(
            new Label("Parent Key:"),
            parentIssueField,
            generateBtn,
            generateFromEpicBtn
        );

        VBox centerPanel = new VBox(5);
        centerPanel.getStyleClass().add("card");
        centerPanel.setPadding(new Insets(10));
        BorderPane.setMargin(centerPanel, new Insets(10, 0, 10, 0));
        
        Label centerTitle = new Label("2. Generated Template (for Task Builder)");
        centerTitle.getStyleClass().add("card-title");
        
        templateArea.setStyle("-fx-font-family: monospace;");
        templateArea.setEditable(false);
        templateArea.setWrapText(true);
        templateArea.setPrefRowCount(25);
        templateArea.setMinHeight(350);
        VBox.setVgrow(templateArea, Priority.ALWAYS);

        HBox bottomActions = new HBox();
        bottomActions.setPadding(new Insets(5, 0, 0, 0));
        bottomActions.getChildren().add(copyBtn);
        
        centerPanel.getChildren().addAll(centerTitle, templateArea, bottomActions);

        HBox statusPanel = new HBox();
        statusPanel.getStyleClass().add("status-bar");
        statusLabel.getStyleClass().add("status-text");
        statusPanel.getChildren().add(statusLabel);

        setTop(topPanel);
        setCenter(centerPanel);
        setBottom(statusPanel);

        generateBtn.setOnAction(e -> generateTemplate());
        generateFromEpicBtn.setOnAction(e -> generateEpicTemplate());
        copyBtn.setOnAction(e -> copyToClipboard());
    }

    private void generateTemplate() {
        String parentKey = parentIssueField.getText().trim().toUpperCase();
        if (isInputInvalid(parentKey)) return;

        setBusyState(true, "Fetching data for " + parentKey + "...");

        ExecutionService.submit(() -> {
            try {
                JiraApiService service = mainFrame.getService();
                String baseUrl = mainFrame.getBaseUrl();

                String parentResponse = service.executeRequest(baseUrl + "/rest/api/2/issue/" + parentKey + "?fields=project,components", "GET", null);
                JSONObject parentJson = new JSONObject(parentResponse);
                String defaultComponent = getDefaultComponent(parentJson);

                String jql = "parent = " + parentKey;
                JSONObject payload = new JSONObject()
                        .put("jql", jql)
                        .put("fields", new JSONArray().put("summary").put("description").put("issuetype"))
                        .put("maxResults", 200);

                String subtaskResponse = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
                JSONObject subtaskJson = new JSONObject(subtaskResponse);
                JSONArray subtasks = subtaskJson.getJSONArray("issues");

                String templateContent = buildTemplateFromSubtasks(subtasks, defaultComponent);

                Platform.runLater(() -> {
                    updateTemplateArea(templateContent, "Success! Generated template from " + subtasks.length() + " sub-tasks.");
                    setBusyState(false, null);
                });
            } catch (Exception ex) {
                handleApiError(ex);
            }
        });
    }

    private void generateEpicTemplate() {
        final String issueKey = parentIssueField.getText().trim().toUpperCase();
        if (isInputInvalid(issueKey)) return;

        setBusyState(true, "Checking if '" + issueKey + "' is an Epic or part of one...");

        ExecutionService.submit(() -> {
            try {
                JiraApiService service = mainFrame.getService();
                String baseUrl = mainFrame.getBaseUrl();

                String fieldsToFetch = epicLinkFieldId + ",components,issuetype";
                String issueDetailsResponse = service.executeRequest(baseUrl + "/rest/api/2/issue/" + issueKey + "?fields=" + fieldsToFetch, "GET", null);
                JSONObject issueJson = new JSONObject(issueDetailsResponse);
                JSONObject fields = issueJson.getJSONObject("fields");
                final String defaultComponent = getDefaultComponent(issueJson);

                String epicKey;
                String issueType = fields.getJSONObject("issuetype").getString("name");

                if ("Epic".equalsIgnoreCase(issueType)) {
                    epicKey = issueKey;
                    updateStatus("'" + issueKey + "' is an Epic. Fetching all child issues...");
                } else {
                    epicKey = fields.optString(epicLinkFieldId, null);
                    if (epicKey == null || epicKey.isEmpty()) {
                        throw new Exception("Issue '" + issueKey + "' is not an Epic and does not belong to one. Verify the '" + epicLinkFieldId + "' custom field ID.");
                    }
                    updateStatus("Found Epic " + epicKey + ". Fetching all child issues...");
                }

                String issuesInEpicJql = String.format("'%s' = '%s'", epicLinkFieldId, epicKey);
                JSONObject epicSearchPayload = new JSONObject().put("jql", issuesInEpicJql).put("fields", new JSONArray().put("key")).put("maxResults", 500);
                String issuesInEpicResponse = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", epicSearchPayload.toString());
                JSONArray issuesInEpic = new JSONObject(issuesInEpicResponse).getJSONArray("issues");

                ArrayList<String> issueKeys = new ArrayList<>();
                for (int i = 0; i < issuesInEpic.length(); i++) {
                    issueKeys.add(issuesInEpic.getJSONObject(i).getString("key"));
                }
                updateStatus("Found " + issueKeys.size() + " issues in Epic. Fetching all their sub-tasks...");

                StringBuilder subtaskJql = new StringBuilder();
                subtaskJql.append("parent = '").append(epicKey).append("'"); 
                if (!issueKeys.isEmpty()) {
                    subtaskJql.append(" OR parent in (");
                    for (int i = 0; i < issueKeys.size(); i++) {
                        subtaskJql.append("'").append(issueKeys.get(i)).append("'");
                        if (i < issueKeys.size() - 1) {
                            subtaskJql.append(",");
                        }
                    }
                    subtaskJql.append(")");
                }
                
                JSONObject subtaskSearchPayload = new JSONObject()
                        .put("jql", subtaskJql.toString())
                        .put("fields", new JSONArray().put("summary").put("description").put("issuetype"))
                        .put("maxResults", 1000);

                String allSubtasksResponse = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", subtaskSearchPayload.toString());
                final JSONArray allSubtasks = new JSONObject(allSubtasksResponse).getJSONArray("issues");
                
                final String templateContent = buildTemplateFromSubtasks(allSubtasks, defaultComponent);
                final String finalEpicKey = epicKey;

                Platform.runLater(() -> {
                    updateTemplateArea(templateContent, "Success! Generated template from " + allSubtasks.length() + " sub-tasks in Epic " + finalEpicKey + ".");
                    setBusyState(false, null);
                });

            } catch (Exception ex) {
                handleApiError(ex);
            }
        });
    }

    private String buildTemplateFromSubtasks(JSONArray subtasks, String defaultComponent) {
        StringBuilder sb = new StringBuilder();
        sb.append("PARENT_TICKET:\n");
        sb.append("DEFAULT_TYPE:Sub-task\n");
        sb.append("DEFAULT_ASSIGNEE:\n");
        sb.append("DEFAULT_COMPONENT:").append(defaultComponent).append("\n");
        sb.append("DEFAULT_TRANSITION:\n\n");

        for (int i = 0; i < subtasks.length(); i++) {
            JSONObject subtaskFields = subtasks.getJSONObject(i).getJSONObject("fields");
            String summary = subtaskFields.optString("summary", "").trim();
            String description = subtaskFields.optString("description", "").trim();
            String actualIssueType = subtaskFields.has("issuetype") ? subtaskFields.getJSONObject("issuetype").getString("name") : "Sub-task";

            sb.append("**********************************\n");
            sb.append(summary).append("\n");

            if (!actualIssueType.equals("Sub-task")) {
                sb.append("issue-type: ").append(actualIssueType).append("\n");
            }
            if (!description.isEmpty()) {
                sb.append(description).append("\n");
            }
        }
        return sb.toString();
    }

    private void copyToClipboard() {
        String textToCopy = templateArea.getText();
        if (textToCopy != null && !textToCopy.isEmpty()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(textToCopy);
            clipboard.setContent(content);
            statusLabel.setText("Template content copied to clipboard!");
        }
    }

    private boolean isInputInvalid(String input) {
        if (input.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Input Error", "Please enter an issue key.");
            return true;
        }
        return false;
    }

    private void setBusyState(boolean isBusy, String statusText) {
        generateBtn.setDisable(isBusy);
        generateFromEpicBtn.setDisable(isBusy);
        copyBtn.setDisable(isBusy);
        if (statusText != null) {
            statusLabel.setText(statusText);
        }
        if (isBusy) {
            templateArea.setText("");
        }
    }
    
    private void updateStatus(String text) {
        Platform.runLater(() -> statusLabel.setText(text));
    }

    private void updateTemplateArea(String content, String status) {
        templateArea.setText(content);
        templateArea.selectRange(0, 0); // scroll to top
        statusLabel.setText(status);
    }

    private void handleApiError(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String errorMessage = "API Error:\n" + ex.getMessage() + "\n\n" + sw.toString();
        Platform.runLater(() -> {
             showAlert(Alert.AlertType.ERROR, "Execution Error", errorMessage);
             setBusyState(false, "Error generating template. Check logs or error dialog.");
        });
    }

    private String getDefaultComponent(JSONObject parentJson) {
        JSONObject parentFields = parentJson.getJSONObject("fields");
        if (parentFields.has("components") && !parentFields.getJSONArray("components").isEmpty()) {
            return parentFields.getJSONArray("components").getJSONObject(0).getString("name");
        }
        return "";
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
