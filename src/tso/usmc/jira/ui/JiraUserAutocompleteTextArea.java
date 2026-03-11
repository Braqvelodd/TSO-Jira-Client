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
        if (isUpdating || service == null) return;
        
        SwingUtilities.invokeLater(() -> {
            try {
                int pos = getCaretPosition();
                String text = getText();
                if (pos == 0) { popup.setVisible(false); return; }
                
                // Identify the current line and prefix
                int lineStart = getLineStartOffset(getLineOfOffset(pos));
                String line = text.substring(lineStart, pos);
                String lineLower = line.toLowerCase();
                
                String userInput = "";
                boolean shouldShow = false;
                
                if (lineLower.startsWith("assignee:")) {
                    userInput = line.substring(9).trim();
                    shouldShow = true;
                } else if (lineLower.startsWith("notify:")) {
                    // Notify can be a CSV list, so get the last part
                    String csvPart = line.substring(7);
                    int lastComma = csvPart.lastIndexOf(',');
                    if (lastComma != -1) {
                        userInput = csvPart.substring(lastComma + 1).trim();
                    } else {
                        userInput = csvPart.trim();
                    }
                    shouldShow = true;
                }

                if (!shouldShow || userInput.length() < 2) {
                    popup.setVisible(false);
                    return;
                }

                List<String> matches = service.getUserSuggestions(userInput);

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
            
            int replaceStart = pos;
            if (line.toLowerCase().startsWith("assignee:")) {
                replaceStart = lineStart + 9;
                while (replaceStart < pos && Character.isWhitespace(text.charAt(replaceStart))) replaceStart++;
            } else if (line.toLowerCase().startsWith("notify:")) {
                int lastComma = line.lastIndexOf(',');
                if (lastComma != -1) {
                    replaceStart = lineStart + lastComma + 1;
                } else {
                    replaceStart = lineStart + 7;
                }
                while (replaceStart < pos && Character.isWhitespace(text.charAt(replaceStart))) replaceStart++;
            }

            replaceRange(selected, replaceStart, pos);
            setCaretPosition(replaceStart + selected.length());
            popup.setVisible(false);
        } catch (BadLocationException e) {
            e.printStackTrace();
        } finally {
            isUpdating = false;
        }
    }
}
