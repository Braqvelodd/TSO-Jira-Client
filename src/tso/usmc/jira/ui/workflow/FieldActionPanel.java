package tso.usmc.jira.ui.workflow;

import tso.usmc.jira.workflow.FieldAction;
import tso.usmc.jira.ui.SwingUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class FieldActionPanel extends JPanel {
    public interface FieldActionListener {
        void onMoveUp(FieldActionPanel panel);
        void onMoveDown(FieldActionPanel panel);
        void onRemove(FieldActionPanel panel);
    }

    private final JComboBox<FieldAction.MappingMode> modeCombo;
    private final JComboBox<String> keyCombo;
    private final JPanel valuePanel;
    private final JTextField valueField;
    private final JTextField promptQuestionField;
    private final JTextField promptOptionsField;
    private final CardLayout cardLayout;
    private Map<String, String> currentOptions;
    private Map<String, JSONObject> fullMetadata;

    public FieldActionPanel(FieldAction action, Map<String, String> fieldOptions, Map<String, JSONObject> fullMetadata, FieldActionListener listener) {
        this.currentOptions = fieldOptions;
        this.fullMetadata = fullMetadata;
        setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));

        // Rearrangement and Removal Buttons
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 1, 0));
        JButton upBtn = new JButton("▲");
        JButton downBtn = new JButton("▼");
        JButton delBtn = new JButton("X");
        
        Dimension btnDim = new Dimension(22, 22);
        upBtn.setPreferredSize(btnDim);
        downBtn.setPreferredSize(btnDim);
        delBtn.setPreferredSize(btnDim);
        
        upBtn.setMargin(new Insets(0, 0, 0, 0));
        downBtn.setMargin(new Insets(0, 0, 0, 0));
        delBtn.setMargin(new Insets(0, 0, 0, 0));
        delBtn.setForeground(Color.RED);
        
        upBtn.addActionListener(e -> listener.onMoveUp(this));
        downBtn.addActionListener(e -> listener.onMoveDown(this));
        delBtn.addActionListener(e -> listener.onRemove(this));
        
        controls.add(upBtn);
        controls.add(downBtn);
        controls.add(delBtn);
        add(controls);
        
        Vector<String> options = new Vector<>();
        if (fieldOptions != null) {
            options.addAll(fieldOptions.keySet());
            Collections.sort(options);
        }
        
        keyCombo = new JComboBox<>(options);
        keyCombo.setEditable(true);
        keyCombo.setPreferredSize(new Dimension(200, 25));
        
        if (action != null) {
            keyCombo.setSelectedItem(action.getFieldId());
        }
        
        modeCombo = new JComboBox<>(FieldAction.MappingMode.values());
        
        valuePanel = new JPanel();
        cardLayout = new CardLayout();
        valuePanel.setLayout(cardLayout);
        
        valueField = new JTextField(15);
        
        // Prompt Panel: Question + Optional Options
        JPanel promptPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        promptQuestionField = new JTextField("Question?", 10);
        promptQuestionField.setToolTipText("The label/question for the prompt");
        promptOptionsField = new JTextField("", 10);
        promptOptionsField.setToolTipText("Optional: Comma-separated list of selection values");
        promptPanel.add(promptQuestionField);
        promptPanel.add(new JLabel("Opts:"));
        promptPanel.add(promptOptionsField);

        SwingUtils.setupExpandedView(valueField);
        SwingUtils.setupExpandedView(promptQuestionField);
        SwingUtils.setupExpandedView(promptOptionsField);
        
        valueField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { validateValue(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { validateValue(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { validateValue(); }
        });

        valuePanel.add(valueField, FieldAction.MappingMode.SET.toString());
        valuePanel.add(promptPanel, FieldAction.MappingMode.PROMPT.toString());
        
        modeCombo.addActionListener(e -> {
            FieldAction.MappingMode mode = (FieldAction.MappingMode) modeCombo.getSelectedItem();
            cardLayout.show(valuePanel, mode.toString());
            validateValue();
        });

        add(new JLabel("Field:"));
        add(keyCombo);
        add(new JLabel("Mode:"));
        add(modeCombo);
        add(valuePanel);

        // Init values
        if (action != null) {
            modeCombo.setSelectedItem(action.getMode());
            if (action.getMode() == FieldAction.MappingMode.SET && action.getValue() != null) {
                valueField.setText(action.getValue().toString());
            }
            if (action.getMode() == FieldAction.MappingMode.PROMPT) {
                promptQuestionField.setText(action.getPromptLabel());
                if (action.getValue() != null) promptOptionsField.setText(action.getValue().toString());
            }
            cardLayout.show(valuePanel, action.getMode().toString());
        }
        validateValue();
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
        
        if (action.getMode() == FieldAction.MappingMode.SET) {
            action.setValue(valueField.getText());
        } else if (action.getMode() == FieldAction.MappingMode.PROMPT) {
            action.setPromptLabel(promptQuestionField.getText());
            action.setValue(promptOptionsField.getText());
        }
        
        return action;
    }

    public void refreshMetadata(Map<String, String> fieldOptions, Map<String, JSONObject> fullMetadata) {
        this.currentOptions = fieldOptions;
        this.fullMetadata = fullMetadata;
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
                validateValue();
                return;
            }
        }
        keyCombo.setSelectedItem(currentStr);
        validateValue();
    }

    private void validateValue() {
        if (modeCombo.getSelectedItem() != FieldAction.MappingMode.SET) {
            valueField.setBorder(UIManager.getLookAndFeelDefaults().getBorder("TextField.border"));
            return;
        }

        String val = valueField.getText().trim();
        if (val.isEmpty() || val.contains("{{")) {
            valueField.setBorder(UIManager.getLookAndFeelDefaults().getBorder("TextField.border"));
            return;
        }

        String fieldId = getSelectedFieldId();
        if (fullMetadata != null && fullMetadata.containsKey(fieldId)) {
            JSONObject meta = fullMetadata.get(fieldId);
            if (meta.has("allowedValues")) {
                JSONArray allowed = meta.getJSONArray("allowedValues");
                boolean found = false;
                
                // For arrays, split by comma
                boolean isArray = false;
                if (meta.has("schema")) isArray = "array".equals(meta.getJSONObject("schema").optString("type"));
                
                String[] parts = isArray ? val.split(",") : new String[]{val};
                
                for (String part : parts) {
                    String p = part.trim();
                    boolean partFound = false;
                    for (int i = 0; i < allowed.length(); i++) {
                        JSONObject av = allowed.getJSONObject(i);
                        String name = av.optString("name", av.optString("value", ""));
                        String id = av.optString("id", "");
                        if (p.equalsIgnoreCase(name) || p.equalsIgnoreCase(id)) {
                            partFound = true;
                            break;
                        }
                    }
                    if (!partFound) {
                        found = false;
                        break;
                    }
                    found = true;
                }

                if (!found) {
                    valueField.setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                    valueField.setToolTipText("Value not in allowed list for this field.");
                } else {
                    valueField.setBorder(UIManager.getLookAndFeelDefaults().getBorder("TextField.border"));
                    valueField.setToolTipText(null);
                }
                return;
            }
        }
        valueField.setBorder(UIManager.getLookAndFeelDefaults().getBorder("TextField.border"));
        valueField.setToolTipText(null);
    }

    private String getSelectedFieldId() {
        Object selected = keyCombo.getSelectedItem();
        String selectedStr = selected != null ? selected.toString() : "";
        if (currentOptions != null && currentOptions.containsKey(selectedStr)) {
            return currentOptions.get(selectedStr);
        }
        return selectedStr;
    }
}
