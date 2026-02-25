package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraApiService;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

public class TemplateExtractorPanel extends JPanel {

    private static final String EPIC_LINK_FIELD_ID = "customfield_13056";
    private final JiraApiClientGui mainFrame;

    // UI Components
    private final JTextField parentIssueField = new JTextField(20);
    private final JButton generateBtn = new JButton("Generate Template from Sub-tasks");
    private final JButton generateFromEpicBtn = new JButton("Generate from Epic's Issues");
    private final JTextArea templateArea = new JTextArea();
    private final JButton copyBtn = new JButton("Copy to Clipboard");
    private final JLabel statusLabel = new JLabel("Enter a parent issue key (e.g., a Story) and click Generate.");

    public TemplateExtractorPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- UI Setup (unchanged) ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        topPanel.setBorder(BorderFactory.createTitledBorder("1. Source Parent Issue"));
        topPanel.add(new JLabel("Parent Key:"));
        topPanel.add(parentIssueField);
        topPanel.add(generateBtn);
        topPanel.add(generateFromEpicBtn);
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("2. Generated Template (for Task Builder)"));
        templateArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        templateArea.setEditable(false);
        templateArea.setLineWrap(true);
        templateArea.setWrapStyleWord(true);
        centerPanel.add(new JScrollPane(templateArea), BorderLayout.CENTER);
        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomActions.add(copyBtn);
        centerPanel.add(bottomActions, BorderLayout.SOUTH);
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel);
        add(topPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);
        generateBtn.addActionListener(e -> generateTemplate());
        generateFromEpicBtn.addActionListener(e -> generateEpicTemplate());
        copyBtn.addActionListener(e -> copyToClipboard());
    }
    private void generateTemplate() {
        String parentKey = parentIssueField.getText().trim().toUpperCase();
        if (isInputInvalid(parentKey)) return;

        setBusyState(true, "Fetching data for " + parentKey + "...");

        new Thread(() -> {
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

                SwingUtilities.invokeLater(() -> {
                    updateTemplateArea(templateContent, "Success! Generated template from " + subtasks.length() + " sub-tasks.");
                    setBusyState(false, null);
                });
            } catch (Exception ex) {
                handleApiError(ex);
            }
        }).start();
    }

    // NEW: Method to handle the new "Generate from Epic" button's logic.
    private void generateEpicTemplate() {
        final String issueKey = parentIssueField.getText().trim().toUpperCase();
        if (isInputInvalid(issueKey)) return;

        setBusyState(true, "Checking if '" + issueKey + "' is an Epic or part of one...");

        new Thread(new Runnable() {
            public void run() {
                try {
                    JiraApiService service = mainFrame.getService();
                    String baseUrl = mainFrame.getBaseUrl();

                    String fieldsToFetch = EPIC_LINK_FIELD_ID + ",components,issuetype";
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
                        epicKey = fields.optString(EPIC_LINK_FIELD_ID, null);
                        if (epicKey == null || epicKey.isEmpty()) {
                            throw new Exception("Issue '" + issueKey + "' is not an Epic and does not belong to one. Verify the '" + EPIC_LINK_FIELD_ID + "' custom field ID.");
                        }
                        updateStatus("Found Epic " + epicKey + ". Fetching all child issues...");
                    }

                    // Step 1: Find all issues within that Epic using the custom field.
                    String issuesInEpicJql = String.format("'%s' = '%s'", EPIC_LINK_FIELD_ID, epicKey);
                    JSONObject epicSearchPayload = new JSONObject().put("jql", issuesInEpicJql).put("fields", new JSONArray().put("key")).put("maxResults", 500);
                    String issuesInEpicResponse = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", epicSearchPayload.toString());
                    JSONArray issuesInEpic = new JSONObject(issuesInEpicResponse).getJSONArray("issues");

                    // Use non-generic List for compatibility with older Java versions.
                    ArrayList issueKeys = new ArrayList();
                    for (int i = 0; i < issuesInEpic.length(); i++) {
                        issueKeys.add(issuesInEpic.getJSONObject(i).getString("key"));
                    }
                    updateStatus("Found " + issueKeys.size() + " issues in Epic. Fetching all their sub-tasks...");

                    // Step 2: Build the final JQL to get all sub-tasks.
                    StringBuffer subtaskJql = new StringBuffer();
                    // Include sub-tasks parented directly to the Epic
                    subtaskJql.append("parent = '").append(epicKey).append("'"); 
                    // Also include sub-tasks of all issues found in the Epic
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
                    final String finalEpicKey = epicKey; // Final variable for use in inner class

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            updateTemplateArea(templateContent, "Success! Generated template from " + allSubtasks.length() + " sub-tasks in Epic " + finalEpicKey + ".");
                            setBusyState(false, null);
                        }
                    });

                } catch (Exception ex) {
                    handleApiError(ex);
                }
            }
        }).start();
    }

    // NEW: Refactored logic to build the template string into its own method.
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
            StringSelection stringSelection = new StringSelection(textToCopy);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
            statusLabel.setText("Template content copied to clipboard!");
        }
    }
    private boolean isInputInvalid(String input) {
            if (input.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter an issue key.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return true;
            }
            return false;
        }

    private void setBusyState(boolean isBusy, String statusText) {
        generateBtn.setEnabled(!isBusy);
        generateFromEpicBtn.setEnabled(!isBusy);
        copyBtn.setEnabled(!isBusy);
        if (statusText != null) {
            statusLabel.setText(statusText);
        }
        if (isBusy) {
            templateArea.setText("");
        }
    }
    
    private void updateStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    private void updateTemplateArea(String content, String status) {
        templateArea.setText(content);
        templateArea.setCaretPosition(0);
        statusLabel.setText(status);
    }

    private void handleApiError(Exception ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        String errorMessage = "API Error:\n" + ex.getMessage() + "\n\n" + sw.toString();
        SwingUtilities.invokeLater(() -> {
             JOptionPane.showMessageDialog(this, errorMessage, "Execution Error", JOptionPane.ERROR_MESSAGE);
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
}