package tso.usmc.jira.ui;

import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JiraConfig;
import tso.usmc.jira.util.ThemeManager;

public class ThemeSettingsPanel extends JPanel {
    private final JiraApiClientGui mainFrame;
    private final JiraConfig config;
    private final ThemeManager themeManager;

    private JComboBox<String> themeDropdown;
    private JButton colorPickerBtn;
    private JPanel colorPreviewPanel;
    private JTextArea cssTextArea;
    private JScrollPane cssScrollPane;
    private JLabel statusLabel;
    private Timer saveDebouncer;
    private boolean isUpdatingFromCode = false;

    public ThemeSettingsPanel(JiraApiClientGui mainFrame, JiraConfig config, ThemeManager themeManager) {
        this.mainFrame = mainFrame;
        this.config = config;
        this.themeManager = themeManager;

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // Mark this panel for custom styling if needed
        putClientProperty("styleClass", "settings-panel");

        initComponents();
        setupListeners();
        loadSettingsToUi();
    }

    private void initComponents() {
        // --- TOP PANEL: Configuration ---
        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("Theme Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Theme Dropdown
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        topPanel.add(new JLabel("Active Theme:"), gbc);

        themeDropdown = new JComboBox<>(new String[]{
            "System Default", 
            "Modern Dark", 
            "Glassmorphism Blue", 
            "Custom Accent", 
            "Custom CSS"
        });
        gbc.gridx = 1; gbc.weightx = 1.0;
        topPanel.add(themeDropdown, gbc);

        // Accent Color Selection
        colorPickerBtn = new JButton("Pick Accent Color");
        colorPreviewPanel = new JPanel();
        colorPreviewPanel.setPreferredSize(new Dimension(30, 20));
        colorPreviewPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        JPanel colorPickerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        colorPickerPanel.add(colorPickerBtn);
        colorPickerPanel.add(colorPreviewPanel);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        topPanel.add(new JLabel("Accent Color:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        topPanel.add(colorPickerPanel, gbc);

        add(topPanel, BorderLayout.NORTH);

        // --- CENTER PANEL: CSS Editor ---
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Custom CSS Editor"));

        cssTextArea = new JTextArea();
        cssTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        cssTextArea.setTabSize(4);
        cssScrollPane = new JScrollPane(cssTextArea);

        // Quick CSS Documentation panel on the right side
        JPanel helpPanel = new JPanel();
        helpPanel.setLayout(new BoxLayout(helpPanel, BoxLayout.Y_AXIS));
        helpPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        helpPanel.setPreferredSize(new Dimension(280, 0));
        
        JLabel helpTitle = new JLabel("CSS Styling Reference");
        helpTitle.setFont(new Font(helpTitle.getFont().getName(), Font.BOLD, 14));
        helpTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        helpPanel.add(helpTitle);
        helpPanel.add(Box.createVerticalStrut(10));

        String helpTextHtml = "<html><body style='font-family:sans-serif; font-size:10px;'>" +
            "<b>Supported Selectors:</b><br>" +
            "&bull; <code>JPanel</code>, <code>JButton</code>, <code>JLabel</code><br>" +
            "&bull; <code>JTextField</code>, <code>JTextArea</code>, <code>JComboBox</code><br>" +
            "&bull; <code>JTable</code>, <code>JTabbedPane</code><br>" +
            "&bull; <code>.glass-panel</code> (Custom Class)<br>" +
            "&bull; <code>.primary-button</code> (Custom Class)<br>" +
            "&bull; <code>:hover</code> states (e.g. <code>JButton:hover</code>)<br><br>" +
            "<b>Supported Properties:</b><br>" +
            "&bull; <code>background-color</code> (hex, rgb, rgba, transparent)<br>" +
            "&bull; <code>color</code> / <code>foreground-color</code><br>" +
            "&bull; <code>border-color</code>, <code>border-width</code><br>" +
            "&bull; <code>border-radius</code> (Rounded corners)<br>" +
            "&bull; <code>font-size</code>, <code>font-family</code>, <code>font-weight</code><br>" +
            "&bull; <code>padding</code> (Internal text padding)<br><br>" +
            "<b>Glassmorphic Panel Template:</b><br>" +
            "<pre style='font-size:9px; background:#f1f5f9; padding:4px;'>" +
            "JPanel {\n" +
            "  background-color: rgba(255,255,255,0.1);\n" +
            "  border-color: rgba(255,255,255,0.2);\n" +
            "  border-radius: 12px;\n" +
            "  border-width: 1px;\n" +
            "}</pre>" +
            "</body></html>";
        
        JLabel helpBody = new JLabel(helpTextHtml);
        helpBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        helpPanel.add(helpBody);

        centerPanel.add(cssScrollPane, BorderLayout.CENTER);
        centerPanel.add(helpPanel, BorderLayout.EAST);

        add(centerPanel, BorderLayout.CENTER);

        // --- BOTTOM PANEL: Status ---
        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void setupListeners() {
        // Dropdown selection listener
        themeDropdown.addActionListener(e -> {
            if (isUpdatingFromCode) return;
            
            String selected = (String) themeDropdown.getSelectedItem();
            String themeVal = "default";
            
            if ("Modern Dark".equals(selected)) {
                themeVal = "dark";
            } else if ("Glassmorphism Blue".equals(selected)) {
                themeVal = "glass";
            } else if ("Custom Accent".equals(selected)) {
                themeVal = "custom";
            } else if ("Custom CSS".equals(selected)) {
                themeVal = "css";
            }
            
            config.setTheme(themeVal);
            updateUiState();
            
            // Apply theme changes dynamically to the window
            themeManager.applyTheme(mainFrame);
            statusLabel.setText("Theme changed to: " + selected);
        });

        // Color picker action
        colorPickerBtn.addActionListener(e -> {
            Color initialColor = ThemeManager.parseColor(config.getThemeAccentColor());
            if (initialColor == null) initialColor = new Color(0, 120, 215);

            Color selectedColor = JColorChooser.showDialog(this, "Select Custom Accent Color", initialColor);
            if (selectedColor != null) {
                String hex = String.format("#%02x%02x%02x", selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue());
                config.setThemeAccentColor(hex);
                colorPreviewPanel.setBackground(selectedColor);
                
                // Trigger application theme refresh
                themeManager.applyTheme(mainFrame);
                statusLabel.setText("Custom Accent Color updated: " + hex);
            }
        });

        // CSS Text Area Live Editor Debouncing (500ms)
        saveDebouncer = new Timer(500, e -> {
            saveCssToFile();
        });
        saveDebouncer.setRepeats(false);

        cssTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { triggerDebounce(); }
            @Override
            public void removeUpdate(DocumentEvent e) { triggerDebounce(); }
            @Override
            public void changedUpdate(DocumentEvent e) { triggerDebounce(); }

            private void triggerDebounce() {
                if (isUpdatingFromCode) return;
                statusLabel.setText("Editing Custom CSS... (typing)");
                saveDebouncer.restart();
            }
        });
    }

    private void triggerThemeReapply() {
        SwingUtilities.invokeLater(() -> {
            themeManager.applyTheme(mainFrame);
        });
    }

    private void saveCssToFile() {
        try {
            String cssPath = config.getThemeCssFilePath();
            Files.write(new File(cssPath).toPath(), cssTextArea.getText().getBytes());
            statusLabel.setText("CSS saved and hot-reloaded successfully.");
            triggerThemeReapply();
        } catch (Exception ex) {
            statusLabel.setText("Error saving CSS: " + ex.getMessage());
        }
    }

    private void loadSettingsToUi() {
        isUpdatingFromCode = true;
        try {
            String theme = config.getTheme();
            if ("dark".equals(theme)) {
                themeDropdown.setSelectedItem("Modern Dark");
            } else if ("glass".equals(theme)) {
                themeDropdown.setSelectedItem("Glassmorphism Blue");
            } else if ("custom".equals(theme)) {
                themeDropdown.setSelectedItem("Custom Accent");
            } else if ("css".equals(theme)) {
                themeDropdown.setSelectedItem("Custom CSS");
            } else {
                themeDropdown.setSelectedItem("System Default");
            }

            // Accent color preview setup
            Color accentColor = ThemeManager.parseColor(config.getThemeAccentColor());
            if (accentColor != null) {
                colorPreviewPanel.setBackground(accentColor);
            }

            // Load Custom CSS file content
            String cssPath = config.getThemeCssFilePath();
            File cssFile = new File(cssPath);
            if (cssFile.exists()) {
                String cssText = new String(Files.readAllBytes(cssFile.toPath()));
                cssTextArea.setText(cssText);
            }
            
            updateUiState();
        } catch (Exception e) {
            System.err.println("Error loading theme settings to panel: " + e.getMessage());
        } finally {
            isUpdatingFromCode = false;
        }
    }

    private void updateUiState() {
        String selected = (String) themeDropdown.getSelectedItem();
        boolean isCustomAccent = "Custom Accent".equals(selected);
        boolean isCustomCss = "Custom CSS".equals(selected);

        colorPickerBtn.setEnabled(isCustomAccent);
        cssTextArea.setEnabled(isCustomCss);
        cssTextArea.setEditable(isCustomCss);

        if (isCustomCss) {
            cssTextArea.setBackground(Color.WHITE);
            cssTextArea.setForeground(Color.BLACK);
        } else {
            // Read-only/disabled styling
            cssTextArea.setBackground(new Color(240, 240, 240));
            cssTextArea.setForeground(Color.GRAY);
        }
    }
}
