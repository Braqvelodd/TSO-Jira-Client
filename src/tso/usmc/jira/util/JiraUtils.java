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

    /**
     * Expands team mentions (e.g. @lifeline, [~team.lifeline]) in a text string
     * to their individual member list mentions (e.g. [~HULL.JAMES.DOUGLAS] [~user1] ...).
     */
    public static String expandTeamMentions(String text, JiraConfig config) {
        if (text == null || text.trim().isEmpty() || config == null) return text;
        
        String[] teamKeys = config.getWorkflowTeamKeys();
        if (teamKeys == null || teamKeys.length == 0) return text;
        
        String result = text;
        for (String teamKey : teamKeys) {
            String membersStr = config.getTeamProperty(teamKey, "members");
            if (membersStr == null || membersStr.trim().isEmpty()) continue;
            
            // Build the replacement list
            StringBuilder replacement = new StringBuilder();
            for (String member : membersStr.split(",")) {
                String m = member.trim();
                if (!m.isEmpty()) {
                    if (replacement.length() > 0) replacement.append(" ");
                    replacement.append("[~").append(m).append("]");
                }
            }
            
            if (replacement.length() == 0) continue;
            
            String rep = replacement.toString();
            String escapedKey = java.util.regex.Pattern.quote(teamKey);
            
            result = result.replaceAll("(?i)\\[~team\\." + escapedKey + "\\]", rep);
            result = result.replaceAll("(?i)\\[~" + escapedKey + "\\]", rep);
            result = result.replaceAll("(?i)@team\\." + escapedKey + "\\b", rep);
            result = result.replaceAll("(?i)@" + escapedKey + "\\b", rep);
        }
        return result;
    }

    /**
     * Recursively traverses a JSON structure (JSONObject or JSONArray) and expands team mentions in all string values.
     */
    public static void expandTeamMentionsInJson(Object json, JiraConfig config) {
        if (json == null || config == null) return;
        
        if (json instanceof JSONObject) {
            JSONObject obj = (JSONObject) json;
            for (String key : new java.util.ArrayList<>(obj.keySet())) {
                Object val = obj.get(key);
                if (val instanceof String) {
                    obj.put(key, expandTeamMentions((String) val, config));
                } else if (val != null) {
                    expandTeamMentionsInJson(val, config);
                }
            }
        } else if (json instanceof JSONArray) {
            JSONArray arr = (JSONArray) json;
            for (int i = 0; i < arr.length(); i++) {
                Object val = arr.opt(i);
                if (val instanceof String) {
                    arr.put(i, expandTeamMentions((String) val, config));
                } else if (val != null) {
                    expandTeamMentionsInJson(val, config);
                }
            }
        }
    }
}
