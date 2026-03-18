package tso.usmc.jira.ui;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AutocompleteTextField extends JPanel {
    private final JTextField textField;
    private final JWindow popup;
    private final JList<String> suggestionList;
    private final DefaultListModel<String> listModel;
    private List<String> allSuggestions = new ArrayList<>();
    private boolean enabled = true;

    public AutocompleteTextField(int columns) {
        super(new BorderLayout());
        
        textField = new JTextField(columns);
        listModel = new DefaultListModel<>();
        suggestionList = new JList<>(listModel);
        
        popup = new JWindow(SwingUtilities.getWindowAncestor(this));
        popup.setType(Window.Type.POPUP);
        popup.setFocusableWindowState(false);
        popup.setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(suggestionList);
        scrollPane.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        popup.add(scrollPane);

        setupListeners();
        add(textField, BorderLayout.CENTER);
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
    
    public JTextField getTextField() {
        return textField;
    }

    private void setupListeners() {
        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                showSuggestions();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                showSuggestions();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                showSuggestions();
            }
        });

        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // Hide popup if focus is lost to a component outside the popup
                if (e.getOppositeComponent() != null && SwingUtilities.getWindowAncestor(e.getOppositeComponent()) != popup) {
                    popup.setVisible(false);
                }
            }
        });

        suggestionList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1) {
                    selectFromList();
                }
            }
        });

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!popup.isVisible()) return;

                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN:
                        suggestionList.setSelectedIndex(Math.min(listModel.getSize() - 1, suggestionList.getSelectedIndex() + 1));
                        suggestionList.ensureIndexIsVisible(suggestionList.getSelectedIndex());
                        e.consume();
                        break;
                    case KeyEvent.VK_UP:
                        suggestionList.setSelectedIndex(Math.max(0, suggestionList.getSelectedIndex() - 1));
                        suggestionList.ensureIndexIsVisible(suggestionList.getSelectedIndex());
                        e.consume();
                        break;
                    case KeyEvent.VK_ENTER:
                        selectFromList();
                        e.consume();
                        break;
                    case KeyEvent.VK_ESCAPE:
                        popup.setVisible(false);
                        e.consume();
                        break;
                }
            }
        });
    }

    private void showSuggestions() {
        if (!enabled || allSuggestions.isEmpty()) {
            popup.setVisible(false);
            return;
        }
        
        String text = textField.getText().toLowerCase();
        listModel.clear();

        List<String> filtered = allSuggestions.stream()
                .filter(s -> s.toLowerCase().contains(text))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) {
            popup.setVisible(false);
            return;
        }

        for (String s : filtered) {
            listModel.addElement(s);
        }
        
        Point location = textField.getLocationOnScreen();
        popup.setLocation(location.x, location.y + textField.getHeight());
        popup.pack();
        
        // Ensure popup does not exceed a reasonable height
        int listHeight = suggestionList.getPreferredSize().height;
        int maxHeight = 200;
        if (listHeight > maxHeight) {
            popup.setSize(popup.getWidth(), maxHeight);
        }

        if (!popup.isVisible()) {
            popup.setVisible(true);
        }
    }

    private void selectFromList() {
        if (suggestionList.getSelectedIndex() != -1) {
            SwingUtilities.invokeLater(() -> {
                textField.setText(suggestionList.getSelectedValue());
                popup.setVisible(false);
            });
        }
    }
}
