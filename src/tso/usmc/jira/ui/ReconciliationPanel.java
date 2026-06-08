package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.service.JiraApiService;
import tso.usmc.jira.util.ExecutionService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReconciliationPanel extends JPanel {

    private static final List<String> ISPW_PREFIXES = Arrays.asList(
            "COB", "PROC", "JCL", "SYS", "ASM", "COPY", "DMGR", "DCLG", "CMAP"
    );

    // Helper classes
    private static class JiraReconInfo {
        String subtaskKey;
        String subtaskSummary;
        String parentKey;
        String parentSummary;
        String assignee = "Unassigned";
        String status = "N/A";
    }

    private static class IspwReconInfo {
        String fullTaskName;
        String srNumber;
        String userId;
        String action;
    }

    private final JiraApiClientGui mainFrame;

    // UI Components
    private final JTextArea jiraParentKeysArea = new JTextArea("TFS-49439\nTFS-35035");
    private final JButton fetchJiraBtn = new JButton("Fetch Jira Sub-tasks");
    private final JTextArea ispwReportArea = new JTextArea();
    private final JButton configureColumnsBtn = new JButton("Configure Columns...");
    private final JButton compareBtn = new JButton("Compare Jira vs. ISPW");
    private final JLabel statusLabel = new JLabel("Ready. Fetch Jira tasks and paste ISPW report.");

    private final DefaultTableModel onlyInIspwModel = new DefaultTableModel();
    private final DefaultTableModel onlyInJiraModel = new DefaultTableModel();
    private final DefaultTableModel matchesModel = new DefaultTableModel();
    private final JTable onlyInIspwTable = new JTable(onlyInIspwModel);
    private final JTable onlyInJiraTable = new JTable(onlyInJiraModel);
    private final JTable matchesTable = new JTable(matchesModel);

    // Data holders
    private Map<String, JiraReconInfo> jiraTaskMap = new HashMap<>();
    private Map<String, IspwReconInfo> ispwTaskMap = new HashMap<>();

    public ReconciliationPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- UI Setup ---
        JPanel topPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        JPanel jiraPanel = new JPanel(new BorderLayout(5, 5));
        jiraPanel.setBorder(BorderFactory.createTitledBorder("1. Jira Input"));
        JScrollPane jiraScroll = new JScrollPane(jiraParentKeysArea);
        jiraScroll.setPreferredSize(new Dimension(0, 150)); 
        jiraPanel.add(jiraScroll, BorderLayout.CENTER);
        jiraPanel.add(fetchJiraBtn, BorderLayout.SOUTH);
        topPanel.add(jiraPanel);
        JPanel ispwPanel = new JPanel(new BorderLayout(5, 5));
        ispwPanel.setBorder(BorderFactory.createTitledBorder("2. Paste ISPW Report"));
        JScrollPane ispwScroll = new JScrollPane(ispwReportArea);
        ispwScroll.setPreferredSize(new Dimension(0, 150));
        ispwPanel.add(ispwScroll, BorderLayout.CENTER);
        ispwPanel.add(configureColumnsBtn, BorderLayout.SOUTH);
        topPanel.add(ispwPanel);
        JPanel comparePanel = new JPanel(new GridBagLayout());
        comparePanel.add(compareBtn);
        
        JTabbedPane resultsTabs = new JTabbedPane();
        
        onlyInIspwModel.setColumnIdentifiers(new String[]{"Type", "Name", "Action", "SR Number", "User ID"});
        onlyInJiraModel.setColumnIdentifiers(new String[]{"Type", "Name", "Parent Issue", "Assignee", "Status", "Link"});
        matchesModel.setColumnIdentifiers(new String[]{"Type", "Name", "Jira Key", "Status", "Assignee", "ISPW Action", "SR Number", "ISPW User", "Link"});
        
        onlyInIspwTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        onlyInIspwTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        onlyInJiraTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        matchesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        
        resultsTabs.addTab("Only in ISPW (Not in Jira)", new JScrollPane(onlyInIspwTable));
        resultsTabs.addTab("Only in Jira (Not in ISPW)", new JScrollPane(onlyInJiraTable));
        resultsTabs.addTab("Matches (Both)", new JScrollPane(matchesTable));
        
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.setBorder(BorderFactory.createEtchedBorder());
        statusPanel.add(statusLabel);
        JPanel centerContainer = new JPanel(new BorderLayout(10,10));
        centerContainer.add(comparePanel, BorderLayout.NORTH);
        centerContainer.add(resultsTabs, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);
        add(centerContainer, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);

        fetchJiraBtn.addActionListener(e -> fetchJiraTasks());
        compareBtn.addActionListener(e -> performComparison());
        configureColumnsBtn.addActionListener(e -> {
            String text = ispwReportArea.getText();
            if (text.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please paste an ISPW report first to use as a template.");
                return;
            }
            new IspwColumnConfigDialog((JFrame)SwingUtilities.getWindowAncestor(this), text, mainFrame.getJiraConfig()).setVisible(true);
        });

        setupContextMenu();
    }
    
    private void performComparison() {
        statusLabel.setText("Parsing ISPW report and performing comparison...");
        this.ispwTaskMap = new HashMap<>();
        String ispwText = ispwReportArea.getText();
        
        tso.usmc.jira.util.JiraConfig config = mainFrame.getJiraConfig();
        int minLen = config.getIspwMinLineLength(65);
        int[] typeBounds = config.getIspwColumnBounds("ci_type", new int[]{0, 4});
        int[] nameBounds = config.getIspwColumnBounds("ci_name", new int[]{5, 13});
        int[] srBounds = config.getIspwColumnBounds("sr", new int[]{30, 40});
        int[] userBounds = config.getIspwColumnBounds("user", new int[]{41, 47});
        int[] actionBounds = config.getIspwActionBounds(new int[]{55, 56});

        for (String line : ispwText.split("\n")) {
            try {
                if (line.length() < minLen) continue;
                String typePart = line.substring(typeBounds[0], typeBounds[1]).trim();
                String namePart = line.substring(nameBounds[0], nameBounds[1]).trim();
                
                if (!typePart.isEmpty() && !namePart.isEmpty()) {
                    String rawTaskName = typePart + " " + namePart;
                    String normalizedName = rawTaskName.trim().replaceAll("\\s+", " ");
                    IspwReconInfo info = new IspwReconInfo();
                    info.fullTaskName = normalizedName;
                    info.srNumber = line.substring(srBounds[0], srBounds[1]).trim();
                    info.userId = line.substring(userBounds[0], userBounds[1]).trim();
                    info.action = line.substring(actionBounds[0], actionBounds[1]).trim();
                    this.ispwTaskMap.put(normalizedName, info);
                }
            } catch (Exception e) { System.err.println("Could not parse line: " + line); }
        }

        if (this.jiraTaskMap.isEmpty()) {
            statusLabel.setText("Jira data has not been fetched. Please click 'Fetch Jira Sub-tasks' first.");
            return;
        }
        if (this.ispwTaskMap.isEmpty()) {
            statusLabel.setText("No valid task names could be parsed from the ISPW report.");
            return;
        }

        Set<String> ispwKeys = ispwTaskMap.keySet();
        Set<String> jiraKeys = jiraTaskMap.keySet();
        Set<String> onlyInIspw = new HashSet<>(ispwKeys);
        onlyInIspw.removeAll(jiraKeys);
        Set<String> onlyInJira = new HashSet<>(jiraKeys);
        onlyInJira.removeAll(ispwKeys);
        Set<String> matches = new HashSet<>(ispwKeys);
        matches.retainAll(jiraKeys);
        
        SwingUtilities.invokeLater(() -> {
            onlyInIspwModel.setRowCount(0);
            for (String key : onlyInIspw) {
                IspwReconInfo info = ispwTaskMap.get(key);
                String[] parts = info.fullTaskName.split(" ", 2);
                String type = (parts.length > 0) ? parts[0] : info.fullTaskName;
                String name = (parts.length > 1) ? parts[1] : "";
                onlyInIspwModel.addRow(new Object[]{type, name, formatIspwAction(info.action), info.srNumber, info.userId});
            }

            onlyInJiraModel.setRowCount(0);
            for (String key : onlyInJira) {
                JiraReconInfo info = jiraTaskMap.get(key);
                String[] parts = info.subtaskSummary.split(" ", 2);
                String type = (parts.length > 0) ? parts[0] : info.subtaskSummary;
                String name = (parts.length > 1) ? parts[1] : "";
                String link = mainFrame.getBaseUrl() + "/browse/" + info.subtaskKey;
                onlyInJiraModel.addRow(new Object[]{type, name, info.parentSummary, info.assignee, info.status, link});
            }

            matchesModel.setRowCount(0);
            for (String key : matches) {
                IspwReconInfo ispw = ispwTaskMap.get(key);
                JiraReconInfo jira = jiraTaskMap.get(key);
                String[] parts = ispw.fullTaskName.split(" ", 2);
                String type = (parts.length > 0) ? parts[0] : ispw.fullTaskName;
                String name = (parts.length > 1) ? parts[1] : "";
                String link = mainFrame.getBaseUrl() + "/browse/" + jira.subtaskKey;
                matchesModel.addRow(new Object[]{
                    type, name, jira.subtaskKey, jira.status, jira.assignee, 
                    formatIspwAction(ispw.action), ispw.srNumber, ispw.userId, link
                });
            }

            autoResizeColumnWidths(onlyInIspwTable);
            autoResizeColumnWidths(onlyInJiraTable);
            autoResizeColumnWidths(matchesTable);
            
            statusLabel.setText("Comparison Complete: " + matches.size() + " matches, " + 
                onlyInIspw.size() + " only in ISPW, " + onlyInJira.size() + " only in Jira.");
        });
    }

    private String formatIspwAction(String action) {
        if (action == null) return "";
        String upper = action.toUpperCase();
        if (upper.equals("C")) return "Compile only";
        if (upper.equals("D")) return "Delete";
        return action;
    }

    private void autoResizeColumnWidths(JTable table) {
        final TableColumnModel columnModel = table.getColumnModel();
        for (int column = 0; column < table.getColumnCount(); column++) {
            int width = 50;
            TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(table, columnModel.getColumn(column).getHeaderValue(), false, false, 0, column);
            width = Math.max(width, headerComp.getPreferredSize().width);
            for (int row = 0; row < table.getRowCount(); row++) {
                TableCellRenderer renderer = table.getCellRenderer(row, column);
                Component comp = table.prepareRenderer(renderer, row, column);
                width = Math.max(width, comp.getPreferredSize().width);
            }
            columnModel.getColumn(column).setPreferredWidth(width + 15);
        }
    }
    
    private void fetchJiraTasks() {
        String[] topLevelKeys = jiraParentKeysArea.getText().trim().toUpperCase().split("\\s+");
        if (topLevelKeys.length == 0 || (topLevelKeys.length == 1 && topLevelKeys[0].isEmpty())) {
            JOptionPane.showMessageDialog(this, "Please enter at least one Jira Parent/Epic key.");
            return;
        }
        fetchJiraBtn.setEnabled(false);
        statusLabel.setText("Fetching Jira data...");
        ExecutionService.submit(() -> {
            try {
                JiraApiService service = mainFrame.getService();
                String baseUrl = mainFrame.getBaseUrl();
                SwingUtilities.invokeLater(() -> statusLabel.setText("Step 1/3: Fetching top-level summaries..."));
                Map<String, String> topLevelSummaries = fetchIssueSummaries(service, baseUrl, topLevelKeys);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Step 2/3: Fetching stories..."));
                Map<String, String> storySummaries = fetchStoriesInEpics(service, baseUrl, topLevelKeys);
                Map<String, String> allParentSummaries = new HashMap<>(topLevelSummaries);
                allParentSummaries.putAll(storySummaries);
                Set<String> allPotentialParentKeys = new HashSet<>(allParentSummaries.keySet());
                SwingUtilities.invokeLater(() -> statusLabel.setText("Step 3/3: Fetching all sub-tasks..."));
                List<JiraReconInfo> fetchedTasks = fetchAllSubtaskInfo(service, baseUrl, allPotentialParentKeys);
                this.jiraTaskMap = new HashMap<>();
                for (JiraReconInfo task : fetchedTasks) {
                    task.parentSummary = allParentSummaries.getOrDefault(task.parentKey, "N/A");
                    this.jiraTaskMap.put(task.subtaskSummary, task);
                }
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Success! Fetched " + this.jiraTaskMap.size() + " unique, ISPW-related Jira sub-tasks.");
                    fetchJiraBtn.setEnabled(true);
                });
            } catch (Exception ex) {
                StringWriter sw = new StringWriter();
                ex.printStackTrace(new PrintWriter(sw));
                JOptionPane.showMessageDialog(this, "Jira API Error:\n" + ex.getMessage(), "Execution Error", JOptionPane.ERROR_MESSAGE);
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Error fetching Jira data.");
                    fetchJiraBtn.setEnabled(true);
                });
            }
        });
    }
    
    private Map<String, String> fetchIssueSummaries(JiraApiService service, String baseUrl, String[] keys) throws Exception {
         Map<String, String> summaries = new HashMap<>();
         if (keys.length == 0) return summaries;
        String jql = "key in (" + String.join(",", keys) + ")";
        JSONObject payload = new JSONObject().put("jql", jql).put("fields", new JSONArray().put("summary"));
        String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
        JSONArray issues = new JSONObject(response).getJSONArray("issues");
        for (int i = 0; i < issues.length(); i++) {
            JSONObject issue = issues.getJSONObject(i);
            summaries.put(issue.getString("key"), issue.getJSONObject("fields").getString("summary"));
        }
        return summaries;
    }
    
    private Map<String, String> fetchStoriesInEpics(JiraApiService service, String baseUrl, String[] epicKeys) throws Exception {
        Map<String, String> storySummaries = new HashMap<>();
        if (epicKeys.length == 0) return storySummaries;
        String jql = String.format("\"Epic Link\" in (%s)", String.join(",", epicKeys));
        int startAt = 0;
        int total;
        do {
            JSONObject payload = new JSONObject()
                .put("jql", jql)
                .put("fields", new JSONArray().put("key").put("summary"))
                .put("startAt", startAt)
                .put("maxResults", 500); 
            String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
            JSONObject responseJson = new JSONObject(response);
            total = responseJson.getInt("total");
            JSONArray issues = responseJson.getJSONArray("issues");
            for (int i = 0; i < issues.length(); i++) {
                JSONObject issue = issues.getJSONObject(i);
                storySummaries.put(issue.getString("key"), issue.getJSONObject("fields").getString("summary"));
            }
            startAt += issues.length();
        } while (startAt < total);
        return storySummaries;
    }

    private List<JiraReconInfo> fetchAllSubtaskInfo(JiraApiService service, String baseUrl, Set<String> parentKeys) throws Exception {
        List<JiraReconInfo> tasks = new ArrayList<>();
        if (parentKeys.isEmpty()) return tasks;
        List<String> parentKeyList = new ArrayList<>(parentKeys);
        int batchSize = 200; 
        for (int i = 0; i < parentKeyList.size(); i += batchSize) {
            List<String> batch = parentKeyList.subList(i, Math.min(i + batchSize, parentKeyList.size()));
            String jql = "parent in (" + String.join(",", batch) + ") AND status != Canceled";
            int startAt = 0;
            int total;
            do {
                JSONObject payload = new JSONObject()
                    .put("jql", jql)
                    .put("fields", new JSONArray().put("summary").put("parent").put("assignee").put("status"))
                    .put("startAt", startAt)
                    .put("maxResults", 500);
                String response = service.executeRequest(baseUrl + "/rest/api/2/search", "POST", payload.toString());
                JSONObject responseJson = new JSONObject(response);
                total = responseJson.getInt("total");
                JSONArray issues = responseJson.getJSONArray("issues");
                for (int j = 0; j < issues.length(); j++) {
                    JSONObject issue = issues.getJSONObject(j);
                    JSONObject fields = issue.getJSONObject("fields");
                    String rawSummary = fields.getString("summary");
                    String tempSummary = rawSummary.trim().replaceAll("\\s+", " ");
                    String[] parts = tempSummary.split(" ");
                    String normalizedSummary;
                    if (parts.length >= 2) {
                        normalizedSummary = parts[0] + " " + parts[1];
                    } else {
                        normalizedSummary = tempSummary;
                    }
                    if (ISPW_PREFIXES.stream().anyMatch(prefix -> normalizedSummary.startsWith(prefix))) {
                        JiraReconInfo info = new JiraReconInfo();
                        info.subtaskKey = issue.getString("key");
                        info.subtaskSummary = normalizedSummary;
                        info.parentKey = fields.getJSONObject("parent").getString("key");
                        if (fields.has("assignee") && !fields.isNull("assignee")) {
                            info.assignee = fields.getJSONObject("assignee").getString("displayName");
                        }
                        if (fields.has("status") && !fields.isNull("status")) {
                            info.status = fields.getJSONObject("status").getString("name");
                        }
                        tasks.add(info);
                    }
                }
                startAt += issues.length();
            } while (startAt < total);
        }
        return tasks;
    }

    private void setupContextMenu() {
        onlyInIspwTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }

            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }

            private void showPopup(java.awt.event.MouseEvent e) {
                int row = onlyInIspwTable.rowAtPoint(e.getPoint());
                if (row >= 0 && row < onlyInIspwTable.getRowCount()) {
                    if (!onlyInIspwTable.isRowSelected(row)) {
                        onlyInIspwTable.setRowSelectionInterval(row, row);
                    }
                    
                    int[] selectedRows = onlyInIspwTable.getSelectedRows();
                    if (selectedRows.length > 0) {
                        JPopupMenu popup = new JPopupMenu();
                        JMenuItem buildItem = new JMenuItem("Send selected to Task Builder");
                        buildItem.addActionListener(al -> buildTaskBuilderEntries(selectedRows));
                        popup.add(buildItem);
                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });
    }

    private void buildTaskBuilderEntries(int[] selectedRows) {
        // 1. Get the list of parent keys from the jiraParentKeysArea
        String[] rawKeys = jiraParentKeysArea.getText().trim().toUpperCase().split("\\s+");
        java.util.List<String> keyList = new java.util.ArrayList<>();
        keyList.add("-- None / Use Defaults --");
        for (String k : rawKeys) {
            String clean = k.trim();
            if (!clean.isEmpty()) {
                keyList.add(clean);
            }
        }
        
        String selectedParent = null;
        if (keyList.size() > 1) {
            Object selected = JOptionPane.showInputDialog(
                this,
                "Select Parent Key for the selected ISPW tasks:",
                "Assign Parent Key",
                JOptionPane.QUESTION_MESSAGE,
                null,
                keyList.toArray(new String[0]),
                keyList.get(1) // Default to the first actual key
            );
            if (selected == null) {
                return; // User canceled the dialog
            }
            selectedParent = selected.toString();
            if (selectedParent.startsWith("--")) {
                selectedParent = null;
            }
        }
        
        // 2. Build the task builder formatted string
        StringBuilder sb = new StringBuilder();
        String sep = "******************************************************************";
        
        for (int i = 0; i < selectedRows.length; i++) {
            int modelRow = onlyInIspwTable.convertRowIndexToModel(selectedRows[i]);
            String type = (String) onlyInIspwModel.getValueAt(modelRow, 0);
            String name = (String) onlyInIspwModel.getValueAt(modelRow, 1);
            String action = (String) onlyInIspwModel.getValueAt(modelRow, 2);
            String srNumber = (String) onlyInIspwModel.getValueAt(modelRow, 3);
            String userId = (String) onlyInIspwModel.getValueAt(modelRow, 4);
            
            sb.append(type).append(" ").append(name).append("\n");
            sb.append("Action: ").append(action).append("\n");
            sb.append("SR Number: ").append(srNumber).append("\n");
            sb.append("User ID: ").append(userId).append("\n");
            
            if (selectedParent != null) {
                sb.append("parent: ").append(selectedParent).append("\n");
            }
            
            if (i < selectedRows.length - 1) {
                sb.append(sep).append("\n\n");
            } else {
                sb.append(sep);
            }
        }
        
        final String tasksText = sb.toString();
        
        // 3. Swap to Task Builder panel and populate
        SwingUtilities.invokeLater(() -> {
            mainFrame.showPanel("Task Builder");
            TaskBuilderPanel tbp = mainFrame.getTaskBuilderPanel();
            if (tbp != null) {
                String currentText = tbp.getInputAreaText();
                if (currentText != null && !currentText.trim().isEmpty()) {
                    int choice = JOptionPane.showConfirmDialog(
                        tbp,
                        "Task Builder already has content. Do you want to append the new tasks?\n(Selecting 'No' will overwrite the existing content)",
                        "Append or Overwrite",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                    );
                    if (choice == JOptionPane.YES_OPTION) {
                        tbp.appendInputAreaText(tasksText);
                    } else if (choice == JOptionPane.NO_OPTION) {
                        tbp.setInputAreaText(tasksText);
                    }
                } else {
                    tbp.setInputAreaText(tasksText);
                }
            }
        });
    }
}
