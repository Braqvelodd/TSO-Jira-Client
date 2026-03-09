package tso.usmc.jira.util;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A utility class for common Jira-related helper functions.
 */
public class JiraUtils {

    /**
     * Searches a JSON string containing an array of transitions and returns the ID of the
     * transition that matches the given name (case-insensitive).
     *
     * @param transitionsJson The JSON string response from a /transitions API call.
     * @param transitionName  The display name of the transition to find (e.g., "In Progress", "Done").
     * @return The string ID of the found transition, or null if no match is found.
     */
    public static String findTransitionIdByName(String transitionsJson, String transitionName) {
        // Guard against null or empty input
        if (transitionsJson == null || transitionsJson.isEmpty() || transitionName == null) {
            return null;
        }

        JSONObject response = new JSONObject(transitionsJson);

        // Check if the 'transitions' key exists and is an array
        if (!response.has("transitions")) {
            return null;
        }

        JSONArray transitions = response.getJSONArray("transitions");
        for (int i = 0; i < transitions.length(); i++) {
            JSONObject t = transitions.getJSONObject(i);
            if (t.has("name") && t.getString("name").equalsIgnoreCase(transitionName)) {
                return t.getString("id");
            }
        }

        // Return null if no transition with that name was found
        return null;
    }

    /**
     * Cleans an issue key string by removing parentheses and extracting the standard 
     * PROJECT-123 format. This is useful for dealing with noisy data from tokens or 
     * manual input.
     */
    public static String cleanIssueKey(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return s;

        // Remove surrounding parentheses if they exist (e.g. "(TFS-123)")
        if (s.startsWith("(") && s.endsWith(")")) {
            s = s.substring(1, s.length() - 1).trim();
        }

        // Use regex to find the first standard Jira key (e.g. PROJECT-123 or 123-456)
        // Pattern: Starts with 1+ alphanum, hyphen, 1+ digits
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([A-Z0-9]+-[0-9]+)").matcher(s);
        if (m.find()) {
            return m.group(1);
        }
        
        return s;
    }

    /**
     * Converts simple date formats (YYYY-MM-DD or YYYYMMDD) into the full 
     * Jira-compliant ISO 8601 timestamp (e.g., 2026-01-01T09:00:00.000+0000).
     */
    public static String formatJiraDateTime(String input) {
        if (input == null || input.trim().isEmpty()) return input;
        String s = input.trim();
        
        // Already looks like a full timestamp
        if (s.contains("T") && s.contains(":")) return s;
        
        String offset = new java.text.SimpleDateFormat("Z").format(new java.util.Date());
        
        // YYYY-MM-DD
        if (s.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return s + "T09:00:00.000" + offset;
        }
        
        // YYYYMMDD
        if (s.matches("\\d{8}")) {
            String y = s.substring(0, 4);
            String m = s.substring(4, 6);
            String d = s.substring(6, 8);
            return y + "-" + m + "-" + d + "T09:00:00.000" + offset;
        }
        
        return s;
    }

    // You can add other static utility methods here in the future
    public static void setupExpandedView(javax.swing.JTextField field) {
        field.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    javax.swing.JTextArea area = new javax.swing.JTextArea(15, 50);
                    area.setText(field.getText());
                    area.setLineWrap(true);
                    area.setWrapStyleWord(true);
                    int result = javax.swing.JOptionPane.showConfirmDialog(field, new javax.swing.JScrollPane(area), 
                        "Expanded Input", javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
                    if (result == javax.swing.JOptionPane.OK_OPTION) {
                        field.setText(area.getText());
                    }
                }
            }
        });
        field.setToolTipText("Double-click to expand");
    }
}
