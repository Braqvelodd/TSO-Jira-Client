package tso.usmc.jira.ui;

import tso.usmc.jira.service.JqlAutocompleteService;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import java.util.List;

public class JiraUserAutocompleteTextField extends TextField {
    private JqlAutocompleteService service;
    private final Popup popup;
    private final ListView<String> suggestionList;
    private boolean isUpdating = false;
    private boolean enabled = true;

    public JiraUserAutocompleteTextField(int columns) {
        this.suggestionList = new ListView<>();
        this.popup = new Popup();
        
        setupUI();
        setupListeners();
    }

    public void setService(JqlAutocompleteService service) {
        this.service = service;
    }

    public void setAutocompleteEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private void setupUI() {
        popup.setAutoHide(true);
        popup.getContent().add(suggestionList);
        suggestionList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        suggestionList.setPrefWidth(250);
        suggestionList.setPrefHeight(150);
        
        suggestionList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) insertSelectedSuggestion();
        });
    }

    private void setupListeners() {
        textProperty().addListener((observable, oldValue, newValue) -> {
            updatePopup();
        });

        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (popup.isShowing()) {
                if (e.getCode() == KeyCode.DOWN) {
                    int index = suggestionList.getSelectionModel().getSelectedIndex();
                    suggestionList.getSelectionModel().select(Math.min(suggestionList.getItems().size() - 1, index + 1));
                    suggestionList.scrollTo(suggestionList.getSelectionModel().getSelectedIndex());
                    e.consume();
                } else if (e.getCode() == KeyCode.UP) {
                    int index = suggestionList.getSelectionModel().getSelectedIndex();
                    suggestionList.getSelectionModel().select(Math.max(0, index - 1));
                    suggestionList.scrollTo(suggestionList.getSelectionModel().getSelectedIndex());
                    e.consume();
                } else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) {
                    insertSelectedSuggestion();
                    e.consume();
                } else if (e.getCode() == KeyCode.ESCAPE) {
                    popup.hide();
                    e.consume();
                }
            }
        });

        focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) popup.hide();
        });
    }

    private void updatePopup() {
        if (!enabled || isUpdating || service == null) return;
        
        Platform.runLater(() -> {
            String text = getText();
            int pos = getCaretPosition();
            if (pos == 0) { popup.hide(); return; }

            String beforeCaret = text.substring(0, pos);
            String userInput = beforeCaret.trim();
            
            if (userInput.startsWith("@")) {
                userInput = userInput.substring(1);
            }
            
            if (userInput.length() < 1) {
                popup.hide();
                return;
            }

            try {
                List<String> matches = service.getUserSuggestions(userInput);

                if (matches.isEmpty()) {
                    popup.hide();
                } else {
                    suggestionList.getItems().clear();
                    matches.stream().distinct().limit(20).forEach(suggestionList.getItems()::add);
                    suggestionList.getSelectionModel().select(0);
                    
                    Bounds bounds = localToScreen(getBoundsInLocal());
                    if (bounds != null) {
                        popup.show(this, bounds.getMinX(), bounds.getMaxY());
                    }
                }
            } catch (Exception e) {
                popup.hide();
            }
        });
    }

    private void insertSelectedSuggestion() {
        String selected = suggestionList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        try {
            isUpdating = true;
            String text = getText();
            int pos = getCaretPosition();
            
            String newText = selected + text.substring(pos);
            setText(newText);
            positionCaret(selected.length());
            popup.hide();
        } finally {
            isUpdating = false;
        }
    }
}
