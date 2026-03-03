package tso.usmc.jira.ui.workflow;

import tso.usmc.jira.workflow.FieldAction;
import javax.swing.*;
import java.awt.*;
import java.util.*;

public class FieldActionPanel extends JPanel {
    private final JComboBox<FieldAction.MappingMode> modeCombo;
    private final JComboBox<String> keyCombo; // Replaced keyField with an editable combo box
    private final JPanel valuePanel;
    private final JTextField staticField;
    private final JTextField variableField;
    private final JTextField promptQuestionField;
    private final JTextField promptOptionsField;
    private final CardLayout cardLayout;
    private Map<String, String> currentOptions;

    public FieldActionPanel(FieldAction action, Map<String, String> fieldOptions) {
        this.currentOptions = fieldOptions;
        setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        Vector<String> options = new Vector<>();
        if (fieldOptions != null) {
            options.addAll(fieldOptions.keySet());
            Collections.sort(options);
        }
        
        keyCombo = new JComboBox<>(options);
        keyCombo.setEditable(true);
        keyCombo.setPreferredSize(new Dimension(150, 25));
        
        if (action != null) {
            keyCombo.setSelectedItem(action.getFieldId());
        }
        
        modeCombo = new JComboBox<>(FieldAction.MappingMode.values());
        
        valuePanel = new JPanel();
        cardLayout = new CardLayout();
        valuePanel.setLayout(cardLayout);
        
        staticField = new JTextField(15);
        variableField = new JTextField("{{issue.key}}", 15);
        
        // Prompt Panel: Question + Optional Options
        JPanel promptPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        promptQuestionField = new JTextField("Question?", 10);
        promptQuestionField.setToolTipText("The label/question for the prompt");
        promptOptionsField = new JTextField("", 10);
        promptOptionsField.setToolTipText("Optional: Comma-separated list of selection values");
        promptPanel.add(promptQuestionField);
        promptPanel.add(new JLabel("Opts:"));
        promptPanel.add(promptOptionsField);

        tso.usmc.jira.util.JiraUtils.setupExpandedView(staticField);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(variableField);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(promptQuestionField);
        tso.usmc.jira.util.JiraUtils.setupExpandedView(promptOptionsField);
        
        valuePanel.add(staticField, FieldAction.MappingMode.STATIC.toString());
        valuePanel.add(variableField, FieldAction.MappingMode.VARIABLE.toString());
        valuePanel.add(promptPanel, FieldAction.MappingMode.PROMPT.toString());
        
        modeCombo.addActionListener(e -> {
            FieldAction.MappingMode mode = (FieldAction.MappingMode) modeCombo.getSelectedItem();
            cardLayout.show(valuePanel, mode.toString());
        });

        add(new JLabel("Field:"));
        add(keyCombo);
        add(new JLabel("Mode:"));
        add(modeCombo);
        add(valuePanel);

        // Init values
        if (action != null) {
            modeCombo.setSelectedItem(action.getMode());
            if (action.getMode() == FieldAction.MappingMode.STATIC && action.getValue() != null) staticField.setText(action.getValue().toString());
            if (action.getMode() == FieldAction.MappingMode.VARIABLE && action.getValue() != null) variableField.setText(action.getValue().toString());
            if (action.getMode() == FieldAction.MappingMode.PROMPT) {
                promptQuestionField.setText(action.getPromptLabel());
                if (action.getValue() != null) promptOptionsField.setText(action.getValue().toString());
            }
        }
    }

    public FieldAction getFieldAction() {
        FieldAction action = new FieldAction();
        Object selected = keyCombo.getSelectedItem();
        String selectedStr = selected != null ? selected.toString() : "";
        
        String fieldId = selectedStr;
        if (currentOptions != null && currentOptions.containsKey(selectedStr)) {
            fieldId = currentOptions.get(selectedStr);
        }
        
        action.setFieldId(fieldId);
        action.setMode((FieldAction.MappingMode) modeCombo.getSelectedItem());
        
        if (action.getMode() == FieldAction.MappingMode.STATIC) action.setValue(staticField.getText());
        if (action.getMode() == FieldAction.MappingMode.VARIABLE) action.setValue(variableField.getText());
        if (action.getMode() == FieldAction.MappingMode.PROMPT) {
            action.setPromptLabel(promptQuestionField.getText());
            action.setValue(promptOptionsField.getText());
        }
        
        return action;
    }

    public void refreshMetadata(Map<String, String> fieldOptions) {
        this.currentOptions = fieldOptions;
        Object current = keyCombo.getSelectedItem();
        String currentStr = current != null ? current.toString() : "";
        
        // Find existing ID
        String currentId = currentStr;
        if (fieldOptions.containsKey(currentStr)) {
            currentId = fieldOptions.get(currentStr);
        }

        Vector<String> options = new Vector<>(fieldOptions.keySet());
        Collections.sort(options);
        
        keyCombo.setModel(new DefaultComboBoxModel<>(options));
        
        // Try to re-select based on ID
        for (String label : fieldOptions.keySet()) {
            if (fieldOptions.get(label).equals(currentId)) {
                keyCombo.setSelectedItem(label);
                return;
            }
        }
        keyCombo.setSelectedItem(currentStr);
    }
}
