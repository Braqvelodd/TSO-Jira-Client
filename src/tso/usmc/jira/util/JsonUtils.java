package tso.usmc.jira.util;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class JsonUtils {
    // In your JsonUtils.java file

    public static String buildManualJson(String project, String parent, String summary, String description, String issueType, String assignee, String component, String duedate) {
        JSONObject payload = new JSONObject();
        JSONObject fields = new JSONObject();

        fields.put("project", new JSONObject().put("key", project));
        fields.put("parent", new JSONObject().put("key", parent));
        fields.put("summary", summary);
        fields.put("description", description);
        fields.put("issuetype", new JSONObject().put("name", issueType));

        if (assignee != null && !assignee.isEmpty()) {
            fields.put("assignee", new JSONObject().put("name", assignee));
        }

        if (component != null && !component.isEmpty()) {
            JSONArray components = new JSONArray();
            components.put(new JSONObject().put("name", component));
            fields.put("components", components);
        }
        
        // Add this new block to handle the due date
        if (duedate != null && !duedate.isEmpty()) {
            fields.put("duedate", duedate); // Jira API field for due date is "duedate"
        }

        payload.put("fields", fields);
        return payload.toString();
    }

    /**
     * Builds JSON for a single issue. 
     * Forces Project and Parent to UpperCase as requested.
     */
    // public static String buildManualJson(String projectKey, String parent, String summary, String desc, String type, String assignee, String components) {
    //     StringBuilder json = new StringBuilder();
    //     json.append("{ \"fields\": { ");
    //     json.append("\"project\": {\"key\": \"").append(escape(projectKey.toUpperCase())).append("\"}, ");
    //     json.append("\"summary\": \"").append(escape(summary)).append("\", ");
    //     json.append("\"description\": \"").append(escape(desc)).append("\", ");
    //     json.append("\"issuetype\": {\"name\": \"").append(escape(type)).append("\"} ");

    //     if (parent != null && !parent.trim().isEmpty()) {
    //         json.append(", \"parent\": {\"key\": \"").append(escape(parent.toUpperCase())).append("\"} ");
    //     }

    //     if (assignee != null && !assignee.trim().isEmpty()) {
    //         json.append(", \"assignee\": {\"name\": \"").append(escape(assignee)).append("\"} ");
    //     }

    //     // --- COMPONENTS LOGIC (Comma Separated) ---
    //     if (components != null && !components.isEmpty()) {
    //         json.append(", \"components\": [");
    //         String[] parts = components.split(",");
    //         for (int i = 0; i < parts.length; i++) {
    //             String compName = parts[i].trim();
    //             if (!compName.isEmpty()) {
    //                 json.append("{\"name\": \"").append(escape(compName)).append("\"}");
    //                 if (i < parts.length - 1) json.append(",");
    //             }
    //         }   
    //         if (duedate != null && !duedate.isEmpty()) {
    //             fields.put("duedate", duedate); // Jira API field for due date is "duedate"
    //         }
    //         // Clean up trailing comma if last part was empty
    //         if (json.toString().endsWith(",")) {
    //             json.setLength(json.length() - 1);
    //         }
    //         json.append("]");
    //     }

    //     json.append("}}");
    //     return json.toString();
    // }

    /**
     * Wraps multiple issue objects into a single bulk update request.
     */
    public static String buildBulkJson(List<String> individualJsons) {
        StringBuilder bulk = new StringBuilder();
        bulk.append("{ \"issueUpdates\": [");
        for (int i = 0; i < individualJsons.size(); i++) {
            bulk.append(individualJsons.get(i));
            if (i < individualJsons.size() - 1) bulk.append(",");
        }
        bulk.append("] }");
        return bulk.toString();
    }

    /**
     * Builds JSON for a transition using the transition Name.
     */
    public static String buildTransitionJson(String transitionName) {
        return "{ \"transition\": { \"name\": \"" + escape(transitionName) + "\" } }";
    }

    /**
     * Extracts a value for a specific key from a JSON string.
     */
    public static String getFieldValue(String json, String field) {
        try {
            String key = "\"" + field + "\":\"";
            int start = json.indexOf(key);
            if (start == -1) return "";
            start += key.length();
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Formats JSON for the UI display.
     */
    public static String prettyPrintJson(String json) {
        if (json == null || json.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int indentLevel = 0;
        boolean inQuotes = false;
        for (char c : json.toCharArray()) {
            if (c == '\"') inQuotes = !inQuotes;
            if (!inQuotes) {
                if (c == '{' || c == '[') {
                    sb.append(c).append("\n").append(indent(++indentLevel));
                    continue;
                } else if (c == '}' || c == ']') {
                    sb.append("\n").append(indent(--indentLevel)).append(c);
                    continue;
                } else if (c == ',') {
                    sb.append(c).append("\n").append(indent(indentLevel));
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String indent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) sb.append("    ");
        return sb.toString();
    }
}