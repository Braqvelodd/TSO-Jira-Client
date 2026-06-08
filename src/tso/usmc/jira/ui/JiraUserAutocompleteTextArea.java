package tso.usmc.jira.ui;

import tso.usmc.jira.service.JqlAutocompleteService;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class JiraUserAutocompleteTextArea extends JTextArea {
    private JqlAutocompleteService service;
    private final JPopupMenu popup;
    private final DefaultListModel<String> listModel;
    private final JList<String> suggestionList;
    private boolean isUpdating = false;
    private boolean enabled = true;

    public JiraUserAutocompleteTextArea(JqlAutocompleteService service) {
        this.service = service;
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
                int pos = getCaretPosition();
                String text = getText();
                if (pos == 0) { popup.setVisible(false); return; }
                
                // Identify the current line and prefix
                int lineStart = getLineStartOffset(getLineOfOffset(pos));
                String line = text.substring(lineStart, pos);
                
                int colonIndex = line.indexOf(':');
                if (colonIndex == -1) {
                    popup.setVisible(false);
                    return;
                }
                
                String prefix = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1);
                
                boolean isUserField = prefix.equals("assignee") || prefix.equals("default_assignee") || prefix.equals("notify");
                boolean isComponentField = prefix.equals("component") || prefix.equals("default_component");
                boolean isTransitionField = prefix.equals("transition") || prefix.equals("default_transition");
                boolean isIssueTypeField = prefix.equals("issue-type") || prefix.equals("default_type");
                
                if (!isUserField && !isComponentField && !isTransitionField && !isIssueTypeField) {
                    popup.setVisible(false);
                    return;
                }
                
                String userInput;
                int lastComma = value.lastIndexOf(',');
                if (lastComma != -1) {
                    userInput = value.substring(lastComma + 1).trim();
                } else {
                    userInput = value.trim();
                }
                
                // Also support and strip '@' prefix if typed
                if (userInput.startsWith("@")) {
                    userInput = userInput.substring(1);
                }
                
                if (userInput.length() < 1) {
                    popup.setVisible(false);
                    return;
                }
                
                List<String> matches = new java.util.ArrayList<>();
                if (isUserField) {
                    matches = service.getUserSuggestions(userInput);
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
                    popup.setVisible(false);
                } else {
                    listModel.clear();
                    matches.stream().distinct().limit(20).forEach(listModel::addElement);
                    suggestionList.setSelectedIndex(0);
                    
                    Rectangle rect = modelToView(pos);
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
            int pos = getCaretPosition();
            String text = getText();
            int lineStart = getLineStartOffset(getLineOfOffset(pos));
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
                
                // If they typed '@', skip/replace it as well
                if (replaceStartInLine < line.length() && line.charAt(replaceStartInLine) == '@') {
                    replaceStartInLine++;
                }
                
                int replaceStart = lineStart + replaceStartInLine;
                replaceRange(selected, replaceStart, pos);
                setCaretPosition(replaceStart + selected.length());
            }
            popup.setVisible(false);
        } catch (BadLocationException e) {
            e.printStackTrace();
        } finally {
            isUpdating = false;
        }
    }
}
