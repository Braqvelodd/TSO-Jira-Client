package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.ui.workflow.StepEditorPanel;
import tso.usmc.jira.workflow.*;
import tso.usmc.jira.service.MetadataCacheService;
import tso.usmc.jira.util.JiraUtils;
import tso.usmc.jira.util.WorkflowFieldsConfig;
import tso.usmc.jira.util.ExecutionService;
import org.json.JSONObject;
import org.json.JSONArray;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class WorkflowOrchestratorPanel extends JPanel {

    private final JiraApiClientGui mainFrame;
    private final WorkflowManager workflowManager;
    private final WorkflowFieldsConfig fieldsConfig;
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
    private final JCheckBox verboseLogCheck = new JCheckBox("Verbose API Logs");

    // Results data
    private final List<JSONObject> currentSearchIssues = new ArrayList<>();

    // Execution Variables
    private final Map<String, String> executionVars = new HashMap<>();
    private final Map<String, JSONObject> jsonContexts = new HashMap<>();

    private final Map<String, JSONObject> cachedFullMeta = new HashMap<>();
    private final List<String> allTokens = new ArrayList<>();

    public WorkflowOrchestratorPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        this.workflowManager = new WorkflowManager();
        this.fieldsConfig = new WorkflowFieldsConfig();
        this.cachedFullMeta.putAll(fieldsConfig.getFieldMetadata());
        
        setLayout(new BorderLayout());

        JiraUtils.setupExpandedView(recipeNameField);
        JiraUtils.setupExpandedView(jqlField);
        JiraUtils.setupExpandedView(contextIssueField);
        JiraUtils.setupExpandedView(runnerJqlField);

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("Designer", createDesignerPanel());
        mainTabs.addTab("Runner", createRunnerPanel());
        
        add(mainTabs, BorderLayout.CENTER);
        
        refreshRecipeList();
        updateTokensFromCache();

        runnerRecipeCombo.addActionListener(e -> updateRunnerInputs());
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

        tokenSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTokens(); }
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
                    log("ERROR: Recipe not found: " + recipeName);
                    return;
                }

                JSONObject metaSnap = recipe.getMetadataSnapshot();
                String[] keys = issueKeys.split(",");
                
                log("Starting Direct Execution of '" + recipeName + "' on " + keys.length + " issues...");

                for (String key : keys) {
                    String cleanKey = JiraUtils.cleanIssueKey(key.trim());
                    if (cleanKey.isEmpty()) continue;

                    try {
                        log("--- Processing " + cleanKey + " ---");
                        String searchUrl = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + cleanKey + "?expand=names,renderedFields&fields=*all,attachment,issuelinks";
                        String resp = mainFrame.getService().executeRequest(searchUrl, "GET", null);
                        JSONObject issue = new JSONObject(resp);
                        
                        executionVars.clear();
                        jsonContexts.clear();
                        executionVars.put("issue.key", cleanKey);
                        executionVars.put("key", cleanKey);
                        jsonContexts.put("issue", issue);

                        for (WorkflowStep step : recipe.getSteps()) {
                            log("Step: " + step.getLabel());
                            executeStep(step, issue, new java.util.HashMap<>(), metaSnap);
                        }
                    } catch (Exception ex) {
                        log("  > Error processing " + cleanKey + ": " + ex.getMessage());
                    }
                }
                log("Workflow Execution Complete.");
            } catch (Exception e) {
                log("Execution Error: " + e.getMessage());
                e.printStackTrace();
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
        gbc.gridx = 2; gbc.weightx = 0; top.add(verboseLogCheck, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; top.add(new JLabel("JQL Query / Issue Key:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; top.add(runnerJqlField, gbc);
        gbc.gridx = 2; gbc.weightx = 0; top.add(searchBtn, gbc);

        runnerInputsPanel.setLayout(new GridLayout(0, 2, 5, 5));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
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
        JButton clearLogBtn = new JButton("Clear Log");
        bottom.add(clearLogBtn);
        bottom.add(runBtn);
        panel.add(bottom, BorderLayout.SOUTH);

        searchBtn.addActionListener(e -> executeRunnerSearch());
        runBtn.addActionListener(e -> runWorkflowOnSelected());
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
        log("Searching: " + jql);
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
                    log("Found " + issues.length() + " issues.");
                    updateRunnerInputs();
                });
            } catch (Exception e) {
                log("Search Error: " + e.getMessage());
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
        Map<String, Map<String, String>> teamDataByPrompt = new HashMap<>();

        for (String label : promptFields.keySet()) {
            JComponent comp = promptFields.get(label);
            String val = "";
            if (comp instanceof JTextField) {
                val = ((JTextField) comp).getText();
            } else if (comp instanceof JComboBox) {
                Object selected = ((JComboBox<?>) comp).getSelectedItem();
                if (selected instanceof ConfigOption) {
                    ConfigOption co = (ConfigOption) selected;
                    val = co.value;
                    if (co.teamKey != null) {
                        Map<String, String> tMap = new HashMap<>();
                        tMap.put("name", mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "name"));
                        tMap.put("lead", mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "lead"));
                        tMap.put("component", mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "component"));
                        tMap.put("id", mainFrame.getJiraConfig().getTeamProperty(co.teamKey, "id"));
                        teamDataByPrompt.put(label, tMap);
                    }
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

        runnerLog.setText("Starting workflow on " + issuesToProcess.size() + " selected issues...\n");
        ExecutionService.submit(() -> {
            try {
                WorkflowRecipe recipe = workflowManager.loadWorkflow(recipeName);
                if (recipe == null) {
                    String val = mainFrame.getJiraConfig().getProperty("workflow." + recipeName);
                    if (val != null) recipe = WorkflowRecipe.fromJson(val);
                }
                if (recipe == null) return;
                
                JSONObject metaSnap = recipe.getMetadataSnapshot();
                for (JSONObject issue : issuesToProcess) {
                    String key = issue.getString("key");
                    log("--- Processing " + key + " ---");
                    executionVars.clear();
                    jsonContexts.clear();
                    executionVars.put("issue.key", key);
                    executionVars.put("key", key);
                    jsonContexts.put("issue", issue);
                    
                    for(String pLabel : promptValues.keySet()) {
                        String cleanLabel = pLabel.replaceAll("\\[.*?\\]", "").trim();
                        executionVars.put(cleanLabel + ".value", promptValues.get(pLabel));
                        if (teamDataByPrompt.containsKey(pLabel)) {
                            Map<String, String> tMap = teamDataByPrompt.get(pLabel);
                            for(String tProp : tMap.keySet()) {
                                executionVars.put(cleanLabel + "." + tProp, tMap.get(tProp));
                                if (!executionVars.containsKey("team." + tProp) || cleanLabel.toLowerCase().contains("team")) {
                                    executionVars.put("team." + tProp, tMap.get(tProp));
                                }
                            }
                        }
                    }
                    
                    for (WorkflowStep step : recipe.getSteps()) {
                        log("Step: " + step.getLabel());
                        executeStep(step, issue, promptValues, metaSnap);
                    }
                }
                log("Workflow Execution Complete.");
            } catch (Exception e) {
                log("FATAL ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        });
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
                        addDynamicPrompt(labels, "Project (" + step.getLabel() + ")", cs.getProjectKey(), contextIssue);
                        addDynamicPrompt(labels, "Issue Type (" + step.getLabel() + ")", cs.getIssueType(), contextIssue);
                    }
                    
                    if (step instanceof WorklogStep) {
                        WorklogStep ws = (WorklogStep) step;
                        addDynamicPrompt(labels, "Time Spent (" + step.getLabel() + ")", ws.getTimeSpent(), contextIssue);
                        addDynamicPrompt(labels, "Comment (" + step.getLabel() + ")", ws.getComment(), contextIssue);
                        addDynamicPrompt(labels, "Started (" + step.getLabel() + ")", ws.getStarted(), contextIssue);
                    }
                    
                    if (step instanceof AssetStep) {
                        AssetStep as = (AssetStep) step;
                        if (as.isPromptOptions()) {
                            String label = "Asset Options (" + step.getLabel() + ")";
                            if (!labels.contains(label)) {
                                labels.add(label);
                                runnerInputsPanel.add(new JLabel(label + ":"));
                                AssetOptionsPromptPanel panel = new AssetOptionsPromptPanel(as.isCopyAttachments(), as.isCopyLinks(), as.isCopySubTasks());
                                runnerInputsPanel.add(panel);
                                promptFields.put(label, panel);
                            }
                        }
                    }
                    
                    for (FieldAction fa : step.getFieldActions().values()) {
                        if (fa.getMode() == FieldAction.MappingMode.PROMPT) {
                            addDynamicPrompt(labels, fa.getPromptLabel(), fa.getValue() != null ? fa.getValue().toString() : null, contextIssue);
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

    private void addDynamicPrompt(Set<String> labels, String label, String value, JSONObject contextIssue) {
        if (label == null || label.trim().isEmpty()) return;
        String cleanLabel = label.replaceAll("\\[.*?\\]", "").trim();
        String resolvedValue = value;
        if (contextIssue != null && value != null && value.contains("{{")) {
            resolvedValue = TokenEngine.replaceTokens(value, contextIssue);
        }

        boolean isDynamic = (value != null && (value.contains(",") || value.contains("[config:") || value.contains("[choice:"))) || label.contains("[config:") || label.contains("[choice:");
        
        if (isDynamic) {
            if (!labels.contains(cleanLabel)) {
                labels.add(cleanLabel);
                runnerInputsPanel.add(new JLabel(cleanLabel + ":"));
                JComponent input = createPromptInput(label, value, contextIssue);
                runnerInputsPanel.add(input);
                promptFields.put(cleanLabel, input);
            }
        } else {
            if (label.startsWith("Project (") || label.startsWith("Issue Type (") || label.startsWith("Time Spent (") || label.startsWith("Comment (") || label.startsWith("Started (")) return;
            
            if (!labels.contains(cleanLabel)) {
                labels.add(cleanLabel);
                runnerInputsPanel.add(new JLabel(cleanLabel + ":"));
                JComponent input = new JTextField(resolvedValue != null ? resolvedValue : "");
                runnerInputsPanel.add(input);
                promptFields.put(cleanLabel, input);
            }
        }
    }

    private JComponent createPromptInput(String label, String staticOptions, JSONObject contextIssue) {
        String tagSource = null;
        if (staticOptions != null && (staticOptions.contains("[config:") || staticOptions.contains("[choice:") || staticOptions.contains("[allowed:"))) tagSource = staticOptions;
        else if (label != null && (label.contains("[config:") || label.contains("[choice:") || label.contains("[allowed:"))) tagSource = label;

        if (tagSource != null) {
            try {
                if (tagSource.contains("[allowed:")) {
                    int start = tagSource.indexOf("[allowed:") + 9;
                    int end = tagSource.indexOf("]", start);
                    if (end > start) {
                        String fieldId = tagSource.substring(start, end).trim();
                        if (cachedFullMeta.containsKey(fieldId)) {
                            JSONObject meta = cachedFullMeta.get(fieldId);
                            if (meta.has("allowedValues")) {
                                JSONArray allowed = meta.getJSONArray("allowedValues");
                                Vector<String> options = new Vector<>();
                                for (int i = 0; i < allowed.length(); i++) {
                                    JSONObject av = allowed.getJSONObject(i);
                                    options.add(av.optString("name", av.optString("value", "")));
                                }

                                boolean isArray = false;
                                if (meta.has("schema")) isArray = "array".equals(meta.getJSONObject("schema").optString("type"));

                                if (isArray) {
                                    JList<String> list = new JList<>(options);
                                    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                                    list.setVisibleRowCount(4);
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
        
        return new JTextField();
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
            setLayout(new FlowLayout(FlowLayout.LEFT));
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
                    cachedFullMeta.clear(); 
                    cachedFieldOptions.clear();
                    log("--- Starting Fresh " + (isFiltered ? "Filtered" : "Global") + " Sync ---");
                } else {
                    log("--- Starting Incremental Sync for: " + targetProjects + " ---");
                }

                List<String> projects;
                if (isFiltered) {
                    projects = targetProjects;
                } else {
                    projects = helper.getProjectKeys();
                    log("Found " + projects.size() + " total projects.");
                }

                for (String pKey : projects) {
                    if (pKey.isEmpty()) continue;
                    try {
                        log("Syncing Project: " + pKey);
                        // FETCH ALL ISSUE TYPES FOR THIS PROJECT
                        List<JSONObject> types = helper.getIssueTypesForProject(pKey);
                        log("  > Found " + types.size() + " issue types in " + pKey);
                        
                        for (JSONObject type : types) {
                            String typeName = type.getString("name");
                            log("    > Fetching metadata for type: " + typeName);
                            Map<String, JSONObject> typeMeta = helper.getCreateMetadata(pKey, typeName);
                            cachedFullMeta.putAll(typeMeta);
                        }
                    } catch (Exception e) {
                        log("  ! Project Sync Error (" + pKey + "): " + e.getMessage());
                    }
                }

                if (isIncremental) {
                    fieldsConfig.updateMetadata(cachedFullMeta);
                } else {
                    fieldsConfig.replaceMetadata(cachedFullMeta);
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
                    log("--- Deep Sync Complete ---");
                    fetchMetaBtn.setEnabled(true);
                    fetchMetaBtn.setText("Fetch Metadata");
                    syncProgress.setVisible(false);
                    JOptionPane.showMessageDialog(this, "Metadata Sync Complete!\nTotal Fields: " + cachedFullMeta.size());
                });
            } catch (Exception ex) {
                log("CRITICAL METADATA ERROR: " + ex.getMessage());
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
                fieldsConfig.updateMetadata(meta);

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
                fieldsConfig.updateMetadata(meta);
                
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

    private void executeStep(WorkflowStep step, JSONObject issue, Map<String, String> prompts, JSONObject metaSnap) throws Exception {
        String baseUrl = mainFrame.getBaseUrl();

        if (step instanceof CreateStep) {
            CreateStep cs = (CreateStep) step;
            String proj = resolveStepProperty(cs.getProjectKey(), "Project (" + step.getLabel() + ")", prompts);
            String type = resolveStepProperty(cs.getIssueType(), "Issue Type (" + step.getLabel() + ")", prompts);
            
            JSONObject fields = buildFields(step, issue, prompts, metaSnap);
            fields.put("project", new JSONObject().put("key", proj));
            fields.put("issuetype", new JSONObject().put("name", type));

            String payload = new JSONObject().put("fields", fields).toString(4);
            if (verboseLogCheck.isSelected()) {
                log("  > Request URL: POST " + baseUrl + "/rest/api/2/issue");
                log("  > Request Body:\n" + payload);
            }

            String resp = mainFrame.getService().executeRequest(baseUrl + "/rest/api/2/issue", "POST", payload);
            JSONObject respJson = new JSONObject(resp);
            String newKey = respJson.getString("key");
            
            executionVars.put("last_key", newKey);
            executionVars.put("last.key", newKey);
            executionVars.put("last.id", respJson.getString("id"));
            jsonContexts.put("last", respJson);
            
            log("  > Created " + newKey);
        } else if (step instanceof UpdateStep) {
            UpdateStep us = (UpdateStep) step;
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(us.getTargetIssueToken(), issue));
            if (targetKey == null || targetKey.trim().isEmpty()) {
                log("  > SKIP: Target issue key resolved to empty string for step: " + step.getLabel());
                return;
            }
            
            JSONObject fields = buildFields(step, issue, prompts, metaSnap);
            String payload = new JSONObject().put("fields", fields).toString(4);
            String url = baseUrl + "/rest/api/2/issue/" + targetKey;
            
            if (verboseLogCheck.isSelected()) {
                log("  > Request URL: PUT " + url);
                log("  > Request Body:\n" + payload);
            }

            mainFrame.getService().executeRequest(url, "PUT", payload);
            executionVars.put("last_key", targetKey);
            executionVars.put("last.key", targetKey);
            log("  > Updated " + targetKey);
        } else if (step instanceof TransitionStep) {
            TransitionStep ts = (TransitionStep) step;
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(ts.getTargetIssueToken(), issue));
            if (targetKey == null || targetKey.trim().isEmpty()) {
                log("  > SKIP: Target issue key resolved to empty string for step: " + step.getLabel());
                return;
            }

            String transUrl = baseUrl + "/rest/api/2/issue/" + targetKey + "/transitions";
            String transMeta = mainFrame.getService().executeRequest(transUrl, "GET", null);
            String tid = JiraUtils.findTransitionIdByName(transMeta, ts.getTargetStatus());
            if (tid != null) {
                JSONObject body = new JSONObject();
                body.put("transition", new JSONObject().put("id", tid));
                JSONObject fields = buildFields(step, issue, prompts, metaSnap);
                if (fields.length() > 0) body.put("fields", fields);
                
                String payload = body.toString(4);
                if (verboseLogCheck.isSelected()) {
                    log("  > Request URL: POST " + transUrl);
                    log("  > Request Body:\n" + payload);
                }

                mainFrame.getService().executeRequest(transUrl, "POST", payload);
                executionVars.put("last_key", targetKey);
                executionVars.put("last.key", targetKey);
                executionVars.put("last_transition_id", tid);
                executionVars.put("last.transition_id", tid);
                log("  > Transitioned " + targetKey + " to " + ts.getTargetStatus() + (fields.length() > 0 ? " (with fields)" : ""));
            } else log("  > ERROR: Transition '" + ts.getTargetStatus() + "' not found on " + targetKey);
        } else if (step instanceof LinkStep) {
            LinkStep ls = (LinkStep) step;
            for (LinkAction la : ls.getLinkActions()) {
                String inward = JiraUtils.cleanIssueKey(resolveTokens(la.getInwardIssueToken(), issue));
                if (inward == null || inward.trim().isEmpty()) continue;

                if (la.isRemote()) {
                    String resolvedUrl = resolveTokens(la.getUrl(), issue);
                    String resolvedTitle = resolveTokens(la.getTitle(), issue);
                    String resolved=" + resolvedUrl);
                } else {
                    String outward = JiraUtils.cleanIssueKey(resolveTokens(la.getOutwardIssueToken(), issue));
                    if (outward == null || outward.trim().isEmpty()) continue;

                    JSONObject body = new JSONObject();
                    body.put("type", new JSONObject().put("name", la.getLinkType()));
                    body.put("inwardIssue", new JSONObject().put("key", inward));
                    body.put("outwardIssue", new JSONObject().put("key", outward));

                    String payload = body.toString(4);
                    String url = baseUrl + "/rest/api/2/issueLink";
                    if (verboseLogCheck.isSelected()) {
                        log("  > Request URL: POST " + url);
                        log("  > Request Body:\n" + payload);
                    }
                    mainFrame.getService().executeRequest(url, "POST", payload);
                    log("  > Linked " + inward + " to " + outward + " (" + la.getLinkType() + ")");
                }
            }
        } else if (step instanceof AssetStep) {
            AssetStep as = (AssetStep) step;
            String srcKey = JiraUtils.cleanIssueKey(resolveTokens(as.getSourceIssueToken(), issue));
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(as.getTargetIssueToken(), issue));

            if (srcKey == null || srcKey.trim().isEmpty() || targetKey == null || targetKey.trim().isEmpty()) return;

            JSONObject sourceData = issue;
            if (!srcKey.equals(issue.getString("key"))) {
                String url = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + srcKey + "?expand=names,renderedFields&fields=*all,attachment,issuelinks";
                String srcResp = mainFrame.getService().executeRequest(url, "GET", null);
                sourceData = new JSONObject(srcResp);
            }

            boolean doAtt = as.isCopyAttachments(), doLinks = as.isCopyLinks(), doSub = as.isCopySubTasks();
            if (as.isPromptOptions()) {
                String p = prompts.get("Asset Options (" + step.getLabel() + ")");
                if (p != null && p.contains(",")) {
                    String[] parts = p.split(",");
                    if (parts.length >= 3) {
                        doAtt = Boolean.parseBoolean(parts[0]);
                        doLinks = Boolean.parseBoolean(parts[1]);
                        doSub = Boolean.parseBoolean(parts[2]);
                    }
                }
            }

            if (doAtt) copyAttachments(sourceData, targetKey);
            if (doLinks) copyLinks(sourceData, targetKey);
            if (doSub) copySubTasks(sourceData, targetKey, as);
        } else if (step instanceof WorklogStep) {
            WorklogStep ws = (WorklogStep) step;
            String targetKey = JiraUtils.cleanIssueKey(resolveTokens(ws.getTargetIssueToken(), issue));
            if (targetKey == null || targetKey.trim().isEmpty()) return;

            String timeSpent = resolveStepProperty(ws.getTimeSpent(), "Time Spent (" + step.getLabel() + ")", prompts);
            String comment = resolveStepProperty(ws.getComment(), "Comment (" + step.getLabel() + ")", prompts);
            String started = resolveStepProperty(ws.getStarted(), "Started (" + step.getLabel() + ")", prompts);
            started = resolveTokens(started, issue);
            started = JiraUtils.formatJiraDateTime(started);

            JSONObject body = new JSONObject();
            if (timeSpent != null && !timeSpent.trim().isEmpty()) body.put("timeSpent", timeSpent);
            if (comment != null && !comment.trim().isEmpty()) body.put("comment", comment);
            if (started != null && !started.trim().isEmpty()) body.put("started", started);

            String url = baseUrl + "/rest/api/2/issue/" + targetKey + "/worklog";
            mainFrame.getService().executeRequest(url, "POST", body.toString(4));
            log("  > Added Worklog to " + targetKey + " (" + timeSpent + ")");
        }
    }

    private String resolveStepProperty(String value, String promptLabel, Map<String, String> prompts) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("[config:") || value.contains("[choice:")) {
            if (prompts.containsKey(promptLabel)) return prompts.get(promptLabel);
        }
        return value;
    }

    private void copySubTasks(JSONObject sourceIssue, String targetParentKey, AssetStep as) throws Exception {
        String sourceKey = sourceIssue.getString("key");
        String subtaskUrl = mainFrame.getBaseUrl() + "/rest/api/2/issue/" + sourceKey + "/subtask";
        String subtaskResp = mainFrame.getService().executeRequest(subtaskUrl, "GET", null);
        JSONArray subtasks;
        
        if (subtaskResp.trim().startsWith("[")) subtasks = new JSONArray(subtaskResp);
        else {
            JSONObject respObj = new JSONObject(subtaskResp);
            subtasks = respObj.optJSONArray("subtasks");
            if (subtasks == null) return;
        }

        for (int i = 0; i < subtasks.length(); i++) {
            JSONObject subData = subtasks.getJSONObject(i);
            String subKey = subData.getString("key");
            JSONObject subFields = subData.getJSONObject("fields");
            
            JSONObject newSubFields = new JSONObject();
            newSubFields.put("project", new JSONObject().put("key", targetParentKey.split("-")[0]));
            newSubFields.put("parent", new JSONObject().put("key", targetParentKey));
            newSubFields.put("issuetype", subFields.getJSONObject("issuetype"));
            
            String csv = as.getSubTaskFields();
            if (csv != null && !csv.trim().isEmpty()) copySubTaskFields(subFields, newSubFields, csv.split(","));

            try {
                mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue", "POST", new JSONObject().put("fields", newSubFields).toString(4));
                log("    > Created sub-task copy: " + subKey);
            } catch (Exception e) {
                log("    > Error copying sub-task " + subKey + ": " + e.getMessage());
            }
        }
    }

    private void copySubTaskFields(JSONObject src, JSONObject dest, String[] fields) {
        for (String f : fields) {
            String fieldId = f.trim();
            if (src.has(fieldId) && !src.isNull(fieldId)) {
                Object val = src.get(fieldId);
                if (val instanceof JSONObject) {
                    JSONObject obj = (JSONObject) val;
                    if (fieldId.equals("reporter") || fieldId.equals("assignee")) {
                        if (obj.has("name")) dest.put(fieldId, new JSONObject().put("name", obj.getString("name")));
                    } else if (obj.has("id")) dest.put(fieldId, new JSONObject().put("id", obj.getString("id")));
                    else if (obj.has("value")) dest.put(fieldId, new JSONObject().put("value", obj.getString("value")));
                    else dest.put(fieldId, val);
                } else dest.put(fieldId, val);
            }
        }
        for (String key : src.keySet()) {
            if (key.startsWith("customfield_") && !src.isNull(key)) {
                boolean skip = false;
                for(String f : fields) if(f.trim().equals(key)) skip = true;
                if(!skip) dest.put(key, src.get(key));
            }
        }
    }

    private void copyAttachments(JSONObject sourceIssue, String targetKey) throws Exception {
        if (!sourceIssue.getJSONObject("fields").has("attachment")) return;
        JSONArray attachments = sourceIssue.getJSONObject("fields").getJSONArray("attachment");
        for (int i = 0; i < attachments.length(); i++) {
            JSONObject att = attachments.getJSONObject(i);
            String filename = att.getString("filename");
            java.io.File tempFile = mainFrame.getService().downloadAttachmentToTempFile(att.getString("content"), filename);
            try {
                mainFrame.getService().uploadAttachment(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + targetKey + "/attachments", tempFile, filename);
                log("  > Copied attachment: " + filename);
            } finally { if (tempFile != null) tempFile.delete(); }
        }
    }

    private void copyLinks(JSONObject sourceIssue, String targetKey) throws Exception {
        if (!sourceIssue.getJSONObject("fields").has("issuelinks")) return;
        JSONArray links = sourceIssue.getJSONObject("fields").getJSONArray("issuelinks");
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.getJSONObject(i);
            String typeName = link.getJSONObject("type").getString("name");
            String otherKey = link.has("inwardIssue") ? link.getJSONObject("inwardIssue").getString("key") : (link.has("outwardIssue") ? link.getJSONObject("outwardIssue").getString("key") : null);
            if (otherKey == null) continue;

            JSONObject body = new JSONObject().put("type", new JSONObject().put("name", typeName));
            if (link.has("inwardIssue")) { body.put("inwardIssue", new JSONObject().put("key", targetKey)); body.put("outwardIssue", new JSONObject().put("key", otherKey)); }
            else { body.put("inwardIssue", new JSONObject().put("key", otherKey)); body.put("outwardIssue", new JSONObject().put("key", targetKey)); }
            
            try { mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issueLink", "POST", body.toString()); log("  > Copied link: " + typeName + " to " + otherKey); }
            catch (Exception e) { log("  > Warning: Could not copy link to " + otherKey); }
        }
    }

    private JSONObject buildFields(WorkflowStep step, JSONObject issue, Map<String, String> prompts, JSONObject metaSnap) {
        JSONObject fields = new JSONObject();
        for (FieldAction fa : step.getFieldActions().values()) {
            if ("teams_selection".equalsIgnoreCase(fa.getFieldId())) continue;
            String val = resolveValue(fa, issue, prompts);
            String fieldId = fa.getFieldId();
            if (val == null || val.equalsIgnoreCase("null")) { fields.put(fieldId, JSONObject.NULL); continue; }

            String trimmed = val.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try { if (trimmed.startsWith("{")) fields.put(fieldId, new JSONObject(trimmed)); else fields.put(fieldId, new JSONArray(trimmed)); continue; }
                catch (Exception ignored) {}
            }

            JSONObject fieldMeta = metaSnap != null ? metaSnap.optJSONObject(fieldId) : null;
            boolean isArray = (fieldMeta != null && fieldMeta.has("schema") && "array".equals(fieldMeta.getJSONObject("schema").optString("type"))) || (fieldId.equals("labels") || fieldId.equals("components") || fieldId.equals("fixVersions") || fieldId.equals("versions"));

            if (isArray) {
                JSONArray arr = new JSONArray();
                for (String p : val.split(",")) arr.put(wrapSingleValue(fieldId, p.trim(), fieldMeta));
                fields.put(fieldId, arr);
            } else fields.put(fieldId, wrapSingleValue(fieldId, val, fieldMeta));
        }
        return fields;
    }

    private Object wrapSingleValue(String fieldId, String val, JSONObject fieldMeta) {
        if (val == null || val.equalsIgnoreCase("null") || val.trim().isEmpty()) return JSONObject.NULL;
        String type = null;
        if (fieldMeta != null && fieldMeta.has("schema")) {
            JSONObject schema = fieldMeta.getJSONObject("schema");
            type = "array".equals(schema.optString("type")) ? schema.optString("items") : schema.optString("type");
        }
        if (type == null) {
            if (fieldId.equals("assignee") || fieldId.equals("reporter") || fieldId.contains("user") || fieldId.contains("owner")) type = "user";
            else if (fieldId.equals("priority") || fieldId.equals("resolution") || fieldId.startsWith("customfield_")) type = "option";
            else if (fieldId.equals("labels")) type = "string";
        }
        if ("user".equals(type)) return new JSONObject().put("name", val);
        if ("parent".equals(fieldId)) return new JSONObject().put("key", JiraUtils.cleanIssueKey(val));
        if ("option".equals(type) || "component".equals(type) || "version".equals(type)) return new JSONObject().put(("component".equals(type) || "version".equals(type) || fieldId.equals("components") || fieldId.contains("Version")) ? "name" : "value", val);
        if ("string".equals(type)) return val;
        if (val.matches("-?\\d+(\\.\\d+)?")) { try { return val.contains(".") ? Double.parseDouble(val) : Long.parseLong(val); } catch (Exception ignored) {} }
        return val;
    }

    private String resolveTokens(String input, JSONObject issue) {
        jsonContexts.put("issue", issue);
        return TokenEngine.replaceTokens(input, jsonContexts, executionVars);
    }

    private String resolveValue(FieldAction fa, JSONObject issue, Map<String, String> prompts) {
        if (fa.getMode() == FieldAction.MappingMode.SET) return resolveTokens(fa.getValue().toString(), issue);
        if (fa.getMode() == FieldAction.MappingMode.PROMPT) {
            String label = fa.getPromptLabel(), clean = label.replaceAll("\\[.*?\\]", "").trim();
            if (prompts.containsKey(clean)) return prompts.get(clean);
            return JOptionPane.showInputDialog(this, label, "Runtime Prompt", JOptionPane.QUESTION_MESSAGE);
        }
        return "";
    }

    private void log(String msg) { SwingUtilities.invokeLater(() -> { runnerLog.append(msg + "\n"); runnerLog.setCaretPosition(runnerLog.getDocument().getLength()); }); }

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
