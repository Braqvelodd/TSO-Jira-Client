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
    private final JTextField promptField;
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
        promptField = new JTextField("Enter prompt question...", 15);
        
        valuePanel.add(staticField, FieldAction.MappingMode.STATIC.toString());
        valuePanel.add(variableField, FieldAction.MappingMode.VARIABLE.toString());
        valuePanel.add(promptField, FieldAction.MappingMode.PROMPT.toString());
        
        add(new JLabel("Field:"));
        add(keyCombo);
        add(new JLabel("Mode:"));
        add(modeCombo);
        add(valuePanel);

        // Init values
        if (action != null) {
            modeCombo.setSelectedItem(action.getMode());
            if (action.getMode() == FieldAction.MappingMode.STATIC) staticField.setText(action.getValue().toString());
            if (action.getMode() == FieldAction.MappingMode.VARIABLE) variableField.setText(action.getValue().toString());
            if (action.getMode() == FieldAction.MappingMode.PROMPT) promptField.setText(action.getPromptLabel());
        }

        modeCombo.addActionListener(e -> {
            FieldAction.MappingMode mode = (FieldAction.MappingMode) modeCombo.getSelectedItem();
            cardLayout.show(valuePanel, mode.toString());
        });
    }

    public FieldAction getFieldAction() {
        FieldAction action = new FieldAction();
        Object selected = keyCombo.getSelectedItem();
        String selectedStr = selected != null ? selected.toString() : "";
        
        // Resolve ID: If the label is in our map, use the mapped ID. 
        // Otherwise, use the raw text (which might already be an ID).
        String fieldId = selectedStr;
        if (currentOptions != null && currentOptions.containsKey(selectedStr)) {
            fieldId = currentOptions.get(selectedStr);
        }
        
        action.setFieldId(fieldId);
        action.setMode((FieldAction.MappingMode) modeCombo.getSelectedItem());
        
        if (action.getMode() == FieldAction.MappingMode.STATIC) action.setValue(staticField.getText());
        if (action.getMode() == FieldAction.MappingMode.VARIABLE) action.setValue(variableField.getText());
        if (action.getMode() == FieldAction.MappingMode.PROMPT) action.setPromptLabel(promptField.getText());
        
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
