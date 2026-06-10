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
    private Label statusLabel;
    private Timeline saveDebouncer;
    private boolean isUpdatingFromCode = false;
    private String pendingCssText = "";

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
        "    padding-right: 50px;\n" +
        "    box-sizing: border-box;\n" +
        "    white-space: pre;\n" +
        "    overflow-y: scroll;\n" +
        "    overflow-x: auto;\n" +
        "  }\n" +
        "  #pickers-gutter {\n" +
        "    position: absolute;\n" +
        "    top: 0;\n" +
        "    right: 15px;\n" +
        "    width: 30px;\n" +
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
        "        while ((match = colorPattern.exec(lineText)) !== null) {\n" +
        "          const colorStr = match[0];\n" +
        "          const matchIndex = match.index;\n" +
        "          const yPos = padding + (lineIndex * lineHeight) - scrollOffset;\n" +
        "          if (yPos >= 0 && yPos <= textarea.clientHeight) {\n" +
        "            createPicker(colorStr, lineIndex, matchIndex, colorStr.length, yPos);\n" +
        "          }\n" +
        "        }\n" +
        "      });\n" +
        "    }\n" +
        "\n" +
        "    function createPicker(colorStr, lineIndex, charIndex, length, yPos) {\n" +
        "      const wrapper = document.createElement('div');\n" +
        "      wrapper.className = 'color-picker-wrapper';\n" +
        "      wrapper.style.top = yPos + 'px';\n" +
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
        "      input.addEventListener('input', (e) => {\n" +
        "        const newHex = e.target.value;\n" +
        "        const formatted = formatColorLike(newHex, colorStr);\n" +
        "        updateColorInText(lineIndex, charIndex, length, formatted);\n" +
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
        "    function updateColorInText(lineIndex, charIndex, length, newColorStr) {\n" +
        "      const lines = textarea.value.split('\\n');\n" +
        "      const lineText = lines[lineIndex];\n" +
        "      const before = lineText.substring(0, charIndex);\n" +
        "      const after = lineText.substring(charIndex + length);\n" +
        "      lines[lineIndex] = before + newColorStr + after;\n" +
        "      \n" +
        "      textarea.value = lines.join('\\n');\n" +
        "      \n" +
        "      if (window.javaConnector) {\n" +
        "        window.javaConnector.onColorPicked(textarea.value);\n" +
        "      }\n" +
        "      parseColors();\n" +
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

        cssWebView = new WebView();
        cssWebView.getEngine().loadContent(EDITOR_HTML);
        cssWebView.getEngine().getLoadWorker().stateProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) cssWebView.getEngine().executeScript("window");
                window.setMember("javaConnector", new JavaConnector());
                if (pendingCssText != null && !pendingCssText.isEmpty()) {
                    setEditorText(pendingCssText);
                }
            }
        });
        centerPanel.setCenter(cssWebView);

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
                setEditorText(cssText);
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
        cssWebView.setDisable(!isCustomCss);
        try {
            cssWebView.getEngine().executeScript("document.getElementById('textarea').disabled = " + !isCustomCss);
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
