package tso.usmc.jira.ui;

import tso.usmc.jira.service.JqlAutocompleteService;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

public class JiraUserAutocompleteTextField extends JTextField {
    private JqlAutocompleteService service;
    private final JPopupMenu popup;
    private final DefaultListModel<String> listModel;
    private final JList<String> suggestionList;
    private boolean isUpdating = false;

    public JiraUserAutocompleteTextField(int columns) {
        super(columns);
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
                String userInput = getText();
                if (userInput.length() < 2) {
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
                    
                    Rectangle rect = modelToView(getCaretPosition());
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
            setText(selected);
            popup.setVisible(false);
        } finally {
            isUpdating = false;
        }
    }
}
