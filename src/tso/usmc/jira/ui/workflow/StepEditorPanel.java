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
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StepEditorPanel extends JPanel {
    private final WorkflowStep step;
    private final JTextField labelField;
    private final JPanel fieldsContainer;
    private final List<FieldActionPanel> actionPanels = new ArrayList<>();
    private final Map<String, String> fieldOptions; // Label -> ID mapping

    // Specific fields for specialized steps
    private JTextField projField;
    private JTextField typeField;
    private JTextField inwardField;
    private JTextField linkTypeField;
    private JTextField outwardField;

    public StepEditorPanel(WorkflowStep step, Map<String, String> fieldOptions, Runnable onRemove) {
        this.step = step;
        this.fieldOptions = fieldOptions;
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
        header.add(labelField);
        
        if (step instanceof TransitionStep) {
            header.add(new JLabel("Target Status:"));
            JTextField targetField = new JTextField(((TransitionStep)step).getTargetStatus(), 15);
            targetField.getDocument().addDocumentListener(new SimpleDocumentListener(() -> ((TransitionStep)step).setTargetStatus(targetField.getText())));
            header.add(targetField);
        }

        if (step instanceof CreateStep) {
            CreateStep cs = (CreateStep) step;
            header.add(new JLabel("Project:"));
            projField = new JTextField(cs.getProjectKey(), 5);
            header.add(projField);
            header.add(new JLabel("Type:"));
            typeField = new JTextField(cs.getIssueType(), 10);
            header.add(typeField);
        }

        if (step instanceof LinkStep) {
            LinkStep ls = (LinkStep) step;
            header.add(new JLabel("Inward:"));
            inwardField = new JTextField(ls.getInwardIssueToken(), 10);
            header.add(inwardField);
            header.add(new JLabel("Type:"));
            linkTypeField = new JTextField(ls.getLinkType(), 10);
            header.add(linkTypeField);
            header.add(new JLabel("Outward:"));
            outwardField = new JTextField(ls.getOutwardIssueToken(), 10);
            header.add(outwardField);
        }

        if (step instanceof CloneStep) {
            CloneStep cs = (CloneStep) step;
            JCheckBox att = new JCheckBox("Attachments", cs.isCopyAttachments());
            att.addActionListener(e -> cs.setCopyAttachments(att.isSelected()));
            JCheckBox links = new JCheckBox("Links", cs.isCopyLinks());
            links.addActionListener(e -> cs.setCopyLinks(links.isSelected()));
            header.add(att);
            header.add(links);
        }

        JButton removeBtn = new JButton("X");
        removeBtn.setForeground(Color.RED);
        removeBtn.addActionListener(e -> onRemove.run());
        header.add(removeBtn);
        
        add(header, BorderLayout.NORTH);

        // Fields Container
        fieldsContainer = new JPanel();
        fieldsContainer.setLayout(new BoxLayout(fieldsContainer, BoxLayout.Y_AXIS));
        for (FieldAction action : step.getFieldActions().values()) {
            addField(action);
        }
        add(fieldsContainer, BorderLayout.CENTER);

        // Footer (Add Field) - Only for steps that support fields
        if (step.getType() != WorkflowStep.StepType.CLONE && step.getType() != WorkflowStep.StepType.LINK) {
            JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton addFieldBtn = new JButton("+ Add Field");
            addFieldBtn.addActionListener(e -> addField(new FieldAction("", FieldAction.MappingMode.STATIC, "", "")));
            footer.add(addFieldBtn);
            add(footer, BorderLayout.SOUTH);
        }
    }

    private void addField(FieldAction action) {
        FieldActionPanel panel = new FieldActionPanel(action, fieldOptions);
        actionPanels.add(panel);
        fieldsContainer.add(panel);
        fieldsContainer.revalidate();
        fieldsContainer.repaint();
    }

    public void saveToStep() {
        step.setLabel(labelField.getText());
        if (step instanceof CreateStep) {
            ((CreateStep)step).setProjectKey(projField.getText());
            ((CreateStep)step).setIssueType(typeField.getText());
        }
        if (step instanceof LinkStep) {
            ((LinkStep)step).setInwardIssueToken(inwardField.getText());
            ((LinkStep)step).setLinkType(linkTypeField.getText());
            ((LinkStep)step).setOutwardIssueToken(outwardField.getText());
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
