package tso.usmc.jira.util;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import tso.usmc.jira.ui.StyledBorder;

public class ThemeManager {
    private static final String MODERN_DARK_CSS = 
        "JPanel {\n" +
        "    background-color: #0f172a;\n" +
        "    border-color: #1e293b;\n" +
        "    border-width: 1px;\n" +
        "}\n" +
        "JLabel {\n" +
        "    color: #f8fafc;\n" +
        "}\n" +
        "JButton {\n" +
        "    background-color: #3b82f6;\n" +
        "    color: #ffffff;\n" +
        "    border-color: #2563eb;\n" +
        "    border-width: 1px;\n" +
        "    border-radius: 6px;\n" +
        "    padding: 6px;\n" +
        "}\n" +
        "JButton:hover {\n" +
        "    background-color: #2563eb;\n" +
        "    border-color: #1d4ed8;\n" +
        "}\n" +
        "JTextField, JTextArea, JComboBox, JTable {\n" +
        "    background-color: #1e293b;\n" +
        "    color: #f1f5f9;\n" +
        "    border-color: #334155;\n" +
        "    border-width: 1px;\n" +
        "    border-radius: 4px;\n" +
        "}\n" +
        "JTabbedPane {\n" +
        "    background-color: #0f172a;\n" +
        "    color: #f8fafc;\n" +
        "}\n";

    private static final String GLASSMORPHISM_BLUE_CSS = 
        "JPanel {\n" +
        "    background-color: rgba(255, 255, 255, 0.08);\n" +
        "    border-color: rgba(255, 255, 255, 0.15);\n" +
        "    border-width: 1px;\n" +
        "    border-radius: 12px;\n" +
        "}\n" +
        "JLabel {\n" +
        "    color: #ffffff;\n" +
        "}\n" +
        "JButton {\n" +
        "    background-color: rgba(59, 130, 246, 0.35);\n" +
        "    color: #ffffff;\n" +
        "    border-color: rgba(255, 255, 255, 0.2);\n" +
        "    border-width: 1px;\n" +
        "    border-radius: 8px;\n" +
        "    padding: 6px;\n" +
        "}\n" +
        "JButton:hover {\n" +
        "    background-color: rgba(59, 130, 246, 0.55);\n" +
        "    border-color: rgba(255, 255, 255, 0.35);\n" +
        "}\n" +
        "JTextField, JTextArea, JComboBox, JTable {\n" +
        "    background-color: rgba(15, 23, 42, 0.4);\n" +
        "    color: #f1f5f9;\n" +
        "    border-color: rgba(255, 255, 255, 0.12);\n" +
        "    border-width: 1px;\n" +
        "    border-radius: 6px;\n" +
        "}\n" +
        "JTabbedPane {\n" +
        "    background-color: rgba(255, 255, 255, 0.04);\n" +
        "    color: #ffffff;\n" +
        "}\n" +
        ".glass-panel {\n" +
        "    background-color: rgba(255, 255, 255, 0.12);\n" +
        "    border-color: rgba(255, 255, 255, 0.25);\n" +
        "    border-radius: 16px;\n" +
        "}\n";

    private final JiraConfig config;
    private List<StyleRule> currentRules = new ArrayList<>();
    private final Map<Component, Border> originalBorders = new HashMap<>();
    private final Map<Component, Color> originalBackgrounds = new HashMap<>();
    private final Map<Component, Color> originalForegrounds = new HashMap<>();
    private final Map<Component, Boolean> originalOpaques = new HashMap<>();

    public ThemeManager(JiraConfig config) {
        this.config = config;
        reloadTheme();
    }

    public void reloadTheme() {
        String activeTheme = config.getTheme();
        String cssContent = "";

        if ("dark".equals(activeTheme)) {
            cssContent = MODERN_DARK_CSS;
        } else if ("glass".equals(activeTheme)) {
            cssContent = GLASSMORPHISM_BLUE_CSS;
        } else if ("custom".equals(activeTheme)) {
            cssContent = generateCustomAccentCss(config.getThemeAccentColor());
        } else if ("css".equals(activeTheme)) {
            try {
                File cssFile = new File(config.getThemeCssFilePath());
                if (cssFile.exists()) {
                    cssContent = new String(Files.readAllBytes(cssFile.toPath()));
                }
            } catch (Exception e) {
                System.err.println("Error reading custom CSS file: " + e.getMessage());
            }
        }

        if (cssContent.isEmpty()) {
            currentRules.clear();
        } else {
            currentRules = parseCss(cssContent);
        }
    }

    private String generateCustomAccentCss(String accentColorHex) {
        Color baseAccent = parseColor(accentColorHex);
        if (baseAccent == null) baseAccent = new Color(0, 120, 215);

        // Calculate hover color (lightened or darkened)
        float[] hsb = Color.RGBtoHSB(baseAccent.getRed(), baseAccent.getGreen(), baseAccent.getBlue(), null);
        float hoverBrightness = hsb[2] > 0.5f ? hsb[2] - 0.15f : hsb[2] + 0.15f;
        Color hoverAccent = Color.getHSBColor(hsb[0], hsb[1], Math.max(0f, Math.min(hoverBrightness, 1f)));
        String hoverAccentHex = String.format("#%02x%02x%02x", hoverAccent.getRed(), hoverAccent.getGreen(), hoverAccent.getBlue());

        return "JPanel {\n" +
               "    background-color: #0f172a;\n" +
               "    border-color: #1e293b;\n" +
               "    border-width: 1px;\n" +
               "}\n" +
               "JLabel {\n" +
               "    color: #f8fafc;\n" +
               "}\n" +
               "JButton {\n" +
               "    background-color: " + accentColorHex + ";\n" +
               "    color: #ffffff;\n" +
               "    border-color: " + accentColorHex + ";\n" +
               "    border-width: 1px;\n" +
               "    border-radius: 6px;\n" +
               "    padding: 6px;\n" +
               "}\n" +
               "JButton:hover {\n" +
               "    background-color: " + hoverAccentHex + ";\n" +
               "    border-color: " + hoverAccentHex + ";\n" +
               "}\n" +
               "JTextField, JTextArea, JComboBox, JTable {\n" +
               "    background-color: #1e293b;\n" +
               "    color: #f1f5f9;\n" +
               "    border-color: " + accentColorHex + ";\n" +
               "    border-width: 1px;\n" +
               "    border-radius: 4px;\n" +
               "}\n" +
               "JTabbedPane {\n" +
               "    background-color: #0f172a;\n" +
               "    color: #f8fafc;\n" +
               "}\n";
    }

    public void applyTheme(JFrame frame) {
        String activeTheme = config.getTheme();
        if ("default".equals(activeTheme)) {
            // Restore native LaF styling
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                restoreOriginalStyles(frame);
                SwingUtilities.updateComponentTreeUI(frame);
            } catch (Exception ignored) {}
            return;
        }

        // Apply custom colors/styles recursively
        reloadTheme();
        originalBorders.putIfAbsent(frame, null); // Anchor
        applyThemeRecursive(frame);
        frame.repaint();
    }

    private void restoreOriginalStyles(Component comp) {
        if (originalBorders.containsKey(comp)) {
            if (comp instanceof JComponent) {
                ((JComponent) comp).setBorder(originalBorders.get(comp));
            }
        }
        if (originalBackgrounds.containsKey(comp)) {
            comp.setBackground(originalBackgrounds.get(comp));
        }
        if (originalForegrounds.containsKey(comp)) {
            comp.setForeground(originalForegrounds.get(comp));
        }
        if (originalOpaques.containsKey(comp)) {
            if (comp instanceof JComponent) {
                ((JComponent) comp).setOpaque(originalOpaques.get(comp));
            }
        }
        if (comp instanceof JButton) {
            ((JButton) comp).setContentAreaFilled(true);
        }

        // Remove mouse listeners if any
        if (comp instanceof JComponent) {
            MouseAdapter listener = (MouseAdapter) ((JComponent) comp).getClientProperty("themeHoverListener");
            if (listener != null) {
                comp.removeMouseListener(listener);
                ((JComponent) comp).putClientProperty("themeHoverListener", null);
            }
        }

        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                restoreOriginalStyles(child);
            }
        }
    }

    private void applyThemeRecursive(Component comp) {
        // 1. Capture original state if not already saved
        if (comp instanceof JComponent) {
            originalBorders.putIfAbsent(comp, ((JComponent) comp).getBorder());
            originalOpaques.putIfAbsent(comp, ((JComponent) comp).isOpaque());
        }
        originalBackgrounds.putIfAbsent(comp, comp.getBackground());
        originalForegrounds.putIfAbsent(comp, comp.getForeground());

        // 2. Setup Hover Event Listeners
        setupHoverListener(comp);

        // 3. Match rules and build styling declarations
        Map<String, String> style = matchRules(comp);

        // 4. Apply styles
        applyComponentStyles(comp, style);

        // 5. Recurse down tree
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                applyThemeRecursive(child);
            }
        }
    }

    private void setupHoverListener(Component comp) {
        if (comp instanceof JComponent) {
            JComponent jc = (JComponent) comp;
            if (jc.getClientProperty("themeHoverListener") == null) {
                MouseAdapter hoverListener = new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        jc.putClientProperty("isHovered", Boolean.TRUE);
                        reapplyStyleToSingleComponent(comp);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        jc.putClientProperty("isHovered", Boolean.FALSE);
                        reapplyStyleToSingleComponent(comp);
                    }
                };
                jc.addMouseListener(hoverListener);
                jc.putClientProperty("themeHoverListener", hoverListener);
            }
        }
    }

    private void reapplyStyleToSingleComponent(Component comp) {
        Map<String, String> style = matchRules(comp);
        applyComponentStyles(comp, style);
        comp.repaint();
    }

    private Map<String, String> matchRules(Component comp) {
        Map<String, String> accumulated = new HashMap<>();
        String className = comp.getClass().getSimpleName();
        String customClass = "";
        if (comp instanceof JComponent) {
            Object styleObj = ((JComponent) comp).getClientProperty("styleClass");
            if (styleObj != null) customClass = styleObj.toString().trim();
            if (comp.getName() != null && !comp.getName().isEmpty()) {
                customClass = customClass + " " + comp.getName();
            }
        }

        boolean isHovered = comp instanceof JComponent && Boolean.TRUE.equals(((JComponent) comp).getClientProperty("isHovered"));

        for (StyleRule rule : currentRules) {
            for (String selector : rule.selectors) {
                boolean matches = false;
                boolean isHoverSelector = selector.endsWith(":hover");
                String baseSelector = isHoverSelector ? selector.substring(0, selector.length() - 6).trim() : selector;

                // Check if hover status matches the rule requirement
                if (isHoverSelector && !isHovered) {
                    continue; 
                }

                if (baseSelector.startsWith(".")) {
                    String targetClass = baseSelector.substring(1);
                    if (customClass.contains(targetClass)) {
                        matches = true;
                    }
                } else {
                    if (className.equals(baseSelector) || isSubclassOf(comp.getClass(), baseSelector)) {
                        matches = true;
                    }
                }

                if (matches) {
                    accumulated.putAll(rule.properties);
                }
            }
        }

        return accumulated;
    }

    private boolean isSubclassOf(Class<?> clazz, String className) {
        Class<?> current = clazz;
        while (current != null) {
            if (current.getSimpleName().equals(className)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private void applyComponentStyles(Component comp, Map<String, String> style) {
        if (style.isEmpty()) return;

        // Foreground / Color
        if (style.containsKey("color") || style.containsKey("foreground-color")) {
            String colorStr = style.containsKey("color") ? style.get("color") : style.get("foreground-color");
            Color fg = parseColor(colorStr);
            if (fg != null) comp.setForeground(fg);
        }

        // Font
        Font currentFont = comp.getFont();
        if (currentFont != null) {
            String family = style.getOrDefault("font-family", currentFont.getFamily());
            int size = currentFont.getSize();
            if (style.containsKey("font-size")) {
                try {
                    size = Integer.parseInt(style.get("font-size").replace("px", "").replace("pt", "").trim());
                } catch (Exception ignored) {}
            }
            int styleVal = Font.PLAIN;
            if (style.containsKey("font-weight") && "bold".equalsIgnoreCase(style.get("font-weight").trim())) {
                styleVal = Font.BOLD;
            }
            comp.setFont(new Font(family, styleVal, size));
        }

        // Background, borders, and rounded corners
        Color bg = parseColor(style.get("background-color"));
        Color borderCol = parseColor(style.get("border-color"));
        int borderWidth = 0;
        if (style.containsKey("border-width")) {
            try {
                borderWidth = Integer.parseInt(style.get("border-width").replace("px", "").trim());
            } catch (Exception ignored) {}
        }
        int borderRadius = 0;
        if (style.containsKey("border-radius")) {
            try {
                borderRadius = Integer.parseInt(style.get("border-radius").replace("px", "").trim());
            } catch (Exception ignored) {}
        }

        // Padding
        Insets padding = null;
        if (style.containsKey("padding")) {
            try {
                int pad = Integer.parseInt(style.get("padding").replace("px", "").trim());
                padding = new Insets(pad, pad, pad, pad);
            } catch (Exception ignored) {}
        }

        if (comp instanceof JComponent) {
            JComponent jc = (JComponent) comp;

            // Handle title text color on TitledBorder
            Border currentBorder = jc.getBorder();
            if (currentBorder instanceof TitledBorder) {
                TitledBorder tb = (TitledBorder) currentBorder;
                Color fgColor = parseColor(style.get("color"));
                if (fgColor != null) {
                    tb.setTitleColor(fgColor);
                }
            }

            if (borderRadius > 0 || (bg != null && bg.getAlpha() < 255) || (borderCol != null && borderWidth > 0)) {
                // To support transparency or rounded corners, turn off native background opacity painting
                jc.setOpaque(false);
                if (comp instanceof JButton) {
                    ((JButton) comp).setContentAreaFilled(false);
                }

                // If titled border exists, wrap it inside or preserve it. Otherwise set custom styled border
                if (!(currentBorder instanceof TitledBorder)) {
                    jc.setBorder(new StyledBorder(bg, borderCol, borderWidth, borderRadius, padding));
                }
            } else {
                // Standard solid rectangle rendering
                jc.setOpaque(true);
                if (bg != null) comp.setBackground(bg);
                if (comp instanceof JButton) {
                    ((JButton) comp).setContentAreaFilled(true);
                }
                
                if (borderCol != null && borderWidth > 0 && !(currentBorder instanceof TitledBorder)) {
                    jc.setBorder(BorderFactory.createLineBorder(borderCol, borderWidth));
                } else if (!(currentBorder instanceof TitledBorder)) {
                    // Restore original border if no border specified in style
                    jc.setBorder(originalBorders.get(comp));
                }
            }
        } else {
            // AWT Component
            if (bg != null) comp.setBackground(bg);
        }
    }

    // --- CSS Parsing Logic ---
    private List<StyleRule> parseCss(String css) {
        List<StyleRule> rules = new ArrayList<>();
        // Remove comments
        css = css.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", "");
        
        String[] blocks = css.split("\\}");
        for (String block : blocks) {
            String[] parts = block.split("\\{");
            if (parts.length < 2) continue;

            String selectorsStr = parts[0].trim();
            String propertiesStr = parts[1].trim();

            if (selectorsStr.isEmpty() || propertiesStr.isEmpty()) continue;

            StyleRule rule = new StyleRule();
            for (String selector : selectorsStr.split(",")) {
                rule.selectors.add(selector.trim());
            }

            for (String declaration : propertiesStr.split(";")) {
                String[] declParts = declaration.split(":", 2);
                if (declParts.length < 2) continue;
                String propertyName = declParts[0].trim().toLowerCase();
                String propertyValue = declParts[1].trim();
                rule.properties.put(propertyName, propertyValue);
            }

            rules.add(rule);
        }
        return rules;
    }

    public static Color parseColor(String str) {
        if (str == null) return null;
        str = str.trim().toLowerCase();
        if (str.equals("transparent")) return new Color(0, 0, 0, 0);
        
        if (str.startsWith("#")) {
            String hex = str.substring(1);
            try {
                if (hex.length() == 3) {
                    String r = hex.substring(0, 1);
                    String g = hex.substring(1, 2);
                    String b = hex.substring(2, 3);
                    hex = r + r + g + g + b + b;
                }
                if (hex.length() == 6) {
                    return new Color(Integer.parseInt(hex, 16));
                } else if (hex.length() == 8) {
                    // AA RRGGBB format
                    long val = Long.parseLong(hex, 16);
                    int a = (int) ((val >> 24) & 0xFF);
                    int r = (int) ((val >> 16) & 0xFF);
                    int g = (int) ((val >> 8) & 0xFF);
                    int b = (int) (val & 0xFF);
                    return new Color(r, g, b, a);
                }
            } catch (Exception ignored) {}
        } else if (str.startsWith("rgb")) {
            try {
                int start = str.indexOf("(");
                int end = str.indexOf(")");
                if (start != -1 && end != -1) {
                    String[] parts = str.substring(start + 1, end).split(",");
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    if (parts.length > 3) {
                        float a = Float.parseFloat(parts[3].trim());
                        int alpha = Math.round(a * 255);
                        return new Color(r, g, b, alpha);
                    } else {
                        return new Color(r, g, b);
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static class StyleRule {
        public final List<String> selectors = new ArrayList<>();
        public final Map<String, String> properties = new HashMap<>();
    }
}
