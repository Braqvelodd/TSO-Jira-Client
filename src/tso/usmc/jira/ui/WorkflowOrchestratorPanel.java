package tso.usmc.jira.ui;

import org.json.JSONArray;
import org.json.JSONObject;
import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JiraConfig;
import tso.usmc.jira.util.JsonUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class WorkflowOrchestratorPanel extends JPanel {

    private final JiraApiClientGui mainFrame;
    private final JiraConfig jiraConfig;

    private final DefaultListModel<String> workflowListModel = new DefaultListModel<>();
    private final JList<String> workflowList = new JList<>(workflowListModel);
    
    private final DefaultTableModel stepsTableModel = new DefaultTableModel(new String[]{"Action", "Method", "Endpoint", "Body"}, 0);
    private final JTable stepsTable = new JTable(stepsTableModel);
    
    private final JTextArea logArea = new JTextArea();
    private final JTextField workflowNameField = new JTextField(20);

    public WorkflowOrchestratorPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        this.jiraConfig = mainFrame.getJiraConfig();
        setLayout(new BorderLayout());

        // --- LEFT: Workflow List ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("Saved Workflows"));
        leftPanel.setPreferredSize(new Dimension(250, 0));
        leftPanel.add(new JScrollPane(workflowList), BorderLayout.CENTER);
        
        JPanel listButtons = new JPanel(new FlowLayout());
        JButton deleteBtn = new JButton("Delete");
        listButtons.add(deleteBtn);
        leftPanel.add(listButtons, BorderLayout.SOUTH);

        // --- CENTER: Builder ---
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder("Workflow Builder"));
        
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        namePanel.add(new JLabel("Workflow Name:"));
        namePanel.add(workflowNameField);
        JButton saveBtn = new JButton("Save Workflow");
        namePanel.add(saveBtn);
        centerPanel.add(namePanel, BorderLayout.NORTH);

        centerPanel.add(new JScrollPane(stepsTable), BorderLayout.CENTER);
        
        JPanel stepButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addStepBtn = new JButton("Add Step from API Template");
        JButton removeStepBtn = new JButton("Remove Step");
        JButton moveUpBtn = new JButton("Move Up");
        JButton moveDownBtn = new JButton("Move Down");
        stepButtons.add(addStepBtn);
        stepButtons.add(removeStepBtn);
        stepButtons.add(moveUpBtn);
        stepButtons.add(moveDownBtn);
        centerPanel.add(stepButtons, BorderLayout.SOUTH);

        // --- BOTTOM: Log and Execute ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setPreferredSize(new Dimension(0, 200));
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        bottomPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        
        JButton executeBtn = new JButton("Execute Workflow");
        executeBtn.setFont(executeBtn.getFont().deriveFont(Font.BOLD, 14f));
        executeBtn.setBackground(new Color(200, 255, 200));
        bottomPanel.add(executeBtn, BorderLayout.SOUTH);

        // --- Layout Assembly ---
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, centerPanel);
        add(mainSplit, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- Action Listeners ---
        addStepBtn.addActionListener(e -> addStepFromTemplate());
        removeStepBtn.addActionListener(e -> {
            int row = stepsTable.getSelectedRow();
            if (row != -1) stepsTableModel.removeRow(row);
        });
        moveUpBtn.addActionListener(e -> moveStep(-1));
        moveDownBtn.addActionListener(e -> moveStep(1));
        saveBtn.addActionListener(e -> saveWorkflow());
        executeBtn.addActionListener(e -> executeWorkflow());
        workflowList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadWorkflow(workflowList.getSelectedValue());
        });
        deleteBtn.addActionListener(e -> deleteWorkflow());

        loadWorkflowNames();
    }

    private void addStepFromTemplate() {
        String[] templates = jiraConfig.getRawApiTemplateKeys();
        if (templates.length == 0) {
            JOptionPane.showMessageDialog(this, "No API templates found in configuration.");
            return;
        }
        
        String selected = (String) JOptionPane.showInputDialog(this, "Select Template", "Add Step",
                JOptionPane.QUESTION_MESSAGE, null, templates, templates[0]);
        
        if (selected != null) {
            String val = jiraConfig.getRawApiTemplate(selected);
            String[] parts = val.split("\\|");
            if (parts.length >= 3) {
                String label = parts[0];
                String method = parts[1];
                String endpoint = parts[2];
                String body = (parts.length > 3) ? parts[3] : "";
                stepsTableModel.addRow(new Object[]{label, method, endpoint, body});
            }
        }
    }

    private void moveStep(int direction) {
        int row = stepsTable.getSelectedRow();
        if (row == -1) return;
        int target = row + direction;
        if (target >= 0 && target < stepsTableModel.getRowCount()) {
            stepsTableModel.moveRow(row, row, target);
            stepsTable.setRowSelectionInterval(target, target);
        }
    }

    private void saveWorkflow() {
        String name = workflowNameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a workflow name.");
            return;
        }

        JSONArray steps = new JSONArray();
        for (int i = 0; i < stepsTableModel.getRowCount(); i++) {
            JSONObject step = new JSONObject();
            step.put("label", stepsTableModel.getValueAt(i, 0));
            step.put("method", stepsTableModel.getValueAt(i, 1));
            step.put("endpoint", stepsTableModel.getValueAt(i, 2));
            step.put("body", stepsTableModel.getValueAt(i, 3));
            steps.put(step);
        }

        String key = "workflow." + name.replace(" ", "_");
        String encoded = steps.toString().replace("\n", "\\n");
        
        try {
            File iniFile = jiraConfig.getTemplateFile();
            List<String> lines = Files.readAllLines(iniFile.toPath());
            boolean updated = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(key + " =")) {
                    lines.set(i, key + " = " + encoded);
                    updated = true;
                    break;
                }
            }
            if (!updated) {
                lines.add(key + " = " + encoded);
            }
            Files.write(iniFile.toPath(), lines);
            jiraConfig.reload();
            loadWorkflowNames();
            JOptionPane.showMessageDialog(this, "Workflow saved successfully.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error saving workflow: " + ex.getMessage());
        }
    }

    private void loadWorkflowNames() {
        workflowListModel.clear();
        try {
            File iniFile = jiraConfig.getTemplateFile();
            List<String> lines = Files.readAllLines(iniFile.toPath());
            for (String line : lines) {
                if (line.startsWith("workflow.")) {
                    String name = line.split("=")[0].substring(9).trim().replace("_", " ");
                    workflowListModel.addElement(name);
                }
            }
        } catch (Exception ignored) {}
    }

    private void loadWorkflow(String name) {
        if (name == null) return;
        workflowNameField.setText(name);
        stepsTableModel.setRowCount(0);
        
        String key = "workflow." + name.replace(" ", "_");
        String val = jiraConfig.getProperty(key);
        if (val != null) {
            try {
                JSONArray steps = new JSONArray(val.replace("\\n", "\n"));
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject s = steps.getJSONObject(i);
                    stepsTableModel.addRow(new Object[]{
                        s.optString("label"),
                        s.optString("method"),
                        s.optString("endpoint"),
                        s.optString("body")
                    });
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void deleteWorkflow() {
        String selected = workflowList.getSelectedValue();
        if (selected == null) return;
        
        int choice = JOptionPane.showConfirmDialog(this, "Delete workflow '" + selected + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;
        
        String key = "workflow." + selected.replace(" ", "_");
        try {
            File iniFile = jiraConfig.getTemplateFile();
            List<String> lines = Files.readAllLines(iniFile.toPath());
            lines.removeIf(line -> line.startsWith(key + " ="));
            Files.write(iniFile.toPath(), lines);
            jiraConfig.reload();
            loadWorkflowNames();
            stepsTableModel.setRowCount(0);
            workflowNameField.setText("");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error deleting workflow: " + ex.getMessage());
        }
    }

    private void executeWorkflow() {
        logArea.setText("Starting execution of '" + workflowNameField.getText() + "'...\n");
        new Thread(() -> {
            Map<String, String> variables = new HashMap<>();
            
            for (int i = 0; i < stepsTableModel.getRowCount(); i++) {
                String label = (String) stepsTableModel.getValueAt(i, 0);
                String method = (String) stepsTableModel.getValueAt(i, 1);
                String endpoint = (String) stepsTableModel.getValueAt(i, 2);
                String body = (String) stepsTableModel.getValueAt(i, 3);
                
                // Replace variables in endpoint and body
                for (Map.Entry<String, String> var : variables.entrySet()) {
                    endpoint = endpoint.replace("{" + var.getKey() + "}", var.getValue());
                    body = body.replace("{" + var.getKey() + "}", var.getValue());
                }
                
                log("Step " + (i+1) + " [" + label + "]: " + method + " " + endpoint);
                
                try {
                    String response = mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + endpoint, method, body.isEmpty() ? null : body);
                    log("Response: " + (response.length() > 200 ? response.substring(0, 200) + "..." : response));
                    
                    // Basic variable extraction (key or id)
                    if (response.startsWith("{")) {
                        JSONObject json = new JSONObject(response);
                        if (json.has("key")) variables.put("last_key", json.getString("key"));
                        if (json.has("id")) variables.put("last_id", json.getString("id"));
                    }
                    
                } catch (Exception ex) {
                    log("ERROR: " + ex.getMessage());
                    int choice = JOptionPane.showConfirmDialog(this, "Step failed: " + ex.getMessage() + "\nContinue with next step?", "Error", JOptionPane.YES_NO_OPTION);
                    if (choice != JOptionPane.YES_OPTION) break;
                }
                log("------------------------------------------");
            }
            log("Workflow Execution Complete.");
        }).start();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
