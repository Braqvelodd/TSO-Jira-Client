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
        return replaceTokens(input, issueJson, null);
    }

    public static String replaceTokens(String input, JSONObject issueJson, java.util.Map<String, String> variables) {
        if (input == null || !input.contains("{{")) return input;

        Matcher matcher = TOKEN_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String fullContent = matcher.group(1).trim();
            String value = null;
            
            if (fullContent.startsWith("COALESCE(") && fullContent.endsWith(")")) {
                String argsStr = fullContent.substring(9, fullContent.length() - 1);
                String[] args = argsStr.split(",");
                for (String arg : args) {
                    String path = arg.trim();
                    
                    // 1. Check for quoted literals
                    if ((path.startsWith("\"") && path.endsWith("\"")) || (path.startsWith("'") && path.endsWith("'"))) {
                        value = path.substring(1, path.length() - 1);
                        break;
                    }
                    
                    // 2. Check Issue JSON
                    value = resolvePath(issueJson, path);
                    if (value == null && !path.startsWith("fields.")) {
                        value = resolvePath(issueJson, "fields." + path);
                    }
                    
                    // 3. Check Variables (Prompts)
                    if ((value == null || value.isEmpty() || value.equalsIgnoreCase("null")) && variables != null) {
                        value = variables.get(path);
                    }

                    if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("null")) break;
                }
            } else {
                String path = fullContent;
                value = resolvePath(issueJson, path);
                if (value == null && !path.startsWith("fields.")) {
                    value = resolvePath(issueJson, "fields." + path);
                }
                if ((value == null || value.isEmpty() || value.equalsIgnoreCase("null")) && variables != null) {
                    value = variables.get(path);
                }
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
