package tso.usmc.jira.ui.workflow;

import tso.usmc.jira.workflow.FieldAction;
import tso.usmc.jira.ui.AutocompleteTextField;
import tso.usmc.jira.ui.UiUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

public class FieldActionPanel extends HBox {
    public interface FieldActionListener {
        void onMoveUp(FieldActionPanel panel);
        void onMoveDown(FieldActionPanel panel);
        void onRemove(FieldActionPanel panel);
    }

    private final ComboBox<FieldAction.MappingMode> modeCombo;
    private final ComboBox<String> keyCombo;
    private final StackPane valuePanel;
    private final AutocompleteTextField valueField;
    private final TextField promptQuestionField;
    private final TextField promptOptionsField;
    private final HBox promptPanel;
    private Map<String, String> currentOptions;
    private Map<String, JSONObject> fullMetadata;

    public FieldActionPanel(FieldAction action, Map<String, String> fieldOptions, Map<String, JSONObject> fullMetadata, FieldActionListener listener) {
        this.currentOptions = fieldOptions;
        this.fullMetadata = fullMetadata;

        setSpacing(5);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(2, 5, 2, 5));
        setMinWidth(Region.USE_PREF_SIZE);

        // Rearrangement and Removal Buttons
        HBox controls = new HBox(2);
        controls.setAlignment(Pos.CENTER_LEFT);
        
        Button upBtn = new Button("^");
        Button downBtn = new Button("v");
        Button delBtn = new Button("X");
        
        upBtn.setMinSize(22, 22); upBtn.setMaxSize(22, 22);
        downBtn.setMinSize(22, 22); downBtn.setMaxSize(22, 22);
        delBtn.setMinSize(22, 22); delBtn.setMaxSize(22, 22);
        
        upBtn.getStyleClass().addAll("list-action-btn", "action-btn-up");
        downBtn.getStyleClass().addAll("list-action-btn", "action-btn-down");
        delBtn.getStyleClass().addAll("list-action-btn", "action-btn-delete");
        
        upBtn.setOnAction(e -> listener.onMoveUp(this));
        downBtn.setOnAction(e -> listener.onMoveDown(this));
        delBtn.setOnAction(e -> listener.onRemove(this));
        
        controls.getChildren().addAll(upBtn, downBtn, delBtn);
        getChildren().add(controls);
        
        List<String> options = new ArrayList<>();
        if (fieldOptions != null) {
            options.addAll(fieldOptions.keySet());
            Collections.sort(options);
        }
        
        keyCombo = new ComboBox<>();
        keyCombo.getItems().addAll(options);
        keyCombo.setEditable(true);
        keyCombo.setPrefWidth(200);
        
        if (action != null) {
            keyCombo.getSelectionModel().select(action.getFieldId());
        }
        
        modeCombo = new ComboBox<>();
        modeCombo.getItems().addAll(FieldAction.MappingMode.values());
        
        valuePanel = new StackPane();
        
        valueField = new AutocompleteTextField();
        valueField.setPrefWidth(150);
        
        // Prompt Panel: Question + Optional Options
        promptPanel = new HBox(5);
        promptPanel.setAlignment(Pos.CENTER_LEFT);
        promptQuestionField = new TextField("Question?");
        promptQuestionField.setPrefWidth(100);
        promptQuestionField.setTooltip(new Tooltip("The label/question for the prompt"));
        promptOptionsField = new TextField("");
        promptOptionsField.setPrefWidth(100);
        promptOptionsField.setTooltip(new Tooltip("Optional: Comma-separated list of selection values"));
        promptPanel.getChildren().addAll(promptQuestionField, new Label("Opts:"), promptOptionsField);

        UiUtils.setupExpandedView(valueField.getTextField());
        UiUtils.setupExpandedView(promptQuestionField);
        UiUtils.setupExpandedView(promptOptionsField);
        
        valueField.getTextField().textProperty().addListener((obs, oldVal, newVal) -> validateValue());

        valuePanel.getChildren().addAll(valueField, promptPanel);
        
        modeCombo.setOnAction(e -> {
            FieldAction.MappingMode mode = modeCombo.getSelectionModel().getSelectedItem();
            valueField.setVisible(mode == FieldAction.MappingMode.SET);
            valueField.setManaged(mode == FieldAction.MappingMode.SET);
            promptPanel.setVisible(mode == FieldAction.MappingMode.PROMPT);
            promptPanel.setManaged(mode == FieldAction.MappingMode.PROMPT);
            validateValue();
        });

        keyCombo.setOnAction(e -> updateValueSuggestions());

        getChildren().addAll(
            new Label("Field:"), keyCombo,
            new Label("Mode:"), modeCombo,
            valuePanel
        );

        // Init values
        if (action != null) {
            modeCombo.getSelectionModel().select(action.getMode());
            if (action.getMode() == FieldAction.MappingMode.SET && action.getValue() != null) {
                valueField.setText(action.getValue().toString());
            }
            if (action.getMode() == FieldAction.MappingMode.PROMPT) {
                promptQuestionField.setText(action.getPromptLabel());
                if (action.getValue() != null) promptOptionsField.setText(action.getValue().toString());
            }
            FieldAction.MappingMode mode = action.getMode();
            valueField.setVisible(mode == FieldAction.MappingMode.SET);
            valueField.setManaged(mode == FieldAction.MappingMode.SET);
            promptPanel.setVisible(mode == FieldAction.MappingMode.PROMPT);
            promptPanel.setManaged(mode == FieldAction.MappingMode.PROMPT);
        } else {
            modeCombo.getSelectionModel().select(FieldAction.MappingMode.SET);
            valueField.setVisible(true);
            valueField.setManaged(true);
            promptPanel.setVisible(false);
            promptPanel.setManaged(false);
        }
        updateValueSuggestions();
        validateValue();
    }

    public FieldAction getFieldAction() {
        FieldAction action = new FieldAction();
        String selectedStr = getSelectedFieldIdOrLabel();
        
        String fieldId = selectedStr;
        if (currentOptions != null && currentOptions.containsKey(selectedStr)) {
            fieldId = currentOptions.get(selectedStr);
        }
        
        action.setFieldId(fieldId);
        action.setMode(modeCombo.getSelectionModel().getSelectedItem());
        
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
        String currentStr = getSelectedFieldIdOrLabel();
        
        // Find existing ID
        String currentId = currentStr;
        if (fieldOptions.containsKey(currentStr)) {
            currentId = fieldOptions.get(currentStr);
        }

        List<String> options = new ArrayList<>(fieldOptions.keySet());
        Collections.sort(options);
        
        keyCombo.getItems().setAll(options);
        
        // Try to re-select based on ID
        for (String label : fieldOptions.keySet()) {
            if (fieldOptions.get(label).equals(currentId)) {
                keyCombo.getSelectionModel().select(label);
                updateValueSuggestions();
                validateValue();
                return;
            }
        }
        keyCombo.getSelectionModel().select(currentStr);
        updateValueSuggestions();
        validateValue();
    }

    private void updateValueSuggestions() {
        String fieldId = getSelectedFieldId();
        if (fullMetadata != null && fullMetadata.containsKey(fieldId)) {
            JSONObject meta = fullMetadata.get(fieldId);
            if (meta.has("allowedValues")) {
                JSONArray allowed = meta.getJSONArray("allowedValues");
                List<String> suggestions = new ArrayList<>();
                for (int i = 0; i < allowed.length(); i++) {
                    JSONObject av = allowed.getJSONObject(i);
                    suggestions.add(av.optString("name", av.optString("value", "")));
                }
                valueField.setSuggestions(suggestions);
                return;
            }
        }
        valueField.setSuggestions(Collections.emptyList());
    }
    
    private void validateValue() {
        TextField activeTextField = valueField.getTextField();
        if (modeCombo.getSelectionModel().getSelectedItem() != FieldAction.MappingMode.SET) {
            activeTextField.setStyle("");
            activeTextField.setTooltip(null);
            return;
        }

        String val = valueField.getText().trim();
        if (val.isEmpty() || val.contains("{{")) {
            activeTextField.setStyle("");
            activeTextField.setTooltip(null);
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
                    activeTextField.setStyle("-fx-border-color: red; -fx-border-width: 2px;");
                    activeTextField.setTooltip(new Tooltip("Value not in allowed list for this field."));
                } else {
                    activeTextField.setStyle("");
                    activeTextField.setTooltip(null);
                }
                return;
            }
        }
        activeTextField.setStyle("");
        activeTextField.setTooltip(null);
    }

    private String getSelectedFieldId() {
        String selectedStr = getSelectedFieldIdOrLabel();
        if (currentOptions != null && currentOptions.containsKey(selectedStr)) {
            return currentOptions.get(selectedStr);
        }
        return selectedStr;
    }

    private String getSelectedFieldIdOrLabel() {
        String selected = keyCombo.getSelectionModel().getSelectedItem();
        if (selected == null || selected.trim().isEmpty()) {
            selected = keyCombo.getEditor().getText();
        }
        return selected != null ? selected.trim() : "";
    }
}
