package tso.usmc.jira.ui;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JiraConfig;
import tso.usmc.jira.util.JiraUtils;
import tso.usmc.jira.ui.AssigneeOption;

/**
 * A self-contained panel to automate the 5-step issue processing workflow.
 * It now includes its own local response pane for displaying logs.
 */
public class WorkflowPanel extends JPanel implements tso.usmc.jira.util.ConfigChangeListener {

    private final JiraApiClientGui mainFrame;
    private final JiraConfig jiraConfig;
    private boolean isUpdating = false;

    // --- UI Components ---
    private final JButton refreshButton = new JButton("Refresh Issue List");
    private final DefaultTableModel tableModel=new DefaultTableModel(new String[]{"Key","Summary","Status"},0){@Override public boolean isCellEditable(int row,int column){return false;}};
    private final JTable resultsTable = new JTable(tableModel);
    private final JLabel statusLabel = new JLabel("Enter a JQL query and click Execute.");

    // Options Panel Components
    private final JComboBox<String> issueTypeComboBox = new JComboBox<>(
            new String[] { "Utility/Extract", "FCR", "PTR", "Table Update" });
    private final JComboBox<AssigneeOption> assigneeComboBox = new JComboBox<>();
    private final JComboBox<String> maintenanceTypeComboBox = new JComboBox<>(
            new String[] { "Maintenance", "Enhancement", "Fallout" });
    private final JTextField fySummaryIssueField = new JTextField();
    private final JRadioButton useOriginalDueDateRadio = new JRadioButton("Use Original Due Date", true);
    private final JRadioButton useManualDueDateRadio = new JRadioButton("Manual Due Date:");
    private final JTextField manualDueDateField = new JTextField(10);
    private final JButton processButton = new JButton("Process Selected Issue");

    // ** NEW: Local response pane for this panel only **
    private final JEditorPane localResponsePane = new JEditorPane();

    public WorkflowPanel(JiraApiClientGui mainFrame, JiraConfig jiraConfig) {
        this.mainFrame = mainFrame;
        this.jiraConfig = jiraConfig;
        this.jiraConfig.addConfigChangeListener(this);
        
        fySummaryIssueField.setText(jiraConfig.getWorkflowFySummaryIssue());
        populateAssigneeOptions();
        assigneeComboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                    boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof AssigneeOption) {
                    setText(((AssigneeOption) value).getDisplayName());
                }
                return this;
            }
        });
        // Use a border layout for the entire panel
        setLayout(new BorderLayout());

        // --- Top Content Panel (Controls and Table) ---
        JPanel topContentPanel = new JPanel(new BorderLayout(10, 10));
        topContentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel for the refresh button
        JPanel topButtonPanel = new JPanel(new BorderLayout());
        topButtonPanel.add(refreshButton, BorderLayout.WEST);

        // Table setup
        resultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane tableScrollPane = new JScrollPane(resultsTable);

        // Panel for all the processing options
        JPanel processPanel = new JPanel(new BorderLayout(10, 10));
        processPanel.setBorder(BorderFactory.createTitledBorder("Processing Options"));
        JPanel optionsGrid = new JPanel(new GridLayout(0, 2, 10, 10));
        optionsGrid.add(new JLabel("New Issue Type:"));
        optionsGrid.add(issueTypeComboBox);
        optionsGrid.add(new JLabel("Assign To:"));
        optionsGrid.add(assigneeComboBox);
        optionsGrid.add(new JLabel("Maintenance Type:"));
        optionsGrid.add(maintenanceTypeComboBox);
        optionsGrid.add(new JLabel("FY Summary Issue:"));
        optionsGrid.add(fySummaryIssueField);
        JPanel dueDatePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        ButtonGroup dueDateGroup = new ButtonGroup();
        dueDateGroup.add(useOriginalDueDateRadio);
        dueDateGroup.add(useManualDueDateRadio);
        dueDatePanel.add(useOriginalDueDateRadio);
        dueDatePanel.add(useManualDueDateRadio);
        manualDueDateField.setEnabled(false);
        dueDatePanel.add(manualDueDateField);
        optionsGrid.add(new JLabel("Extended Due Date:"));
        optionsGrid.add(dueDatePanel);
        processPanel.add(optionsGrid, BorderLayout.CENTER);
        processButton.setFont(processButton.getFont().deriveFont(Font.BOLD));
        processPanel.add(processButton, BorderLayout.SOUTH);
        // --- BOTTOM: Status Bar ---
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel);

        // Add components to the top content panel
        topContentPanel.add(topButtonPanel, BorderLayout.NORTH);
        topContentPanel.add(tableScrollPane, BorderLayout.CENTER);
        topContentPanel.add(processPanel, BorderLayout.SOUTH);

        // --- Bottom Content Panel (Local Log) ---
        localResponsePane.setEditable(false);
        localResponsePane.setContentType("text/html");
        JScrollPane localLogScrollPane = new JScrollPane(localResponsePane);
        localLogScrollPane.setMinimumSize(new Dimension(0, 300)); // Give it a minimum height

        // --- Split Pane to combine top and bottom ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topContentPanel, localLogScrollPane);
        splitPane.setResizeWeight(0.65); // Give the top panel more space initially

        // Add the split pane to this panel's main layout
        add(splitPane, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

        // --- Action Listeners ---
        refreshButton.addActionListener(e -> fetchIssues());
        processButton.addActionListener(e -> startWorkflow());
        useManualDueDateRadio.addActionListener(e -> manualDueDateField.setEnabled(true));
        useOriginalDueDateRadio.addActionListener(e -> manualDueDateField.setEnabled(false));
    }

    private void populateAssigneeOptions() {
        assigneeComboBox.removeAllItems();
        String unassignedAssigneeId = jiraConfig.getUnassignedBacklogAssignee();
        assigneeComboBox.addItem(new AssigneeOption("Unassigned backlog", unassignedAssigneeId, null, null));

        String[] teamKeys = jiraConfig.getWorkflowTeamKeys();
        for (String key : teamKeys) {
            String details = jiraConfig.getTeamDetails(key);
            if (details != null) {
                String[] parts = details.split("\\|");
                if (parts.length == 4) {
                    assigneeComboBox.addItem(new AssigneeOption(parts[0], parts[1], parts[2], parts[3]));
                }
            }
        }
    }

    private void fetchIssues() {
        if (isUpdating)
            return;

        isUpdating = true;
        refreshButton.setEnabled(false);
        processButton.setEnabled(false);
        statusLabel.setText("Fetching issues from Jira...");
        tableModel.setRowCount(0);

        SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                String jql = jiraConfig.getWorkflowJql();
                String encodedJql = URLEncoder.encode(jql, "UTF-8");
                String url = mainFrame.getBaseUrl() + "/rest/api/2/search?jql=" + encodedJql
                        + "&fields=summary,status,duedate";
                return mainFrame.getService().executeRequest(url, "GET", null);
            }

            @Override
            protected void done() {
                try {
                    String response = get();
                    JSONObject result = new JSONObject(response);
                    JSONArray issues = result.getJSONArray("issues");
                    for (int i = 0; i < issues.length(); i++) {
                        JSONObject issue = issues.getJSONObject(i);
                        String key = issue.getString("key");
                        String summary = issue.getJSONObject("fields").getString("summary");
                        String status = issue.getJSONObject("fields").getJSONObject("status").getString("name");
                        tableModel.addRow(new Object[] { key, summary, status });
                    }
                    statusLabel.setText("Found " + issues.length() + " issues.");
                } catch (Exception e) {
                    statusLabel.setText("Error fetching issues.");
                    // ** CHANGED: Use local response pane **
                    localResponsePane.setText("<html><font color='red'><b>Failed to fetch issues:</b><br>"
                            + e.getMessage() + "</font></html>");
                    e.printStackTrace();
                } finally {
                    isUpdating = false;
                    refreshButton.setEnabled(true);
                    processButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    @Override
    public void onConfigChanged() {
        SwingUtilities.invokeLater(() -> {
            fySummaryIssueField.setText(jiraConfig.getWorkflowFySummaryIssue());
            populateAssigneeOptions();
            // Optional: Auto-refresh issue list if JQL changed
            // fetchIssues();
        });
    }

    private void startWorkflow() {
        if (isUpdating)
            return;

        int selectedRow = resultsTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(mainFrame.getMainFrame(), "Please select an issue from the table to process.",
                    "No Issue Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final String originalIssueKey = (String) tableModel.getValueAt(selectedRow, 0);
        final String originalSummary = (String) tableModel.getValueAt(selectedRow, 1);
        final String newIssueType = (String) issueTypeComboBox.getSelectedItem();
        final AssigneeOption selectedAssignment = (AssigneeOption) assigneeComboBox.getSelectedItem();
        final String maintenanceType = (String) maintenanceTypeComboBox.getSelectedItem();
        final String fySummaryIssue = fySummaryIssueField.getText().trim();
        // --- NEW: Capture Due Date Preferences ---
        final boolean useOriginalDueDate = useOriginalDueDateRadio.isSelected();
        final String manualDueDateValue = manualDueDateField.getText().trim();

        // Basic validation for manual due date
        if (!useOriginalDueDate && manualDueDateValue.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame.getMainFrame(),
                    "Please enter a manual due date or select 'Use Original Due Date'.", "Manual Due Date Missing",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        isUpdating = true;
        processButton.setEnabled(false);
        refreshButton.setEnabled(false);
        // ** CHANGED: Use local response pane **
        localResponsePane.setText("");
        statusLabel.setText("Processing " + originalIssueKey + "...");

        ProcessIssueWorker worker = new ProcessIssueWorker(
                originalIssueKey, originalSummary, newIssueType,
                selectedAssignment,
                maintenanceType, fySummaryIssue,
                useOriginalDueDate, manualDueDateValue);
        worker.execute();
    }

    private class ProcessIssueWorker extends SwingWorker<String, Void> {
        private final String originalIssueKey, originalSummary, newIssueType, maintenanceType, fySummaryIssue,
                manualDueDate;
        private final AssigneeOption selectedAssignment;
        private final boolean useOriginalDueDate;
        private final StringBuilder report;
        private boolean workflowSucceeded = true;
        private String reporterNameToUpdate;

        ProcessIssueWorker(String originalIssueKey, String originalSummary, String newIssueType,
                AssigneeOption selectedAssignment, String maintenanceType, String fySummaryIssue,
                boolean useOriginalDueDate, String manualDueDate) {
            this.originalIssueKey = originalIssueKey;
            this.originalSummary = originalSummary;
            this.newIssueType = newIssueType;
            this.selectedAssignment = selectedAssignment;
            this.maintenanceType = maintenanceType;
            this.fySummaryIssue = fySummaryIssue;
            this.report = new StringBuilder("<html><h2>Workflow Report for " + originalIssueKey
                    + "</h2><table border='1' style='width:100%'><tr><th>Step</th><th>Action</th><th>Result</th></tr>");
            // --- NEW: Set new member variables ---
            this.useOriginalDueDate = useOriginalDueDate;
            this.manualDueDate = manualDueDate;
        }

        @Override
        protected String doInBackground() throws Exception {
            try {
                String originalIssueJson = mainFrame.getService().executeRequest(
                        mainFrame.getBaseUrl() + "/rest/api/2/issue/" + this.originalIssueKey
                                + "?fields=summary,status,duedate,description,reporter,attachment,issuelinks",
                        "GET", null);

                JSONObject debugJson = new JSONObject(originalIssueJson);
                // addReportRow("DEBUG", "Full JSON Response from Jira", "<pre
                // style='font-size:10px; word-wrap:break-word; white-space:pre-wrap;'>" +
                // debugJson.toString(4) + "</pre>");
                JSONObject sourceIssue = new JSONObject(originalIssueJson);

                step1_UpdateOriginalIssue();
                String newIssueKey = step2_3_5_CreateMovedClone(sourceIssue);

                if (newIssueKey != null) {
                    // This is the new, correct order of operations.
                    // step3a_UpdateClonedIssue("TFS-63111", sourceIssue);
                    step3a_UpdateClonedIssue(newIssueKey, sourceIssue);

                    // cloneAttachments("TFS-63111", sourceIssue);
                    // cloneLinks("TFS-63111", sourceIssue);
                    // step4_LinkIssues("TFS-63111");
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
            }
            return report.toString();
        }

        @Override
        protected void done() {
            try {
                String finalReport = get();
                localResponsePane.setText(finalReport);
                if (workflowSucceeded) {
                    statusLabel.setText("Workflow for " + originalIssueKey + " completed.");
                } else {
                    statusLabel.setText("Workflow for " + originalIssueKey + " failed. See report for details.");
                }
            } catch (Exception e) {
                statusLabel.setText("An unexpected error occurred in the UI thread: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isUpdating = false;
                processButton.setEnabled(true);
                refreshButton.setEnabled(true);
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

        // This method now correctly OMMITS the problematic fields.
        private String step2_3_5_CreateMovedClone(JSONObject sourceIssue) throws Exception {
            JSONObject fieldsForCreation = new JSONObject(sourceIssue.getJSONObject("fields").toString());
            // JSONObject sourceFields = sourceIssue.getJSONObject("fields");
            String originalDescription = fieldsForCreation.optString("description", "");
            this.reporterNameToUpdate = null; // Reset before use
            if (fieldsForCreation.has("reporter") && !fieldsForCreation.isNull("reporter")) {
                this.reporterNameToUpdate = fieldsForCreation.getJSONObject("reporter").getString("name");
            } else {
                addReportRow("2.2", "Capture reporter for update",
                        "<font color='orange'>Warning: Reporter field not found on original issue.</font>");
            }
            cleanJsonForCreation(fieldsForCreation);

            fieldsForCreation.put("project", new JSONObject().put("key", "TFS"));
            fieldsForCreation.put("summary", originalIssueKey + " - " + originalSummary);
            fieldsForCreation.put("issuetype", new JSONObject().put("name", newIssueType));
            fieldsForCreation.put("description", originalDescription);

            // Problematic fields are no longer set here.
            fieldsForCreation.put("customfield_10400", originalIssueKey);
            fieldsForCreation.put("customfield_10522", new JSONObject().put("value", maintenanceType));
            if (fySummaryIssue != null && !fySummaryIssue.isEmpty()) {
                fieldsForCreation.put("customfield_13056", fySummaryIssue);
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
            fieldsToUpdate.put("customfield_10523", new JSONArray().put(new JSONObject().put("value", "Design")));
            fieldsToUpdate.put("customfield_10512", new JSONArray().put(new JSONObject().put("value", "Analysis")));
            // --- Part 1: Set Assignee and Component/Team ---
            String assigneeId = selectedAssignment.getAssigneeJiraId();
            fieldsToUpdate.put("assignee", new JSONObject().put("name", assigneeId));
            addReportRow("3.1", "Set Assignee", "Assigning to: " + assigneeId);

            // If it's a team assignment (i.e., not "Unassigned Backlog")
            if (selectedAssignment.getComponentName() != null) {
                String componentName = selectedAssignment.getComponentName();
                String teamId = selectedAssignment.getTeamId();

                // Set the 'components' field (This is a standard Jira field)
                JSONArray componentsArray = new JSONArray().put(new JSONObject().put("name", componentName));
                fieldsToUpdate.put("components", componentsArray);

                fieldsToUpdate.put("customfield_15350", teamId);

                addReportRow("3.2", "Set Team & Component",
                        "Component: '" + componentName + "', Team: '" + componentName + "'");
            }

            // --- Part 2: Set Original Reporter ---
            if (this.reporterNameToUpdate != null) {
                fieldsToUpdate.put("reporter", new JSONObject().put("name", this.reporterNameToUpdate));
                fieldsToUpdate.put("customfield_10540", new JSONObject().put("name", this.reporterNameToUpdate));
                addReportRow("3.3", "Set Reporter Fields", "Original Reporter: " + this.reporterNameToUpdate);
            }

            // --- Part 3: Set Due Date ---
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
                fieldsToUpdate.put("duedate", finalDueDate);
                fieldsToUpdate.put("customfield_10517", finalDueDate);
            }

            // --- Part 4: Execute the Update Request ---
            String updateJsonBody = new JSONObject().put("fields", fieldsToUpdate).toString();

            try {
                // This is the single API call that updates all the fields at once.
                mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + newIssueKey,
                        "PUT", updateJsonBody);
                addReportRow("3.5", "Update All Fields", "<font color='green'>Success</font>");
            } catch (Exception e) {
                // If this fails, the ENTIRE update failed. The error message is key.
                addReportRow("3.5", "Update All Fields", "<font color='red'>Failed: " + e.getMessage() + "</font>");
                // Also log the JSON we tried to send for debugging.
                // addReportRow("DEBUG", "Failed JSON Payload", "<pre>" + new
                // JSONObject(updateJsonBody).toString(4) + "</pre>");
            }

            // --- Part 5: Transition Status (only for Unassigned) ---
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

            String fieldUpdateJsonBody = new JSONObject()
                    .put("fields", new JSONObject().put("customfield_10519", JSONObject.NULL)).toString();
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
            SwingUtilities.invokeLater(() -> {
                report.append("<tr><td>").append(step).append("</td><td>").append(action).append("</td><td>")
                        .append(result).append("</td></tr>");
                localResponsePane.setText(report.toString() + "</table></html>");
            });
        }
    }

}
