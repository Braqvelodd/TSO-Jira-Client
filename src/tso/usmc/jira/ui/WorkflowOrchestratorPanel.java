package tso.usmc.jira.ui;

import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.ui.workflow.StepEditorPanel;
import tso.usmc.jira.workflow.*;
import tso.usmc.jira.service.JiraMetadataHelper;
import tso.usmc.jira.util.JiraUtils;
import org.json.JSONObject;
import org.json.JSONArray;

import javax.swing.*;
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
    
    // Runner Components
    private final JComboBox<String> runnerRecipeCombo = new JComboBox<>();
    private final JTextArea runnerLog = new JTextArea();
    private final JButton runBtn = new JButton("Run Workflow");

    // Execution Variables
    private final Map<String, String> executionVars = new HashMap<>();

    public WorkflowOrchestratorPanel(JiraApiClientGui mainFrame) {
        this.mainFrame = mainFrame;
        this.workflowManager = new WorkflowManager();
        setLayout(new BorderLayout());

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.addTab("Designer", createDesignerPanel());
        mainTabs.addTab("Runner", createRunnerPanel());
        
        add(mainTabs, BorderLayout.CENTER);
        
        refreshRecipeList();
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
        
        return panel;
    }

    private JPanel createRunnerPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Select Recipe:"));
        top.add(runnerRecipeCombo);
        panel.add(top, BorderLayout.NORTH);
        runnerLog.setEditable(false);
        runnerLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panel.add(new JScrollPane(runnerLog), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout());
        runBtn.setBackground(new Color(200, 255, 200));
        bottom.add(runBtn);
        panel.add(bottom, BorderLayout.SOUTH);
        runBtn.addActionListener(e -> runWorkflow());
        return panel;
    }

    private void fetchLiveMetadata() {
        String key = contextIssueField.getText().trim();
        if (key.isEmpty()) return;
        new Thread(() -> {
            try {
                JiraMetadataHelper helper = new JiraMetadataHelper(mainFrame.getService(), mainFrame.getBaseUrl());
                Map<String, JSONObject> meta = helper.getEditMetadata(key);
                cachedFieldOptions.clear();
                for (String fieldId : meta.keySet()) {
                    cachedFieldOptions.put(meta.get(fieldId).getString("name") + " (" + fieldId + ")", fieldId);
                }
                SwingUtilities.invokeLater(() -> {
                    for (Component c : stepsContainer.getComponents()) {
                        if (c instanceof StepEditorPanel) {
                            ((StepEditorPanel) c).refreshMetadata(cachedFieldOptions);
                        }
                    }
                    JOptionPane.showMessageDialog(this, "Fetched " + cachedFieldOptions.size() + " fields.");
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

    private void runWorkflow() {
        String recipeName = (String) runnerRecipeCombo.getSelectedItem();
        if (recipeName == null) return;
        runnerLog.setText("Starting " + recipeName + "...\n");
        new Thread(() -> {
            try {
                WorkflowRecipe recipe = workflowManager.loadWorkflow(recipeName);
                if (recipe == null) return;
                String encodedJql = java.net.URLEncoder.encode(recipe.getJqlQuery(), "UTF-8");
                String searchUrl = mainFrame.getBaseUrl() + "/rest/api/2/search?jql=" + encodedJql + "&expand=names,renderedFields&fields=*all,attachment,issuelinks";
                String searchResp = mainFrame.getService().executeRequest(searchUrl, "GET", null);
                JSONArray issues = new JSONObject(searchResp).getJSONArray("issues");
                
                log("Found " + issues.length() + " issues.");
                for (int i = 0; i < issues.length(); i++) {
                    JSONObject issue = issues.getJSONObject(i);
                    String key = issue.getString("key");
                    log("--- Processing " + key + " ---");
                    executionVars.clear();
                    executionVars.put("issue.key", key);
                    executionVars.put("key", key); // Shortcut
                    
                    for (WorkflowStep step : recipe.getSteps()) {
                        log("Step: " + step.getLabel());
                        executeStep(step, issue);
                    }
                }
                log("Workflow Execution Complete.");
            } catch (Exception e) {
                log("FATAL ERROR: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void executeStep(WorkflowStep step, JSONObject issue) throws Exception {
        String baseUrl = mainFrame.getBaseUrl();
        String currentKey = issue.getString("key");

        if (step instanceof CreateStep) {
            CreateStep cs = (CreateStep) step;
            JSONObject fields = buildFields(step, issue);
            fields.put("project", new JSONObject().put("key", cs.getProjectKey()));
            fields.put("issuetype", new JSONObject().put("name", cs.getIssueType()));
            String resp = mainFrame.getService().executeRequest(baseUrl + "/rest/api/2/issue", "POST", new JSONObject().put("fields", fields).toString());
            String newKey = new JSONObject(resp).getString("key");
            executionVars.put("last_key", newKey);
            log("  > Created " + newKey);
        } else if (step instanceof UpdateStep) {
            JSONObject fields = buildFields(step, issue);
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
            String targetKey = resolveTokens(cls.getSourceIssueToken(), issue);
            
            if (cls.isCopyAttachments()) {
                cloneAttachments(issue, targetKey);
            }
            if (cls.isCopyLinks()) {
                cloneLinks(issue, targetKey);
            }
        }
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

    private JSONObject buildFields(WorkflowStep step, JSONObject issue) {
        JSONObject fields = new JSONObject();
        for (FieldAction fa : step.getFieldActions().values()) {
            String val = resolveValue(fa, issue);
            if (val == null || val.equalsIgnoreCase("null")) fields.put(fa.getFieldId(), JSONObject.NULL);
            else fields.put(fa.getFieldId(), val);
        }
        return fields;
    }

    private String resolveTokens(String input, JSONObject issue) {
        String result = TokenEngine.replaceTokens(input, issue);
        for (String var : executionVars.keySet()) {
            result = result.replace("{{" + var + "}}", executionVars.get(var));
        }
        return result;
    }

    private String resolveValue(FieldAction fa, JSONObject issue) {
        if (fa.getMode() == FieldAction.MappingMode.STATIC) return fa.getValue().toString();
        if (fa.getMode() == FieldAction.MappingMode.VARIABLE) return resolveTokens(fa.getValue().toString(), issue);
        if (fa.getMode() == FieldAction.MappingMode.PROMPT) {
            return JOptionPane.showInputDialog(this, fa.getPromptLabel(), "Runtime Prompt", JOptionPane.QUESTION_MESSAGE);
        }
        return "";
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> { runnerLog.append(msg + "\n"); runnerLog.setCaretPosition(runnerLog.getDocument().getLength()); });
    }
}
