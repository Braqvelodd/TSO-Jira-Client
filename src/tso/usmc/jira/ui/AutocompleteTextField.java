package tso.usmc.jira.ui;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
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
    private boolean enabled = true;

    public void setUserAutocompleteService(JqlAutocompleteService service) {
        this.service = service;
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

        textField.setOnKeyPressed(e -> {
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
            } else if (e.getCode() == KeyCode.ENTER) {
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
        
        if (service != null && allSuggestions.isEmpty()) {
            String trimmed = text.trim();
            if (trimmed.startsWith("@")) {
                trimmed = trimmed.substring(1);
            }
            if (trimmed.length() < 1) {
                popup.hide();
                return;
            }
            
            final String query = trimmed;
            new Thread(() -> {
                try {
                    List<String> matches = service.getUserSuggestions(query);
                    Platform.runLater(() -> {
                        if (!query.equals(textField.getText().trim().replace("@", ""))) {
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
        
        String textLower = text.toLowerCase();
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
                textField.setText(selected);
                popup.hide();
            });
        }
    }
}
