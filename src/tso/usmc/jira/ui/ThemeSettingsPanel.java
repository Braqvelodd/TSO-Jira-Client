package tso.usmc.jira.ui;

import java.io.File;
import java.nio.file.Files;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import tso.usmc.jira.app.JiraApiClientGui;
import tso.usmc.jira.util.JiraConfig;
import tso.usmc.jira.util.ThemeManager;
import netscape.javascript.JSObject;

public class ThemeSettingsPanel extends BorderPane {
    private final JiraApiClientGui mainFrame;
    private final JiraConfig config;
    private final ThemeManager themeManager;

    private ComboBox<String> themeDropdown;
    private ColorPicker colorPicker;
    private WebView cssWebView;
    private WebView helpPanel;
    private Label statusLabel;
    private Timeline saveDebouncer;
    private boolean isUpdatingFromCode = false;
    private String pendingCssText = "";
    private final JavaConnector javaConnector = new JavaConnector();

    private static final String EDITOR_HTML = 
        "<!DOCTYPE html>\n" +
        "<html>\n" +
        "<head>\n" +
        "<style>\n" +
        "  body, html {\n" +
        "    margin: 0; padding: 0; height: 100%;\n" +
        "    background-color: #0f172a; color: #f8fafc;\n" +
        "    font-family: 'Consolas', 'Courier New', monospace;\n" +
        "    overflow: hidden;\n" +
        "  }\n" +
        "  body.light, body.light #textarea {\n" +
        "    background-color: #ffffff;\n" +
        "    color: #1e293b;\n" +
        "  }\n" +
        "  body.light #line-numbers {\n" +
        "    background-color: #f1f5f9;\n" +
        "    color: #64748b;\n" +
        "    border-right: 1px solid #cbd5e1;\n" +
        "  }\n" +
        "  #editor-container {\n" +
        "    display: flex;\n" +
        "    position: relative;\n" +
        "    width: 100%; height: 100%;\n" +
        "    box-sizing: border-box;\n" +
        "  }\n" +
        "  #line-numbers {\n" +
        "    width: 45px;\n" +
        "    height: 100%;\n" +
        "    background-color: #1e293b;\n" +
        "    color: #64748b;\n" +
        "    text-align: right;\n" +
        "    padding: 10px 8px 10px 0;\n" +
        "    box-sizing: border-box;\n" +
        "    font-size: 13px;\n" +
        "    line-height: 20px;\n" +
        "    user-select: none;\n" +
        "    border-right: 1px solid #334155;\n" +
        "    overflow: hidden;\n" +
        "  }\n" +
        "  #textarea-container {\n" +
        "    position: relative;\n" +
        "    flex: 1;\n" +
        "    height: 100%;\n" +
        "  }\n" +
        "  #textarea {\n" +
        "    width: 100%;\n" +
        "    height: 100%;\n" +
        "    background-color: #0f172a;\n" +
        "    color: #f8fafc;\n" +
        "    border: none;\n" +
        "    outline: none;\n" +
        "    resize: none;\n" +
        "    font-family: inherit;\n" +
        "    font-size: 13px;\n" +
        "    line-height: 20px;\n" +
        "    padding: 10px;\n" +
        "    padding-right: 100px;\n" +
        "    box-sizing: border-box;\n" +
        "    white-space: pre;\n" +
        "    overflow-y: scroll;\n" +
        "    overflow-x: auto;\n" +
        "  }\n" +
        "  #pickers-gutter {\n" +
        "    position: absolute;\n" +
        "    top: 0;\n" +
        "    right: 10px;\n" +
        "    width: 85px;\n" +
        "    height: 100%;\n" +
        "    pointer-events: none;\n" +
        "    overflow: hidden;\n" +
        "  }\n" +
        "  .color-picker-wrapper {\n" +
        "    position: absolute;\n" +
        "    width: 20px;\n" +
        "    height: 20px;\n" +
        "    pointer-events: auto;\n" +
        "    display: flex;\n" +
        "    align-items: center;\n" +
        "    justify-content: center;\n" +
        "  }\n" +
        "  .color-dot {\n" +
        "    width: 14px;\n" +
        "    height: 14px;\n" +
        "    border: 1px solid #ffffff44;\n" +
        "    border-radius: 3px;\n" +
        "    cursor: pointer;\n" +
        "    box-shadow: 0 0 2px rgba(0,0,0,0.5);\n" +
        "  }\n" +
        "  .color-input {\n" +
        "    position: absolute;\n" +
        "    opacity: 0;\n" +
        "    width: 14px;\n" +
        "    height: 14px;\n" +
        "    cursor: pointer;\n" +
        "  }\n" +
        "</style>\n" +
        "</head>\n" +
        "<body>\n" +
        "  <div id=\"editor-container\">\n" +
        "    <div id=\"line-numbers\">1</div>\n" +
        "    <div id=\"textarea-container\">\n" +
        "      <textarea id=\"textarea\" spellcheck=\"false\"></textarea>\n" +
        "      <div id=\"pickers-gutter\"></div>\n" +
        "    </div>\n" +
        "  </div>\n" +
        "  <script>\n" +
        "    const textarea = document.getElementById('textarea');\n" +
        "    const lineNumbers = document.getElementById('line-numbers');\n" +
        "    const pickersGutter = document.getElementById('pickers-gutter');\n" +
        "    const lineHeight = 20;\n" +
        "\n" +
        "    function updateLineNumbers() {\n" +
        "      const lines = textarea.value.split('\\n');\n" +
        "      const numLines = lines.length;\n" +
        "      let html = '';\n" +
        "      for (let i = 1; i <= numLines; i++) {\n" +
        "        html += i + '<br>';\n" +
        "      }\n" +
        "      lineNumbers.innerHTML = html;\n" +
        "      lineNumbers.scrollTop = textarea.scrollTop;\n" +
        "    }\n" +
        "\n" +
        "    function replaceColorOnLine(lineText, matchIndexToReplace, newColorStr) {\n" +
        "      const colorPattern = /(#[a-fA-F0-9]{8}\\b|#[a-fA-F0-9]{6}\\b|#[a-fA-F0-9]{3,4}\\b|rgba?\\(\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*(?:,\\s*[0-9.]+\\s*)?\\))/gi;\n" +
        "      let match;\n" +
        "      let currentMatchIndex = 0;\n" +
        "      let lastIndex = 0;\n" +
        "      let result = '';\n" +
        "      \n" +
        "      while ((match = colorPattern.exec(lineText)) !== null) {\n" +
        "        result += lineText.substring(lastIndex, match.index);\n" +
        "        if (currentMatchIndex === matchIndexToReplace) {\n" +
        "          result += newColorStr;\n" +
        "        } else {\n" +
        "          result += match[0];\n" +
        "        }\n" +
        "        lastIndex = colorPattern.lastIndex;\n" +
        "        currentMatchIndex++;\n" +
        "      }\n" +
        "      result += lineText.substring(lastIndex);\n" +
        "      return result;\n" +
        "    }\n" +
        "\n" +
        "    function parseColors() {\n" +
        "      pickersGutter.innerHTML = '';\n" +
        "      const lines = textarea.value.split('\\n');\n" +
        "      const colorPattern = /(#[a-fA-F0-9]{8}\\b|#[a-fA-F0-9]{6}\\b|#[a-fA-F0-9]{3,4}\\b|rgba?\\(\\s*\\d+\\s*,\\s*\\d+\\s*,\\s*\\d+\\s*(?:,\\s*[0-9.]+\\s*)?\\))/gi;\n" +
        "      \n" +
        "      const scrollOffset = textarea.scrollTop;\n" +
        "      const padding = 10;\n" +
        "\n" +
        "      lines.forEach((lineText, lineIndex) => {\n" +
        "        let match;\n" +
        "        colorPattern.lastIndex = 0;\n" +
        "        let pickerIndex = 0;\n" +
        "        while ((match = colorPattern.exec(lineText)) !== null) {\n" +
        "          const colorStr = match[0];\n" +
        "          const yPos = padding + (lineIndex * lineHeight) - scrollOffset;\n" +
        "          if (yPos >= 0 && yPos <= textarea.clientHeight) {\n" +
        "            createPicker(colorStr, lineIndex, yPos, pickerIndex);\n" +
        "          }\n" +
        "          pickerIndex++;\n" +
        "        }\n" +
        "      });\n" +
        "    }\n" +
        "\n" +
        "    function createPicker(colorStr, lineIndex, yPos, pickerIndex) {\n" +
        "      const wrapper = document.createElement('div');\n" +
        "      wrapper.className = 'color-picker-wrapper';\n" +
        "      wrapper.style.top = yPos + 'px';\n" +
        "      wrapper.style.left = (pickerIndex * 22) + 'px';\n" +
        "      \n" +
        "      const dot = document.createElement('div');\n" +
        "      dot.className = 'color-dot';\n" +
        "      dot.style.backgroundColor = colorStr;\n" +
        "      \n" +
        "      const input = document.createElement('input');\n" +
        "      input.type = 'color';\n" +
        "      input.className = 'color-input';\n" +
        "      input.value = convertToHex(colorStr);\n" +
        "      \n" +
        "      const handleUpdate = (newHex, isFinal) => {\n" +
        "        const formatted = formatColorLike(newHex, colorStr);\n" +
        "        const lines = textarea.value.split('\\n');\n" +
        "        \n" +
        "        lines[lineIndex] = replaceColorOnLine(lines[lineIndex], pickerIndex, formatted);\n" +
        "        textarea.value = lines.join('\\n');\n" +
        "        \n" +
        "        dot.style.backgroundColor = formatted;\n" +
        "        \n" +
        "        if (window.javaConnector) {\n" +
        "          if (isFinal) {\n" +
        "            window.javaConnector.onColorPicked(textarea.value);\n" +
        "          } else {\n" +
        "            window.javaConnector.onTextChange(textarea.value);\n" +
        "          }\n" +
        "        }\n" +
        "        \n" +
        "        if (isFinal) {\n" +
        "          parseColors();\n" +
        "        }\n" +
        "      };\n" +
        "\n" +
        "      input.addEventListener('input', (e) => {\n" +
        "        handleUpdate(e.target.value, false);\n" +
        "      });\n" +
        "\n" +
        "      input.addEventListener('change', (e) => {\n" +
        "        handleUpdate(e.target.value, true);\n" +
        "      });\n" +
        "\n" +
        "      wrapper.appendChild(dot);\n" +
        "      wrapper.appendChild(input);\n" +
        "      pickersGutter.appendChild(wrapper);\n" +
        "    }\n" +
        "\n" +
        "    function convertToHex(colorStr) {\n" +
        "      if (colorStr.startsWith('#')) {\n" +
        "        if (colorStr.length === 4 || colorStr.length === 5) {\n" +
        "          const r = colorStr[1];\n" +
        "          const g = colorStr[2];\n" +
        "          const b = colorStr[3];\n" +
        "          return '#' + r + r + g + g + b + b;\n" +
        "        }\n" +
        "        return colorStr.substring(0, 7);\n" +
        "      }\n" +
        "      const match = colorStr.match(/rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)/i);\n" +
        "      if (match) {\n" +
        "        const r = parseInt(match[1]).toString(16).padStart(2, '0');\n" +
        "        const g = parseInt(match[2]).toString(16).padStart(2, '0');\n" +
        "        const b = parseInt(match[3]).toString(16).padStart(2, '0');\n" +
        "        return '#' + r + g + b;\n" +
        "      }\n" +
        "      return '#000000';\n" +
        "    }\n" +
        "\n" +
        "    function formatColorLike(hex, original) {\n" +
        "      if (original.startsWith('#')) {\n" +
        "        return hex;\n" +
        "      }\n" +
        "      const match = original.match(/rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)(,\\s*[0-9.]+)?/i);\n" +
        "      if (match) {\n" +
        "        const r = parseInt(hex.substring(1, 3), 16);\n" +
        "        const g = parseInt(hex.substring(3, 5), 16);\n" +
        "        const b = parseInt(hex.substring(5, 7), 16);\n" +
        "        if (original.startsWith('rgba') && match[4]) {\n" +
        "          return 'rgba(' + r + ', ' + g + ', ' + b + match[4] + ')';\n" +
        "        } else {\n" +
        "          return 'rgb(' + r + ', ' + g + ', ' + b + ')';\n" +
        "        }\n" +
        "      }\n" +
        "      return hex;\n" +
        "    }\n" +
        "\n" +
        "    textarea.addEventListener('input', () => {\n" +
        "      updateLineNumbers();\n" +
        "      parseColors();\n" +
        "      if (window.javaConnector) {\n" +
        "        window.javaConnector.onTextChange(textarea.value);\n" +
        "      }\n" +
        "    });\n" +
        "\n" +
        "    textarea.addEventListener('scroll', () => {\n" +
        "      lineNumbers.scrollTop = textarea.scrollTop;\n" +
        "      parseColors();\n" +
        "    });\n" +
        "\n" +
        "    function setText(text) {\n" +
        "      textarea.value = text;\n" +
        "      updateLineNumbers();\n" +
        "      parseColors();\n" +
        "    }\n" +
        "\n" +
        "    function setTheme(theme) {\n" +
        "      document.body.className = theme;\n" +
        "    }\n" +
        "\n" +
        "    window.addEventListener('resize', parseColors);\n" +
        "  </script>\n" +
        "</body>\n" +
        "</html>";

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
        themeDropdown.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                populateThemeDropdown();
            }
        });
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

        cssWebView = new WebView();
        cssWebView.getEngine().loadContent(EDITOR_HTML);
        cssWebView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) cssWebView.getEngine().executeScript("window");
                window.setMember("javaConnector", javaConnector);
                if (pendingCssText != null && !pendingCssText.isEmpty()) {
                    setEditorText(pendingCssText);
                }
                updateWebViewsTheme();
            }
        });
        centerPanel.setCenter(cssWebView);

        // Quick CSS Documentation panel on the right side
        helpPanel = new WebView();
        helpPanel.setPrefWidth(280);
        
        String helpTextHtml = "<html><head><style>" +
            "  body { font-family:sans-serif; font-size:11px; background:#1e293b; color:#f8fafc; margin:10px; }" +
            "  body.light { background:#f4f4f5; color:#18181b; }" +
            "  h4 { color:#3b82f6; margin-top:0; }" +
            "  body.light h4 { color:#2563eb; }" +
            "  code { background:#0f172a; color:#38bdf8; padding:1px 4px; border-radius:3px; }" +
            "  body.light code { background:#e4e4e7; color:#2563eb; }" +
            "  pre { font-size:9px; background:#0f172a; color:#10b981; padding:6px; border-radius:4px; }" +
            "  body.light pre { background:#f4f4f5; color:#059669; border:1px solid #e4e4e7; }" +
            "</style></head><body class='dark'>" +
            "<h4 style='margin-top:0;'>CSS Styling Reference</h4>" +
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
            "<pre>" +
            ".card {\n" +
            "  -fx-background-color:\n" +
            "    rgba(255,255,255,0.05);\n" +
            "  -fx-border-color:\n" +
            "    rgba(255,255,255,0.1);\n" +
            "  -fx-border-radius: 12px;\n" +
            "  -fx-border-width: 1px;\n" +
            "}</pre>" +
            "<script>" +
            "  function setTheme(t) { document.body.className = t; }" +
            "</script>" +
            "</body></html>";
        
        helpPanel.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                updateWebViewsTheme();
            }
        });
        
        helpPanel.getEngine().loadContent(helpTextHtml);
        centerPanel.setRight(helpPanel);
        BorderPane.setMargin(helpPanel, new Insets(0, 0, 0, 10));

        setCenter(centerPanel);

        // --- BOTTOM PANEL: Status ---
        statusLabel = new Label("Ready");
        statusLabel.setStyle("-fx-padding: 5px;");
        setBottom(statusLabel);
    }

    private void populateThemeDropdown() {
        boolean oldUpdating = isUpdatingFromCode;
        isUpdatingFromCode = true;
        try {
            String selectedItem = themeDropdown.getSelectionModel().getSelectedItem();
            
            themeDropdown.getItems().clear();
            themeDropdown.getItems().addAll(
                "System Default", 
                "Modern Dark", 
                "Glassmorphism Blue", 
                "Custom Accent"
            );
            
            File themesDir = new File(config.getConfigFile().getParentFile(), "themes");
            if (!themesDir.exists()) {
                themesDir.mkdirs();
            }
            
            // Ensure default custom_theme.css exists in the new themes directory
            File defaultCss = new File(themesDir, "custom_theme.css");
            if (!defaultCss.exists()) {
                // Check if user has an old custom_theme.css in the parent directory
                File oldCss = new File(config.getConfigFile().getParentFile(), "custom_theme.css");
                if (oldCss.exists()) {
                    try {
                        Files.copy(oldCss.toPath(), defaultCss.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.io.IOException e) {
                        System.err.println("Error migrating custom_theme.css to themes folder: " + e.getMessage());
                    }
                } else {
                    // Otherwise copy default template from classpath resource
                    try (java.io.InputStream is = getClass().getResourceAsStream("/themes/custom.css")) {
                        if (is != null) {
                            Files.copy(is, defaultCss.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            // Fallback
                            java.util.List<String> defaultCssLines = new java.util.ArrayList<>();
                            defaultCssLines.add("/* Custom CSS Stylesheet for USMC TSO Jira Client */");
                            defaultCssLines.add(".root { -fx-font-family: \"Segoe UI\", Arial, sans-serif; }");
                            Files.write(defaultCss.toPath(), defaultCssLines);
                        }
                    } catch (java.io.IOException e) {
                        System.err.println("Error bootstrapping custom_theme.css: " + e.getMessage());
                    }
                }
            }
            
            File[] cssFiles = themesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".css"));
            if (cssFiles != null) {
                for (File file : cssFiles) {
                    String name = file.getName();
                    String displayName = name.substring(0, name.length() - 4);
                    themeDropdown.getItems().add(displayName);
                }
            }
            
            // Restore selection if it still exists in the newly populated list
            if (selectedItem != null && themeDropdown.getItems().contains(selectedItem)) {
                themeDropdown.getSelectionModel().select(selectedItem);
            } else {
                selectActiveThemeFromConfig();
            }
        } catch (Exception e) {
            System.err.println("Error scanning themes directory: " + e.getMessage());
        } finally {
            isUpdatingFromCode = oldUpdating;
        }
    }

    private void selectActiveThemeFromConfig() {
        String theme = config.getTheme();
        if ("dark".equals(theme)) {
            themeDropdown.getSelectionModel().select("Modern Dark");
        } else if ("glass".equals(theme)) {
            themeDropdown.getSelectionModel().select("Glassmorphism Blue");
        } else if ("custom".equals(theme)) {
            themeDropdown.getSelectionModel().select("Custom Accent");
        } else if ("css".equals(theme)) {
            String cssPath = config.getThemeCssFilePath();
            File cssFile = new File(cssPath);
            String fileName = cssFile.getName();
            if (fileName.endsWith(".css")) {
                String themeName = fileName.substring(0, fileName.length() - 4);
                if (themeDropdown.getItems().contains(themeName)) {
                    themeDropdown.getSelectionModel().select(themeName);
                } else {
                    themeDropdown.getSelectionModel().select("custom_theme");
                }
            } else {
                themeDropdown.getSelectionModel().select("custom_theme");
            }
        } else {
            themeDropdown.getSelectionModel().select("System Default");
        }
    }

    private void setupListeners() {
        // Dropdown selection listener
        themeDropdown.setOnAction(e -> {
            if (isUpdatingFromCode) return;
            
            String selected = themeDropdown.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            
            String themeVal;
            if ("Modern Dark".equals(selected)) {
                themeVal = "dark";
            } else if ("Glassmorphism Blue".equals(selected)) {
                themeVal = "glass";
            } else if ("Custom Accent".equals(selected)) {
                themeVal = "custom";
            } else if ("System Default".equals(selected)) {
                themeVal = "default";
            } else {
                // It's a custom CSS file theme (e.g. "ocean" or "custom_theme")
                themeVal = "css";
                File themesDir = new File(config.getConfigFile().getParentFile(), "themes");
                File selectedCssFile = new File(themesDir, selected + ".css");
                config.setThemeCssFilePath(selectedCssFile.getAbsolutePath());
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
        saveDebouncer = new Timeline(new KeyFrame(Duration.millis(500), e -> saveCssToFileNow()));
        saveDebouncer.setCycleCount(1);
    }

    private void saveCssToFileNow() {
        try {
            String cssPath = config.getThemeCssFilePath();
            Files.write(new File(cssPath).toPath(), pendingCssText.getBytes());
            statusLabel.setText("CSS saved and hot-reloaded successfully.");
            Platform.runLater(() -> themeManager.applyTheme(mainFrame.getMainScene()));
        } catch (Exception ex) {
            statusLabel.setText("Error saving CSS: " + ex.getMessage());
        }
    }

    private void setEditorText(String text) {
        pendingCssText = text;
        try {
            String escaped = text.replace("\\", "\\\\")
                                 .replace("`", "\\`")
                                 .replace("$", "\\$");
            cssWebView.getEngine().executeScript("setText(`" + escaped + "`)");
        } catch (Exception ignored) {}
    }

    private void loadSettingsToUi() {
        isUpdatingFromCode = true;
        try {
            populateThemeDropdown();

            // Accent color setup
            String hexColor = config.getThemeAccentColor();
            try {
                colorPicker.setValue(Color.web(hexColor));
            } catch (Exception ignored) {
                colorPicker.setValue(Color.valueOf("#0078D7"));
            }

            // Load Custom CSS file content
            String selected = themeDropdown.getSelectionModel().getSelectedItem();
            boolean isCustomCss = selected != null && 
                                 !"System Default".equals(selected) && 
                                 !"Modern Dark".equals(selected) && 
                                 !"Glassmorphism Blue".equals(selected) && 
                                 !"Custom Accent".equals(selected);
            if (isCustomCss) {
                File themesDir = new File(config.getConfigFile().getParentFile(), "themes");
                File cssFile = new File(themesDir, selected + ".css");
                if (cssFile.exists()) {
                    String cssText = new String(Files.readAllBytes(cssFile.toPath()));
                    setEditorText(cssText);
                }
            } else {
                File themesDir = new File(config.getConfigFile().getParentFile(), "themes");
                File cssFile = new File(themesDir, "custom_theme.css");
                if (cssFile.exists()) {
                    String cssText = new String(Files.readAllBytes(cssFile.toPath()));
                    setEditorText(cssText);
                }
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
        
        boolean isCustomCss = selected != null && 
                             !"System Default".equals(selected) && 
                             !"Modern Dark".equals(selected) && 
                             !"Glassmorphism Blue".equals(selected) && 
                             !"Custom Accent".equals(selected);

        colorPicker.setDisable(!isCustomAccent);
        cssWebView.setDisable(!isCustomCss);
        try {
            cssWebView.getEngine().executeScript("document.getElementById('textarea').disabled = " + !isCustomCss);
        } catch (Exception ignored) {}

        // Also update CSS content in editor when switching between custom CSS files
        if (isCustomCss && !isUpdatingFromCode) {
            try {
                File themesDir = new File(config.getConfigFile().getParentFile(), "themes");
                File cssFile = new File(themesDir, selected + ".css");
                if (cssFile.exists()) {
                    String cssText = new String(Files.readAllBytes(cssFile.toPath()));
                    setEditorText(cssText);
                }
            } catch (Exception e) {
                System.err.println("Error loading custom CSS text: " + e.getMessage());
            }
        }

        updateWebViewsTheme();
    }

    private void updateWebViewsTheme() {
        String selected = themeDropdown.getSelectionModel().getSelectedItem();
        boolean isDark = !"System Default".equals(selected);
        String themeClass = isDark ? "dark" : "light";
        
        try {
            cssWebView.getEngine().executeScript("setTheme('" + themeClass + "')");
        } catch (Exception ignored) {}
        
        try {
            helpPanel.getEngine().executeScript("setTheme('" + themeClass + "')");
        } catch (Exception ignored) {}
    }

    public class JavaConnector {
        public void onTextChange(String text) {
            pendingCssText = text;
            Platform.runLater(() -> saveDebouncer.playFromStart());
        }

        public void onColorPicked(String text) {
            pendingCssText = text;
            Platform.runLater(() -> saveCssToFileNow());
        }
    }
}
