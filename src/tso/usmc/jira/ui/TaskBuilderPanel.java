package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JiraUtils;
import tso.usmc.jira.util.JsonUtils;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
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
    private final JComboBox<String> templateSelector = new JComboBox<>();
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
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 0: Parent and Template
        gbc.gridy = 0;
        gbc.gridx = 0; gbc.weightx = 0; configPanel.add(new JLabel("Parent:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.5; configPanel.add(parentField, gbc);
        gbc.gridx = 2; gbc.weightx = 0; configPanel.add(new JLabel(" Template:"), gbc);
        gbc.gridx = 3; gbc.weightx = 0.5; configPanel.add(templateSelector, gbc);

        addConfigRow(configPanel, "Type:", defTypeField, 1, 1);
        addConfigRow(configPanel, "Assignee:", defAssigneeField, 2, 1);
        addConfigRow(configPanel, "Component:", defCompField, 3, 1);
        addConfigRow(configPanel, "Transition:", defTransField, 4, 1);
        
        addSyncListener(parentField, "PARENT_TICKET");
        defTypeField.addActionListener(e -> syncToText("DEFAULT_TYPE", (String)defTypeField.getSelectedItem()));
        addSyncListener(defAssigneeField, "DEFAULT_ASSIGNEE");
        addSyncListener(defCompField, "DEFAULT_COMPONENT");
        addSyncListener(defTransField, "DEFAULT_TRANSITION");
        loadTemplatesFromDisk();
        templateSelector.addActionListener(e -> loadSelectedTemplate());
        leftPanel.add(configPanel, BorderLayout.NORTH);
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        inputArea.setSelectionColor(new Color(160, 200, 255)); // Slightly deeper blue
        inputArea.setSelectedTextColor(Color.BLACK); // Keep text black when selected
        setupDragAndDrop();
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { parseInput(); }
            public void removeUpdate(DocumentEvent e) { parseInput(); }
            public void changedUpdate(DocumentEvent e) { parseInput(); }
        });
        setupInputAreaKeyBindings();
        setupContextMenu();
        leftPanel.add(new JScrollPane(inputArea), BorderLayout.CENTER);
        JPanel rightPanel = new JPanel(new BorderLayout());
        taskList.setCellRenderer(new TaskCellRenderer()); // Use custom renderer to display HTML
        taskList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = taskList.locationToIndex(e.getPoint());
                    if (index != -1) {
                        JiraTask task = taskListModel.getElementAt(index);
                        
                        // If Ctrl is NOT held, we want to clear other selections and select only this one.
                        // Note: The JList already handles selection on the first click, 
                        // but being explicit ensures the double-click behavior matches the request.
                        if ((e.getModifiersEx() & java.awt.event.InputEvent.CTRL_DOWN_MASK) == 0) {
                            taskList.setSelectedIndex(index);
                        }
                        
                        // Scroll the task to the top of the text area
                        try {
                            Rectangle rect = inputArea.modelToView(task.startIndex);
                            if (rect != null) {
                                // To force the line to the top, we tell it to scroll to a rectangle 
                                // that starts at our line and is as tall as the visible area.
                                rect.height = inputArea.getVisibleRect().height;
                                inputArea.scrollRectToVisible(rect);
                            }
                        } catch (Exception ex) {
                            // Fallback if modelToView fails
                        }
                        
                        inputArea.setCaretPosition(task.startIndex);
                        inputArea.requestFocusInWindow();
                    }
                }
            }
        });
        taskList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    updateHighlights();
                }
            }
        });
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

    private void setupContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        
        JMenuItem addAssignee = new JMenuItem("Set Assignee...");
        addAssignee.addActionListener(e -> applyTaskOverride("assignee:"));
        menu.add(addAssignee);

        JMenuItem addParent = new JMenuItem("Set Parent...");
        addParent.addActionListener(e -> applyTaskOverride("parent:"));
        menu.add(addParent);
        
        JMenu componentMenu = new JMenu("Set Component");
        String[] teamKeys = mainFrame.getJiraConfig().getWorkflowTeamKeys();
        for (String key : teamKeys) {
            String details = mainFrame.getJiraConfig().getTeamDetails(key);
            if (details != null && details.contains("|")) {
                String label = details.split("\\|")[0];
                String compName = label; // Use index 0 as requested
                
                JCheckBoxMenuItem compItem = new JCheckBoxMenuItem(label);
                compItem.addActionListener(e -> {
                    toggleComponentOverride(compName);
                    // Keep the menu open after selection
                    MenuSelectionManager.defaultManager().setSelectedPath(new MenuElement[]{menu, componentMenu, compItem});
                });
                componentMenu.add(compItem);
            }
        }
        
        JMenuItem otherComp = new JMenuItem("Other...");
        otherComp.addActionListener(e -> applyTaskOverride("component:"));
        componentMenu.add(otherComp);
        
        // NEW: Update check states when the menu is shown
        componentMenu.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                String currentText = inputArea.getText();
                int caretPos = inputArea.getCaretPosition();
                int taskStart = 0;
                int lastPos = 0;
                while (true) {
                    int nextMatch = currentText.indexOf("***", lastPos);
                    if (nextMatch == -1 || nextMatch >= caretPos) break;
                    taskStart = nextMatch + 3;
                    lastPos = nextMatch + 3;
                }
                int taskEnd = currentText.indexOf("***", taskStart);
                if (taskEnd == -1) taskEnd = currentText.length();
                String block = currentText.substring(taskStart, taskEnd);
                
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^component:(.*)$");
                java.util.regex.Matcher m = p.matcher(block);
                Set<String> activeComps = new HashSet<>();
                if (m.find()) {
                    for (String s : m.group(1).split(",")) {
                        activeComps.add(s.trim());
                    }
                }
                
                for (Component c : componentMenu.getMenuComponents()) {
                    if (c instanceof JCheckBoxMenuItem) {
                        JCheckBoxMenuItem item = (JCheckBoxMenuItem) c;
                        // Find the corresponding component name from the config again
                        String[] teamKeys = mainFrame.getJiraConfig().getWorkflowTeamKeys();
                        for (String key : teamKeys) {
                            String details = mainFrame.getJiraConfig().getTeamDetails(key);
                            if (details != null && details.split("\\|")[0].equals(item.getText())) {
                                item.setSelected(activeComps.contains(details.split("\\|")[0]));
                                break;
                            }
                        }
                    }
                }
            }
            @Override public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });
        
        menu.add(componentMenu);
        
        JMenu transitionMenu = new JMenu("Set Transition");
        String[] transOptions = {"HOLD", "CANCELED", "IN PROGRESS", "DONE"};
        for (String trans : transOptions) {
            JMenuItem transItem = new JMenuItem(trans);
            transItem.addActionListener(e -> applyTaskOverride("transition:", trans));
            transitionMenu.add(transItem);
        }
        JMenuItem otherTrans = new JMenuItem("Other...");
        otherTrans.addActionListener(e -> applyTaskOverride("transition:"));
        transitionMenu.add(otherTrans);
        menu.add(transitionMenu);
        
        JMenu issueTypeMenu = new JMenu("Set Issue-Type");
        String[] types = {"Sub-task", "ST-PCU", "ST-Database", "ST-Interface"};
        for (String type : types) {
            JMenuItem typeItem = new JMenuItem(type);
            typeItem.addActionListener(e -> applyTaskOverride("issue-type:", type));
            issueTypeMenu.add(typeItem);
        }
        JMenuItem otherType = new JMenuItem("Other...");
        otherType.addActionListener(e -> applyTaskOverride("issue-type:"));
        issueTypeMenu.add(otherType);
        menu.add(issueTypeMenu);

        JMenuItem addDueDate = new JMenuItem("Set Due Date...");
        // Use current date as an example format
        String dateExample = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        addDueDate.addActionListener(e -> applyTaskOverride("duedate:", dateExample));
        menu.add(addDueDate);

        JMenuItem addNotify = new JMenuItem("Set Notify...");
        addNotify.addActionListener(e -> applyTaskOverride("notify:"));
        menu.add(addNotify);
        
        menu.addSeparator();
        
        JMenuItem clearAssignee = new JMenuItem("No Assignee");
        clearAssignee.addActionListener(e -> applyTaskOverride("noassignee:"));
        menu.add(clearAssignee);

        JMenuItem clearComp = new JMenuItem("No Component");
        clearComp.addActionListener(e -> applyTaskOverride("nocomponent:"));
        menu.add(clearComp);

        JMenuItem clearTrans = new JMenuItem("No Transition");
        clearTrans.addActionListener(e -> applyTaskOverride("notransition:"));
        menu.add(clearTrans);

        inputArea.setComponentPopupMenu(menu);
        
        // Ensure caret is updated on right click so we know which task we're in
        inputArea.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int pos = inputArea.viewToModel(e.getPoint());
                    inputArea.setCaretPosition(pos);
                }
            }
        });
    }

    private void toggleComponentOverride(String compName) {
        String text = inputArea.getText();
        int caretPos = inputArea.getCaretPosition();
        
        // Find boundaries of current task block
        int taskStart = 0;
        int lastPos = 0;
        while (true) {
            int nextMatch = text.indexOf("***", lastPos);
            if (nextMatch == -1 || nextMatch >= caretPos) break;
            taskStart = nextMatch + 3;
            lastPos = nextMatch + 3;
        }
        int taskEnd = text.indexOf("***", taskStart);
        if (taskEnd == -1) taskEnd = text.length();
        
        String block = text.substring(taskStart, taskEnd);
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^component:(.*)$");
        java.util.regex.Matcher m = p.matcher(block);
        
        if (m.find()) {
            String currentVals = m.group(1).trim();
            Set<String> comps = new LinkedHashSet<>();
            for (String s : currentVals.split(",")) {
                if (!s.trim().isEmpty()) comps.add(s.trim());
            }
            
            if (comps.contains(compName)) {
                comps.remove(compName);
            } else {
                comps.add(compName);
            }
            
            String newLine = "component: " + String.join(", ", comps);
            int lineStart = taskStart + m.start();
            int lineEnd = taskStart + m.end();
            inputArea.replaceRange(newLine, lineStart, lineEnd);
            inputArea.setCaretPosition(lineStart + newLine.length());
        } else {
            // Add as new line
            String before = (taskEnd == 0 || text.charAt(taskEnd - 1) == '\n') ? "" : "\n";
            String newLine = "component: " + compName + "\n";
            inputArea.insert(before + newLine, taskEnd);
            inputArea.setCaretPosition(taskEnd + before.length() + newLine.length() - 1);
        }
        inputArea.requestFocusInWindow();
        parseInput();
    }

    private void applyTaskOverride(String prefix) {
        applyTaskOverride(prefix, "");
    }

    private void applyTaskOverride(String prefix, String value) {
        String text = inputArea.getText();
        int caretPos = inputArea.getCaretPosition();
        
        // Find boundaries of the current task block
        int taskStart = 0;
        int lastPos = 0;
        while (true) {
            int nextMatch = text.indexOf("***", lastPos);
            if (nextMatch == -1 || nextMatch >= caretPos) break;
            taskStart = nextMatch + 3;
            lastPos = nextMatch + 3;
        }
        int taskEnd = text.indexOf("***", taskStart);
        if (taskEnd == -1) taskEnd = text.length();
        
        String block = text.substring(taskStart, taskEnd);
        String prefixMatch = prefix.contains(":") ? prefix.split(":")[0] : prefix;
        String linePrefix = prefixMatch + ":";
        
        // Use regex to find if this prefix already exists on its own line in this block
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^" + linePrefix + "(.*)$");
        java.util.regex.Matcher m = p.matcher(block);
        
        if (m.find()) {
            // Already exists.
            int lineStartInDoc = taskStart + m.start();
            int lineEndInDoc = taskStart + m.end();
            
            if (value.isEmpty()) {
                // If "Other..." or empty value selected, clear existing text and place cursor after colon
                inputArea.replaceRange(linePrefix, lineStartInDoc, lineEndInDoc);
                inputArea.setCaretPosition(lineStartInDoc + linePrefix.length());
            } else {
                // Replace existing value with new value and select it
                String newLine = linePrefix + value;
                inputArea.replaceRange(newLine, lineStartInDoc, lineEndInDoc);
                inputArea.setSelectionStart(lineStartInDoc + linePrefix.length());
                inputArea.setSelectionEnd(lineStartInDoc + newLine.length());
            }
        } else {
            // Not found, insert it at the end of the block
            String before = (taskEnd == 0 || text.charAt(taskEnd - 1) == '\n') ? "" : "\n";
            String after = "\n";
            String insertText = before + linePrefix + value + after;
            
            inputArea.insert(insertText, taskEnd);
            
            // Position caret/selection
            int insertPoint = taskEnd + before.length() + linePrefix.length();
            if (value.isEmpty()) {
                inputArea.setCaretPosition(insertPoint);
            } else {
                inputArea.setSelectionStart(insertPoint);
                inputArea.setSelectionEnd(insertPoint + value.length());
            }
        }
        inputArea.requestFocusInWindow();
        parseInput();
    }

    private void updateHighlights() {
        Highlighter h = inputArea.getHighlighter();
        h.removeAllHighlights();
        // Use semi-transparent green (alpha 100 out of 255) so manual blue selection blends/shows through
        Highlighter.HighlightPainter painter = new DefaultHighlighter.DefaultHighlightPainter(new Color(210, 255, 230, 150));
        
        for (JiraTask selectedTask : taskList.getSelectedValuesList()) {
            try {
                // Ensure indices are within bounds to avoid BadLocationException
                int start = Math.max(0, Math.min(selectedTask.startIndex, inputArea.getText().length()));
                int end = Math.max(0, Math.min(selectedTask.endIndex, inputArea.getText().length()));
                if (start < end) {
                    h.addHighlight(start, end, painter);
                }
            } catch (Exception ex) {
                // Silently ignore highlighting errors
            }
        }
    }

    private void updateStatus(String msg) { SwingUtilities.invokeLater(() -> statusBar.setText(" " + msg)); }

    private void addConfigRow(JPanel p, String label, JComponent f, int y, int gridwidth) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.gridy = y; gbc.gridx = 0; gbc.anchor = GridBagConstraints.EAST; p.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 0.5; gbc.gridwidth = gridwidth; gbc.fill = GridBagConstraints.HORIZONTAL; p.add(f, gbc);
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
        int currentOffset = 0;
        for (String block : text.split("\\*{3,}")) {
            int blockStart = text.indexOf(block, currentOffset);
            if (block.trim().isEmpty()) {
                if (blockStart != -1) currentOffset = blockStart + block.length();
                continue;
            }
            JiraTask task = new JiraTask();
            task.startIndex = blockStart;
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
                if (t.startsWith("parent:")) { task.parent = val(t).toUpperCase(); continue; }
                if (t.startsWith("duedate:")) { task.duedate = val(t); continue; }
                 if (t.startsWith("notify:")) { task.notify = val(t); continue; }
                if (!summaryFound && !t.isEmpty()) { task.summary = t; summaryFound = true; }
                else if (summaryFound) { desc.append(line).append("\n"); }
            }

            if (summaryFound) {
                applyDefaults(task);
                task.description = desc.toString().trim();
                task.endIndex = blockStart + block.length();
                parsedTasks.add(task); // Keep the main list of all parsed tasks
                taskListModel.addElement(task); // Add task to the model for the JList
            }
            if (blockStart != -1) currentOffset = blockStart + block.length();
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
        updateHighlights();
        
        isUpdating = false;
    }
    
    private void loadTemplatesFromDisk() {
        templateSelector.removeAllItems();
        templateSelector.addItem("--- Select Template ---");
        
        try {
            File templateDir = new File(mainFrame.getJiraConfig().getConfigFile().getParentFile(), "template");
            if (!templateDir.exists()) {
                templateDir.mkdirs();
            }
            
            File[] files = templateDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".txt"));
            if (files != null) {
                Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                for (File f : files) {
                    templateSelector.addItem(f.getName());
                }
            }
        } catch (Exception ex) {
            // Silently ignore failures to list templates
        }
    }

    private void loadSelectedTemplate() {
        if (isUpdating) return;
        String selected = (String) templateSelector.getSelectedItem();
        if (selected == null || selected.equals("--- Select Template ---")) return;
        
        try {
            File templateDir = new File(mainFrame.getJiraConfig().getConfigFile().getParentFile(), "template");
            File templateFile = new File(templateDir, selected);
            String content = new String(Files.readAllBytes(templateFile.toPath()));
            
            // Clear input area and set new content
            inputArea.setText(content);
            
            // Force parse and select all tasks
            parseInput();
            setAllTasksSelected(true);
            
            // Reset selector so it can be re-selected if needed
            SwingUtilities.invokeLater(() -> templateSelector.setSelectedIndex(0));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error reading template: " + ex.getMessage());
        }
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

            String defaultParent = parentField.getText().trim().toUpperCase();
            int total = selected.size();
            List<String> createdKeys = new ArrayList<>();

            try {
                if (total > 1 && !MOCK_MODE) {
                    updateStatus("Creating " + total + " tasks in bulk...");
                    List<String> taskJsons = new ArrayList<>();
                    for (JiraTask t : selected) {
                        String parent = (t.parent != null && !t.parent.isEmpty()) ? t.parent : defaultParent;
                        String proj = parent.contains("-") ? parent.split("-")[0] : "PROJ";
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
                        String parent = (t.parent != null && !t.parent.isEmpty()) ? t.parent : defaultParent;
                        String proj = parent.contains("-") ? parent.split("-")[0] : "PROJ";
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
    
        private void setupInputAreaKeyBindings() {
        InputMap im = inputArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = inputArea.getActionMap();

        im.put(KeyStroke.getKeyStroke("ctrl alt DOWN"), "duplicateDown");
        am.put("duplicateDown", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { duplicateLines(true); }
        });

        im.put(KeyStroke.getKeyStroke("ctrl alt UP"), "duplicateUp");
        am.put("duplicateUp", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { duplicateLines(false); }
        });

        im.put(KeyStroke.getKeyStroke("alt DOWN"), "moveDown");
        am.put("moveDown", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { moveLines(true); }
        });

        im.put(KeyStroke.getKeyStroke("alt UP"), "moveUp");
        am.put("moveUp", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { moveLines(false); }
        });

        im.put(KeyStroke.getKeyStroke("ctrl SLASH"), "toggleComment");
        am.put("toggleComment", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { toggleComments(); }
        });

        im.put(KeyStroke.getKeyStroke("ctrl D"), "deleteLines");
        am.put("deleteLines", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) { deleteLines(); }
        });
    }

    private void duplicateLines(boolean down) {
        try {
            int start = inputArea.getSelectionStart();
            int end = inputArea.getSelectionEnd();
            int lineStart = javax.swing.text.Utilities.getRowStart(inputArea, start);
            int lineEnd = javax.swing.text.Utilities.getRowEnd(inputArea, end);
            
            String textToDuplicate = inputArea.getText(lineStart, lineEnd - lineStart);
            if (textToDuplicate.isEmpty()) return;

            if (down) {
                inputArea.insert("\n" + textToDuplicate, lineEnd);
            } else {
                inputArea.insert(textToDuplicate + "\n", lineStart);
                inputArea.setCaretPosition(lineStart);
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void moveLines(boolean down) {
        try {
            int start = inputArea.getSelectionStart();
            int end = inputArea.getSelectionEnd();
            int lineStart = javax.swing.text.Utilities.getRowStart(inputArea, start);
            int lineEnd = javax.swing.text.Utilities.getRowEnd(inputArea, end);
            
            int docLen = inputArea.getDocument().getLength();
            // Include the newline character if it exists
            int selectionEnd = (lineEnd < docLen) ? lineEnd + 1 : lineEnd;
            String textToMove = inputArea.getText(lineStart, selectionEnd - lineStart);
            
            // Ensure the text to move ends with a newline unless it's at the very end of the document
            if (!textToMove.endsWith("\n") && selectionEnd < docLen) {
                textToMove += "\n";
            }

            if (down) {
                if (selectionEnd >= docLen) return; // Already at the bottom
                int nextLineEnd = javax.swing.text.Utilities.getRowEnd(inputArea, selectionEnd);
                int nextSelectionEnd = (nextLineEnd < docLen) ? nextLineEnd + 1 : nextLineEnd;
                String nextLine = inputArea.getText(selectionEnd, nextSelectionEnd - selectionEnd);
                
                if (!nextLine.endsWith("\n") && nextSelectionEnd < docLen) {
                    nextLine += "\n";
                }

                inputArea.replaceRange(nextLine + textToMove, lineStart, nextSelectionEnd);
                int newStart = lineStart + nextLine.length();
                inputArea.setSelectionStart(newStart);
                inputArea.setSelectionEnd(newStart + textToMove.length() - (textToMove.endsWith("\n") ? 1 : 0));
            } else {
                if (lineStart <= 0) return; // Already at the top
                int prevLineStart = javax.swing.text.Utilities.getRowStart(inputArea, lineStart - 1);
                String prevLine = inputArea.getText(prevLineStart, lineStart - prevLineStart);
                
                if (!prevLine.endsWith("\n")) {
                    prevLine += "\n";
                }

                inputArea.replaceRange(textToMove + prevLine, prevLineStart, selectionEnd);
                inputArea.setSelectionStart(prevLineStart);
                inputArea.setSelectionEnd(prevLineStart + textToMove.length() - (textToMove.endsWith("\n") ? 1 : 0));
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void toggleComments() {
        try {
            int start = inputArea.getSelectionStart();
            int end = inputArea.getSelectionEnd();
            int lineStart = javax.swing.text.Utilities.getRowStart(inputArea, start);
            int lineEnd = javax.swing.text.Utilities.getRowEnd(inputArea, end);
            String selectedText = inputArea.getText(lineStart, lineEnd - lineStart);
            
            String[] lines = selectedText.split("\n", -1);
            boolean allCommented = true;
            for (String line : lines) {
                if (!line.trim().isEmpty() && !line.trim().startsWith("--")) {
                    allCommented = false;
                    break;
                }
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (allCommented) {
                    if (line.trim().startsWith("--")) {
                        sb.append(line.replaceFirst("--\\s?", ""));
                    } else {
                        sb.append(line);
                    }
                } else {
                    sb.append("-- ").append(line);
                }
                if (i < lines.length - 1) sb.append("\n");
            }
            
            inputArea.replaceRange(sb.toString(), lineStart, lineEnd);
            inputArea.setSelectionStart(lineStart);
            inputArea.setSelectionEnd(lineStart + sb.length());
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void deleteLines() {
        try {
            int start = inputArea.getSelectionStart();
            int end = inputArea.getSelectionEnd();
            int lineStart = javax.swing.text.Utilities.getRowStart(inputArea, start);
            int lineEnd = javax.swing.text.Utilities.getRowEnd(inputArea, end);
            
            int docLen = inputArea.getDocument().getLength();
            // Include the trailing newline if it's not the last line
            int deletionEnd = (lineEnd < docLen) ? lineEnd + 1 : lineEnd;
            
            inputArea.replaceRange("", lineStart, deletionEnd);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

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
        String summary = "", description = "", type = null, assignee = "", component = "", transition = "", duedate = null, notify = null, parent = null;
        boolean overAssignee = false, overComp = false, overTrans = false;
        int startIndex = 0, endIndex = 0;
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
