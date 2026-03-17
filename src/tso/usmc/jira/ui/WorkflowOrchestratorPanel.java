package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.ui.workflow.StepEditorPanel;
import tso.usmc.jira.workflow.*;
import tso.usmc.jira.service.MetadataCacheService;
import tso.usmc.jira.service.JiraIssueService;
import tso.usmc.jira.ui.SwingUtils;
import tso.usmc.jira.util.JiraUtils;
import tso.usmc.jira.util.ExecutionService;
import org.json.JSONObject;
import org.json.JSONArray;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;

public class WorkflowOrchestratorPanel extends JPanel implements WorkflowProgressListener {

    private final JiraApiClientGui mainFrame;
    private final WorkflowManager workflowManager;
    private final Map<String, String> cachedFieldOptions = new HashMap<>();
    private final List<String> cachedLinkTypes = new ArrayList<>();
    
    // Designer Components
    private final DefaultListModel<String> recipeListModel = new DefaultListModel<>();
    private final JList<String> recipeList = new JList<>(recipeListModel);
    private final JTextField recipeNameField = new JTextField(20);
    private final JTextField jqlField = new JTextField(30);
    private final JTextField contextIssueField = new JTextField(10); 
    private final JButton fetchMetaBtn = new JButton("Fetch Metadata");
    private final JProgressBar syncProgress = new JProgressBar();
    private final JPanel stepsContainer = new JPanel();
    private final DefaultListModel<String> tokenListModel = new DefaultListModel<>();
    private final JList<String> tokenList = new JList<>(tokenListModel);
    private final JTextField tokenSearchField = new JTextField();
    
    // Runner Components
    private final JComboBox<String> runnerRecipeCombo = new JComboBox<>();
    private final JTextField runnerJqlField = new JTextField();
    private final JButton searchBtn = new JButton("Search Issues");
    private final DefaultTableModel runnerTableModel = new DefaultTableModel();
    private final JTable runnerTable = new JTable(runnerTableModel);
    private final JPanel runnerInputsPanel = new JPanel();
    private final Map<String, JComponent> promptFields = new HashMap<>();
    private final JTextArea runnerLog = new JTextArea();
    private final JButton runBtn = new JButton("Run Workflow on Selected");
    private final JButton exportReportBtn = new JButton("Export Report (CSV)");
    private final JCheckBox verboseLogCheck = new JCheckBox("Verbose API Logs");
    private final JCheckBox dryRunCheck = new JCheckBox("Dry Run (Validate only)");
    private final JLabel statusLabel = new JLabel("Ready.");

    // Results data
    private final List<JSONObject> currentSearchIssues = new ArrayList<>();
    private List<WorkflowEngine.ExecutionResult> lastResults = new ArrayList<>();

    private final Map<String, JSONObject> cachedFullMeta = new HashMap<>();
    private final List<String> allTokens = new ArrayList<>();

    public WorkflowOrchestratorPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        this.workflowManager = new WorkflowManager();
        try {
            this.cachedFullMeta.putAll(mainFrame.getMetadataService().getDiskCache());
        } catch (Exception e) {
            System.err.println("Could not load initial metadata: " + e.getMessage());
        }
        
        setLayout(new BorderLayout());

        SwingUtils.setupExpandedView(recipeNameField);
        SwingUtils.setupExpandedView(jqlField);
        SwingUtils.setupExpandedView(contextIssueField);
        SwingUtils.setupExpandedView(runnerJqlField);

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("Designer", createDesignerPanel());
        mainTabs.addTab("Runner", createRunnerPanel());
        
        add(mainTabs, BorderLayout.CENTER);
        
        refreshRecipeList();
        updateTokensFromCache();

        runnerRecipeCombo.addActionListener(e -> updateRunnerInputs());
    }

    // --- WorkflowProgressListener Implementation ---

    @Override
    public void onLog(String message) {
        SwingUtilities.invokeLater(() -> {
            runnerLog.append(message + "\n");
            runnerLog.setCaretPosition(runnerLog.getDocument().getLength());
        });
    }

    @Override
    public void onError(String message, Exception ex) {
        onLog("ERROR: " + message + (ex != null ? " (" + ex.getMessage() + ")" : ""));
        if (ex != null) ex.printStackTrace();
    }

    @Override
    public void onComplete() {
        SwingUtilities.invokeLater(() -> {
            runBtn.setEnabled(true);
            exportReportBtn.setEnabled(true);
            statusLabel.setText("Workflow Execution Complete.");
        });
    }

    private JPanel createDesignerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Left: List
        JPanel left = new JPanel(new BorderLayout());
        left.setBorder(BorderFactory.createTitledBorder("Recipes"));
        left.setPreferredSize(new Dimension(200, 0));
        left.add(new JScrollPane(recipeList), BorderLayout.CENTER);
        
        JPanel leftButtons = new JPanel(new FlowLayout());
        JButton newBtn = new JButton("New");
        JButton delBtn = new JButton("Delete");
        leftButtons.add(newBtn);
        leftButtons.add(delBtn);
        left.add(leftButtons, BorderLayout.SOUTH);
        
        panel.add(left, BorderLayout.WEST);
        
        // Right: Tokens
        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createTitledBorder("Token Browser"));
        right.setMinimumSize(new Dimension(0, 0));
        
        JPanel tokenSearchPanel = new JPanel(new BorderLayout());
        tokenSearchPanel.add(new JLabel(" Search: "), BorderLayout.WEST);
        tokenSearchPanel.add(tokenSearchField, BorderLayout.CENTER);
        right.add(tokenSearchPanel, BorderLayout.NORTH);
        
        tokenList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tokenList.setFont(new Font("Monospaced", Font.PLAIN, 11));
        tokenList.setToolTipText("Double-click to copy token");
        right.add(new JScrollPane(tokenList), BorderLayout.CENTER);
        
        // Center: Editor
        JPanel center = new JPanel(new BorderLayout());
        center.setMinimumSize(new Dimension(300, 0));
        
        // Editor Header
        JPanel header = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx=0; gbc.gridy=0; header.add(new JLabel("Recipe Name:"), gbc);
        gbc.gridx=1; gbc.weightx=1.0; header.add(recipeNameField, gbc);
        
        gbc.gridx=0; gbc.gridy=1; gbc.weightx=0; header.add(new JLabel("JQL Query:"), gbc);
        gbc.gridx=1; gbc.weightx=1.0; header.add(jqlField, gbc);
        
        gbc.gridx=0; gbc.gridy=2; gbc.weightx=0; header.add(new JLabel("Project Filter / Context Issue:"), gbc);
        gbc.gridx=1; gbc.weightx=1.0; header.add(contextIssueField, gbc);
        gbc.gridx=2; gbc.weightx=0; header.add(fetchMetaBtn, gbc);
        contextIssueField.setToolTipText("Deep Sync: PROJ1, PROJ2 (Rebuild Filtered) or +PROJ1 (Incremental Add). Transition Meta: Issue Key.");

        syncProgress.setIndeterminate(true);
        syncProgress.setVisible(false);
        gbc.gridx=0; gbc.gridy=3; gbc.gridwidth=3; gbc.weightx=1.0; header.add(syncProgress, gbc);

        JButton saveBtn = new JButton("Save Recipe");
        gbc.gridy=0; gbc.gridx=2; gbc.weightx=0; header.add(saveBtn, gbc);

        JButton toggleTokensBtn = new JButton("Toggle Tokens");
        gbc.gridy=1; gbc.gridx=2; header.add(toggleTokensBtn, gbc);
        
        center.add(header, BorderLayout.NORTH);
        
        // Editor Steps
        stepsContainer.setLayout(new BoxLayout(stepsContainer, BoxLayout.Y_AXIS));
        JPanel stepsWrapper = new JPanel(new BorderLayout());
        stepsWrapper.add(stepsContainer, BorderLayout.NORTH);
        JScrollPane stepsScroll = new JScrollPane(stepsWrapper);
        stepsScroll.getVerticalScrollBar().setUnitIncrement(16);
        center.add(stepsScroll, BorderLayout.CENTER);
        
        // Editor Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addTransBtn = new JButton("Add Transition");
        JButton addUpdateBtn = new JButton("Add Update");
        JButton addCreateBtn = new JButton("Add Create");
        JButton addLinkBtn = new JButton("Add Link");
        JButton addAssetBtn = new JButton("Add Asset (Links/Att/Sub)");
        JButton addWorklogBtn = new JButton("Add Worklog");
        footer.add(addTransBtn);
        footer.add(addUpdateBtn);
        footer.add(addCreateBtn);
        footer.add(addLinkBtn);
        footer.add(addAssetBtn);
        footer.add(addWorklogBtn);
        center.add(footer, BorderLayout.SOUTH);

        // Split Editor and Tokens
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, right);
        split.setResizeWeight(1.0);
        split.setOneTouchExpandable(true);
        split.setDividerLocation(1.0);
        
        panel.add(split, BorderLayout.CENTER);
        
        toggleTokensBtn.addActionListener(e -> {
            int location = split.getDividerLocation();
            int max = split.getMaximumDividerLocation();
            if (location >= max - 50) {
                split.setDividerLocation(max - 250);
            } else {
                split.setDividerLocation(max);
            }
        });
        
        // Listeners
        recipeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadRecipe(recipeList.getSelectedValue());
        });
        
        newBtn.addActionListener(e -> clearEditor());
        delBtn.addActionListener(e -> deleteRecipe());
        saveBtn.addActionListener(e -> saveRecipe());
        fetchMetaBtn.addActionListener(e -> fetchLiveMetadata());
        
        addTransBtn.addActionListener(e -> addStep(new TransitionStep()));
        addUpdateBtn.addActionListener(e -> addStep(new UpdateStep()));
        addCreateBtn.addActionListener(e -> addStep(new CreateStep()));
        addLinkBtn.addActionListener(e -> addStep(new LinkStep()));
        addAssetBtn.addActionListener(e -> addStep(new AssetStep()));
        addWorklogBtn.addActionListener(e -> addStep(new WorklogStep()));

        tokenSearchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterTokens(); }
            public void removeUpdate(DocumentEvent e) { filterTokens(); }
            public void changedUpdate(DocumentEvent e) { filterTokens(); }
        });

        tokenList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = tokenList.getSelectedValue();
                    if (selected != null) {
                        int start = selected.indexOf("{{");
                        int end = selected.lastIndexOf("}}");
                        if (start >= 0 && end > start) {
                            String token = selected.substring(start, end + 2);
                            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(token);
                            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                        }
                    }
                }
            }
        });
        
        return panel;
    }

    public void setRunnerIssueKey(String recipeName, String key) {
        runnerRecipeCombo.setSelectedItem(recipeName);
        runnerJqlField.setText(key);
        updateRunnerInputs();
    }

    public void runWorkflowDirectly(String recipeName, String issueKeys) {
        SwingUtilities.invokeLater(() -> {
            mainFrame.showPanel("Workflow Orchestrator");
            for (Component c : getComponents()) {
                if (c instanceof JTabbedPane) {
                    ((JTabbedPane) c).setSelectedIndex(1);
                    break;
                }
            }
            runnerRecipeCombo.setSelectedItem(recipeName);
            runnerJqlField.setText(issueKeys);
            runnerLog.setText("");
            executeRunnerSearch();
        });

        ExecutionService.submit(() -> {
            try {
                WorkflowRecipe recipe = workflowManager.loadWorkflow(recipeName);
                if (recipe == null) {
                    String val = mainFrame.getJiraConfig().getProperty("workflow." + recipeName);
                    if (val != null) recipe = WorkflowRecipe.fromJson(val);
                }
                if (recipe == null) {
                    onLog("ERROR: Recipe not found: " + recipeName);
                    return;
                }

                String[] keys = issueKeys.split(",");
                List<JSONObject> issues = new ArrayList<>();
                for (String key : keys) {
                    String cleanKey = JiraUtils.cleanIssueKey(key.trim());
                    if (cleanKey.isEmpty()) continue;
                    
                    onLog("Fetching data for " + cleanKey + "...");
                    String searchUrl = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + cleanKey + "?expand=names,renderedFields&fields=*all,attachment,issuelinks";
                    String resp = mainFrame.getService().executeRequest(searchUrl, "GET", null);
                    issues.add(new JSONObject(resp));
                }

                WorkflowEngine engine = new WorkflowEngine(mainFrame.getService(), mainFrame.getIssueService(), mainFrame.getBaseUrl(), this);
                engine.setVerboseLogging(verboseLogCheck.isSelected());
                lastResults = engine.execute(recipe, issues, new HashMap<>());

            } catch (Exception e) {
                onError("Execution Error", e);
            }
        });
    }

    private JPanel createRunnerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; top.add(new JLabel("Select Recipe:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; top.add(runnerRecipeCombo, gbc);
        
        JPanel checkPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        checkPanel.add(verboseLogCheck);
        checkPanel.add(dryRunCheck);
        gbc.gridx = 2; gbc.weightx = 0; top.add(checkPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; top.add(new JLabel("JQL Query / Issue Key:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; top.add(runnerJqlField, gbc);
        gbc.gridx = 2; gbc.weightx = 0; top.add(searchBtn, gbc);

        runnerInputsPanel.setLayout(new GridBagLayout());
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        top.add(runnerInputsPanel, gbc);

        runnerTable.setFillsViewportHeight(true);
        runnerTable.setAutoCreateRowSorter(true);
        runnerTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = runnerTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        String key = (String) runnerTableModel.getValueAt(runnerTable.convertRowIndexToModel(row), 0);
                        tso.usmc.jira.util.JiraUtils.browseIssue(mainFrame.getBaseUrl(), key);
                    }
                }
            }
        });
        runnerTableModel.setColumnIdentifiers(new String[]{"Key", "Summary", "Status", "Assignee"});
        
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setTopComponent(new JScrollPane(runnerTable));
        
        runnerLog.setEditable(false);
        runnerLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        split.setBottomComponent(new JScrollPane(runnerLog));
        split.setDividerLocation(200);

        panel.add(top, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout());
        runBtn.setBackground(new Color(200, 255, 200));
        exportReportBtn.setEnabled(false); // Enable after run
        JButton clearLogBtn = new JButton("Clear Log");
        bottom.add(clearLogBtn);
        bottom.add(exportReportBtn);
        bottom.add(runBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        searchBtn.addActionListener(e -> executeRunnerSearch());
        runBtn.addActionListener(e -> runWorkflowOnSelected());
        exportReportBtn.addActionListener(e -> exportToCsv());
        clearLogBtn.addActionListener(e -> runnerLog.setText(""));

        return panel;
    }

    private void executeRunnerSearch() {
        String finalJql = runnerJqlField.getText().trim();
        if (finalJql.isEmpty()) return;

        if (!finalJql.contains(" ") && !finalJql.contains("=") && !finalJql.contains("(")) {
            if (finalJql.contains(",")) {
                finalJql = "key in (" + finalJql + ")";
            } else {
                finalJql = "key = " + finalJql;
            }
        }

        final String jql = finalJql;
        onLog("Searching: " + jql);
        runnerTableModel.setRowCount(0);
        currentSearchIssues.clear();

        ExecutionService.submit(() -> {
            try {
                String encodedJql = java.net.URLEncoder.encode(jql, "UTF-8");
                String searchUrl = mainFrame.getBaseUrl() + "/rest/api/2/search?jql=" + encodedJql + "&expand=names,renderedFields&fields=*all,attachment,issuelinks";
                String searchResp = mainFrame.getService().executeRequest(searchUrl, "GET", null);
                JSONArray issues = new JSONObject(searchResp).getJSONArray("issues");
                
                SwingUtilities.invokeLater(() -> {
                    for (int i = 0; i < issues.length(); i++) {
                        JSONObject issue = issues.getJSONObject(i);
                        currentSearchIssues.add(issue);
                        JSONObject fields = issue.getJSONObject("fields");
                        String key = issue.getString("key");
                        String summary = fields.optString("summary", "N/A");
                        String status = fields.optJSONObject("status") != null ? fields.getJSONObject("status").getString("name") : "N/A";
                        String assignee = fields.optJSONObject("assignee") != null ? fields.getJSONObject("assignee").getString("displayName") : "Unassigned";
                        
                        runnerTableModel.addRow(new Object[]{key, summary, status, assignee});
                    }
                    onLog("Found " + issues.length() + " issues.");
                    updateRunnerInputs();
                });
            } catch (Exception e) {
                onLog("Search Error: " + e.getMessage());
            }
        });
    }

    private void runWorkflowOnSelected() {
        int[] selectedRows = runnerTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select one or more issues from the table first.");
            return;
        }

        String recipeName = (String) runnerRecipeCombo.getSelectedItem();
        if (recipeName == null) return;

        Map<String, String> promptValues = new HashMap<>();
        for (String label : promptFields.keySet()) {
            JComponent comp = promptFields.get(label);
            String val = "";
            if (comp instanceof JTextField) {
                val = ((JTextField) comp).getText();
            } else if (comp instanceof JComboBox) {
                Object selected = ((JComboBox<?>) comp).getSelectedItem();
                if (selected instanceof ConfigOption) {
                    val = ((ConfigOption) selected).value;
                } else if (selected != null) {
                    val = selected.toString();
                }
            } else if (comp instanceof JScrollPane) {
                Component view = ((JScrollPane) comp).getViewport().getView();
                if (view instanceof JList) {
                    List<String> selectedValues = ((JList<String>) view).getSelectedValuesList();
                    val = String.join(",", selectedValues);
                }
            } else if (comp instanceof PromptChoicePanel) {
                val = ((PromptChoicePanel) comp).getValue();
            } else if (comp instanceof AssetOptionsPromptPanel) {
                val = ((AssetOptionsPromptPanel) comp).getValue();
            }
            promptValues.put(label, val);
        }

        List<JSONObject> issuesToProcess = new ArrayList<>();
        for (int row : selectedRows) {
            int modelRow = runnerTable.convertRowIndexToModel(row);
            issuesToProcess.add(currentSearchIssues.get(modelRow));
        }

        runnerLog.setText("");
        runBtn.setEnabled(false);
        exportReportBtn.setEnabled(false);
        
        ExecutionService.submit(() -> {
            try {
                WorkflowRecipe recipe = workflowManager.loadWorkflow(recipeName);
                if (recipe == null) {
                    String val = mainFrame.getJiraConfig().getProperty("workflow." + recipeName);
                    if (val != null) recipe = WorkflowRecipe.fromJson(val);
                }
                if (recipe == null) {
                    onLog("ERROR: Recipe not found.");
                    onComplete();
                    return;
                }
                
                WorkflowEngine engine = new WorkflowEngine(mainFrame.getService(), mainFrame.getIssueService(), mainFrame.getBaseUrl(), this);
                engine.setVerboseLogging(verboseLogCheck.isSelected());
                engine.setDryRun(dryRunCheck.isSelected());
                lastResults = engine.execute(recipe, issuesToProcess, promptValues);

            } catch (Exception e) {
                onError("FATAL ERROR", e);
                onComplete();
            }
        });
    }

    private void exportToCsv() {
        if (lastResults == null || lastResults.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No results to export.");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("workflow_report_" + System.currentTimeMillis() + ".csv"));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
                pw.println("Issue Key,Status,Duration (ms),Log,Errors");
                for (WorkflowEngine.ExecutionResult res : lastResults) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(res.issueKey).append(",");
                    sb.append(res.status).append(",");
                    sb.append(res.durationMs).append(",");
                    sb.append("\"").append(String.join("; ", res.logEntries).replace("\"", "'")).append("\",");
                    sb.append("\"").append(String.join("; ", res.errors).replace("\"", "'")).append("\"");
                    pw.println(sb.toString());
                }
                JOptionPane.showMessageDialog(this, "Report exported to " + file.getAbsolutePath());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Export Error: " + e.getMessage());
            }
        }
    }

    private void updateRunnerInputs() {
        String recipeName = (String) runnerRecipeCombo.getSelectedItem();
        if (recipeName == null) return;
        
        runnerInputsPanel.removeAll();
        promptFields.clear();
        
        try {
            WorkflowRecipe recipe = workflowManager.loadWorkflow(recipeName);
            if (recipe == null) {
                String val = mainFrame.getJiraConfig().getProperty("workflow." + recipeName);
                if (val != null) recipe = WorkflowRecipe.fromJson(val);
            }
            
            if (recipe != null) {
                if (runnerJqlField.getText().isEmpty()) runnerJqlField.setText(recipe.getJqlQuery());
                
                JSONObject contextIssue = null;
                int selected = runnerTable.getSelectedRow();
                if (selected >= 0 && selected < currentSearchIssues.size()) {
                    contextIssue = currentSearchIssues.get(runnerTable.convertRowIndexToModel(selected));
                } else if (!currentSearchIssues.isEmpty()) {
                    contextIssue = currentSearchIssues.get(0);
                }

                Set<String> labels = new HashSet<>();
                for (WorkflowStep step : recipe.getSteps()) {
                    if (step instanceof CreateStep) {
                        CreateStep cs = (CreateStep) step;
                        addDynamicPrompt(labels, "Project (" + step.getLabel() + ")", cs.getProjectKey(), "project", contextIssue);
                        addDynamicPrompt(labels, "Issue Type (" + step.getLabel() + ")", cs.getIssueType(), "issuetype", contextIssue);
                    }
                    
                    if (step instanceof WorklogStep) {
                        WorklogStep ws = (WorklogStep) step;
                        addDynamicPrompt(labels, "Time Spent (" + step.getLabel() + ")", ws.getTimeSpent(), null, contextIssue);
                        addDynamicPrompt(labels, "Comment (" + step.getLabel() + ")", ws.getComment(), null, contextIssue);
                        addDynamicPrompt(labels, "Started (" + step.getLabel() + ")", ws.getStarted(), null, contextIssue);
                    }
                    
                    if (step instanceof AssetStep) {
                        AssetStep as = (AssetStep) step;
                        if (as.isPromptOptions()) {
                            String label = "Asset Options (" + step.getLabel() + ")";
                            AssetOptionsPromptPanel panel = new AssetOptionsPromptPanel(as.isCopyAttachments(), as.isCopyLinks(), as.isCopySubTasks());
                            addInputRow(label, panel, labels);
                            promptFields.put(label.replaceAll("\\[.*?\\]", "").trim(), panel);
                        }
                    }
                    
                    for (FieldAction fa : step.getFieldActions().values()) {
                        if (fa.getMode() == FieldAction.MappingMode.PROMPT) {
                            addDynamicPrompt(labels, fa.getPromptLabel(), fa.getValue() != null ? fa.getValue().toString() : null, fa.getFieldId(), contextIssue);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        runnerInputsPanel.revalidate();
        runnerInputsPanel.repaint();
    }

    private void addInputRow(String label, JComponent input, Set<String> labels) {
        String cleanLabel = label.replaceAll("\\[.*?\\]", "").trim();
        if (labels.contains(cleanLabel)) return;
        labels.add(cleanLabel);

        GridBagConstraints gbc = new GridBagConstraints();
        int rowCount = runnerInputsPanel.getComponentCount() / 2;
        
        gbc.gridy = rowCount;
        gbc.insets = new Insets(2, 5, 2, 5);
        gbc.anchor = GridBagConstraints.EAST;
        runnerInputsPanel.add(new JLabel(cleanLabel + ":"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        runnerInputsPanel.add(input, gbc);
    }

    private void addDynamicPrompt(Set<String> labels, String label, String value, String fieldId, JSONObject contextIssue) {
        if (label == null || label.trim().isEmpty()) return;
        String cleanLabel = label.replaceAll("\\[.*?\\]", "").trim();
        if (labels.contains(cleanLabel)) return;

        JComponent input = createPromptInput(label, value, fieldId, contextIssue);
        
        addInputRow(label, input, labels);
        promptFields.put(cleanLabel, input);
    }

    private JComponent createPromptInput(String label, String staticOptions, String fieldId, JSONObject contextIssue) {
        // Automatic check for allowedValues based on fieldId
        if (fieldId != null && cachedFullMeta.containsKey(fieldId)) {
            JSONObject meta = cachedFullMeta.get(fieldId);
            if (meta.has("allowedValues")) {
                JSONArray allowed = meta.getJSONArray("allowedValues");
                Vector<String> options = new Vector<>();
                for (int i = 0; i < allowed.length(); i++) {
                    JSONObject av = allowed.getJSONObject(i);
                    options.add(av.optString("name", av.optString("value", "")));
                }

                boolean isArray = meta.has("schema") && "array".equals(meta.getJSONObject("schema").optString("type"));

                if (isArray) {
                    JList<String> list = new JList<>(options);
                    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                    list.setVisibleRowCount(Math.min(options.size(), 4));
                    return new JScrollPane(list);
                } else {
                    return new JComboBox<>(options);
                }
            }
        }
        
        // Fallback to legacy tag-based system
        String tagSource = null;
        if (staticOptions != null && (staticOptions.contains("[config:") || staticOptions.contains("[choice:") || staticOptions.contains("[allowed:"))) tagSource = staticOptions;
        else if (label != null && (label.contains("[config:") || label.contains("[choice:") || label.contains("[allowed:"))) tagSource = label;

        if (tagSource != null) {
            try {
                if (tagSource.contains("[allowed:")) {
                    int start = tagSource.indexOf("[allowed:") + 9;
                    int end = tagSource.indexOf("]", start);
                    if (end > start) {
                        String taggedFieldId = tagSource.substring(start, end).trim();
                        if (cachedFullMeta.containsKey(taggedFieldId)) {
                            JSONObject meta = cachedFullMeta.get(taggedFieldId);
                            if (meta.has("allowedValues")) {
                                JSONArray allowed = meta.getJSONArray("allowedValues");
                                Vector<String> options = new Vector<>();
                                for (int i = 0; i < allowed.length(); i++) {
                                    JSONObject av = allowed.getJSONObject(i);
                                    options.add(av.optString("name", av.optString("value", "")));
                                }

                                boolean isArray = meta.has("schema") && "array".equals(meta.getJSONObject("schema").optString("type"));

                                if (isArray) {
                                    JList<String> list = new JList<>(options);
                                    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                                    list.setVisibleRowCount(Math.min(options.size(), 4));
                                    return new JScrollPane(list);
                                } else {
                                    return new JComboBox<>(options);
                                }
                            }
                        }
                    }
                }

                if (tagSource.contains("[choice:")) {
                    int start = tagSource.indexOf("[choice:") + 8;
                    int end = tagSource.lastIndexOf("]");
                    if (end > start) {
                        String tokenExpr = tagSource.substring(start, end);
                        String resolved = tokenExpr;
                        if (contextIssue != null) resolved = TokenEngine.replaceTokens(tokenExpr, contextIssue);
                        return new PromptChoicePanel(tokenExpr, resolved);
                    }
                }

                if (tagSource.contains("[config:")) {
                    int start = tagSource.indexOf("[config:") + 8;
                    int end = tagSource.lastIndexOf("]");
                    if (end > start) {
                        String tag = tagSource.substring(start, end);
                        String[] parts = tag.split(":");
                        String key = parts[0];
                        
                        if (key.equals("teams")) {
                            String subKey = parts.length > 1 ? parts[1] : "lead";
                            Vector<ConfigOption> options = new Vector<>();
                            String[] teamKeys = mainFrame.getJiraConfig().getWorkflowTeamKeys();
                            for (String tKey : teamKeys) {
                                String name = mainFrame.getJiraConfig().getTeamProperty(tKey, "name");
                                String val = mainFrame.getJiraConfig().getTeamProperty(tKey, subKey);
                                if (name != null && val != null) options.add(new ConfigOption(name, val, tKey));
                            }
                            return new JComboBox<>(options);
                        } else if (key.equals("fy_summary")) {
                            Vector<String> options = new Vector<>();
                            options.add(mainFrame.getJiraConfig().getWorkflowFySummaryIssue());
                            return new JComboBox<>(options);
                        } else {
                            String val = mainFrame.getJiraConfig().getProperty(key);
                            if (val != null) {
                                if (val.contains(",")) {
                                    String[] opts = smartSplit(val);
                                    if (contextIssue != null) {
                                        for (int i = 0; i < opts.length; i++) {
                                            opts[i] = TokenEngine.replaceTokens(opts[i], contextIssue);
                                        }
                                    }
                                    return new JComboBox<>(opts);
                                }
                                String resolved = val;
                                if (contextIssue != null) resolved = TokenEngine.replaceTokens(val, contextIssue);
                                return new JTextField(resolved);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (staticOptions != null && !staticOptions.trim().isEmpty() && staticOptions.contains(",")) {
            String[] opts = smartSplit(staticOptions);
            if (contextIssue != null) {
                for (int i = 0; i < opts.length; i++) {
                    opts[i] = TokenEngine.replaceTokens(opts[i], contextIssue);
                }
            }
            return new JComboBox<>(opts);
        }
        
        String resolvedValue = staticOptions;
        if (contextIssue != null && staticOptions != null && staticOptions.contains("{{")) {
            resolvedValue = TokenEngine.replaceTokens(staticOptions, contextIssue);
        }
        return new JTextField(resolvedValue != null ? resolvedValue : "");
    }

    private String[] smartSplit(String input) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int braceDepth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '{' && i + 1 < input.length() && input.charAt(i + 1) == '{') {
                braceDepth++;
                current.append("{{");
                i++;
            } else if (c == '}' && i + 1 < input.length() && input.charAt(i + 1) == '}') {
                braceDepth--;
                current.append("}}");
                i++;
            } else if (c == ',' && braceDepth == 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    private static class ConfigOption {
        String label, value, teamKey;
        ConfigOption(String l, String v, String tk) { this.label = l; this.value = v; this.teamKey = tk; }
        @Override public String toString() { return label; }
    }

    private static class PromptChoicePanel extends JPanel {
        private final JRadioButton tokenRadio;
        private final JRadioButton manualRadio;
        private final JTextField manualField;
        private final String tokenValue;

        PromptChoicePanel(String tokenName, String resolvedValue) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
            this.tokenValue = resolvedValue;
            
            String displayText = resolvedValue;
            if (resolvedValue.equals(tokenName)) {
                displayText = tokenName;
            } else {
                displayText = resolvedValue + " (" + tokenName + ")";
            }
            
            tokenRadio = new JRadioButton(displayText, true);
            manualRadio = new JRadioButton("Manual:", false);
            manualField = new JTextField(15);
            manualField.setEnabled(false);

            ButtonGroup group = new ButtonGroup();
            group.add(tokenRadio);
            group.add(manualRadio);

            add(tokenRadio);
            add(manualRadio);
            add(manualField);

            manualRadio.addActionListener(e -> { manualField.setEnabled(true); manualField.requestFocus(); });
            tokenRadio.addActionListener(e -> manualField.setEnabled(false));
            
            manualField.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    manualRadio.setSelected(true);
                    manualField.setEnabled(true);
                }
            });
        }

        public String getValue() {
            return tokenRadio.isSelected() ? tokenValue : manualField.getText();
        }
    }

    private static class AssetOptionsPromptPanel extends JPanel {
        private final JCheckBox att, links, sub;
        AssetOptionsPromptPanel(boolean a, boolean l, boolean s) {
            setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
            att = new JCheckBox("Attachments", a);
            links = new JCheckBox("Links", l);
            sub = new JCheckBox("Sub-tasks", s);
            add(att); add(links); add(sub);
        }
        public String getValue() {
            return att.isSelected() + "," + links.isSelected() + "," + sub.isSelected();
        }
    }

    private void filterTokens() {
        String filter = tokenSearchField.getText().toLowerCase();
        tokenListModel.clear();
        for (String t : allTokens) {
            if (t.toLowerCase().contains(filter)) tokenListModel.addElement(t);
        }
    }

    private void fetchLiveMetadata() {
        String filterText = contextIssueField.getText().trim();
        boolean isIncremental = filterText.startsWith("+");
        boolean isFiltered = !filterText.isEmpty();
        
        final List<String> targetProjects = new ArrayList<>();
        if (isFiltered) {
            String cleanFilter = isIncremental ? filterText.substring(1) : filterText;
            String[] parts = cleanFilter.split("\\s*,\\s*");
            for (String p : parts) {
                String pKey = p.trim();
                if (pKey.startsWith("+")) pKey = pKey.substring(1);
                if (!pKey.isEmpty()) targetProjects.add(pKey);
            }
        }

        String msg;
        if (!isFiltered) {
            msg = "Deep Sync will CLEAN and REBUILD the metadata cache for ALL projects.";
        } else if (isIncremental) {
            msg = "Deep Sync will ADD/UPDATE metadata for these projects: " + targetProjects + "\n(Existing cache will be preserved)";
        } else {
            msg = "Deep Sync will CLEAN and REBUILD metadata for ONLY these projects: " + targetProjects;
        }
            
        int choice = JOptionPane.showConfirmDialog(this, 
            msg + "\n\nContinue?", "Rebuild Global Metadata Cache", JOptionPane.YES_NO_OPTION);
        
        if (choice != JOptionPane.YES_OPTION) return;

        fetchMetaBtn.setEnabled(false);
        fetchMetaBtn.setText("Syncing...");
        syncProgress.setVisible(true);

        ExecutionService.submit(() -> {
            try {
                MetadataCacheService helper = mainFrame.getMetadataService();
                
                if (!isIncremental) {
                    helper.clearCache(); // FORCE fresh API calls
                    cachedFullMeta.clear(); 
                    cachedFieldOptions.clear();
                    onLog("--- Starting Fresh " + (isFiltered ? "Filtered" : "Global") + " Sync ---");
                } else {
                    onLog("--- Starting Incremental Sync for: " + targetProjects + " ---");
                }

                List<String> projects;
                if (isFiltered) {
                    projects = targetProjects;
                } else {
                    projects = helper.getProjectKeys();
                    onLog("Found " + projects.size() + " total projects.");
                }

                for (String pKey : projects) {
                    if (pKey.isEmpty()) continue;
                    try {
                        onLog("Syncing Project: " + pKey);
                        List<JSONObject> types = helper.getIssueTypesForProject(pKey);
                        onLog("  > Found " + types.size() + " issue types in " + pKey);
                        
                        for (JSONObject type : types) {
                            String typeName = type.getString("name");
                            onLog("    > Fetching metadata for type: " + typeName);
                            Map<String, JSONObject> typeMeta = helper.getCreateMetadata(pKey, typeName);
                            cachedFullMeta.putAll(typeMeta);
                        }
                    } catch (Exception e) {
                        onLog("  ! Project Sync Error (" + pKey + "): " + e.getMessage());
                    }
                }

                try {
                    onLog("Syncing Global Link Types...");
                    List<JSONObject> links = helper.getIssueLinkTypes();
                    for (JSONObject lt : links) {
                        cachedFullMeta.put("linktype:" + lt.getString("name"), lt);
                    }
                } catch (Exception e) { onLog("  ! Link Type Sync Error: " + e.getMessage()); }

                try {
                    onLog("Syncing Global Field Definitions...");
                    List<JSONObject> fields = helper.getAllFields();
                    for (JSONObject f : fields) {
                        String fId = f.getString("id");
                        if (!cachedFullMeta.containsKey(fId)) {
                            cachedFullMeta.put(fId, f);
                        }
                    }
                } catch (Exception e) { onLog("  ! Global Field Sync Error: " + e.getMessage()); }

                if (isIncremental) {
                    mainFrame.getMetadataService().updateDiskCache(cachedFullMeta);
                } else {
                    mainFrame.getMetadataService().writeDiskCache(cachedFullMeta);
                }
                
                SwingUtilities.invokeLater(() -> {
                    updateTokensFromCache();
                    for (Component c : stepsContainer.getComponents()) {
                        if (c instanceof StepEditorPanel) {
                            StepEditorPanel sep = (StepEditorPanel) c;
                            sep.refreshMetadata(cachedFieldOptions, cachedFullMeta);
                            sep.updateLinkTypes(cachedLinkTypes);
                        }
                    }
                    onLog("--- Deep Sync Complete ---");
                    fetchMetaBtn.setEnabled(true);
                    fetchMetaBtn.setText("Fetch Metadata");
                    syncProgress.setVisible(false);
                    JOptionPane.showMessageDialog(this, "Metadata Sync Complete!\nTotal Fields: " + cachedFullMeta.size());
                });
            } catch (Exception ex) {
                onLog("CRITICAL METADATA ERROR: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    fetchMetaBtn.setEnabled(true);
                    fetchMetaBtn.setText("Fetch Metadata");
                    syncProgress.setVisible(false);
                    JOptionPane.showMessageDialog(this, "Metadata error: " + ex.getMessage());
                });
            }
        });
    }

    private void addStep(WorkflowStep step) {
        step.setLabel("New " + step.getType() + " Step");
        addStepUI(step);
    }

    private void addStepUI(WorkflowStep step) {
        StepEditorPanel panel = new StepEditorPanel(step, cachedFieldOptions, cachedFullMeta, () -> {
            stepsContainer.remove(getStepPanel(step));
            stepsContainer.revalidate();
            stepsContainer.repaint();
        }, new StepEditorPanel.StepActionListener() {
            @Override
            public void onMoveUp(StepEditorPanel p) {
                int idx = getComponentIndex(p);
                if (idx > 0) {
                    stepsContainer.remove(p);
                    stepsContainer.add(p, idx - 1);
                    stepsContainer.revalidate();
                    stepsContainer.repaint();
                }
            }

            @Override
            public void onMoveDown(StepEditorPanel p) {
                int idx = getComponentIndex(p);
                if (idx >= 0 && idx < stepsContainer.getComponentCount() - 1) {
                    stepsContainer.remove(p);
                    stepsContainer.add(p, idx + 1);
                    stepsContainer.revalidate();
                    stepsContainer.repaint();
                }
            }
            
            private int getComponentIndex(Component c) {
                Component[] comps = stepsContainer.getComponents();
                for (int i = 0; i < comps.length; i++) {
                    if (comps[i] == c) return i;
                }
                return -1;
            }
        }, new StepEditorPanel.StepMetadataListener() {
            @Override
            public void onFetchTransitionFields(TransitionStep step) {
                fetchTransitionMetadata(step);
            }

            @Override
            public void onFetchCreateFields(CreateStep step) {
                fetchCreateMetadata(step);
            }
        });
        panel.updateLinkTypes(cachedLinkTypes);
        stepsContainer.add(panel);
        stepsContainer.revalidate();
        stepsContainer.repaint();
    }

    private void fetchCreateMetadata(CreateStep step) {
        String pKey = step.getProjectKey();
        String iType = step.getIssueType();
        if (pKey == null || pKey.isEmpty() || iType == null || iType.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please provide both Project Key and Issue Type.");
            return;
        }

        String cacheKey = "createmeta:" + pKey + ":" + iType;
        if (cachedFullMeta.containsKey(cacheKey)) {
            JSONObject cached = cachedFullMeta.get(cacheKey);
            if (cached.has("values")) {
                JSONArray values = cached.getJSONArray("values");
                Map<String, JSONObject> meta = new HashMap<>();
                for (int i = 0; i < values.length(); i++) {
                    JSONObject f = values.getJSONObject(i);
                    meta.put(f.getString("fieldId"), f);
                }
                applyCreateMetadata(step, meta);
                return;
            }
        }

        ExecutionService.submit(() -> {
            try {
                MetadataCacheService helper = mainFrame.getMetadataService();
                Map<String, JSONObject> meta = helper.getCreateMetadata(pKey, iType);
                if (meta.isEmpty()) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "No metadata found for " + pKey + " / " + iType));
                    return;
                }

                for (String fId : meta.keySet()) {
                    cachedFullMeta.put(fId, meta.get(fId));
                }
                mainFrame.getMetadataService().updateDiskCache(meta);

                SwingUtilities.invokeLater(() -> applyCreateMetadata(step, meta));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Create Meta Error: " + ex.getMessage()));
            }
        });
    }

    private void applyCreateMetadata(CreateStep step, Map<String, JSONObject> meta) {
        updateTokensFromCache();
        Component cp = getStepPanel(step);
        if (cp instanceof StepEditorPanel) {
            StepEditorPanel sep = (StepEditorPanel) cp;
            sep.refreshMetadata(cachedFieldOptions, cachedFullMeta);
            
            int addedCount = 0;
            for (String fId : meta.keySet()) {
                JSONObject fMeta = meta.get(fId);
                if (fMeta.optBoolean("required", false)) {
                    if (fId.equals("project") || fId.equals("issuetype")) continue;
                    if (!step.getFieldActions().containsKey(fId)) {
                        sep.addField(new FieldAction(fId, FieldAction.MappingMode.SET, "", ""));
                        addedCount++;
                    }
                }
            }
            JOptionPane.showMessageDialog(this, "Fetched " + meta.size() + " fields. Added " + addedCount + " required fields.");
        }
    }

    private void fetchTransitionMetadata(TransitionStep step) {
        String filterText = contextIssueField.getText().trim();
        String targetStatus = step.getTargetStatus();
        
        if (filterText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please provide a Context Issue Key (for live API) or Project Key (for cache) in the Filter field.");
            return;
        }

        if (filterText.contains("-")) {
            fetchLiveTransitionMetadata(step, filterText);
        } else {
            String[] projects = filterText.split("\\s*,\\s*");
            for (String p : projects) {
                for (String cKey : cachedFullMeta.keySet()) {
                    if (cKey.startsWith("trans:" + p + ":") && cKey.endsWith(":" + targetStatus)) {
                        JSONObject transMeta = cachedFullMeta.get(cKey);
                        if (transMeta.has("fields")) {
                            JSONObject fieldsJson = transMeta.getJSONObject("fields");
                            Map<String, JSONObject> meta = new HashMap<>();
                            for (String fId : fieldsJson.keySet()) {
                                meta.put(fId, fieldsJson.getJSONObject(fId));
                            }
                            applyTransitionMetadata(step, meta);
                            return;
                        }
                    }
                }
            }
            JOptionPane.showMessageDialog(this, "No cached transition metadata found for '" + targetStatus + "' in projects: " + filterText);
        }
    }

    private void fetchLiveTransitionMetadata(TransitionStep step, String issueKey) {
        ExecutionService.submit(() -> {
            try {
                MetadataCacheService helper = mainFrame.getMetadataService();
                List<JSONObject> trans = helper.getTransitions(issueKey);
                JSONObject match = null;
                for (JSONObject t : trans) {
                    if (t.getString("name").equalsIgnoreCase(step.getTargetStatus())) {
                        match = t; break;
                    }
                }
                
                if (match == null) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Transition '" + step.getTargetStatus() + "' not found on issue " + issueKey));
                    return;
                }
                
                Map<String, JSONObject> meta = new HashMap<>();
                if (match.has("fields")) {
                    JSONObject fieldsJson = match.getJSONObject("fields");
                    for (String fId : fieldsJson.keySet()) {
                        meta.put(fId, fieldsJson.getJSONObject(fId));
                    }
                }

                for (String fId : meta.keySet()) {
                    cachedFullMeta.put(fId, meta.get(fId));
                }
                mainFrame.getMetadataService().updateDiskCache(meta);
                
                SwingUtilities.invokeLater(() -> applyTransitionMetadata(step, meta));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Transition Meta Error: " + ex.getMessage()));
            }
        });
    }

    private void applyTransitionMetadata(TransitionStep step, Map<String, JSONObject> meta) {
        Component cp = getStepPanel(step);
        if (cp instanceof StepEditorPanel) {
            ((StepEditorPanel) cp).refreshMetadata(cachedFieldOptions, cachedFullMeta);
        }
        JOptionPane.showMessageDialog(this, "Fetched " + meta.size() + " fields for transition '" + step.getTargetStatus() + "'");
    }

    private Component getStepPanel(WorkflowStep step) {
        for (Component c : stepsContainer.getComponents()) {
            if (c instanceof StepEditorPanel && ((StepEditorPanel)c).getStep() == step) return c;
        }
        return null;
    }

    private void loadRecipe(String name) {
        if (name == null) return;
        try {
            WorkflowRecipe recipe = workflowManager.loadWorkflow(name);
            if (recipe == null) {
                String key = "workflow." + name;
                String val = mainFrame.getJiraConfig().getProperty(key);
                if (val != null) recipe = WorkflowRecipe.fromJson(val);
            }

            if (recipe != null) {
                recipeNameField.setText(recipe.getRecipeName());
                jqlField.setText(recipe.getJqlQuery());
                stepsContainer.removeAll();
                
                if (recipe.getMetadataSnapshot() != null) {
                    JSONObject snap = recipe.getMetadataSnapshot();
                    for (String key : snap.keySet()) {
                        cachedFullMeta.put(key, snap.getJSONObject(key));
                    }
                    updateTokensFromCache();
                }

                for (WorkflowStep step : recipe.getSteps()) {
                    addStepUI(step);
                }
                stepsContainer.revalidate();
                stepsContainer.repaint();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void refreshRecipeList() {
        Set<String> names = new TreeSet<>(workflowManager.listWorkflows());
        String[] configRecipes = mainFrame.getJiraConfig().getWorkflowRecipeKeys();
        if (configRecipes != null) {
            names.addAll(Arrays.asList(configRecipes));
        }

        recipeListModel.clear();
        runnerRecipeCombo.removeAllItems();
        for (String name : names) {
            recipeListModel.addElement(name);
            runnerRecipeCombo.addItem(name);
        }
        updateRunnerInputs();
    }

    private void saveRecipe() {
        String name = recipeNameField.getText().trim();
        if (name.isEmpty()) return;
        WorkflowRecipe recipe = new WorkflowRecipe();
        recipe.setRecipeName(name);
        recipe.setJqlQuery(jqlField.getText());
        for (Component c : stepsContainer.getComponents()) {
            if (c instanceof StepEditorPanel) {
                ((StepEditorPanel) c).saveToStep();
                recipe.addStep(((StepEditorPanel) c).getStep());
            }
        }

        try {
            workflowManager.saveWorkflow(recipe);
            refreshRecipeList();
            JOptionPane.showMessageDialog(this, "Recipe saved!");
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error saving: " + e.getMessage());
        }
    }

    private void clearEditor() {
        recipeNameField.setText(""); jqlField.setText(""); stepsContainer.removeAll();
        stepsContainer.revalidate(); stepsContainer.repaint(); recipeList.clearSelection();
    }

    private void deleteRecipe() {
        String selected = recipeList.getSelectedValue();
        if (selected == null) return;
        workflowManager.deleteWorkflow(selected);
        refreshRecipeList(); clearEditor();
    }

    private void updateTokensFromCache() {
        cachedFieldOptions.clear(); cachedLinkTypes.clear();
        List<String> tokens = new ArrayList<>();
        tokens.add("Current Issue Key ({{issue.key}})"); tokens.add("Current Summary ({{issue.fields.summary}})");
        tokens.add("Current Parent Key ({{issue.fields.parent.key}})"); tokens.add("Current Timestamp ({{now}})");
        tokens.add("Current Date ({{today}})"); tokens.add("Last Created/Mod Key ({{last.key}})");
        tokens.add("Smart Key Fallback ({{COALESCE(last.key, issue.key)}})"); tokens.add("Last Created/Mod ID ({{last.id}})");
        tokens.add("Selected Team Name ({{team.name}})"); tokens.add("Selected Team Lead ({{team.lead}})");
        tokens.add("Selected Team Component ({{team.component}})"); tokens.add("Selected Team ID ({{team.id}})");
        
        for (String key : cachedFullMeta.keySet()) {
            if (key.startsWith("linktype:")) { cachedLinkTypes.add(key.substring(9)); continue; }
            if (key.startsWith("trans:") || key.startsWith("createmeta:")) continue;
            JSONObject fieldObj = cachedFullMeta.get(key);
            if (fieldObj == null) continue;
            if (fieldObj.has("inward") && fieldObj.has("outward") && fieldObj.has("name")) { cachedLinkTypes.add(fieldObj.getString("name")); continue; }
            String name = fieldObj.optString("name", key);
            cachedFieldOptions.put(name + " (" + key + ")", key);
            tokens.add(name + " ({{issue.fields." + key + "}})");
        }
        Collections.sort(tokens); Collections.sort(cachedLinkTypes);
        allTokens.clear(); allTokens.addAll(tokens); filterTokens();
        cachedFieldOptions.put("teams_selection (Virtual)", "teams_selection");
    }
}
