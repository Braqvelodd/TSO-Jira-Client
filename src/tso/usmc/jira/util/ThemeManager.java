package tso.usmc.jira.util;

import java.io.File;
import javafx.scene.Scene;

public class ThemeManager {
    private final JiraConfig config;

    public ThemeManager(JiraConfig config) {
        this.config = config;
    }

    public void applyTheme(Scene scene) {
        if (scene == null) return;
        scene.getStylesheets().clear();

        String activeTheme = config.getTheme();

        if ("default".equals(activeTheme)) {
            scene.getStylesheets().add(getClass().getResource("/themes/default.css").toExternalForm());
            scene.getRoot().setStyle(""); // Clear inline styles
        } else if ("dark".equals(activeTheme)) {
            scene.getStylesheets().add(getClass().getResource("/themes/dark.css").toExternalForm());
            scene.getRoot().setStyle("");
        } else if ("glass".equals(activeTheme)) {
            scene.getStylesheets().add(getClass().getResource("/themes/glass.css").toExternalForm());
            scene.getRoot().setStyle("");
        } else if ("custom".equals(activeTheme)) {
            scene.getStylesheets().add(getClass().getResource("/themes/custom.css").toExternalForm());
            // Programmatically set the custom accent color variable
            String accentColor = config.getThemeAccentColor();
            scene.getRoot().setStyle("-custom-accent: " + accentColor + ";");
        } else if ("css".equals(activeTheme)) {
            try {
                File cssFile = new File(config.getThemeCssFilePath());
                if (cssFile.exists()) {
                    scene.getStylesheets().add(cssFile.toURI().toURL().toExternalForm());
                }
                scene.getRoot().setStyle("");
            } catch (Exception e) {
                System.err.println("Error applying custom CSS: " + e.getMessage());
            }
        }
    }
}
