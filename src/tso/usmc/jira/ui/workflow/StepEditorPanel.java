package tso.usmc.jira.ui.workflow;

import tso.usmc.jira.workflow.WorkflowStep;
import tso.usmc.jira.workflow.FieldAction;
import tso.usmc.jira.workflow.TransitionStep;
import tso.usmc.jira.workflow.UpdateStep;
import tso.usmc.jira.workflow.CloneStep;
import tso.usmc.jira.workflow.CreateStep;
import tso.usmc.jira.workflow.LinkStep;

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
    private JTextField parentField;
    private JTextField inwardField;
    private JTextField linkTypeField;
    private JTextField outwardField;
    private JTextField sourceTokenField;
    private JTextField targetTokenField;

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
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.setBackground(new Color(230, 230, 230));

        header.add(new JLabel("[" + step.getType() + "] Label:"));
        labelField = new JTextField(step.getLabel(), 20);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(labelField);
        header.add(labelField);
        
        if (step instanceof UpdateStep) {
            header.add(new JLabel("Target Issue:"));
            targetIssueField = new JTextField(((UpdateStep)step).getTargetIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(targetIssueField);
            header.add(targetIssueField);
        }

        if (step instanceof TransitionStep) {
            TransitionStep ts = (TransitionStep) step;
            header.add(new JLabel("Target Issue:"));
            targetIssueField = new JTextField(ts.getTargetIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(targetIssueField);
            header.add(targetIssueField);
            
            header.add(new JLabel("Target Status:"));
            JTextField targetField = new JTextField(ts.getTargetStatus(), 15);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(targetField);
            targetField.getDocument().addDocumentListener(new SimpleDocumentListener(() -> ts.setTargetStatus(targetField.getText())));
            header.add(targetField);
        }

        if (step instanceof CreateStep) {
            CreateStep cs = (CreateStep) step;
            header.add(new JLabel("Project:"));
            projField = new JTextField(cs.getProjectKey(), 5);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(projField);
            header.add(projField);
            header.add(new JLabel("Type:"));
            typeField = new JTextField(cs.getIssueType(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(typeField);
            header.add(typeField);
            header.add(new JLabel("Parent:"));
            parentField = new JTextField(cs.getParentIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(parentField);
            header.add(parentField);
        }

        if (step instanceof LinkStep) {
            LinkStep ls = (LinkStep) step;
            header.add(new JLabel("Inward:"));
            inwardField = new JTextField(ls.getInwardIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(inwardField);
            header.add(inwardField);
            header.add(new JLabel("Type:"));
            linkTypeField = new JTextField(ls.getLinkType(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(linkTypeField);
            header.add(linkTypeField);
            header.add(new JLabel("Outward:"));
            outwardField = new JTextField(ls.getOutwardIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(outwardField);
            header.add(outwardField);
        }

        if (step instanceof CloneStep) {
            CloneStep cs = (CloneStep) step;
            header.add(new JLabel("From:"));
            sourceTokenField = new JTextField(cs.getSourceIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(sourceTokenField);
            header.add(sourceTokenField);
            
            header.add(new JLabel("To:"));
            targetTokenField = new JTextField(cs.getTargetIssueToken(), 10);
            tso.usmc.jira.util.JiraUtils.setupExpandedView(targetTokenField);
            header.add(targetTokenField);
            
            JCheckBox att = new JCheckBox("Attachments", cs.isCopyAttachments());
            att.addActionListener(e -> cs.setCopyAttachments(att.isSelected()));
            JCheckBox links = new JCheckBox("Links", cs.isCopyLinks());
            links.addActionListener(e -> cs.setCopyLinks(links.isSelected()));
            header.add(att);
            header.add(links);
        }

        // Step Rearrangement Buttons
        JButton stepUpBtn = new JButton("▲");
        JButton stepDownBtn = new JButton("▼");
        Dimension stepBtnDim = new Dimension(22, 22);
        stepUpBtn.setPreferredSize(stepBtnDim);
        stepDownBtn.setPreferredSize(stepBtnDim);
        stepUpBtn.setMargin(new Insets(0, 0, 0, 0));
        stepDownBtn.setMargin(new Insets(0, 0, 0, 0));
        stepUpBtn.addActionListener(e -> stepListener.onMoveUp(this));
        stepDownBtn.addActionListener(e -> stepListener.onMoveDown(this));
        header.add(stepUpBtn);
        header.add(stepDownBtn);

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
        if (step.getType() != WorkflowStep.StepType.CLONE && step.getType() != WorkflowStep.StepType.LINK) {
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
            footer.setAlignmentX(Component.LEFT_ALIGNMENT);
            JButton addFieldBtn = new JButton("+ Add Field");
            addFieldBtn.addActionListener(e -> addField(new FieldAction("", FieldAction.MappingMode.STATIC, "", "")));
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

    private void addField(FieldAction action) {
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
            ((CreateStep)step).setParentIssueToken(parentField.getText());
        }
        if (step instanceof LinkStep) {
            ((LinkStep)step).setInwardIssueToken(inwardField.getText());
            ((LinkStep)step).setLinkType(linkTypeField.getText());
            ((LinkStep)step).setOutwardIssueToken(outwardField.getText());
        }
        if (step instanceof CloneStep) {
            ((CloneStep)step).setSourceIssueToken(sourceTokenField.getText());
            ((CloneStep)step).setTargetIssueToken(targetTokenField.getText());
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

    private static class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable onChange;
        SimpleDocumentListener(Runnable onChange) { this.onChange = onChange; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
    }
}
