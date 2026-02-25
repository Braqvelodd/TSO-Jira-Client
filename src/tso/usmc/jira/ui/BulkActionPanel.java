package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraApiService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.json.JSONArray;
import org.json.JSONObject;

public class BulkActionPanel extends JPanel {

    private final JiraApiClientGui mainFrame;

    // UI Components
    private final JTextArea issueKeysArea = new JTextArea();
    private final JComboBox<String> actionTypeCombo = new JComboBox<>(new String[]{
            "Transition", "Change Assignee", "Add Comment", "Add Label", "Remove Label", "Change Priority", "Link Issues"
    });

    private final JPanel actionConfigCards = new JPanel(new CardLayout());
    
    // Transition Components
    private final JTextField transitionNameField = new JTextField(20);
    
    // Assignee Components
    private final JTextField assigneeField = new JTextField(20);
    
    // Comment Components
    private final JTextField commentField = new JTextField(30);
    
    // Label Components
    private final JTextField labelField = new JTextField(20);

    // Priority Components
    private final JComboBox<String> priorityCombo = new JComboBox<>(new String[]{
            "Highest", "High", "Medium", "Low", "Lowest"
    });

    // Link Components
    private final JTextField targetIssueField = new JTextField(15);
    private final JComboBox<String> linkTypeCombo = new JComboBox<>(new String[]{
            "Relates", "Blocks", "Clones", "Duplicates"
    });

    private final JButton executeBtn = new JButton("Execute Bulk Action");
    private final JButton clearResultsBtn = new JButton("Clear Results");
    private final JLabel statusLabel = new JLabel("Enter issue keys and configure the action to apply.");

    private final DefaultTableModel resultsModel = new DefaultTableModel(new String[]{"Issue Key", "Action", "Result"}, 0);
    private final JTable resultsTable = new JTable(resultsModel);

    public BulkActionPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- TOP: Issue Key Input ---
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("1. Paste Issue Keys (one per line or space-separated)"));
        issueKeysArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        inputPanel.add(new JScrollPane(issueKeysArea), BorderLayout.CENTER);
        inputPanel.setPreferredSize(new Dimension(0, 150));

        // --- CENTER: Action Configuration ---
        JPanel actionPanel = new JPanel(new BorderLayout(5, 5));
        actionPanel.setBorder(BorderFactory.createTitledBorder("2. Define Action"));
        
        JPanel typePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        typePanel.add(new JLabel("Action Type:"));
        typePanel.add(actionTypeCombo);
        
        actionPanel.add(typePanel, BorderLayout.NORTH);

        // Card 1: Transition
        JPanel transCard = new JPanel(new FlowLayout(FlowLayout.LEFT));
        transCard.add(new JLabel("Transition Name:"));
        transCard.add(transitionNameField);
        actionConfigCards.add(transCard, "Transition");

        // Card 2: Assignee
        JPanel assignCard = new JPanel(new FlowLayout(FlowLayout.LEFT));
        assignCard.add(new JLabel("New Assignee ID:"));
        assignCard.add(assigneeField);
        actionConfigCards.add(assignCard, "Change Assignee");

        // Card 3: Comment
        JPanel commentCard = new JPanel(new FlowLayout(FlowLayout.LEFT));
        commentCard.add(new JLabel("Comment Body:"));
        commentCard.add(commentField);
        actionConfigCards.add(commentCard, "Add Comment");

        // Card 4: Labels (Shared for Add/Remove)
        JPanel labelCard = new JPanel(new FlowLayout(FlowLayout.LEFT));
        labelCard.add(new JLabel("Label:"));
        labelCard.add(labelField);
        actionConfigCards.add(labelCard, "Add Label");
        actionConfigCards.add(labelCard, "Remove Label");

        // Card 5: Priority
        JPanel priorityCard = new JPanel(new FlowLayout(FlowLayout.LEFT));
        priorityCard.add(new JLabel("New Priority:"));
        priorityCard.add(priorityCombo);
        actionConfigCards.add(priorityCard, "Change Priority");

        // Card 6: Link Issues
        JPanel linkCard = new JPanel(new FlowLayout(FlowLayout.LEFT));
        linkCard.add(new JLabel("Link all to:"));
        linkCard.add(targetIssueField);
        linkCard.add(new JLabel("as"));
        linkCard.add(linkTypeCombo);
        actionConfigCards.add(linkCard, "Link Issues");

        actionPanel.add(actionConfigCards, BorderLayout.CENTER);
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(clearResultsBtn);
        btnPanel.add(executeBtn);
        actionPanel.add(btnPanel, BorderLayout.SOUTH);

        actionTypeCombo.addActionListener(e -> {
            CardLayout cl = (CardLayout) actionConfigCards.getLayout();
            cl.show(actionConfigCards, (String) actionTypeCombo.getSelectedItem());
        });

        // A panel to hold both input and action panels
        JPanel topContainer = new JPanel(new BorderLayout(10, 10));
        topContainer.add(inputPanel, BorderLayout.CENTER);
        topContainer.add(actionPanel, BorderLayout.SOUTH);

        // --- BOTTOM: Results and Status ---
        JPanel bottomContainer = new JPanel(new BorderLayout(10, 10));
        bottomContainer.setBorder(BorderFactory.createTitledBorder("3. Execution Results"));
        resultsTable.setFillsViewportHeight(true);
        bottomContainer.add(new JScrollPane(resultsTable), BorderLayout.CENTER);
        
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel);
        bottomContainer.add(statusPanel, BorderLayout.SOUTH);

        // --- Main Layout ---
        add(topContainer, BorderLayout.NORTH);
        add(bottomContainer, BorderLayout.CENTER);

        // --- Action Listeners ---
        executeBtn.addActionListener(e -> executeBulkAction());
        clearResultsBtn.addActionListener(e -> resultsModel.setRowCount(0));
    }

    private void executeBulkAction() {
        String[] keys = issueKeysArea.getText().trim().toUpperCase().split("\\s+");
        String actionType = (String) actionTypeCombo.getSelectedItem();

        if (keys.length == 0 || (keys.length == 1 && keys[0].isEmpty())) {
            JOptionPane.showMessageDialog(this, "Please enter at least one issue key.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        resultsModel.setRowCount(0); // Clear previous results
        setButtonsEnabled(false);

        new Thread(() -> {
            JiraApiService service = null;
            try {
                service = mainFrame.getService();
            } catch (Exception e) {
                final Exception serviceEx = e;
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Failed to initialize Jira service:\n" + serviceEx.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
                    setButtonsEnabled(true);
                });
                return;
            }
            
            for (int i = 0; i < keys.length; i++) {
                String key = keys[i];
                final int current = i + 1;
                SwingUtilities.invokeLater(() -> statusLabel.setText("Processing " + current + " of " + keys.length + ": " + key));

                try {
                    String actionDesc = "";
                    switch (actionType) {
                        case "Transition":
                            String transName = transitionNameField.getText().trim();
                            if (transName.isEmpty()) throw new Exception("Transition name required");
                            actionDesc = "Transition to '" + transName + "'";
                            
                            String transJson = service.executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "/transitions", "GET", null);
                            String transId = findTransitionIdByName(transJson, transName);
                            if (transId == null) throw new Exception("Transition '" + transName + "' not available for this issue status");
                            
                            JSONObject transPayload = new JSONObject();
                            transPayload.put("transition", new JSONObject().put("id", transId));
                            service.executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "/transitions", "POST", transPayload.toString());
                            break;

                        case "Change Assignee":
                            String assignee = assigneeField.getText().trim();
                            actionDesc = "Assign to '" + assignee + "'";
                            JSONObject assignPayload = new JSONObject();
                            assignPayload.put("name", assignee); // Use "accountId" if Jira Cloud, but USMC likely uses "name" (ID)
                            service.executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "/assignee", "PUT", assignPayload.toString());
                            break;

                        case "Add Comment":
                            String comment = commentField.getText().trim();
                            if (comment.isEmpty()) throw new Exception("Comment body required");
                            actionDesc = "Add Comment";
                            JSONObject commentPayload = new JSONObject();
                            commentPayload.put("body", comment);
                            service.executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "/comment", "POST", commentPayload.toString());
                            break;

                        case "Add Label":
                            String addLabel = labelField.getText().trim();
                            if (addLabel.isEmpty()) throw new Exception("Label required");
                            actionDesc = "Add Label '" + addLabel + "'";
                            JSONObject addPayload = new JSONObject();
                            JSONArray addLabels = new JSONArray().put(new JSONObject().put("add", addLabel));
                            addPayload.put("update", new JSONObject().put("labels", addLabels));
                            service.executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key, "PUT", addPayload.toString());
                            break;

                        case "Remove Label":
                            String remLabel = labelField.getText().trim();
                            if (remLabel.isEmpty()) throw new Exception("Label required");
                            actionDesc = "Remove Label '" + remLabel + "'";
                            JSONObject remPayload = new JSONObject();
                            JSONArray remLabels = new JSONArray().put(new JSONObject().put("remove", remLabel));
                            remPayload.put("update", new JSONObject().put("labels", remLabels));
                            service.executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key, "PUT", remPayload.toString());
                            break;

                        case "Change Priority":
                            String priority = (String) priorityCombo.getSelectedItem();
                            actionDesc = "Set Priority to '" + priority + "'";
                            JSONObject priorityPayload = new JSONObject();
                            priorityPayload.put("fields", new JSONObject().put("priority", new JSONObject().put("name", priority)));
                            service.executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key, "PUT", priorityPayload.toString());
                            break;

                        case "Link Issues":
                            String targetKey = targetIssueField.getText().trim().toUpperCase();
                            String linkType = (String) linkTypeCombo.getSelectedItem();
                            if (targetKey.isEmpty()) throw new Exception("Target issue key required");
                            actionDesc = "Link to '" + targetKey + "' as '" + linkType + "'";
                            
                            JSONObject linkPayload = new JSONObject();
                            linkPayload.put("type", new JSONObject().put("name", linkType));
                            linkPayload.put("inwardIssue", new JSONObject().put("key", key));
                            linkPayload.put("outwardIssue", new JSONObject().put("key", targetKey));
                            service.executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issueLink", "POST", linkPayload.toString());
                            break;
                    }
                    
                    addResultRow(key, actionDesc, "SUCCESS");
                } catch (Exception e) {
                    addResultRow(key, actionType, "ERROR: " + e.getMessage());
                }
            }

            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Bulk execution complete. Processed " + keys.length + " issues.");
                setButtonsEnabled(true);
            });
        }).start();
    }

    private String findTransitionIdByName(String jsonResponse, String transitionName) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) return null;
        JSONObject response = new JSONObject(jsonResponse);
        if (!response.has("transitions")) return null;
        
        JSONArray transitions = response.getJSONArray("transitions");
        for (int i = 0; i < transitions.length(); i++) {
            JSONObject t = transitions.getJSONObject(i);
            if (t.getString("name").equalsIgnoreCase(transitionName)) {
                return t.getString("id");
            }
        }
        return null;
    }

    private void addResultRow(String key, String action, String result) {
        SwingUtilities.invokeLater(() -> resultsModel.addRow(new Object[]{key, action, result}));
    }

    private void setButtonsEnabled(boolean enabled) {
        executeBtn.setEnabled(enabled);
    }
}
