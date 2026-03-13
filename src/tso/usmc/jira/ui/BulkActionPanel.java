package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraIssueService;
import tso.usmc.jira.util.ExecutionService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

public class BulkActionPanel extends JPanel {

    private final JiraApiClientGui mainFrame;
    private final JTextArea issueKeysArea = new JTextArea();
    private final JComboBox<String> actionCombo = new JComboBox<>(new String[]{
            "Transition", "Change Assignee", "Add Comment"
    });
    private final JTextField actionValueField = new JTextField(20);
    private final JTextField targetIssueField = new JTextField(15); 
    private final JComboBox<String> linkTypeCombo = new JComboBox<>(new String[]{"relates to", "duplicates", "blocks", "clones"});
    
    private final JButton executeBtn = new JButton("Run Bulk Action");
    private final DefaultTableModel resultsModel = new DefaultTableModel(new String[]{"Issue Key", "Action", "Result"}, 0);
    private final JTable resultsTable = new JTable(resultsModel);
    private final JLabel statusLabel = new JLabel("Enter issue keys (one per line) and configure the action.");

    public BulkActionPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Left: Input Area ---
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setBorder(BorderFactory.createTitledBorder("1. Issue Keys"));
        leftPanel.add(new JScrollPane(issueKeysArea), BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(250, 0));

        // --- Center: Configuration & Results ---
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        configPanel.setBorder(BorderFactory.createTitledBorder("2. Configure Action"));
        configPanel.add(new JLabel("Action:"));
        configPanel.add(actionCombo);
        configPanel.add(new JLabel("Value:"));
        configPanel.add(actionValueField);
        configPanel.add(executeBtn);
        
        centerPanel.add(configPanel, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(resultsTable), BorderLayout.CENTER);

        // --- Bottom: Status ---
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel);

        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

        executeBtn.addActionListener(e -> executeBulkAction());
    }

    private void executeBulkAction() {
        String[] keys = issueKeysArea.getText().trim().toUpperCase().split("\\s+");
        if (keys.length == 0 || (keys.length == 1 && keys[0].isEmpty())) {
            JOptionPane.showMessageDialog(this, "Please enter at least one issue key.");
            return;
        }

        final String actionType = (String) actionCombo.getSelectedItem();
        final String actionValue = actionValueField.getText().trim();

        if (actionValue.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a value for the action (e.g., Transition Name, Username, or Comment text).");
            return;
        }

        resultsModel.setRowCount(0);
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
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Authentication error: " + e.getMessage());
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
                            finalService.assignIssue(key, actionValue);
                            actionDesc = "Assign to " + actionValue;
                        } else if ("Add Comment".equals(actionType)) {
                            finalService.addComment(key, actionValue);
                            actionDesc = "Comment added";
                        }
                        
                        addResultRow(key, actionDesc, "SUCCESS");
                    } catch (Exception e) {
                        addResultRow(key, actionType, "ERROR: " + e.getMessage());
                    } finally {
                        int current = completedCount.incrementAndGet();
                        SwingUtilities.invokeLater(() -> statusLabel.setText("Parallel Processing: " + current + " of " + total + " complete..."));
                    }
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException ignored) {}

            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Bulk execution complete. Processed " + total + " issues in parallel.");
                setButtonsEnabled(true);
            });
        });
    }

    private void addResultRow(String key, String action, String result) {
        SwingUtilities.invokeLater(() -> resultsModel.addRow(new Object[]{key, action, result}));
    }

    private void setButtonsEnabled(boolean enabled) {
        executeBtn.setEnabled(enabled);
    }
}
