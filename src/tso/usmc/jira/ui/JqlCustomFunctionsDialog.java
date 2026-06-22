package tso.usmc.jira.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;
import tso.usmc.jira.service.ConfigurableJqlFunction;
import tso.usmc.jira.service.CustomJqlFunction;
import tso.usmc.jira.service.JqlExecutionEngine;
import java.util.ArrayList;
import java.util.List;

/**
 * JavaFX Dialog containing the interactive manager/editor for dynamic, configuration-driven JQL functions.
 */
public class JqlCustomFunctionsDialog extends Dialog<Void> {
    private final JqlExecutionEngine engine;
    private final ListView<String> listView = new ListView<>();
    private final ObservableList<String> listItems = FXCollections.observableArrayList();

    private final TextField nameField = new TextField();
    private final TextField fieldsField = new TextField();
    private final TextArea pathsArea = new TextArea();
    private final TextField templateField = new TextField();

    private final Button newBtn = new Button("New");
    private final Button saveBtn = new Button("Save");
    private final Button deleteBtn = new Button("Delete");

    private ConfigurableJqlFunction selectedFunction = null;

    public JqlCustomFunctionsDialog(Window owner, JqlExecutionEngine engine) {
        this.engine = engine;

        setTitle("Manage Custom JQL Functions");
        initOwner(owner);

        ButtonType closeButtonType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().add(closeButtonType);

        // UI Layout splits list on the left and input form on the right
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.35);

        VBox leftPane = new VBox(8);
        leftPane.setPadding(new Insets(10));
        listView.setItems(listItems);
        VBox.setVgrow(listView, Priority.ALWAYS);
        leftPane.getChildren().addAll(new Label("Custom Functions:"), listView);

        VBox rightPane = new VBox(10);
        rightPane.setPadding(new Insets(10));
        
        Label settingsLabel = new Label("Function Settings:");
        settingsLabel.setStyle("-fx-font-weight: bold;");

        VBox nameBox = new VBox(4);
        nameField.setPromptText("e.g., childrenOf");
        nameField.setMaxWidth(Double.MAX_VALUE);
        nameBox.getChildren().addAll(new Label("Function Name:"), nameField);

        VBox fieldsBox = new VBox(4);
        fieldsField.setPromptText("e.g., subtasks, parent (comma-separated)");
        fieldsField.setMaxWidth(Double.MAX_VALUE);
        fieldsBox.getChildren().addAll(new Label("First-Pass Fields:"), fieldsField);

        VBox pathsBox = new VBox(4);
        pathsArea.setPromptText("e.g., issues[*].fields.subtasks[*].key\n(One path per line)");
        pathsArea.setPrefRowCount(4);
        pathsArea.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(pathsArea, Priority.ALWAYS);
        pathsBox.getChildren().addAll(new Label("JSON Extraction Paths:"), pathsArea);
        VBox.setVgrow(pathsBox, Priority.ALWAYS);

        VBox templateBox = new VBox(4);
        templateField.setPromptText("e.g., key in ({{KEYS}})");
        templateField.setMaxWidth(Double.MAX_VALUE);
        templateBox.getChildren().addAll(new Label("JQL Output Template:"), templateField);

        HBox buttonBox = new HBox(8);
        buttonBox.setPadding(new Insets(5, 0, 0, 0));
        buttonBox.getChildren().addAll(newBtn, saveBtn, deleteBtn);

        rightPane.getChildren().addAll(settingsLabel, nameBox, fieldsBox, pathsBox, templateBox, buttonBox);
        VBox.setVgrow(rightPane, Priority.ALWAYS);

        splitPane.getItems().addAll(leftPane, rightPane);

        getDialogPane().setContent(splitPane);
        getDialogPane().setPrefWidth(720);
        getDialogPane().setPrefHeight(500);
        setResizable(true);

        // Wire selections & buttons
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadFunctionDetails(newVal);
            }
        });

        newBtn.setOnAction(e -> clearForm());
        saveBtn.setOnAction(e -> saveCurrentFunction());
        deleteBtn.setOnAction(e -> deleteCurrentFunction());

        refreshList();
    }

    private void refreshList() {
        listItems.clear();
        for (CustomJqlFunction func : engine.getRegisteredFunctions()) {
            if (func instanceof ConfigurableJqlFunction) {
                listItems.add(func.getFunctionName());
            }
        }
        clearForm();
    }

    private void loadFunctionDetails(String name) {
        for (CustomJqlFunction func : engine.getRegisteredFunctions()) {
            if (func instanceof ConfigurableJqlFunction && func.getFunctionName().equalsIgnoreCase(name)) {
                selectedFunction = (ConfigurableJqlFunction) func;
                nameField.setText(selectedFunction.getFunctionName());
                fieldsField.setText(String.join(", ", selectedFunction.getFirstPassFields()));
                pathsArea.setText(String.join("\n", selectedFunction.getJsonPaths()));
                templateField.setText(selectedFunction.getOutputTemplate());
                deleteBtn.setDisable(false);
                return;
            }
        }
    }

    private void clearForm() {
        selectedFunction = null;
        nameField.clear();
        fieldsField.clear();
        pathsArea.clear();
        templateField.setText("key in ({{KEYS}})");
        listView.getSelectionModel().clearSelection();
        deleteBtn.setDisable(true);
    }

    private void saveCurrentFunction() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showAlert("Input Error", "Function name cannot be empty.");
            return;
        }

        if (!name.matches("^[a-zA-Z0-9_]+$")) {
            showAlert("Input Error", "Function name must be alphanumeric and start with a letter.");
            return;
        }

        String fieldsText = fieldsField.getText().trim();
        List<String> firstPassFields = new ArrayList<>();
        if (!fieldsText.isEmpty()) {
            for (String f : fieldsText.split("\\s*,\\s*")) {
                if (!f.trim().isEmpty()) {
                    firstPassFields.add(f.trim());
                }
            }
        }

        String pathsText = pathsArea.getText().trim();
        List<String> jsonPaths = new ArrayList<>();
        if (!pathsText.isEmpty()) {
            for (String p : pathsText.split("\\n")) {
                if (!p.trim().isEmpty()) {
                    jsonPaths.add(p.trim());
                }
            }
        }

        if (jsonPaths.isEmpty()) {
            showAlert("Input Error", "Please provide at least one JSON extraction path.");
            return;
        }

        String template = templateField.getText().trim();

        ConfigurableJqlFunction newFunc = new ConfigurableJqlFunction(name, firstPassFields, jsonPaths, template);
        engine.removeFunction(name);
        engine.registerFunction(newFunc);
        engine.saveCustomFunctions();

        refreshList();
        listView.getSelectionModel().select(name);
    }

    private void deleteCurrentFunction() {
        if (selectedFunction == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, 
            "Are you sure you want to delete the custom JQL function '" + selectedFunction.getFunctionName() + "'?", 
            ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Deletion");
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                engine.removeFunction(selectedFunction.getFunctionName());
                engine.saveCustomFunctions();
                refreshList();
            }
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
