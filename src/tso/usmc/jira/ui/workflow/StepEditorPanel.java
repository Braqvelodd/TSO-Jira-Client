package tso.usmc.jira.ui.workflow;

import tso.usmc.jira.workflow.WorkflowStep;
import tso.usmc.jira.workflow.FieldAction;
import tso.usmc.jira.workflow.TransitionStep;
import tso.usmc.jira.workflow.UpdateStep;
import tso.usmc.jira.workflow.AssetStep;
import tso.usmc.jira.workflow.CreateStep;
import tso.usmc.jira.workflow.LinkAction;
import tso.usmc.jira.workflow.LinkStep;
import tso.usmc.jira.workflow.WorklogStep;
import org.json.JSONObject;
import tso.usmc.jira.ui.SwingUtils;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StepEditorPanel extends JPanel {
    public interface StepActionListener {
        void onMoveUp(StepEditorPanel panel);
        void onMoveDown(StepEditorPanel panel);
    }

    public interface StepMetadataListener {
        void onFetchTransitionFields(TransitionStep step);
        void onFetchCreateFields(CreateStep step);
    }

    private final WorkflowStep step;
    private final JTextField labelField;
    private final JPanel fieldsContainer;
    private final List<FieldActionPanel> actionPanels = new ArrayList<>();
    private final List<LinkActionPanel> linkActionPanels = new ArrayList<>();
    private final Map<String, String> fieldOptions; // Label -> ID mapping
    private final Map<String, JSONObject> fullMetadata;
    private final StepMetadataListener metadataListener;

    private JTextField targetIssueField;
    private JTextField projField;
    private JTextField typeField;
    private JTextField inwardField;
    private JTextField sourceTokenField;
    private JTextField targetTokenField;
    private JTextField timeSpentField;
    private JTextField commentField;
    private JTextField startedField;
    private JTextField subTaskFieldsComp;
    private List<String> cachedLinkTypes = new ArrayList<>();

    // Condition UI
    private final JTextField condTokenField;
    private final JComboBox<String> condOpCombo;
    private final JTextField condValueField;
    private final JPanel conditionInnerPanel;

    public StepEditorPanel(WorkflowStep step, Map<String, String> fieldOptions, Map<String, JSONObject> fullMetadata, Runnable onRemove, StepActionListener stepListener, StepMetadataListener metadataListener) {
        this.step = step;
        this.fieldOptions = fieldOptions;
        this.fullMetadata = fullMetadata;
        this.metadataListener = metadataListener;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        header.setBackground(new Color(230, 230, 230));

        labelField = new JTextField(step.getLabel(), 20);
        SwingUtils.setupExpandedView(labelField);
        header.add(createPair("[" + step.getType() + "] Label:", labelField));
        
        if (step instanceof UpdateStep) {
            targetIssueField = new JTextField(((UpdateStep)step).getTargetIssueToken(), 10);
            SwingUtils.setupExpandedView(targetIssueField);
            header.add(createPair("Target Issue:", targetIssueField));
        }

        if (step instanceof TransitionStep) {
            TransitionStep ts = (TransitionStep) step;
            targetIssueField = new JTextField(ts.getTargetIssueToken(), 10);
            SwingUtils.setupExpandedView(targetIssueField);
            header.add(createPair("Target Issue:", targetIssueField));
            
            JTextField targetField = new JTextField(ts.getTargetStatus(), 15);
            SwingUtils.setupExpandedView(targetField);
            targetField.getDocument().addDocumentListener(new SimpleDocumentListener(() -> ts.setTargetStatus(targetField.getText())));
            header.add(createPair("Target Status:", targetField));
        }

        if (step instanceof CreateStep) {
            CreateStep cs = (CreateStep) step;
            projField = new JTextField(cs.getProjectKey(), 5);
            SwingUtils.setupExpandedView(projField);
            header.add(createPair("Project:", projField));
            
            typeField = new JTextField(cs.getIssueType(), 10);
            SwingUtils.setupExpandedView(typeField);
            header.add(createPair("Type:", typeField));
        }

        if (step instanceof AssetStep) {
            AssetStep as = (AssetStep) step;
            sourceTokenField = new JTextField(as.getSourceIssueToken(), 10);
            SwingUtils.setupExpandedView(sourceTokenField);
            header.add(createPair("From:", sourceTokenField));
            
            targetTokenField = new JTextField(as.getTargetIssueToken(), 10);
            SwingUtils.setupExpandedView(targetTokenField);
            header.add(createPair("To:", targetTokenField));
            
            JCheckBox pOpt = new JCheckBox("Prompt?", as.isPromptOptions());
            pOpt.addActionListener(e -> as.setPromptOptions(pOpt.isSelected()));
            header.add(createPair("", pOpt));
            JCheckBox att = new JCheckBox("Attachments", as.isCopyAttachments());
            att.addActionListener(e -> as.setCopyAttachments(att.isSelected()));
            header.add(createPair("", att));

            JCheckBox links = new JCheckBox("Links", as.isCopyLinks());
            links.addActionListener(e -> as.setCopyLinks(links.isSelected()));
            header.add(createPair("", links));

            JCheckBox subtasks = new JCheckBox("Sub-tasks", as.isCopySubTasks());
            subtasks.addActionListener(e -> as.setCopySubTasks(subtasks.isSelected()));
            header.add(createPair("", subtasks));

            subTaskFieldsComp = new JTextField(as.getSubTaskFields(), 20);
            SwingUtils.setupExpandedView(subTaskFieldsComp);
            header.add(createPair("Fields to Asset (CSV):", subTaskFieldsComp));
        }

        if (step instanceof WorklogStep) {
            WorklogStep ws = (WorklogStep) step;
            targetIssueField = new JTextField(ws.getTargetIssueToken(), 10);
            SwingUtils.setupExpandedView(targetIssueField);
            header.add(createPair("Target Issue:", targetIssueField));
            
            timeSpentField = new JTextField(ws.getTimeSpent(), 8);
            SwingUtils.setupExpandedView(timeSpentField);
            header.add(createPair("Time Spent:", timeSpentField));
            
            commentField = new JTextField(ws.getComment(), 15);
            SwingUtils.setupExpandedView(commentField);
            header.add(createPair("Comment:", commentField));
            
            startedField = new JTextField(ws.getStarted(), 12);
            SwingUtils.setupExpandedView(startedField);
            header.add(createPair("Started:", startedField));
        }

        // Step Rearrangement Buttons
        JPanel movePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        movePanel.setOpaque(false);
        JButton stepUpBtn = new JButton("▲");
        JButton stepDownBtn = new JButton("▼");
        Dimension stepBtnDim = new Dimension(22, 22);
        stepUpBtn.setPreferredSize(stepBtnDim);
        stepDownBtn.setPreferredSize(stepBtnDim);
        stepUpBtn.setMargin(new Insets(0, 0, 0, 0));
        stepDownBtn.setMargin(new Insets(0, 0, 0, 0));
        stepUpBtn.addActionListener(e -> stepListener.onMoveUp(this));
        stepDownBtn.addActionListener(e -> stepListener.onMoveDown(this));
        movePanel.add(stepUpBtn);
        movePanel.add(stepDownBtn);
        header.add(movePanel);

        JButton removeBtn = new JButton("X");
        removeBtn.setForeground(Color.RED);
        removeBtn.addActionListener(e -> onRemove.run());
        header.add(removeBtn);
        
        add(header, BorderLayout.NORTH);

        // Content Wrapper
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // --- CONDITION SECTION ---
        JPanel conditionOuterPanel = new JPanel(new BorderLayout());
        conditionOuterPanel.setBorder(BorderFactory.createTitledBorder("Step Execution Condition (Optional)"));
        conditionOuterPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        conditionInnerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        condTokenField = new JTextField(step.getConditionToken() != null ? step.getConditionToken() : "", 15);
        condOpCombo = new JComboBox<>(new String[]{"ALWAYS", "EQUALS", "NOT_EQUALS", "CONTAINS", "NOT_CONTAINS", "EMPTY", "NOT_EMPTY"});
        condOpCombo.setSelectedItem(step.getConditionOperator() != null ? step.getConditionOperator() : "ALWAYS");
        condValueField = new JTextField(step.getConditionValue() != null ? step.getConditionValue() : "", 15);
        
        conditionInnerPanel.add(new JLabel("If:"));
        conditionInnerPanel.add(condTokenField);
        conditionInnerPanel.add(condOpCombo);
        conditionInnerPanel.add(condValueField);
        conditionInnerPanel.add(new JLabel("then execute."));
        
        conditionOuterPanel.add(conditionInnerPanel, BorderLayout.CENTER);
        contentPanel.add(conditionOuterPanel);

        // Fields Container
        fieldsContainer = new JPanel();
        fieldsContainer.setLayout(new BoxLayout(fieldsContainer, BoxLayout.Y_AXIS));
        fieldsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        if (step instanceof LinkStep) {
            for (LinkAction la : ((LinkStep) step).getLinkActions()) {
                addLinkAction(la);
            }
        } else {
            for (FieldAction action : step.getFieldActions().values()) {
                addField(action);
            }
        }
        contentPanel.add(fieldsContainer);

        // Footer (Add Field/Link)
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        if (step instanceof LinkStep) {
            JButton addLinkBtn = new JButton("+ Add Link");
            addLinkBtn.addActionListener(e -> addLinkAction(new LinkAction()));
            footer.add(addLinkBtn);
        } else if (step.getType() != WorkflowStep.StepType.ASSET && step.getType() != WorkflowStep.StepType.WORKLOG) {
            JButton addFieldBtn = new JButton("+ Add Field");
            addFieldBtn.addActionListener(e -> addField(new FieldAction("", FieldAction.MappingMode.SET, "", "")));
            footer.add(addFieldBtn);
            
            if (step instanceof TransitionStep) {
                JButton fetchBtn = new JButton("Fetch Transition Fields");
                fetchBtn.addActionListener(e -> {
                    if (metadataListener != null) {
                        saveToStep(); // Save latest status/key from UI
                        metadataListener.onFetchTransitionFields((TransitionStep) step);
                    }
                });
                footer.add(fetchBtn);
            }
        }
        contentPanel.add(footer);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.add(contentPanel, BorderLayout.NORTH);
        add(centerWrapper, BorderLayout.CENTER);
    }

    public void addField(FieldAction action) {
        FieldActionPanel panel = new FieldActionPanel(action, fieldOptions, fullMetadata, new FieldActionPanel.FieldActionListener() {
            @Override
            public void onMoveUp(FieldActionPanel p) {
                int idx = actionPanels.indexOf(p);
                if (idx > 0) {
                    actionPanels.remove(idx);
                    actionPanels.add(idx - 1, p);
                    refreshActionLayout();
                }
            }

            @Override
            public void onMoveDown(FieldActionPanel p) {
                int idx = actionPanels.indexOf(p);
                if (idx >= 0 && idx < actionPanels.size() - 1) {
                    actionPanels.remove(idx);
                    actionPanels.add(idx + 1, p);
                    refreshActionLayout();
                }
            }

            @Override
            public void onRemove(FieldActionPanel p) {
                actionPanels.remove(p);
                fieldsContainer.remove(p);
                fieldsContainer.revalidate();
                fieldsContainer.repaint();
            }
        });
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionPanels.add(panel);
        fieldsContainer.add(panel);
        fieldsContainer.revalidate();
        fieldsContainer.repaint();
    }

    public void addLinkAction(LinkAction action) {
        LinkActionPanel panel = new LinkActionPanel(action, cachedLinkTypes, new LinkActionPanel.LinkActionListener() {
            @Override
            public void onMoveUp(LinkActionPanel p) {
                int idx = linkActionPanels.indexOf(p);
                if (idx > 0) {
                    linkActionPanels.remove(idx);
                    linkActionPanels.add(idx - 1, p);
                    refreshActionLayout();
                }
            }

            @Override
            public void onMoveDown(LinkActionPanel p) {
                int idx = linkActionPanels.indexOf(p);
                if (idx >= 0 && idx < linkActionPanels.size() - 1) {
                    linkActionPanels.remove(idx);
                    linkActionPanels.add(idx + 1, p);
                    refreshActionLayout();
                }
            }

            @Override
            public void onRemove(LinkActionPanel p) {
                linkActionPanels.remove(p);
                fieldsContainer.remove(p);
                fieldsContainer.revalidate();
                fieldsContainer.repaint();
            }
        });
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        linkActionPanels.add(panel);
        fieldsContainer.add(panel);
        fieldsContainer.revalidate();
        fieldsContainer.repaint();
    }

    private JPanel createPair(String label, JComponent comp) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        p.setOpaque(false);
        if (label != null && !label.isEmpty()) {
            p.add(new JLabel(label));
        }
        p.add(comp);
        return p;
    }

    private void refreshActionLayout() {
        fieldsContainer.removeAll();
        if (step instanceof LinkStep) {
            for (LinkActionPanel p : linkActionPanels) fieldsContainer.add(p);
        } else {
            for (FieldActionPanel p : actionPanels) fieldsContainer.add(p);
        }
        fieldsContainer.revalidate();
        fieldsContainer.repaint();
    }

    public void saveToStep() {
        step.setLabel(labelField.getText());
        
        // Save Condition
        step.setConditionToken(condTokenField.getText());
        step.setConditionOperator((String) condOpCombo.getSelectedItem());
        step.setConditionValue(condValueField.getText());

        if (step instanceof UpdateStep) {
            ((UpdateStep)step).setTargetIssueToken(targetIssueField.getText());
        }
        if (step instanceof TransitionStep) {
            ((TransitionStep)step).setTargetIssueToken(targetIssueField.getText());
        }
        if (step instanceof CreateStep) {
            ((CreateStep)step).setProjectKey(projField.getText());
            ((CreateStep)step).setIssueType(typeField.getText());
        }
        if (step instanceof LinkStep) {
            LinkStep ls = (LinkStep) step;
            ls.getLinkActions().clear();
            for (LinkActionPanel lap : linkActionPanels) {
                ls.addLinkAction(lap.getLinkAction());
            }
        }
        if (step instanceof AssetStep) {
            AssetStep as = (AssetStep) step;
            as.setSourceIssueToken(sourceTokenField.getText());
            as.setTargetIssueToken(targetTokenField.getText());
            if (subTaskFieldsComp != null) {
                as.setSubTaskFields(subTaskFieldsComp.getText());
            }
        }
        if (step instanceof WorklogStep) {
            WorklogStep ws = (WorklogStep) step;
            ws.setTargetIssueToken(targetIssueField.getText());
            ws.setTimeSpent(timeSpentField.getText());
            ws.setComment(commentField.getText());
            ws.setStarted(startedField.getText());
        }
        
        step.getFieldActions().clear();
        for (FieldActionPanel panel : actionPanels) {
            step.addFieldAction(panel.getFieldAction());
        }
    }

    public WorkflowStep getStep() { return step; }

    public void refreshMetadata(Map<String, String> fieldOptions, Map<String, JSONObject> fullMetadata) {
        for (FieldActionPanel panel : actionPanels) {
            panel.refreshMetadata(fieldOptions, fullMetadata);
        }
    }

    public void updateLinkTypes(List<String> linkTypes) {
        this.cachedLinkTypes = linkTypes;
        for (LinkActionPanel panel : linkActionPanels) {
            panel.updateLinkTypes(linkTypes);
        }
    }

    private static class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable onChange;
        SimpleDocumentListener(Runnable onChange) { this.onChange = onChange; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
    }
}
