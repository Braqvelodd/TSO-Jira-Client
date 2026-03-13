package tso.usmc.jira.util;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A utility class for core Jira-related helper functions.
 * UI-specific utilities have been moved to tso.usmc.jira.ui.SwingUtils.
 */
public class JiraUtils {

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

    /**
     * Opens the default system browser to the Jira issue page.
     */
    public static void browseIssue(String baseUrl, String key) {
        if (key == null || key.trim().isEmpty() || baseUrl == null || baseUrl.trim().isEmpty()) return;
        try {
            String url = baseUrl + "/browse/" + key.trim();
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper to find a transition ID by its display name.
     * @deprecated Use JiraIssueService.findTransitionIdByName instead.
     */
    @Deprecated
    public static String findTransitionIdByName(String jsonResponse, String transitionName) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) return null;
        JSONObject response = new JSONObject(jsonResponse);
        if (!response.has("transitions")) return null;
        
        JSONArray transitions = response.getJSONArray("transitions");
        for (int i = 0; i < transitions.length(); i++) {
            JSONObject t = transitions.getJSONObject(i);
            if (t.getString("name").equalsIgnoreCase(transitionName)) {
                return t.getString("id");
            }
        }
        return null;
    }
}
