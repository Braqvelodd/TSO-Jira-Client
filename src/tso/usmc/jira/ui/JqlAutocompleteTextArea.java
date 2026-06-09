package tso.usmc.jira.ui;

import tso.usmc.jira.service.JqlAutocompleteService;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.stage.Popup;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JqlAutocompleteTextArea extends TextArea {
    private JqlAutocompleteService service;
    private final Popup popup;
    private final ListView<String> suggestionList;
    private boolean isUpdating = false;
    private boolean enabled = true;

    public JqlAutocompleteTextArea(JqlAutocompleteService service) {
        this.service = service;
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

        setOnKeyPressed(e -> {
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
        if (!enabled || isUpdating) return;
        if (service == null) {
            System.out.println("DEBUG: Autocomplete service is null, skipping popup.");
            return;
        }
        
        Platform.runLater(() -> {
            try {
                int pos = getCaretPosition();
                String text = getText();
                if (pos == 0) { popup.hide(); return; }
                
                // Identify the "current word" under the caret
                int start = pos;
                while (start > 0 && !isJqlSeparator(text.charAt(start - 1))) {
                    start--;
                }
                String word = text.substring(start, pos);
                
                if (word.isEmpty() || word.trim().isEmpty()) {
                    popup.hide();
                    return;
                }

                // Search metadata
                service.fetchDataIfNeeded();
                List<String> matches = filter(service.getFieldNames(), word);
                matches.addAll(filter(service.getFunctionNames(), word));
                matches.addAll(filter(service.getReservedWords(), word));
                
                // If it's likely a value (after = or IN), fetch suggestions
                String context = text.substring(0, start).trim();
                if (context.endsWith("=") || context.toLowerCase().endsWith(" in") || context.toLowerCase().endsWith(" is")) {
                    String fieldName = getPreviousField(context);
                    if (fieldName != null) {
                        List<String> suggestions = service.getSuggestions(fieldName, word);
                        matches.addAll(suggestions);
                    }
                }

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

    private boolean isJqlSeparator(char c) {
        return Character.isWhitespace(c) || c == '=' || c == '(' || c == ')' || c == ',' || c == '!' || c == '<' || c == '>';
    }

    private List<String> filter(List<String> source, String input) {
        String lowerInput = input.toLowerCase();
        return source.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }

    private String getPreviousField(String context) {
        String[] parts = context.split("[\\s=(),!<>]+");
        if (parts.length > 0) return parts[parts.length - 1];
        return null;
    }

    private void insertSelectedSuggestion() {
        String selected = suggestionList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        try {
            isUpdating = true;
            int pos = getCaretPosition();
            String text = getText();
            
            int start = pos;
            while (start > 0 && !isJqlSeparator(text.charAt(start - 1))) {
                start--;
            }
            
            if (selected.contains(" ") && !selected.endsWith("()")) {
                selected = "\"" + selected + "\"";
            }

            replaceText(start, pos, selected);
            positionCaret(start + selected.length());
            popup.hide();
        } finally {
            isUpdating = false;
        }
    }
}
