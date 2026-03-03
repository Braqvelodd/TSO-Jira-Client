package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.ui.workflow.StepEditorPanel;
import tso.usmc.jira.workflow.*;
import tso.usmc.jira.service.JiraMetadataHelper;
import tso.usmc.jira.util.JiraUtils;
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
    private final Map<String, String> cachedFieldOptions = new HashMap<>();
    
    // Designer Components
    private final DefaultListModel<String> recipeListModel = new DefaultListModel<>();
    private final JList<String> recipeList = new JList<>(recipeListModel);
    private final JTextField recipeNameField = new JTextField(20);
    private final JTextField jqlField = new JTextField(30);
    private final JTextField contextIssueField = new JTextField(10); 
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

    // Results data
    private final List<JSONObject> currentSearchIssues = new ArrayList<>();

    // Execution Variables
    private final Map<String, String> executionVars = new HashMap<>();

    private final Map<String, JSONObject> cachedFullMeta = new HashMap<>();
    private final List<String> allTokens = new ArrayList<>();

    public WorkflowOrchestratorPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        this.workflowManager = new WorkflowManager();
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
        right.setPreferredSize(new Dimension(250, 0));
        
        JPanel tokenSearchPanel = new JPanel(new BorderLayout());
        tokenSearchPanel.add(new JLabel(" Search: "), BorderLayout.WEST);
        tokenSearchPanel.add(tokenSearchField, BorderLayout.CENTER);
        right.add(tokenSearchPanel, BorderLayout.NORTH);
        
        tokenList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tokenList.setFont(new Font("Monospaced", Font.PLAIN, 11));
        tokenList.setToolTipText("Double-click to copy token");
        right.add(new JScrollPane(tokenList), BorderLayout.CENTER);
        
        panel.add(right, BorderLayout.EAST);
        
        // Center: Editor
        JPanel center = new JPanel(new BorderLayout());
        
        // Editor Header
        JPanel header = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx=0; gbc.gridy=0; header.add(new JLabel("Recipe Name:"), gbc);
        gbc.gridx=1; gbc.weightx=1.0; header.add(recipeNameField, gbc);
        
        gbc.gridx=0; gbc.gridy=1; gbc.weightx=0; header.add(new JLabel("JQL Query:"), gbc);
        gbc.gridx=1; gbc.weightx=1.0; header.add(jqlField, gbc);
        
        gbc.gridx=0; gbc.gridy=2; gbc.weightx=0; header.add(new JLabel("Context Issue (for metadata):"), gbc);
        gbc.gridx=1; gbc.weightx=1.0; header.add(contextIssueField, gbc);
        JButton fetchMetaBtn = new JButton("Fetch Metadata");
        gbc.gridx=2; gbc.weightx=0; header.add(fetchMetaBtn, gbc);

        JButton saveBtn = new JButton("Save Recipe");
        gbc.gridy=0; gbc.gridx=2; header.add(saveBtn, gbc);
        
        center.add(header, BorderLayout.NORTH);
        
        // Editor Steps
        stepsContainer.setLayout(new BoxLayout(stepsContainer, BoxLayout.Y_AXIS));
        center.add(new JScrollPane(stepsContainer), BorderLayout.CENTER);
        
        // Editor Footer (Add Step)
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addTransBtn = new JButton("Add Transition");
        JButton addUpdateBtn = new JButton("Add Update");
        JButton addCreateBtn = new JButton("Add Create");
        JButton addLinkBtn = new JButton("Add Link");
        JButton addCloneBtn = new JButton("Add Clone (Links/Att)");
        footer.add(addTransBtn);
        footer.add(addUpdateBtn);
        footer.add(addCreateBtn);
        footer.add(addLinkBtn);
        footer.add(addCloneBtn);
        center.add(footer, BorderLayout.SOUTH);
        
        panel.add(center, BorderLayout.CENTER);
        
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
        addCloneBtn.addActionListener(e -> addStep(new CloneStep()));

        tokenSearchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filterTokens(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filterTokens(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTokens(); }
        });

        tokenList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = tokenList.getSelectedValue();
                    if (selected != null) {
                        String token = selected.substring(0, selected.indexOf(" "));
                        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(token);
                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                    }
                }
            }
        });
        
        return panel;
    }

    private JPanel createRunnerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Top: Input & Prompts
        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; top.add(new JLabel("Select Recipe:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; top.add(runnerRecipeCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; top.add(new JLabel("JQL Query / Issue Key:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; top.add(runnerJqlField, gbc);
        gbc.gridx = 2; gbc.weightx = 0; top.add(searchBtn, gbc);

        runnerInputsPanel.setLayout(new GridLayout(0, 2, 5, 5));
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        top.add(runnerInputsPanel, gbc);

        // Center: Results Table & Log Split
        runnerTable.setFillsViewportHeight(true);
        runnerTable.setAutoCreateRowSorter(true);
        runnerTableModel.setColumnIdentifiers(new String[]{"Key", "Summary", "Status", "Assignee"});
        
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setTopComponent(new JScrollPane(runnerTable));
        
        runnerLog.setEditable(false);
        runnerLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        split.setBottomComponent(new JScrollPane(runnerLog));
        split.setDividerLocation(200);

        panel.add(top, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);

        // Bottom: Action Buttons
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

        // Simple heuristic for Issue Key vs JQL
        if (!finalJql.contains(" ") && !finalJql.contains("=") && !finalJql.contains("(")) {
            finalJql = "key = " + finalJql;
        }

        final String jql = finalJql;
        log("Searching: " + jql);
        runnerTableModel.setRowCount(0);
        currentSearchIssues.clear();

        new Thread(() -> {
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
                });
            } catch (Exception e) {
                log("Search Error: " + e.getMessage());
            }
        }).start();
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
            }
            promptValues.put(label, val);
        }

        List<JSONObject> issuesToProcess = new ArrayList<>();
        for (int row : selectedRows) {
            int modelRow = runnerTable.convertRowIndexToModel(row);
            issuesToProcess.add(currentSearchIssues.get(modelRow));
        }

        runnerLog.setText("Starting workflow on " + issuesToProcess.size() + " selected issues...\n");
        new Thread(() -> {
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
                    executionVars.put("issue.key", key);
                    executionVars.put("key", key);
                    
                    // Inject tokens
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
        }).start();
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
                runnerJqlField.setText(recipe.getJqlQuery());
                Set<String> labels = new HashSet<>();
                for (WorkflowStep step : recipe.getSteps()) {
                    // Check for dynamic properties in CreateStep
                    if (step instanceof CreateStep) {
                        CreateStep cs = (CreateStep) step;
                        addDynamicPrompt(labels, "Project (" + step.getLabel() + ")", cs.getProjectKey());
                        addDynamicPrompt(labels, "Issue Type (" + step.getLabel() + ")", cs.getIssueType());
                    }
                    
                    for (FieldAction fa : step.getFieldActions().values()) {
                        if (fa.getMode() == FieldAction.MappingMode.PROMPT) {
                            addDynamicPrompt(labels, fa.getPromptLabel(), fa.getValue() != null ? fa.getValue().toString() : null);
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

    private void addDynamicPrompt(Set<String> labels, String label, String value) {
        if (label == null || label.trim().isEmpty()) return;
        
        // Clean label for storage/lookup
        String cleanLabel = label.replaceAll("\\[.*?\\]", "").trim();
        
        // Treat as dynamic if either label or value contains a tag/list
        boolean isDynamic = (value != null && (value.contains(",") || value.contains("[config:"))) || label.contains("[config:");
        
        if (isDynamic) {
            if (!labels.contains(cleanLabel)) {
                labels.add(cleanLabel);
                runnerInputsPanel.add(new JLabel(cleanLabel + ":"));
                JComponent input = createPromptInput(label, value);
                runnerInputsPanel.add(input);
                promptFields.put(cleanLabel, input);
            }
        } else {
            // Standard field prompt (text input)
            if (label.startsWith("Project (") || label.startsWith("Issue Type (")) return;
            
            if (!labels.contains(cleanLabel)) {
                labels.add(cleanLabel);
                runnerInputsPanel.add(new JLabel(cleanLabel + ":"));
                JComponent input = createPromptInput(label, null);
                runnerInputsPanel.add(input);
                promptFields.put(cleanLabel, input);
            }
        }
    }

    private JComponent createPromptInput(String label, String staticOptions) {
        String tagSource = null;
        if (staticOptions != null && staticOptions.contains("[config:")) tagSource = staticOptions;
        else if (label != null && label.contains("[config:")) tagSource = label;

        if (tagSource != null) {
            try {
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
                            if (val.contains(",")) return new JComboBox<>(val.split(","));
                            return new JTextField(val);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // Handle Static Options from Designer
        if (staticOptions != null && !staticOptions.trim().isEmpty() && staticOptions.contains(",")) {
            String[] opts = staticOptions.split(",");
            for (int i = 0; i < opts.length; i++) opts[i] = opts[i].trim();
            return new JComboBox<>(opts);
        }
        
        return new JTextField();
    }

    private static class ConfigOption {
        String label, value, teamKey;
        ConfigOption(String l, String v, String tk) { this.label = l; this.value = v; this.teamKey = tk; }
        @Override public String toString() { return label; }
    }

    private void filterTokens() {
        String filter = tokenSearchField.getText().toLowerCase();
        tokenListModel.clear();
        for (String t : allTokens) {
            if (t.toLowerCase().contains(filter)) tokenListModel.addElement(t);
        }
    }

    private void fetchLiveMetadata() {
        String key = contextIssueField.getText().trim();
        if (key.isEmpty()) return;
        new Thread(() -> {
            try {
                JiraMetadataHelper helper = new JiraMetadataHelper(mainFrame.getService(), mainFrame.getBaseUrl());
                Map<String, JSONObject> meta = helper.getEditMetadata(key);
                cachedFullMeta.clear();
                cachedFullMeta.putAll(meta);
                cachedFieldOptions.clear();
                
                List<String> tokens = new ArrayList<>();
                tokens.add("{{key}} (Issue Key)");
                tokens.add("{{last_key}} (Last Created Issue)");
                tokens.add("{{team.name}} (Selected Team Name)");
                tokens.add("{{team.lead}} (Selected Team Lead)");
                tokens.add("{{team.component}} (Selected Team Component)");
                tokens.add("{{team.id}} (Selected Team ID)");
                
                for (String fieldId : meta.keySet()) {
                    String name = meta.get(fieldId).getString("name");
                    cachedFieldOptions.put(name + " (" + fieldId + ")", fieldId);
                    tokens.add("{{fields." + fieldId + "}} (" + name + ")");
                }
                Collections.sort(tokens);

                SwingUtilities.invokeLater(() -> {
                    allTokens.clear();
                    allTokens.addAll(tokens);
                    filterTokens();

                    for (Component c : stepsContainer.getComponents()) {
                        if (c instanceof StepEditorPanel) {
                            ((StepEditorPanel) c).refreshMetadata(cachedFieldOptions);
                        }
                    }
                    cachedFieldOptions.put("teams_selection (Virtual)", "teams_selection");
                    JOptionPane.showMessageDialog(this, "Fetched " + cachedFieldOptions.size() + " fields and tokens.");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Metadata error: " + ex.getMessage()));
            }
        }).start();
    }

    private void addStep(WorkflowStep step) {
        step.setLabel("New " + step.getType() + " Step");
        addStepUI(step);
    }

    private void addStepUI(WorkflowStep step) {
        StepEditorPanel panel = new StepEditorPanel(step, cachedFieldOptions, () -> {
            stepsContainer.remove(getStepPanel(step));
            stepsContainer.revalidate();
            stepsContainer.repaint();
        });
        stepsContainer.add(panel);
        stepsContainer.revalidate();
        stepsContainer.repaint();
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
            System.out.println("Loading recipe: " + name);
            WorkflowRecipe recipe = workflowManager.loadWorkflow(name);
            if (recipe == null) {
                // Try loading from JiraConfig
                String key = "workflow." + name;
                String val = mainFrame.getJiraConfig().getProperty(key);
                if (val != null) {
                    System.out.println("Found recipe in config: " + key);
                    recipe = WorkflowRecipe.fromJson(val);
                }
            }

            if (recipe != null) {
                recipeNameField.setText(recipe.getRecipeName());
                jqlField.setText(recipe.getJqlQuery());
                stepsContainer.removeAll();
                for (WorkflowStep step : recipe.getSteps()) {
                    addStepUI(step);
                }
                stepsContainer.revalidate();
                stepsContainer.repaint();
                System.out.println("Populated " + recipe.getSteps().size() + " steps.");
            } else {
                System.err.println("Recipe not found: " + name);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading recipe '" + name + "': " + e.getMessage());
        }
    }

    private void refreshRecipeList() {
        Set<String> names = new TreeSet<>(workflowManager.listWorkflows());
        // Add recipes from JiraConfig
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
        
        // Store metadata snapshot if we have any
        if (!cachedFullMeta.isEmpty()) {
            recipe.setMetadataSnapshot(new JSONObject(cachedFullMeta));
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
        String currentKey = issue.getString("key");

        if (step instanceof CreateStep) {
            CreateStep cs = (CreateStep) step;
            String proj = resolveStepProperty(cs.getProjectKey(), "Project (" + step.getLabel() + ")", prompts);
            String type = resolveStepProperty(cs.getIssueType(), "Issue Type (" + step.getLabel() + ")", prompts);
            
            JSONObject fields = buildFields(step, issue, prompts, metaSnap);
            fields.put("project", new JSONObject().put("key", proj));
            fields.put("issuetype", new JSONObject().put("name", type));
            String resp = mainFrame.getService().executeRequest(baseUrl + "/rest/api/2/issue", "POST", new JSONObject().put("fields", fields).toString());
            String newKey = new JSONObject(resp).getString("key");
            executionVars.put("last_key", newKey);
            log("  > Created " + newKey);
        } else if (step instanceof UpdateStep) {
            JSONObject fields = buildFields(step, issue, prompts, metaSnap);
            mainFrame.getService().executeRequest(baseUrl + "/rest/api/2/issue/" + currentKey, "PUT", new JSONObject().put("fields", fields).toString());
            log("  > Updated fields.");
        } else if (step instanceof TransitionStep) {
            TransitionStep ts = (TransitionStep) step;
            String transUrl = baseUrl + "/rest/api/2/issue/" + currentKey + "/transitions";
            String transMeta = mainFrame.getService().executeRequest(transUrl, "GET", null);
            String tid = JiraUtils.findTransitionIdByName(transMeta, ts.getTargetStatus());
            if (tid != null) {
                mainFrame.getService().executeRequest(transUrl, "POST", new JSONObject().put("transition", new JSONObject().put("id", tid)).toString());
                log("  > Transitioned to " + ts.getTargetStatus());
            } else log("  > ERROR: Transition '" + ts.getTargetStatus() + "' not found.");
        } else if (step instanceof LinkStep) {
            LinkStep ls = (LinkStep) step;
            String inward = resolveTokens(ls.getInwardIssueToken(), issue);
            String outward = resolveTokens(ls.getOutwardIssueToken(), issue);
            JSONObject body = new JSONObject();
            body.put("type", new JSONObject().put("name", ls.getLinkType()));
            body.put("inwardIssue", new JSONObject().put("key", inward));
            body.put("outwardIssue", new JSONObject().put("key", outward));
            mainFrame.getService().executeRequest(baseUrl + "/rest/api/2/issueLink", "POST", body.toString());
            log("  > Linked " + inward + " to " + outward);
        } else if (step instanceof CloneStep) {
            CloneStep cls = (CloneStep) step;
            String srcKey = resolveTokens(cls.getSourceIssueToken(), issue);
            String targetKey = resolveTokens(cls.getTargetIssueToken(), issue);
            
            // Fetch source issue data if it's not the one we are currently iterating over
            JSONObject sourceData = issue;
            if (!srcKey.equals(issue.getString("key"))) {
                String srcResp = mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + srcKey + "?expand=names,renderedFields&fields=*all,attachment,issuelinks", "GET", null);
                sourceData = new JSONObject(srcResp);
            }

            if (cls.isCopyAttachments()) {
                cloneAttachments(sourceData, targetKey);
            }
            if (cls.isCopyLinks()) {
                cloneLinks(sourceData, targetKey);
            }
        }
    }

    private String resolveStepProperty(String value, String promptLabel, Map<String, String> prompts) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("[config:")) {
            if (prompts.containsKey(promptLabel)) return prompts.get(promptLabel);
        }
        return value;
    }

    private void cloneAttachments(JSONObject sourceIssue, String targetKey) throws Exception {
        if (!sourceIssue.getJSONObject("fields").has("attachment")) return;
        JSONArray attachments = sourceIssue.getJSONObject("fields").getJSONArray("attachment");
        for (int i = 0; i < attachments.length(); i++) {
            JSONObject att = attachments.getJSONObject(i);
            String filename = att.getString("filename");
            String contentUrl = att.getString("content");
            java.io.File tempFile = mainFrame.getService().downloadAttachmentToTempFile(contentUrl, filename);
            try {
                mainFrame.getService().uploadAttachment(mainFrame.getBaseUrl() + "/rest/api/2/issue/" + targetKey + "/attachments", tempFile, filename);
                log("  > Cloned attachment: " + filename);
            } finally {
                if (tempFile != null) tempFile.delete();
            }
        }
    }

    private void cloneLinks(JSONObject sourceIssue, String targetKey) throws Exception {
        if (!sourceIssue.getJSONObject("fields").has("issuelinks")) return;
        JSONArray links = sourceIssue.getJSONObject("fields").getJSONArray("issuelinks");
        String sourceKey = sourceIssue.getString("key");
        
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.getJSONObject(i);
            String typeName = link.getJSONObject("type").getString("name");
            
            String otherKey = null;
            boolean isInward = false;
            
            if (link.has("inwardIssue")) {
                otherKey = link.getJSONObject("inwardIssue").getString("key");
                isInward = true;
            } else if (link.has("outwardIssue")) {
                otherKey = link.getJSONObject("outwardIssue").getString("key");
            }
            
            if (otherKey == null) continue;

            JSONObject body = new JSONObject().put("type", new JSONObject().put("name", typeName));
            if (isInward) {
                body.put("inwardIssue", new JSONObject().put("key", targetKey));
                body.put("outwardIssue", new JSONObject().put("key", otherKey));
            } else {
                body.put("inwardIssue", new JSONObject().put("key", otherKey));
                body.put("outwardIssue", new JSONObject().put("key", targetKey));
            }
            
            try {
                mainFrame.getService().executeRequest(mainFrame.getBaseUrl() + "/rest/api/2/issueLink", "POST", body.toString());
                log("  > Cloned link: " + typeName + " to " + otherKey);
            } catch (Exception e) {
                log("  > Warning: Could not clone link to " + otherKey);
            }
        }
    }

    private JSONObject buildFields(WorkflowStep step, JSONObject issue, Map<String, String> prompts, JSONObject metaSnap) {
        JSONObject fields = new JSONObject();
        for (FieldAction fa : step.getFieldActions().values()) {
            if ("teams_selection".equalsIgnoreCase(fa.getFieldId())) continue; // Virtual Field
            
            String val = resolveValue(fa, issue, prompts);
            String fieldId = fa.getFieldId();

            if (val == null || val.equalsIgnoreCase("null")) {
                fields.put(fieldId, JSONObject.NULL);
                continue;
            }

            // 1. JSON Parsing Fallback: If user entered raw JSON, use it directly.
            String trimmed = val.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                try {
                    if (trimmed.startsWith("{")) fields.put(fieldId, new JSONObject(trimmed));
                    else fields.put(fieldId, new JSONArray(trimmed));
                    continue;
                } catch (Exception ignored) {}
            }

            // 2. Metadata-driven Smart Wrapping
            JSONObject fieldMeta = metaSnap != null ? metaSnap.optJSONObject(fieldId) : null;
            boolean isArray = false;
            if (fieldMeta != null && fieldMeta.has("schema")) {
                isArray = "array".equals(fieldMeta.getJSONObject("schema").optString("type"));
            } else {
                // Hardcoded defaults for standard array fields if no meta available
                if (fieldId.equals("labels") || fieldId.equals("components") || fieldId.equals("fixVersions") || fieldId.equals("versions")) {
                    isArray = true;
                }
            }

            if (isArray) {
                String[] parts = val.split(",");
                JSONArray arr = new JSONArray();
                for (String p : parts) {
                    arr.put(wrapSingleValue(fieldId, p.trim(), fieldMeta));
                }
                fields.put(fieldId, arr);
            } else {
                fields.put(fieldId, wrapSingleValue(fieldId, val, fieldMeta));
            }
        }
        return fields;
    }

    private Object wrapSingleValue(String fieldId, String val, JSONObject fieldMeta) {
        if (val == null || val.equalsIgnoreCase("null")) return JSONObject.NULL;

        String type = null;
        if (fieldMeta != null && fieldMeta.has("schema")) {
            JSONObject schema = fieldMeta.getJSONObject("schema");
            type = schema.optString("type");
            if ("array".equals(type)) {
                type = schema.optString("items");
            }
        }

        // Hardcoded fallbacks
        if (type == null) {
            if (fieldId.equals("assignee") || fieldId.equals("reporter") || fieldId.contains("user") || fieldId.contains("owner")) type = "user";
            else if (fieldId.equals("priority") || fieldId.equals("resolution")) type = "option";
            else if (fieldId.equals("labels")) type = "string";
            else if (fieldId.startsWith("customfield_")) type = "option";
        }

        if ("user".equals(type)) return new JSONObject().put("name", val);
        if ("option".equals(type) || "component".equals(type) || "version".equals(type)) {
            String subKey = "value";
            if ("component".equals(type) || "version".equals(type) || fieldId.equals("components") || fieldId.contains("Version")) {
                subKey = "name";
            }
            return new JSONObject().put(subKey, val);
        }
        if ("string".equals(type)) return val;

        // Numeric check
        if (val.matches("-?\\d+(\\.\\d+)?")) {
            try {
                if (val.contains(".")) return Double.parseDouble(val);
                return Long.parseLong(val);
            } catch (Exception ignored) {}
        }

        return val;
    }

    private String resolveTokens(String input, JSONObject issue) {
        String result = TokenEngine.replaceTokens(input, issue);
        for (String var : executionVars.keySet()) {
            result = result.replace("{{" + var + "}}", executionVars.get(var));
        }
        return result;
    }

    private String resolveValue(FieldAction fa, JSONObject issue, Map<String, String> prompts) {
        if (fa.getMode() == FieldAction.MappingMode.STATIC) return fa.getValue().toString();
        if (fa.getMode() == FieldAction.MappingMode.VARIABLE) return resolveTokens(fa.getValue().toString(), issue);
        if (fa.getMode() == FieldAction.MappingMode.PROMPT) {
            String label = fa.getPromptLabel();
            String cleanLabel = label.replaceAll("\\[.*?\\]", "").trim();
            if (prompts.containsKey(cleanLabel)) return prompts.get(cleanLabel);
            return JOptionPane.showInputDialog(this, label, "Runtime Prompt", JOptionPane.QUESTION_MESSAGE);
        }
        return "";
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> { runnerLog.append(msg + "\n"); runnerLog.setCaretPosition(runnerLog.getDocument().getLength()); });
    }
}
