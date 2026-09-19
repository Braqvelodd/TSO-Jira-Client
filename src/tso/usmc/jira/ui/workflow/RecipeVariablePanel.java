package tso.usmc.jira.ui.workflow;

import tso.usmc.jira.ui.UiUtils;
import tso.usmc.jira.workflow.RecipeVariable;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class RecipeVariablePanel extends HBox {
    
    public interface RecipeVariableListener {
        void onRemove(RecipeVariablePanel panel);
        void onChange(RecipeVariablePanel panel);
    }

    private final RecipeVariable variable;
    private final TextField nameField;
    private final CheckBox promptCheck;
    private final TextField labelField;
    private final TextField defaultField;
    private final TextField optionsField;

    public RecipeVariablePanel(RecipeVariable variable, RecipeVariableListener listener) {
        this.variable = variable != null ? variable : new RecipeVariable();

        setSpacing(6);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(3, 5, 3, 5));
        setMinWidth(Region.USE_PREF_SIZE);
        setStyle("-fx-border-color: #e0e0e0; -fx-border-width: 0 0 1px 0; -fx-background-color: transparent;");

        // 1. Delete Button
        Button delBtn = new Button("✕");
        delBtn.setMinSize(22, 22);
        delBtn.setMaxSize(22, 22);
        delBtn.getStyleClass().addAll("list-action-btn", "action-btn-delete");
        delBtn.setTooltip(new Tooltip("Remove this variable"));
        delBtn.setOnAction(e -> {
            if (listener != null) listener.onRemove(this);
        });

        // 2. Token / Variable Name
        nameField = new TextField(this.variable.getName());
        nameField.setPromptText("Name (e.g. cycle.date)");
        nameField.setPrefColumnCount(12);
        nameField.setTooltip(new Tooltip("Token placeholder without braces (e.g. 'cycle.date' will be usable as {{cycle.date}})"));
        UiUtils.setupExpandedView(nameField);

        // 3. Prompt at Runtime Checkbox
        promptCheck = new CheckBox("Prompt?");
        promptCheck.setSelected(this.variable.isPrompt());
        promptCheck.setTooltip(new Tooltip("Check to prompt the user for this value when running the workflow. Uncheck for a static constant."));

        // 4. Prompt Label / Question
        labelField = new TextField(this.variable.getLabel());
        labelField.setPromptText("Prompt Question / Label");
        labelField.setPrefColumnCount(14);
        labelField.setTooltip(new Tooltip("Human-readable label shown when prompting the user (e.g. 'Cycle Date (YYYYMMDD)')"));
        UiUtils.setupExpandedView(labelField);

        // 5. Default / Static Value
        defaultField = new TextField(this.variable.getDefaultValue());
        defaultField.setPromptText("Default / Static Value");
        defaultField.setPrefColumnCount(14);
        defaultField.setTooltip(new Tooltip("Default value or static constant. Supports embedded tokens like {{today}} or {{now}}."));
        UiUtils.setupExpandedView(defaultField);

        // 6. Options (comma-separated for dropdown)
        optionsField = new TextField(this.variable.getOptions());
        optionsField.setPromptText("Options (CSV, optional)");
        optionsField.setPrefColumnCount(12);
        optionsField.setTooltip(new Tooltip("Optional comma-separated choices for a dropdown prompt (e.g. 'R1,R2,R3')"));
        UiUtils.setupExpandedView(optionsField);

        // Enable/disable options based on prompt
        optionsField.setDisable(!promptCheck.isSelected());
        labelField.setDisable(!promptCheck.isSelected());
        promptCheck.setOnAction(e -> {
            boolean isPrompt = promptCheck.isSelected();
            optionsField.setDisable(!isPrompt);
            labelField.setDisable(!isPrompt);
            saveToVariable();
            if (listener != null) listener.onChange(this);
        });

        // Notify listener on name change so token autocomplete/palette updates
        nameField.textProperty().addListener((obs, oldV, newV) -> {
            saveToVariable();
            if (listener != null) listener.onChange(this);
        });
        labelField.textProperty().addListener((obs, oldV, newV) -> {
            saveToVariable();
            if (listener != null) listener.onChange(this);
        });
        defaultField.textProperty().addListener((obs, oldV, newV) -> saveToVariable());
        optionsField.textProperty().addListener((obs, oldV, newV) -> saveToVariable());

        Label prefixLabel = new Label("{{");
        prefixLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: gray;");
        Label suffixLabel = new Label("}}");
        suffixLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: gray;");

        HBox tokenBox = new HBox(1, prefixLabel, nameField, suffixLabel);
        tokenBox.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(delBtn, tokenBox, promptCheck, labelField, defaultField, optionsField);
    }

    public RecipeVariable getVariable() {
        return variable;
    }

    public RecipeVariable saveToVariable() {
        variable.setName(nameField.getText());
        variable.setPrompt(promptCheck.isSelected());
        variable.setLabel(labelField.getText());
        variable.setDefaultValue(defaultField.getText());
        variable.setOptions(optionsField.getText());
        return variable;
    }
}
