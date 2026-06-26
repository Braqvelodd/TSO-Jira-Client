package tso.usmc.jira.ui;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Popup;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import tso.usmc.jira.service.JqlAutocompleteService;

public class AutocompleteTextField extends AnchorPane {
    private final TextField textField;
    private final Popup popup;
    private final ListView<String> suggestionList;
    private List<String> allSuggestions = new ArrayList<>();
    private JqlAutocompleteService service;
    private String jqlFieldName;
    private boolean enabled = true;

    public void setUserAutocompleteService(JqlAutocompleteService service) {
        this.service = service;
    }

    public void setJqlFieldName(String jqlFieldName) {
        this.jqlFieldName = jqlFieldName;
    }

    public AutocompleteTextField() {
        textField = new TextField();
        AnchorPane.setTopAnchor(textField, 0.0);
        AnchorPane.setBottomAnchor(textField, 0.0);
        AnchorPane.setLeftAnchor(textField, 0.0);
        AnchorPane.setRightAnchor(textField, 0.0);
        
        suggestionList = new ListView<>();
        popup = new Popup();
        popup.setAutoHide(true);
        popup.getContent().add(suggestionList);

        setupListeners();
        getChildren().add(textField);
    }

    public void setSuggestions(List<String> suggestions) {
        this.allSuggestions = suggestions != null ? new ArrayList<>(suggestions) : new ArrayList<>();
    }

    public void setAutocompleteEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getText() {
        return textField.getText();
    }

    public void setText(String text) {
        textField.setText(text);
    }
    
    public TextField getTextField() {
        return textField;
    }

    private void setupListeners() {
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            showSuggestions();
        });

        textField.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                // Focus lost
                popup.hide();
            }
        });

        suggestionList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                selectFromList();
            }
        });

        textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!popup.isShowing()) return;

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
                selectFromList();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            }
        });
    }

    private void showSuggestions() {
        if (!enabled) {
            popup.hide();
            return;
        }
        
        String text = textField.getText();
        if (text == null) text = "";
        
        int lastComma = text.lastIndexOf(',');
        String queryText = (lastComma != -1) ? text.substring(lastComma + 1) : text;
        String queryTrimmed = queryText.trim();
        if (queryTrimmed.startsWith("@")) {
            queryTrimmed = queryTrimmed.substring(1);
        }
        
        if (service != null && allSuggestions.isEmpty()) {
            if (queryTrimmed.length() < 1) {
                popup.hide();
                return;
            }
            
            final String query = queryTrimmed;
            new Thread(() -> {
                try {
                    List<String> matches;
                    if (jqlFieldName != null) {
                        matches = service.getSuggestions(jqlFieldName, query);
                    } else {
                        matches = service.getUserSuggestions(query);
                    }
                    Platform.runLater(() -> {
                        String curText = textField.getText();
                        if (curText == null) curText = "";
                        int curLastComma = curText.lastIndexOf(',');
                        String curQuery = (curLastComma != -1) ? curText.substring(curLastComma + 1) : curText;
                        if (!query.equals(curQuery.trim().replace("@", ""))) {
                            return;
                        }
                        if (matches.isEmpty()) {
                            popup.hide();
                        } else {
                            suggestionList.getItems().clear();
                            matches.stream().distinct().limit(20).forEach(suggestionList.getItems()::add);
                            suggestionList.getSelectionModel().select(0);
                            
                            Bounds bounds = textField.localToScreen(textField.getBoundsInLocal());
                            if (bounds != null) {
                                popup.show(textField, bounds.getMinX(), bounds.getMaxY());
                                
                                suggestionList.setPrefWidth(bounds.getWidth() > 0 ? bounds.getWidth() : 200);
                                suggestionList.setPrefHeight(Math.min(200, suggestionList.getItems().size() * 24 + 10));
                            }
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(popup::hide);
                }
            }).start();
            return;
        }
        
        if (allSuggestions.isEmpty()) {
            popup.hide();
            return;
        }
        
        String textLower = queryTrimmed.toLowerCase();
        suggestionList.getItems().clear();

        List<String> filtered = allSuggestions.stream()
                .filter(s -> s.toLowerCase().contains(textLower))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            popup.hide();
            return;
        }

        suggestionList.getItems().addAll(filtered);
        
        // Show popup below the text field
        Bounds bounds = textField.localToScreen(textField.getBoundsInLocal());
        if (bounds != null) {
            popup.show(textField, bounds.getMinX(), bounds.getMaxY());
            
            // Set size of suggestionList
            suggestionList.setPrefWidth(bounds.getWidth() > 0 ? bounds.getWidth() : 200);
            suggestionList.setPrefHeight(Math.min(200, filtered.size() * 24 + 10));
        }
    }

    private void selectFromList() {
        String selected = suggestionList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Platform.runLater(() -> {
                String text = textField.getText();
                if (text == null) text = "";
                int lastComma = text.lastIndexOf(',');
                if (lastComma != -1) {
                    String prefix = text.substring(0, lastComma + 1);
                    String trailingSpace = "";
                    if (lastComma + 1 < text.length() && Character.isWhitespace(text.charAt(lastComma + 1))) {
                        trailingSpace = " ";
                    }
                    textField.setText(prefix + trailingSpace + selected);
                } else {
                    textField.setText(selected);
                }
                textField.positionCaret(textField.getText().length());
                popup.hide();
            });
        }
    }
}
