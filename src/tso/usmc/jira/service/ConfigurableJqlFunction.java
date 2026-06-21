package tso.usmc.jira.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Concrete JQL function strategy that resolves relations dynamically based on configured paths.
 * Allows custom runtime JQL functions to be defined via JSON.
 */
public class ConfigurableJqlFunction implements CustomJqlFunction {

    private final String name;
    private final List<String> firstPassFields;
    private final List<String> jsonPaths;
    private final String outputTemplate;

    public ConfigurableJqlFunction(String name, List<String> firstPassFields, List<String> jsonPaths, String outputTemplate) {
        this.name = name;
        this.firstPassFields = firstPassFields;
        this.jsonPaths = jsonPaths;
        this.outputTemplate = (outputTemplate == null || outputTemplate.trim().isEmpty()) 
            ? "key in ({{KEYS}})" 
            : outputTemplate;
    }

    @Override
    public String getFunctionName() {
        return name;
    }

    @Override
    public String extractInnerJql(String fullQuery) {
        return parseInnerJql(fullQuery);
    }

    @Override
    public List<String> getFirstPassFields() {
        return firstPassFields;
    }

    public List<String> getJsonPaths() {
        return jsonPaths;
    }

    public String getOutputTemplate() {
        return outputTemplate;
    }

    @Override
    public String buildFinalJql(JSONObject firstPassResponse) {
        List<String> keys = new ArrayList<>();
        if (firstPassResponse != null) {
            for (String path : jsonPaths) {
                if (path != null && !path.trim().isEmpty()) {
                    String[] parts = path.trim().split("\\.");
                    extractKeys(firstPassResponse, parts, 0, keys);
                }
            }
        }

        // De-duplicate keys and filter empty items
        List<String> uniqueKeys = new ArrayList<>();
        for (String k : keys) {
            if (k != null && !k.trim().isEmpty() && !uniqueKeys.contains(k)) {
                uniqueKeys.add(k);
            }
        }

        if (uniqueKeys.isEmpty()) {
            return "key is empty";
        }

        // Chunk keys if they exceed 1000 items, and join with OR
        int chunkSize = 1000;
        List<String> clauses = new ArrayList<>();
        for (int i = 0; i < uniqueKeys.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, uniqueKeys.size());
            List<String> chunk = uniqueKeys.subList(i, end);
            String joined = String.join(", ", chunk);
            
            String clause;
            if (outputTemplate.contains("{{KEYS}}")) {
                clause = outputTemplate.replace("{{KEYS}}", joined);
            } else {
                clause = "key in (" + joined + ")";
            }
            clauses.add(clause);
        }

        if (clauses.size() == 1) {
            return clauses.get(0);
        } else {
            return "(" + String.join(" OR ", clauses) + ")";
        }
    }

    /**
     * Recursively traverses a JSON structure (objects/arrays) to extract keys.
     */
    private void extractKeys(Object current, String[] parts, int index, List<String> results) {
        if (current == null) {
            return;
        }

        if (index >= parts.length) {
            if (current instanceof String) {
                results.add((String) current);
            } else if (current instanceof JSONObject) {
                JSONObject obj = (JSONObject) current;
                if (obj.has("key")) {
                    results.add(obj.optString("key"));
                }
            }
            return;
        }

        String part = parts[index];
        boolean isArray = part.endsWith("[*]");
        String keyName = isArray ? part.substring(0, part.length() - 3) : part;

        if (current instanceof JSONObject) {
            JSONObject obj = (JSONObject) current;
            if (!obj.has(keyName) || obj.isNull(keyName)) {
                return;
            }

            Object val = obj.get(keyName);
            if (isArray) {
                if (val instanceof JSONArray) {
                    JSONArray arr = (JSONArray) val;
                    for (int i = 0; i < arr.length(); i++) {
                        extractKeys(arr.get(i), parts, index + 1, results);
                    }
                }
            } else {
                extractKeys(val, parts, index + 1, results);
            }
        } else if (current instanceof JSONArray) {
            JSONArray arr = (JSONArray) current;
            for (int i = 0; i < arr.length(); i++) {
                extractKeys(arr.get(i), parts, index, results);
            }
        }
    }
}
