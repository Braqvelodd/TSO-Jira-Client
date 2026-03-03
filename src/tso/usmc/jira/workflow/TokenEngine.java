package tso.usmc.jira.workflow;

import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenEngine {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

    /**
     * Replaces tokens with values from the issue JSON.
     * Supports nested paths (e.g., {{fields.summary}}, {{fields.issuetype.name}}).
     */
    public static String replaceTokens(String input, JSONObject issueJson) {
        if (input == null || !input.contains("{{")) return input;

        Matcher matcher = TOKEN_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String path = matcher.group(1).trim();
            String value = resolvePath(issueJson, path);
            
            // If not found in root, try looking inside the "fields" object automatically
            if (value == null && !path.startsWith("fields.")) {
                value = resolvePath(issueJson, "fields." + path);
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : matcher.group(0)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolvePath(JSONObject json, String path) {
        try {
            Object current = json;
            String[] parts = path.split("\\.");
            for (String part : parts) {
                if (current instanceof JSONObject) {
                    JSONObject obj = (JSONObject) current;
                    if (!obj.has(part) || obj.isNull(part)) return null;
                    current = obj.get(part);
                } else {
                    return null;
                }
            }
            return current != null ? current.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
