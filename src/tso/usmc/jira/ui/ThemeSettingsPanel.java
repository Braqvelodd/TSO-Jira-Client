package tso.usmc.jira.ui;

import java.io.File;
import java.nio.file.Files;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JiraConfig;
import tso.usmc.jira.util.ThemeManager;

public class ThemeSettingsPanel extends BorderPane {
    private final JiraApiClientGui mainFrame;
    private final JiraConfig config;
    private final ThemeManager themeManager;

    private ComboBox<String> themeDropdown;
    private ColorPicker colorPicker;
    private TextArea cssTextArea;
    private Label statusLabel;
    private Timeline saveDebouncer;
    private boolean isUpdatingFromCode = false;

    public ThemeSettingsPanel(JiraApiClientGui mainFrame, JiraConfig config, ThemeManager themeManager) {
        this.mainFrame = mainFrame;
        this.config = config;
        this.themeManager = themeManager;

        setPadding(new Insets(15));

        initComponents();
        setupListeners();
        loadSettingsToUi();
    }

    private void initComponents() {
        // --- TOP PANEL: Configuration ---
        GridPane topPanel = new GridPane();
        topPanel.getStyleClass().add("card");
        topPanel.setHgap(10);
        topPanel.setVgap(10);
        topPanel.setPadding(new Insets(12));

        // Theme Dropdown
        topPanel.add(new Label("Active Theme:"), 0, 0);

        themeDropdown = new ComboBox<>();
        themeDropdown.getItems().addAll(
            "System Default", 
            "Modern Dark", 
            "Glassmorphism Blue", 
            "Custom Accent", 
            "Custom CSS"
        );
        themeDropdown.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(themeDropdown, Priority.ALWAYS);
        topPanel.add(themeDropdown, 1, 0);

        // Accent Color Selection
        topPanel.add(new Label("Accent Color:"), 0, 1);
        colorPicker = new ColorPicker();
        colorPicker.setMaxWidth(Double.MAX_VALUE);
        topPanel.add(colorPicker, 1, 1);

        setTop(topPanel);

        // --- CENTER PANEL: CSS Editor ---
        BorderPane centerPanel = new BorderPane();
        centerPanel.getStyleClass().add("card");
        centerPanel.setPadding(new Insets(12));
        BorderPane.setMargin(centerPanel, new Insets(10, 0, 10, 0));

        Label editorTitle = new Label("Custom CSS Editor");
        editorTitle.getStyleClass().add("card-title");
        centerPanel.setTop(editorTitle);

        cssTextArea = new TextArea();
        cssTextArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace;");
        centerPanel.setCenter(cssTextArea);

        // Quick CSS Documentation panel on the right side
        WebView helpPanel = new WebView();
        helpPanel.setPrefWidth(280);
        
        String helpTextHtml = "<html><body style='font-family:sans-serif; font-size:11px; background:#1e293b; color:#f8fafc; margin:10px;'>" +
            "<h4 style='color:#3b82f6; margin-top:0;'>CSS Styling Reference</h4>" +
            "<b>Supported Selectors:</b><br>" +
            "&bull; <code>.root</code>, <code>.button</code>, <code>.label</code><br>" +
            "&bull; <code>.text-input</code>, <code>.combo-box</code>, <code>.tab-pane</code><br>" +
            "&bull; <code>.table-view</code>, <code>.list-view</code><br>" +
            "&bull; <code>.connection-panel</code> (Top Header)<br>" +
            "&bull; <code>.card</code> (Custom Class)<br>" +
            "&bull; <code>.card-title</code> (Card Headers)<br><br>" +
            "<b>Supported Variables:</b><br>" +
            "&bull; <code>-fx-background</code> (Window backdrop)<br>" +
            "&bull; <code>-fx-base</code> (Base component color)<br>" +
            "&bull; <code>-fx-control-inner-background</code> (Input backgrounds)<br>" +
            "&bull; <code>-fx-accent</code> (Focus/Highlight color)<br>" +
            "&bull; <code>-fx-text-base-color</code> (Text color)<br><br>" +
            "<b>Glassmorphic Card Example:</b><br>" +
            "<pre style='font-size:9px; background:#0f172a; color:#10b981; padding:6px; border-radius:4px;'>" +
            ".card {\n" +
            "  -fx-background-color:\n" +
            "    rgba(255,255,255,0.05);\n" +
            "  -fx-border-color:\n" +
            "    rgba(255,255,255,0.1);\n" +
            "  -fx-border-radius: 12px;\n" +
            "  -fx-border-width: 1px;\n" +
            "}</pre>" +
            "</body></html>";
        
        helpPanel.getEngine().loadContent(helpTextHtml);
        centerPanel.setRight(helpPanel);
        BorderPane.setMargin(helpPanel, new Insets(0, 0, 0, 10));

        setCenter(centerPanel);

        // --- BOTTOM PANEL: Status ---
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-padding: 5px;");
        setBottom(statusLabel);
    }

    private void setupListeners() {
        // Dropdown selection listener
        themeDropdown.setOnAction(e -> {
            if (isUpdatingFromCode) return;
            
            String selected = themeDropdown.getSelectionModel().getSelectedItem();
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
            themeManager.applyTheme(mainFrame.getMainScene());
            statusLabel.setText("Theme changed to: " + selected);
        });

        // Color picker action
        colorPicker.setOnAction(e -> {
            if (isUpdatingFromCode) return;
            Color color = colorPicker.getValue();
            String hex = String.format("#%02x%02x%02x", 
                (int)(color.getRed() * 255), 
                (int)(color.getGreen() * 255), 
                (int)(color.getBlue() * 255));
            
            config.setThemeAccentColor(hex);
            
            // Trigger application theme refresh
            themeManager.applyTheme(mainFrame.getMainScene());
            statusLabel.setText("Custom Accent Color updated: " + hex);
        });

        // CSS Text Area Live Editor Debouncing (500ms)
        saveDebouncer = new Timeline(new KeyFrame(Duration.millis(500), e -> saveCssToFile()));
        saveDebouncer.setCycleCount(1);

        cssTextArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (isUpdatingFromCode) return;
            statusLabel.setText("Editing Custom CSS... (typing)");
            saveDebouncer.playFromStart(); // Restarts timeline
        });
    }

    private void saveCssToFile() {
        try {
            String cssPath = config.getThemeCssFilePath();
            Files.write(new File(cssPath).toPath(), cssTextArea.getText().getBytes());
            statusLabel.setText("CSS saved and hot-reloaded successfully.");
            Platform.runLater(() -> themeManager.applyTheme(mainFrame.getMainScene()));
        } catch (Exception ex) {
            statusLabel.setText("Error saving CSS: " + ex.getMessage());
        }
    }

    private void loadSettingsToUi() {
        isUpdatingFromCode = true;
        try {
            String theme = config.getTheme();
            if ("dark".equals(theme)) {
                themeDropdown.getSelectionModel().select("Modern Dark");
            } else if ("glass".equals(theme)) {
                themeDropdown.getSelectionModel().select("Glassmorphism Blue");
            } else if ("custom".equals(theme)) {
                themeDropdown.getSelectionModel().select("Custom Accent");
            } else if ("css".equals(theme)) {
                themeDropdown.getSelectionModel().select("Custom CSS");
            } else {
                themeDropdown.getSelectionModel().select("System Default");
            }

            // Accent color setup
            String hexColor = config.getThemeAccentColor();
            try {
                colorPicker.setValue(Color.web(hexColor));
            } catch (Exception ignored) {
                colorPicker.setValue(Color.valueOf("#0078D7"));
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
        String selected = themeDropdown.getSelectionModel().getSelectedItem();
        boolean isCustomAccent = "Custom Accent".equals(selected);
        boolean isCustomCss = "Custom CSS".equals(selected);

        colorPicker.setDisable(!isCustomAccent);
        cssTextArea.setDisable(!isCustomCss);
        cssTextArea.setEditable(isCustomCss);
    }
}
