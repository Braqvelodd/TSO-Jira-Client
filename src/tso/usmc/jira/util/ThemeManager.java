package tso.usmc.jira.util;

import java.io.File;
import javafx.scene.Scene;
import javafx.scene.web.WebView;

public class ThemeManager {
    private final JiraConfig config;

    public ThemeManager(JiraConfig config) {
        this.config = config;
    }

    public void applyTheme(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().clear();

        String activeTheme = config.getTheme();
        String stylesheetUrl = null;

        if ("default".equals(activeTheme)) {
            stylesheetUrl = getClass().getResource("/themes/default.css").toExternalForm();
            scene.getStylesheets().add(stylesheetUrl);
            scene.getRoot().setStyle(""); // Clear inline styles
        } else if ("dark".equals(activeTheme)) {
            stylesheetUrl = getClass().getResource("/themes/dark.css").toExternalForm();
            scene.getStylesheets().add(stylesheetUrl);
            scene.getRoot().setStyle("");
        } else if ("glass".equals(activeTheme)) {
            stylesheetUrl = getClass().getResource("/themes/glass.css").toExternalForm();
            scene.getStylesheets().add(stylesheetUrl);
            scene.getRoot().setStyle("");
        } else if ("custom".equals(activeTheme)) {
            stylesheetUrl = getClass().getResource("/themes/custom.css").toExternalForm();
            scene.getStylesheets().add(stylesheetUrl);
            // Programmatically set the custom accent color variable
            String accentColor = config.getThemeAccentColor();
            scene.getRoot().setStyle("-custom-accent: " + accentColor + ";");
        } else if ("css".equals(activeTheme)) {
            try {
                File cssFile = new File(config.getThemeCssFilePath());
                if (cssFile.exists()) {
                    stylesheetUrl = cssFile.toURI().toURL().toExternalForm();
                    scene.getStylesheets().add(stylesheetUrl);
                }
                scene.getRoot().setStyle("");
            } catch (Exception e) {
                System.err.println("Error applying custom CSS: " + e.getMessage());
            }
        }

        if (stylesheetUrl != null) {
            applyThemeToWebViews(scene.getRoot(), stylesheetUrl);
        }
    }

    private void applyThemeToWebViews(javafx.scene.Parent root, String stylesheetUrl) {
        if (root == null) return;
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

