package tso.usmc.jira.workflow;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenEngine {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{(.+?)\\}\\}");

    /**
     * Replaces tokens with values from the issue JSON.
     */
    public static String replaceTokens(String input, JSONObject issueJson) {
        return replaceTokens(input, issueJson, null, false);
    }

    /**
     * Replaces tokens with values from the issue JSON and variables.
     */
    public static String replaceTokens(String input, JSONObject issueJson, Map<String, String> variables) {
        return replaceTokens(input, issueJson, variables, false);
    }

    public static String replaceTokens(String input, JSONObject issueJson, Map<String, String> variables, boolean preserveUnresolved) {
        Map<String, JSONObject> contexts = new HashMap<>();
        if (issueJson != null) {
            contexts.put("issue", issueJson);
        }
        return replaceTokens(input, contexts, variables, preserveUnresolved);
    }

    /**
     * More robust replacement supporting multiple JSON contexts.
     * Prefixes like {{issue.summary}} or {{last.key}} can be used.
     * If no prefix is used, it defaults to the 'issue' context.
     */
    public static String replaceTokens(String input, Map<String, JSONObject> contexts, Map<String, String> variables) {
        return replaceTokens(input, contexts, variables, false);
    }

    public static String replaceTokens(String input, Map<String, JSONObject> contexts, Map<String, String> variables, boolean preserveUnresolved) {
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
                    value = resolveToken(arg.trim(), contexts, variables);
                    if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("null")) break;
                }
            } else {
                value = resolveToken(fullContent, contexts, variables);
            }

            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else if (preserveUnresolved) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("{{" + fullContent + "}}"));
            } else {
                // If token is unresolved, we replace it with an empty string to avoid breaking Jira API calls with literal "{{token}}" strings.
                matcher.appendReplacement(sb, Matcher.quoteReplacement(""));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String resolveToken(String path, Map<String, JSONObject> contexts, Map<String, String> variables) {
        // 1. Check for quoted literals
        if ((path.startsWith("\"") && path.endsWith("\"")) || (path.startsWith("'") && path.endsWith("'"))) {
            return path.substring(1, path.length() - 1);
        }

        // 2. Built-in date/time tokens
        if (path.equalsIgnoreCase("now")) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").format(new java.util.Date());
        }
        if (path.equalsIgnoreCase("today")) {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }

        // 2.5 Direct variable check (custom tokens / execution variables take precedence if path is an explicit variable)
        if (!path.startsWith("issue.") && !path.startsWith("last.") && !path.startsWith("fields.") && variables != null) {
            if (variables.containsKey(path)) return variables.get(path);
            if (path.startsWith("var.") && variables.containsKey(path.substring(4))) return variables.get(path.substring(4));
            if (path.startsWith("variable.") && variables.containsKey(path.substring(9))) return variables.get(path.substring(9));
        }

        String value = null;
        String resolvedPath = path;
        JSONObject targetContext = contexts.get("issue"); // Default context

        // 3. Check for explicit context prefix (e.g., issue.summary, last.key)
        for (String contextName : contexts.keySet()) {
            if (path.startsWith(contextName + ".")) {
                targetContext = contexts.get(contextName);
                resolvedPath = path.substring(contextName.length() + 1);
                break;
            }
        }

        // 3. Resolve in JSON context
        if (targetContext != null) {
            value = resolvePath(targetContext, resolvedPath);
            // Fallback to fields.path if not found and not already prefixed with fields.
            if (value == null && !resolvedPath.startsWith("fields.")) {
                value = resolvePath(targetContext, "fields." + resolvedPath);
            }
        }

        // 4. Check Variables (Prompts/Execution Vars) if not found in JSON or if explicitly requested
        if ((value == null || value.isEmpty() || value.equalsIgnoreCase("null")) && variables != null) {
            if (variables.containsKey(path)) {
                value = variables.get(path);
            } else if (path.startsWith("var.") && variables.containsKey(path.substring(4))) {
                value = variables.get(path.substring(4));
            } else if (path.startsWith("variable.") && variables.containsKey(path.substring(9))) {
                value = variables.get(path.substring(9));
            }
        }

        return value;
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
