package tso.usmc.jira.ui;

import tso.usmc.jira.service.JqlAutocompleteService;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.stream.Collectors;

public class JiraFieldAutocompleteTextField extends JTextField {
    private JqlAutocompleteService service;
    private final JPopupMenu popup;
    private final DefaultListModel<String> listModel;
    private final JList<String> suggestionList;
    private boolean isUpdating = false;
    private boolean enabled = true;

    public JiraFieldAutocompleteTextField(String text) {
        super(text);
        this.listModel = new DefaultListModel<>();
        this.suggestionList = new JList<>(listModel);
        this.popup = new JPopupMenu();
        
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
        if (!enabled || isUpdating || service == null) return;
        
        SwingUtilities.invokeLater(() -> {
            try {
                String text = getText();
                int pos = getCaretPosition();
                
                // Find the start of the current field (after the last comma)
                int start = pos;
                while (start > 0 && text.charAt(start - 1) != ',' && !Character.isWhitespace(text.charAt(start - 1))) {
                    start--;
                }
                
                String userInput = text.substring(start, pos).trim();
                if (userInput.isEmpty()) {
                    popup.setVisible(false);
                    return;
                }

                service.fetchDataIfNeeded();
                List<String> fieldNames = service.getFieldNames();
                String lowerInput = userInput.toLowerCase();
                List<String> matches = fieldNames.stream()
                        .filter(f -> f.toLowerCase().startsWith(lowerInput))
                        .distinct()
                        .limit(20)
                        .collect(Collectors.toList());

                if (matches.isEmpty()) {
                    popup.setVisible(false);
                } else {
                    listModel.clear();
                    matches.forEach(listModel::addElement);
                    suggestionList.setSelectedIndex(0);
                    
                    Rectangle rect = modelToView(start);
                    if (rect != null) {
                        popup.show(this, rect.x, rect.y + rect.height);
                    }
                }
            } catch (BadLocationException e) {
                popup.setVisible(false);
            }
        });
    }

    private void insertSelectedSuggestion() {
        String selected = suggestionList.getSelectedValue();
        if (selected == null) return;
        
        try {
            isUpdating = true;
            String text = getText();
            int pos = getCaretPosition();
            
            int start = pos;
            while (start > 0 && text.charAt(start - 1) != ',' && !Character.isWhitespace(text.charAt(start - 1))) {
                start--;
            }
            
            // Reconstruct the text with the selected suggestion
            String before = text.substring(0, start);
            String after = text.substring(pos);
            
            // Add a trailing comma if it's not already there or at the end
            String replacement = selected;
            
            setText(before + replacement + after);
            setCaretPosition(start + replacement.length());
            
            popup.setVisible(false);
        } finally {
            isUpdating = false;
        }
    }
}
