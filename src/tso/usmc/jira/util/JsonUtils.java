package tso.usmc.jira.util;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Standardized utility class for Jira JSON operations using the org.json library.
 * This class replaces all manual string concatenation and index-based parsing
 * to ensure data integrity and prevent JSON injection.
 */
public class JsonUtils {

    /**
     * Builds JSON for a single issue creation.
     */
    public static String buildManualJson(String project, String parent, String summary, String description, String issueType, String assignee, String component, String duedate) {
        JSONObject fields = new JSONObject();

        fields.put("project", new JSONObject().put("key", project));
        
        if (parent != null && !parent.trim().isEmpty()) {
            fields.put("parent", new JSONObject().put("key", parent.trim()));
        }
        
        fields.put("summary", summary);
        fields.put("description", description);
        fields.put("issuetype", new JSONObject().put("name", issueType));

        if (assignee != null && !assignee.trim().isEmpty()) {
            fields.put("assignee", new JSONObject().put("name", assignee.trim()));
        }

        if (component != null && !component.trim().isEmpty()) {
            JSONArray componentsArr = new JSONArray();
            String[] parts = component.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    componentsArr.put(new JSONObject().put("name", trimmed));
                }
            }
            if (componentsArr.length() > 0) {
                fields.put("components", componentsArr);
            }
        }
        
        if (duedate != null && !duedate.trim().isEmpty()) {
            fields.put("duedate", duedate.trim());
        }

        return new JSONObject().put("fields", fields).toString();
    }

    /**
     * Wraps multiple issue objects into a single bulk update request.
     * Takes a list of issue JSON strings and combines them under an "issueUpdates" key.
     */
    public static String buildBulkJson(List<String> individualJsons) {
        JSONArray issueUpdates = new JSONArray();
        for (String json : individualJsons) {
            try {
                if (json != null && !json.trim().isEmpty()) {
                    issueUpdates.put(new JSONObject(json));
                }
            } catch (Exception e) {
                // Silently skip invalid JSON issues to prevent total failure
            }
        }
        return new JSONObject().put("issueUpdates", issueUpdates).toString();
    }

    /**
     * Builds JSON for a transition request using the transition Name.
     */
    public static String buildTransitionJson(String transitionName) {
        return new JSONObject()
                .put("transition", new JSONObject().put("name", transitionName))
                .toString();
    }

    /**
     * Extracts a value for a specific key from a JSON string.
     * Supports dot notation for nested fields (e.g., "fields.summary").
     */
    public static String getFieldValue(String json, String field) {
        if (json == null || json.isEmpty() || field == null || field.isEmpty()) return "";
        try {
            JSONObject obj = new JSONObject(json);
            
            // Direct path
            if (obj.has(field)) {
                return String.valueOf(obj.get(field));
            }

            // Nested path (dot notation)
            if (field.contains(".")) {
                String[] parts = field.split("\\.");
                Object current = obj;
                for (int i = 0; i < parts.length; i++) {
                    if (!(current instanceof JSONObject)) return "";
                    JSONObject curObj = (JSONObject) current;
                    if (!curObj.has(parts[i])) return "";
                    current = curObj.get(parts[i]);
                }
                return String.valueOf(current);
            }
        } catch (Exception e) {
            // Return empty string on parsing or path failure to match legacy behavior
        }
        return "";
    }

    /**
     * Formats JSON for UI display using the org.json standard implementation.
     * Handles both JSONObject and JSONArray strings.
     */
    public static String prettyPrintJson(String json) {
        if (json == null || json.trim().isEmpty()) return "";
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("[")) {
                return new JSONArray(trimmed).toString(4);
            } else if (trimmed.startsWith("{")) {
                return new JSONObject(trimmed).toString(4);
            }
        } catch (Exception e) {
            // Not a valid JSON structure, return as is
        }
        return json;
    }
}
