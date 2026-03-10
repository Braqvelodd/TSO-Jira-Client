package tso.usmc.jira.ui.workflow;

import tso.usmc.jira.workflow.WorkflowStep;
import tso.usmc.jira.workflow.FieldAction;
import tso.usmc.jira.workflow.TransitionStep;
import tso.usmc.jira.workflow.UpdateStep;
import tso.usmc.jira.workflow.AssetStep;
import tso.usmc.jira.workflow.CreateStep;
import tso.usmc.jira.workflow.LinkStep;
import tso.usmc.jira.workflow.WorklogStep;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
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
    private final Map<String, String> fieldOptions; // Label -> ID mapping
    private final StepMetadataListener metadataListener;

    private JTextField targetIssueField;
    private JTextField projField;
    private JTextField typeField;
    private JTextField inwardField;
    private JTextField linkTypeField;
    private JComboBox<String> linkTypeCombo;
    private JTextField outwardField;
    private JTextField sourceTokenField;
    private JTextField targetTokenField;
    private JTextField timeSpentField;
    private JTextField commentField;
    private JTextField startedField;
    private JTextField subTaskFieldsComp;

    public StepEditorPanel(WorkflowStep step, Map<String, String> fieldOptions, Runnable onRemove, StepActionListener stepListener, StepMetadataListener metadataListener) {
        this.step = step;
        this.fieldOptions = fieldOptions;
        this.metadataListener = metadataListener;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // Header
        JPanel header = new JPanel(new tso.usmc.jira.util.JiraUtils.WrapLayout(FlowLayout.LEFT, 5, 5));
        header.setBackground(new Color(230, 230, 230));

        labelField = new JTextField(step.getLabel(), 20);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(labelField);
        header.add(createPair("[" + step.getType() + "] Label:", labelField));
        
        if (step instanceof UpdateStep) {
            targetIssueField = new JTextField(((UpdateStep)step).getTargetIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(targetIssueField);
            header.add(createPair("Target Issue:", targetIssueField));
        }

        if (step instanceof TransitionStep) {
            TransitionStep ts = (TransitionStep) step;
            targetIssueField = new JTextField(ts.getTargetIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(targetIssueField);
            header.add(createPair("Target Issue:", targetIssueField));
            
            JTextField targetField = new JTextField(ts.getTargetStatus(), 15);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(targetField);
            targetField.getDocument().addDocumentListener(new SimpleDocumentListener(() -> ts.setTargetStatus(targetField.getText())));
            header.add(createPair("Target Status:", targetField));
        }

        if (step instanceof CreateStep) {
            CreateStep cs = (CreateStep) step;
            projField = new JTextField(cs.getProjectKey(), 5);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(projField);
            header.add(createPair("Project:", projField));
            
            typeField = new JTextField(cs.getIssueType(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(typeField);
            header.add(createPair("Type:", typeField));
        }

        if (step instanceof LinkStep) {
            LinkStep ls = (LinkStep) step;
            inwardField = new JTextField(ls.getInwardIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(inwardField);
            header.add(createPair("Inward:", inwardField));
            
            linkTypeCombo = new JComboBox<>();
            linkTypeCombo.setPreferredSize(new Dimension(120, 22));
            linkTypeCombo.addActionListener(e -> {
                String selected = (String) linkTypeCombo.getSelectedItem();
                if (selected != null && !selected.isEmpty()) {
                    linkTypeField.setText(selected);
                }
            });
            linkTypeField = new JTextField(ls.getLinkType(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(linkTypeField);
            header.add(createPair("Type:", linkTypeField));
            header.add(linkTypeCombo);
            
            outwardField = new JTextField(ls.getOutwardIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(outwardField);
            header.add(createPair("Outward:", outwardField));
        }

        if (step instanceof AssetStep) {
            AssetStep as = (AssetStep) step;
            sourceTokenField = new JTextField(as.getSourceIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(sourceTokenField);
            header.add(createPair("From:", sourceTokenField));
            
            targetTokenField = new JTextField(as.getTargetIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(targetTokenField);
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
            tso.usmc.jira.util.JiraUtils.setupExpandedView(subTaskFieldsComp);
            header.add(createPair("Fields to Asset (CSV):", subTaskFieldsComp));
        }

        if (step instanceof WorklogStep) {
            WorklogStep ws = (WorklogStep) step;
            targetIssueField = new JTextField(ws.getTargetIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(targetIssueField);
            header.add(createPair("Target Issue:", targetIssueField));
            
            timeSpentField = new JTextField(ws.getTimeSpent(), 8);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(timeSpentField);
            header.add(createPair("Time Spent:", timeSpentField));
            
            commentField = new JTextField(ws.getComment(), 15);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(commentField);
            header.add(createPair("Comment:", commentField));
            
            startedField = new JTextField(ws.getStarted(), 12);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(startedField);
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

        // Content Wrapper (to keep fields and footer packed at the top)
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // Fields Container
        fieldsContainer = new JPanel();
        fieldsContainer.setLayout(new BoxLayout(fieldsContainer, BoxLayout.Y_AXIS));
        fieldsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (FieldAction action : step.getFieldActions().values()) {
            addField(action);
        }
        contentPanel.add(fieldsContainer);

        // Footer (Add Field) - Only for steps that support fields
        if (step.getType() != WorkflowStep.StepType.ASSET && step.getType() != WorkflowStep.StepType.LINK && step.getType() != WorkflowStep.StepType.WORKLOG) {
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
            footer.setAlignmentX(Component.LEFT_ALIGNMENT);
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
            contentPanel.add(footer);
        }

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.add(contentPanel, BorderLayout.NORTH);
        add(centerWrapper, BorderLayout.CENTER);
    }

    public void addField(FieldAction action) {
        FieldActionPanel panel = new FieldActionPanel(action, fieldOptions, new FieldActionPanel.FieldActionListener() {
            @Override
            public void onMoveUp(FieldActionPanel p) {
                int idx = actionPanels.indexOf(p);
                if (idx > 0) {
                    actionPanels.remove(idx);
                    actionPanels.add(idx - 1, p);
                    refreshFieldLayout();
                }
            }

            @Override
            public void onMoveDown(FieldActionPanel p) {
                int idx = actionPanels.indexOf(p);
                if (idx >= 0 && idx < actionPanels.size() - 1) {
                    actionPanels.remove(idx);
                    actionPanels.add(idx + 1, p);
                    refreshFieldLayout();
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

    private JPanel createPair(String label, JComponent comp) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        p.setOpaque(false);
        if (label != null && !label.isEmpty()) {
            p.add(new JLabel(label));
        }
        p.add(comp);
        return p;
    }

    private void refreshFieldLayout() {
        fieldsContainer.removeAll();
        for (FieldActionPanel p : actionPanels) {
            fieldsContainer.add(p);
        }
        fieldsContainer.revalidate();
        fieldsContainer.repaint();
    }

    public void saveToStep() {
        step.setLabel(labelField.getText());
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
            ((LinkStep)step).setInwardIssueToken(inwardField.getText());
            ((LinkStep)step).setLinkType(linkTypeField.getText());
            ((LinkStep)step).setOutwardIssueToken(outwardField.getText());
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

    public void refreshMetadata(Map<String, String> fieldOptions) {
        for (FieldActionPanel panel : actionPanels) {
            panel.refreshMetadata(fieldOptions);
        }
    }

    public void updateLinkTypes(List<String> linkTypes) {
        if (linkTypeCombo != null) {
            String current = (String) linkTypeCombo.getSelectedItem();
            linkTypeCombo.removeAllItems();
            linkTypeCombo.addItem(""); // Default empty
            for (String lt : linkTypes) {
                linkTypeCombo.addItem(lt);
            }
            if (current != null) linkTypeCombo.setSelectedItem(current);
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
