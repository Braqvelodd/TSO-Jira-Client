package tso.usmc.jira.service;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * Strategy interface that all custom JQL functions (e.g., parentsOf, childrenOf) must implement.
 */
public interface CustomJqlFunction {

    /**
     * Returns the exact function name (e.g., "parentsOf").
     */
    String getFunctionName();

    /**
     * Parses the contents inside the function's parentheses.
     * E.g., extracts "status = Done" from "parentsOf('status = Done')".
     */
    String extractInnerJql(String fullQuery);

    /**
     * Returns the list of fields needed in the first-pass query to resolve this relationship.
     * E.g., ["parent"] for parentsOf.
     */
    List<String> getFirstPassFields();

    /**
     * Takes the JSON response from the first API call, extracts the relevant identifiers,
     * and constructs the final standard JQL string (e.g., "key in (KEY-1, KEY-2)").
     * Handles empty results gracefully and handles JQL length limits by chunking.
     */
    String buildFinalJql(JSONObject firstPassResponse);

    /**
     * Helper to extract the inner JQL from a function call with matching parentheses
     * and optional single/double quotes.
     */
    default String parseInnerJql(String fullQuery) {
        String lowerQuery = fullQuery.toLowerCase();
        String searchStr = getFunctionName().toLowerCase() + "(";
        int startIdx = lowerQuery.indexOf(searchStr);
        if (startIdx == -1) {
            return "";
        }

        int contentStartIdx = startIdx + searchStr.length();
        int depth = 0;
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        int endIdx = -1;

        for (int i = contentStartIdx; i < fullQuery.length(); i++) {
            char c = fullQuery.charAt(i);

            // Handle escaped characters
            if (c == '\\') {
                i++;
                continue;
            }

            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
            } else if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
            } else if (!inDoubleQuotes && !inSingleQuotes) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    if (depth == 0) {
                        endIdx = i;
                        break;
                    } else {
                        depth--;
                    }
                }
            }
        }

        if (endIdx == -1) {
            return "";
        }

        String inner = fullQuery.substring(contentStartIdx, endIdx).trim();
        if ((inner.startsWith("\"") && inner.endsWith("\"")) || (inner.startsWith("'") && inner.endsWith("'"))) {
            if (inner.length() >= 2) {
                inner = inner.substring(1, inner.length() - 1).trim();
            }
        }
        return inner;
    }

    /**
     * Common helper method to de-duplicate keys and build a standard JQL key-in clause.
     * If the total number of keys exceeds 1000, it chunks them into groups of 1000 connected by OR
     * to avoid Jira database limit errors.
     */
    static String buildChunkedKeyInQuery(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "key is empty";
        }

        List<String> uniqueKeys = new ArrayList<>();
        for (String k : keys) {
            if (k != null && !k.trim().isEmpty() && !uniqueKeys.contains(k)) {
                uniqueKeys.add(k);
            }
        }

        if (uniqueKeys.isEmpty()) {
            return "key is empty";
        }

        int chunkSize = 1000;
        List<String> clauses = new ArrayList<>();
        for (int i = 0; i < uniqueKeys.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, uniqueKeys.size());
            List<String> chunk = uniqueKeys.subList(i, end);
            String joined = String.join(", ", chunk);
            clauses.add("key in (" + joined + ")");
        }

        if (clauses.size() == 1) {
            return clauses.get(0);
        } else {
            return "(" + String.join(" OR ", clauses) + ")";
        }
    }
}
