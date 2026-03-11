package tso.usmc.jira.ui;

import tso.usmc.jira.service.JqlAutocompleteService;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JqlAutocompleteTextArea extends JTextArea {
    private final JqlAutocompleteService service;
    private final JPopupMenu popup;
    private final DefaultListModel<String> listModel;
    private final JList<String> suggestionList;
    private boolean isUpdating = false;

    public JqlAutocompleteTextArea(JqlAutocompleteService service) {
        this.service = service;
        this.listModel = new DefaultListModel<>();
        this.suggestionList = new JList<>(listModel);
        this.popup = new JPopupMenu();
        
        setupUI();
        setupListeners();
    }

    private void setupUI() {
        JScrollPane scroll = new JScrollPane(suggestionList);
        scroll.setPreferredSize(new Dimension(250, 150));
        popup.add(scroll);
        popup.setFocusable(false);
        
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) insertSelectedSuggestion();
            }
        });
    }

    private void setupListeners() {
        getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updatePopup(); }
            @Override public void removeUpdate(DocumentEvent e) { updatePopup(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePopup(); }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (popup.isVisible()) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_DOWN:
                            int nextIdx = suggestionList.getSelectedIndex() + 1;
                            if (nextIdx < listModel.size()) suggestionList.setSelectedIndex(nextIdx);
                            e.consume();
                            break;
                        case KeyEvent.VK_UP:
                            int prevIdx = suggestionList.getSelectedIndex() - 1;
                            if (prevIdx >= 0) suggestionList.setSelectedIndex(prevIdx);
                            e.consume();
                            break;
                        case KeyEvent.VK_ENTER:
                        case KeyEvent.VK_TAB:
                            insertSelectedSuggestion();
                            e.consume();
                            break;
                        case KeyEvent.VK_ESCAPE:
                            popup.setVisible(false);
                            e.consume();
                            break;
                    }
                }
            }
        });

        addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) { popup.setVisible(false); }
        });
    }

    private void updatePopup() {
        if (isUpdating) return;
        
        SwingUtilities.invokeLater(() -> {
            try {
                int pos = getCaretPosition();
                String text = getText();
                if (pos == 0) { popup.setVisible(false); return; }
                
                // Identify the "current word" under the caret
                int start = pos;
                while (start > 0 && !isJqlSeparator(text.charAt(start - 1))) {
                    start--;
                }
                String word = text.substring(start, pos);
                
                if (word.isEmpty() || word.trim().isEmpty()) {
                    popup.setVisible(false);
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
                        matches.addAll(service.getSuggestions(fieldName, word));
                    }
                }

                if (matches.isEmpty()) {
                    popup.setVisible(false);
                } else {
                    listModel.clear();
                    matches.stream().distinct().limit(20).forEach(listModel::addElement);
                    suggestionList.setSelectedIndex(0);
                    
                    Rectangle rect = modelToView(start);
                    if (rect != null) {
                        popup.show(this, rect.x, rect.y + rect.height);
                        requestFocusInWindow();
                    }
                }
            } catch (BadLocationException e) {
                popup.setVisible(false);
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
        // Simple regex or split to find the last word before the operator
        String[] parts = context.split("[\\s=(),!<>]+");
        if (parts.length > 0) return parts[parts.length - 1];
        return null;
    }

    private void insertSelectedSuggestion() {
        String selected = suggestionList.getSelectedValue();
        if (selected == null) return;
        
        try {
            isUpdating = true;
            int pos = getCaretPosition();
            String text = getText();
            
            int start = pos;
            while (start > 0 && !isJqlSeparator(text.charAt(start - 1))) {
                start--;
            }
            
            // If the suggestion has spaces and isn't a function, wrap in quotes
            if (selected.contains(" ") && !selected.endsWith("()")) {
                selected = "\"" + selected + "\"";
            }

            replaceRange(selected, start, pos);
            setCaretPosition(start + selected.length());
            popup.setVisible(false);
        } finally {
            isUpdating = false;
        }
    }
}
