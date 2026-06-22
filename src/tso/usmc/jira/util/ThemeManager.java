package tso.usmc.jira.util;

import java.io.File;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class ThemeManager {
    private final JiraConfig config;

    public ThemeManager(JiraConfig config) {
        this.config = config;
    }

    public String getThemeStylesheetUrl() {
        String activeTheme = config.getTheme();
        if ("default".equals(activeTheme)) {
            return getClass().getResource("/themes/default.css").toExternalForm();
        } else if ("dark".equals(activeTheme)) {
            return getClass().getResource("/themes/dark.css").toExternalForm();
        } else if ("glass".equals(activeTheme)) {
            return getClass().getResource("/themes/glass.css").toExternalForm();
        } else if ("custom".equals(activeTheme)) {
            return getClass().getResource("/themes/custom.css").toExternalForm();
        } else if ("css".equals(activeTheme)) {
            try {
                File cssFile = new File(config.getThemeCssFilePath());
                if (cssFile.exists()) {
                    return cssFile.toURI().toURL().toExternalForm();
                }
            } catch (Exception e) {
                System.err.println("Error resolving custom CSS: " + e.getMessage());
            }
        }
        return null;
    }

    public void applyTheme(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().clear();

        String activeTheme = config.getTheme();
        String stylesheetUrl = getThemeStylesheetUrl();

        if (stylesheetUrl != null) {
            scene.getStylesheets().add(stylesheetUrl);
            if ("custom".equals(activeTheme) || "css".equals(activeTheme)) {
                String accentColor = config.getThemeAccentColor();
                scene.getRoot().setStyle("-custom-accent: " + accentColor + ";");
            } else {
                scene.getRoot().setStyle(""); // Clear inline styles
            }

            String webViewStylesheetUrl = getThemeStylesheetAsDataUri(activeTheme, stylesheetUrl);
            applyThemeToWebViews(scene.getRoot(), webViewStylesheetUrl);
        }
    }

    public void applyThemeToWebView(WebView webView) {
        if (webView == null) return;
        String activeTheme = config.getTheme();
        String stylesheetUrl = getThemeStylesheetUrl();
        if (stylesheetUrl != null) {
            String webViewStylesheetUrl = getThemeStylesheetAsDataUri(activeTheme, stylesheetUrl);
            try {
                webView.getEngine().setUserStyleSheetLocation(webViewStylesheetUrl);
            } catch (Exception e) {
                System.err.println("Error setting webview stylesheet: " + e.getMessage());
            }
        }
    }

    private String getThemeStylesheetAsDataUri(String activeTheme, String originalUrl) {
        try {
            String cssContent = "";
            if ("css".equals(activeTheme)) {
                File cssFile = new File(config.getThemeCssFilePath());
                if (cssFile.exists()) {
                    cssContent = new String(java.nio.file.Files.readAllBytes(cssFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                }
            } else {
                String resourcePath = null;
                if ("default".equals(activeTheme)) resourcePath = "/themes/default.css";
                else if ("dark".equals(activeTheme)) resourcePath = "/themes/dark.css";
                else if ("glass".equals(activeTheme)) resourcePath = "/themes/glass.css";
                else if ("custom".equals(activeTheme)) resourcePath = "/themes/custom.css";

                if (resourcePath != null) {
                    try (java.io.InputStream is = getClass().getResourceAsStream(resourcePath)) {
                        if (is != null) {
                            try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                while ((bytesRead = is.read(buffer)) != -1) {
                                    bos.write(buffer, 0, bytesRead);
                                }
                                cssContent = bos.toString("UTF-8");
                            }
                        }
                    }
                }
            }

            if (cssContent == null || cssContent.isEmpty()) {
                return originalUrl;
            }

            // Replace custom accent variable with its actual color code for WebView compatibility
            if (cssContent.contains("-custom-accent")) {
                String accentColor = config.getThemeAccentColor();
                cssContent = cssContent.replace("-custom-accent", accentColor);
            }

            String base64Css = java.util.Base64.getEncoder().encodeToString(cssContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "data:text/css;charset=utf-8;base64," + base64Css;
        } catch (Exception e) {
            System.err.println("Error generating data URI for WebView stylesheet: " + e.getMessage());
            return originalUrl;
        }
    }

    private void applyThemeToWebViews(javafx.scene.Parent root, String stylesheetUrl) {
        if (root == null) return;

        // If the root node itself is a WebView, apply the stylesheet directly
        if (root instanceof WebView) {
            if (!isInsideThemeSettings(root)) {
                try {
                    ((WebView) root).getEngine().setUserStyleSheetLocation(stylesheetUrl);
                } catch (Exception e) {
                    System.err.println("Error setting webview stylesheet: " + e.getMessage());
                }
            }
            return;
        }

        // If the root is a TabPane, traverse all tabs (even if inactive/not in active scene graph)
        if (root instanceof TabPane) {
            TabPane tabPane = (TabPane) root;
            for (Tab tab : tabPane.getTabs()) {
                javafx.scene.Node content = tab.getContent();
                if (content instanceof javafx.scene.Parent) {
                    applyThemeToWebViews((javafx.scene.Parent) content, stylesheetUrl);
                }
            }
        }

        for (javafx.scene.Node node : root.getChildrenUnmodifiable()) {
            if (node instanceof WebView) {
                if (!isInsideThemeSettings(node)) {
                    try {
                        ((WebView) node).getEngine().setUserStyleSheetLocation(stylesheetUrl);
                    } catch (Exception e) {
                        System.err.println("Error setting webview stylesheet: " + e.getMessage());
                    }
                }
            } else if (node instanceof javafx.scene.Parent) {
                applyThemeToWebViews((javafx.scene.Parent) node, stylesheetUrl);
            }
        }
    }

    private boolean isInsideThemeSettings(javafx.scene.Node node) {
        javafx.scene.Parent parent = node.getParent();
        while (parent != null) {
            if (parent.getClass().getSimpleName().equals("ThemeSettingsPanel")) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }
}

