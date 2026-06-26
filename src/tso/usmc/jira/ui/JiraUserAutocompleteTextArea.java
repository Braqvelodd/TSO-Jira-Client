package tso.usmc.jira.ui;

import tso.usmc.jira.service.JqlAutocompleteService;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import java.util.List;

public class JiraUserAutocompleteTextArea extends TextArea {
    private JqlAutocompleteService service;
    private final Popup popup;
    private final ListView<String> suggestionList;
    private boolean isUpdating = false;
    private boolean enabled = true;

    public JiraUserAutocompleteTextArea(JqlAutocompleteService service) {
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
            try {
                int pos = getCaretPosition();
                String text = getText();
                if (pos == 0) { popup.hide(); return; }
                
                // Identify the current line and prefix
                int lineStart = pos;
                while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
                    lineStart--;
                }
                String line = text.substring(lineStart, pos);
                
                int colonIndex = line.indexOf(':');
                if (colonIndex == -1) {
                    popup.hide();
                    return;
                }
                
                String prefix = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1);
                
                boolean isUserField = prefix.equals("assignee") || prefix.equals("default_assignee") || prefix.equals("notify");
                boolean isComponentField = prefix.equals("component") || prefix.equals("default_component");
                boolean isTransitionField = prefix.equals("transition") || prefix.equals("default_transition");
                boolean isIssueTypeField = prefix.equals("issue-type") || prefix.equals("default_type");
                
                if (!isUserField && !isComponentField && !isTransitionField && !isIssueTypeField) {
                    popup.hide();
                    return;
                }
                
                String userInput;
                int lastComma = value.lastIndexOf(',');
                if (lastComma != -1) {
                    userInput = value.substring(lastComma + 1).trim();
                } else {
                    userInput = value.trim();
                }
                
                // Strip '@' if typed
                if (userInput.startsWith("@")) {
                    userInput = userInput.substring(1);
                }
                
                if (userInput.length() < 1) {
                    popup.hide();
                    return;
                }
                
                List<String> matches = new java.util.ArrayList<>();
                if (isUserField) {
                    matches = service.getUserSuggestions(userInput, prefix.equals("notify"));
                } else {
                    String fieldName = null;
                    if (isComponentField) fieldName = "component";
                    else if (isTransitionField) fieldName = "status";
                    else if (isIssueTypeField) fieldName = "issuetype";
                    
                    if (fieldName != null) {
                        service.fetchDataIfNeeded();
                        matches = service.getSuggestions(fieldName, userInput);
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

    private void insertSelectedSuggestion() {
        String selected = suggestionList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        
        try {
            isUpdating = true;
            int pos = getCaretPosition();
            String text = getText();
            
            int lineStart = pos;
            while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
                lineStart--;
            }
            String line = text.substring(lineStart, pos);
            
            int colonIndex = line.indexOf(':');
            if (colonIndex != -1) {
                int lastComma = line.lastIndexOf(',');
                int replaceStartInLine;
                if (lastComma > colonIndex) {
                    replaceStartInLine = lastComma + 1;
                } else {
                    replaceStartInLine = colonIndex + 1;
                }
                
                while (replaceStartInLine < line.length() && Character.isWhitespace(line.charAt(replaceStartInLine))) {
                    replaceStartInLine++;
                }
                
                // If they typed '@', skip it
                if (replaceStartInLine < line.length() && line.charAt(replaceStartInLine) == '@') {
                    replaceStartInLine++;
                }
                
                int replaceStart = lineStart + replaceStartInLine;
                replaceText(replaceStart, pos, selected);
                positionCaret(replaceStart + selected.length());
            }
            popup.hide();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            isUpdating = false;
        }
    }
}
