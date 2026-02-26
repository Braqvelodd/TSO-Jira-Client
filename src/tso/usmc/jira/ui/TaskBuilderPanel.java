package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JiraUtils;
import tso.usmc.jira.util.JsonUtils;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;


public class TaskBuilderPanel extends JPanel {

    private static final boolean MOCK_MODE = false;

    private final JiraApiClientGui mainFrame;

    private boolean isUpdating = false;

    // UI Components
    private final JTextField parentField = new JTextField(20);
    private final JComboBox<String> defTypeField = new JComboBox<>(new String[]{"Sub-task", "ST-PCU", "ST-Database", "ST-Interface"});
    private final JTextField defAssigneeField = new JTextField(20);
    private final JTextField defCompField = new JTextField(20);
    private final JTextField defTransField = new JTextField(20);
    private final JTextArea inputArea = new JTextArea();
    private final DefaultListModel<JiraTask> taskListModel = new DefaultListModel<>();
    private final JList<JiraTask> taskList = new JList<>(taskListModel);
    private final List<JiraTask> parsedTasks = new ArrayList<>();

    private final DefaultTableModel resultsTableModel = new DefaultTableModel(new Object[]{"Summary", "Status", "Jira Link"}, 0);
    private final JTable resultsTable = new JTable(resultsTableModel);

    private final JLabel statusBar = new JLabel(" Ready");

    private final JButton selectAllBtn = new JButton("Select All");
    private final JButton unselectAllBtn = new JButton("Unselect All");

    public TaskBuilderPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new BorderLayout());

        // --- All UI setup code is largely unchanged from your original version ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Defaults " + (MOCK_MODE ? "(MOCK)" : "")));
        addConfigRow(configPanel, "Parent:", parentField, 0);
        addConfigRow(configPanel, "Type:", defTypeField, 1);
        addConfigRow(configPanel, "Assignee:", defAssigneeField, 2);
        addConfigRow(configPanel, "Component:", defCompField, 3);
        addConfigRow(configPanel, "Transition:", defTransField, 4);
        addSyncListener(parentField, "PARENT_TICKET");
        defTypeField.addActionListener(e -> syncToText("DEFAULT_TYPE", (String)defTypeField.getSelectedItem()));
        addSyncListener(defAssigneeField, "DEFAULT_ASSIGNEE");
        addSyncListener(defCompField, "DEFAULT_COMPONENT");
        addSyncListener(defTransField, "DEFAULT_TRANSITION");
        leftPanel.add(configPanel, BorderLayout.NORTH);
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        setupDragAndDrop();
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { parseInput(); }
            public void removeUpdate(DocumentEvent e) { parseInput(); }
            public void changedUpdate(DocumentEvent e) { parseInput(); }
        });
        leftPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        JPanel rightPanel = new JPanel(new BorderLayout());
        taskList.setCellRenderer(new TaskCellRenderer()); // Use custom renderer to display HTML
        rightPanel.add(new JScrollPane(taskList), BorderLayout.CENTER);

        JPanel actionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5));
        actionButtonsPanel.add(selectAllBtn);
        actionButtonsPanel.add(unselectAllBtn);
        JButton executeBtn = new JButton(MOCK_MODE ? "Run Mock Execution" : "Execute Selected Tasks");
        actionButtonsPanel.add(executeBtn);
        rightPanel.add(actionButtonsPanel, BorderLayout.SOUTH);
        setupResultsTable();
        JPanel bottomContainer = new JPanel(new BorderLayout());
        JScrollPane tableScroll = new JScrollPane(resultsTable);
        tableScroll.setPreferredSize(new Dimension(0, 150));
        bottomContainer.add(tableScroll, BorderLayout.CENTER);
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.setPreferredSize(new Dimension(getWidth(), 25));
        bottomContainer.add(statusBar, BorderLayout.SOUTH);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);
        add(bottomContainer, BorderLayout.SOUTH);
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.5));
        selectAllBtn.addActionListener(e -> setAllTasksSelected(true));
        unselectAllBtn.addActionListener(e -> setAllTasksSelected(false));
        executeBtn.addActionListener(e -> executeTasks());
    }

    private void updateStatus(String msg) { SwingUtilities.invokeLater(() -> statusBar.setText(" " + msg)); }

    private void addConfigRow(JPanel p, String label, JComponent f, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = y; gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST; p.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 1.0; p.add(f, gbc);
    }

    private void addSyncListener(JTextField field, String prefix) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { syncToText(prefix, field.getText()); }
            public void removeUpdate(DocumentEvent e) { syncToText(prefix, field.getText()); }
            public void changedUpdate(DocumentEvent e) { syncToText(prefix, field.getText()); }
        });
    }

    private void syncToText(String prefix, String newValue) {
        if (isUpdating) return;
        isUpdating = true;
        String content = inputArea.getText();
        String lineStart = prefix + ":";
        if (content.contains(lineStart)) {
            content = content.replaceAll("(?m)^" + lineStart + ".*$", lineStart + newValue);
        } else {
            content = lineStart + newValue + "\n" + content;
        }
        inputArea.setText(content);
        isUpdating = false;
    }

    private void setupResultsTable() {
        resultsTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
                super.getTableCellRendererComponent(t, v, isS, hasF, r, c);
                if (v != null && v.toString().startsWith("http")) {
                    setText("<html><a href=''>" + v.toString() + "</a></html>"); setForeground(Color.BLUE);
                }
                return this;
            }
        });
        resultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int col = resultsTable.columnAtPoint(e.getPoint());
                if (col == 2) {
                    try { Desktop.getDesktop().browse(new java.net.URI((String)resultsTableModel.getValueAt(resultsTable.rowAtPoint(e.getPoint()), 2))); } catch (Exception ex) {}
                }
            }
        });
    }

    /**
     * USES ORIGINAL, WORKING PARSE LOGIC.
     * Now adds items to the JList model instead of creating JCheckBoxes.
     */
    private void parseInput() {
        if (isUpdating) return;
        isUpdating = true;

        // 1. Save the current selection state before destroying the UI
        Set<String> selectedSummaries = new HashSet<>();
        for (JiraTask selectedTask : taskList.getSelectedValuesList()) {
            selectedSummaries.add(selectedTask.summary);
        }

        // 2. Clear the UI and the task list
        parsedTasks.clear();
        taskListModel.clear();

        String text = inputArea.getText();
        for (String block : text.split("\\*{3,}")) {
            if (block.trim().isEmpty()) continue;
            JiraTask task = new JiraTask();
            StringBuilder desc = new StringBuilder();
            boolean summaryFound = false;

            for (String line : block.split("\n")) {
                String t = line.trim();
                if (t.startsWith("--")) continue;
                if (t.startsWith("DEFAULT_TYPE:")) { defTypeField.setSelectedItem(val(t)); continue; }
                if (t.startsWith("DEFAULT_ASSIGNEE:")) { defAssigneeField.setText(val(t)); continue; }
                if (t.startsWith("DEFAULT_COMPONENT:")) { defCompField.setText(val(t)); continue; }
                if (t.startsWith("DEFAULT_TRANSITION:")) { defTransField.setText(val(t)); continue; }
                if (t.startsWith("PARENT_TICKET:")) { parentField.setText(val(t).toUpperCase()); continue; }
                if (t.equalsIgnoreCase("noassignee:")) { task.assignee = ""; task.overAssignee = true; continue; }
                if (t.equalsIgnoreCase("nocomponent:")) { task.component = ""; task.overComp = true; continue; }
                if (t.equalsIgnoreCase("notransition:")) { task.transition = ""; task.overTrans = true; continue; }
                if (t.startsWith("assignee:")) { task.assignee = val(t); task.overAssignee = true; continue; }
                if (t.startsWith("component:")) { task.component = val(t); task.overComp = true; continue; }
                if (t.startsWith("issue-type:")) { task.type = val(t); continue; }
                if (t.startsWith("transition:")) { task.transition = val(t); task.overTrans = true; continue; }
                if (t.startsWith("duedate:")) { task.duedate = val(t); continue; }
                 if (t.startsWith("notify:")) { task.notify = val(t); continue; }
                if (!summaryFound && !t.isEmpty()) { task.summary = t; summaryFound = true; }
                else if (summaryFound) { desc.append(line).append("\n"); }
            }

            if (summaryFound) {
                applyDefaults(task);
                task.description = desc.toString().trim();
                parsedTasks.add(task); // Keep the main list of all parsed tasks
                taskListModel.addElement(task); // Add task to the model for the JList
            }
        }
        
        // 3. Re-apply the saved state
        List<JiraTask> tasksToSelect = new ArrayList<>();
        for (int i = 0; i < taskListModel.size(); i++) {
            JiraTask currentTask = taskListModel.getElementAt(i);
            // Select if it was selected before, or if it's a new item (no previous selection existed)
            if (selectedSummaries.contains(currentTask.summary) || selectedSummaries.isEmpty()) {
                tasksToSelect.add(currentTask);
            }
        }
        
        // JList requires setting an array of indices to select multiple items
        int[] indicesToSelect = new int[tasksToSelect.size()];
        for(int i = 0; i < tasksToSelect.size(); i++) {
            indicesToSelect[i] = taskListModel.indexOf(tasksToSelect.get(i));
        }
        taskList.setSelectedIndices(indicesToSelect);
        
        isUpdating = false;
    }
    
    private void applyDefaults(JiraTask t) {
        if (t.type == null) t.type = (String)defTypeField.getSelectedItem();
        if (!t.overAssignee) t.assignee = defAssigneeField.getText();
        if (!t.overComp) t.component = defCompField.getText();
        if (!t.overTrans) t.transition = defTransField.getText();
    }

    private String val(String s) { return s.contains(":") ? s.substring(s.indexOf(":") + 1).trim() : ""; }

    private void executeTasks() {
        resultsTableModel.setRowCount(0);
        parseInput(); // Ensure defaults are fresh before execution
        new Thread(() -> {
            List<JiraTask> selected = taskList.getSelectedValuesList();

            if (selected.isEmpty()) {
                updateStatus("No tasks selected.");
                return;
            }

            String parent = parentField.getText().trim().toUpperCase();
            String proj = parent.contains("-") ? parent.split("-")[0] : "PROJ";
            int total = selected.size();
            List<String> createdKeys = new ArrayList<>();

            try {
                if (total > 1 && !MOCK_MODE) {
                    updateStatus("Creating " + total + " tasks in bulk...");
                    List<String> taskJsons = new ArrayList<>();
                    for (JiraTask t : selected) {
                        String assignee = t.assignee;
                        List<String> noAssigneeTypes = Arrays.asList("ST-PCU", "ST-Database", "ST-Interface");
                        if (t.type != null && noAssigneeTypes.contains(t.type)) {
                            assignee = null;
                        }
                        taskJsons.add(JsonUtils.buildManualJson(proj, parent, t.summary, t.description, t.type, assignee, t.component, t.duedate));
                    }
                    String bulkJson = JsonUtils.buildBulkJson(taskJsons);
                    String resp = mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/bulk", "POST", bulkJson);
                    
                    JSONObject bulkResp = new JSONObject(resp);
                    JSONArray issues = bulkResp.getJSONArray("issues");
                    for (int i = 0; i < issues.length(); i++) {
                        createdKeys.add(issues.getJSONObject(i).getString("key"));
                    }
                } else {
                    // Single create or Mock mode
                    for (int i = 0; i < total; i++) {
                        JiraTask t = selected.get(i);
                        if (MOCK_MODE) {
                            Thread.sleep(400);
                            createdKeys.add(proj + "-" + (100 + new Random().nextInt(900)));
                        } else {
                            String assignee = t.assignee;
                            List<String> noAssigneeTypes = Arrays.asList("ST-PCU", "ST-Database", "ST-Interface");
                            if (t.type != null && noAssigneeTypes.contains(t.type)) {
                                assignee = null;
                            }
                            String createJson = JsonUtils.buildManualJson(proj, parent, t.summary, t.description, t.type, assignee, t.component, t.duedate);
                            String resp = mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue", "POST", createJson);
                            createdKeys.add(new JSONObject(resp).getString("key"));
                        }
                    }
                }

                // Now process transitions and notifications for the created issues
                for (int i = 0; i < selected.size(); i++) {
                    JiraTask t = selected.get(i);
                    String key = createdKeys.get(i);
                    String link = mainFrame.getBaseUrl() + "/browse/" + key;
                    String status = "CREATED";

                    if (!t.transition.isEmpty()) {
                        updateStatus("Transitioning " + key + " to " + t.transition + "...");
                        try {
                            String transitionId = null;
                            if (MOCK_MODE) {
                                Thread.sleep(300);
                                transitionId = "711";
                            } else {
                                String transitionsResponse = mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "/transitions", "GET", null);
                                transitionId = JiraUtils.findTransitionIdByName(transitionsResponse, t.transition);
                            }
                            if (transitionId != null) {
                                if (!MOCK_MODE) {
                                    JSONObject transitionPayload = new JSONObject();
                                    transitionPayload.put("transition", new JSONObject().put("id", transitionId));
                                    mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "/transitions", "POST", transitionPayload.toString());
                                }
                                status = "CREATED & MOVED TO: " + t.transition.toUpperCase();
                            } else {
                                status = "CREATED (Trans. '" + t.transition + "' not found)";
                            }
                        } catch (Exception ex) {
                            status = "CREATED (Trans. Failed: " + ex.getMessage() + ")";
                        }
                    }

                    if (t.notify != null && !t.notify.trim().isEmpty()) {
                        updateStatus("Notifying users for " + key + "...");
                        try {
                            if (MOCK_MODE) {
                                Thread.sleep(200);
                            } else {
                                JSONObject notifyPayload = new JSONObject();
                                notifyPayload.put("subject", "Task Created: " + t.summary);
                                notifyPayload.put("textBody", "A new issue has been created that you were listed to be notified about.\n\n" +
                                        "Summary: " + t.summary + "\n" +
                                        "Link: " + link);
                                JSONArray usersToNotify = new JSONArray();
                                String[] userNames = t.notify.split("\\s*,\\s*");
                                for (String userName : userNames) {
                                    if (!userName.trim().isEmpty()) {
                                        usersToNotify.put(new JSONObject().put("name", userName.trim()));
                                    }
                                }
                                notifyPayload.put("to", new JSONObject().put("users", usersToNotify));
                                mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + key + "/notify", "POST", notifyPayload.toString());
                            }
                            status += " & NOTIFIED";
                        } catch (Exception notifyEx) {
                            status += " (Notify Failed: " + notifyEx.getMessage() + ")";
                        }
                    }
                    addRow(t.summary, status, link);
                }
                updateStatus("Execution Complete. " + total + " tasks processed.");
            } catch (Exception e) {
                updateStatus("Execution Failed: " + e.getMessage());
                addRow("SYSTEM ERROR", e.getMessage(), "N/A");
            }
        }).start();
    }

    private void addRow(String s, String st, String l) { SwingUtilities.invokeLater(() -> resultsTableModel.addRow(new Object[]{s, st, l})); }
    
        private void setupDragAndDrop() {
        inputArea.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent e) {
                try {
                    e.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> files = (List<File>) e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : files) {
                        if (inputArea.getText().length() > 0) {
                            inputArea.append("\n***\n");
                        }
                        inputArea.append(new String(Files.readAllBytes(f.toPath())));
                    }
                } catch (Exception ex) {
                    // Handle exception
                }
            }
        });
    }
    
    private void setAllTasksSelected(boolean selected) {
        if (selected) {
            taskList.setSelectionInterval(0, taskListModel.getSize() - 1);
        } else {
            taskList.clearSelection();
        }
    }
    
    // JiraTask class is unchanged, but no longer needs a JCheckBox member
    private class JiraTask {
        String summary = "", description = "", type = null, assignee = "", component = "", transition = "", duedate = null, notify = null;
        boolean overAssignee = false, overComp = false, overTrans = false;
    }
    public void setParentTicket(String issueKey) {
        // Replace 'parentTicketField' with the actual name of your parent ticket JTextField
        if (issueKey != null) {
            parentField.setText(issueKey);
        }
    }
    public void setInputAreaText(String text) {
    // IMPORTANT: Replace 'yourInputTextArea' with the actual name of your JTextArea variable!
    if (text != null) {
        inputArea.setText(text);
    }
}

    /**
     * NEW: Custom renderer to display JiraTask objects in the JList
     * This creates the same HTML label that the JCheckBox used to.
     */
    private static class TaskCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            // Let the default renderer configure the colors and border for selection
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value instanceof JiraTask) {
                JiraTask task = (JiraTask) value;
                String label = "<html>" + task.summary + (task.transition.isEmpty() ? "" : " <font color='red'>[" + task.transition + "]</font>") + "</html>";
                setText(label);
            }
            return this;
        }
    }
}
