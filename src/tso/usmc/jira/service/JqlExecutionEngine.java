package tso.usmc.jira.service;

import org.json.JSONObject;
import tso.usmc.jira.util.JiraApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orchestrator that implements the Multi-Pass JQL Execution Engine.
 * It detects custom relational JQL tokens (e.g., parentsOf) and coordinates
 * multiple API requests to produce final query results using standard Jira APIs.
 */
public class JqlExecutionEngine {

    private final JiraApiService apiService;
    private final List<CustomJqlFunction> registeredFunctions = new ArrayList<>();

    public JqlExecutionEngine(JiraApiService apiService) {
        this.apiService = apiService;
        // Register default out-of-the-box functions
        registerFunction(new ParentsOfFunction());
    }

    /**
     * Registers a custom JQL function strategy dynamically.
     */
    public void registerFunction(CustomJqlFunction function) {
        if (function != null) {
            registeredFunctions.add(function);
        }
    }

    /**
     * Executes a JQL query. If custom JQL tokens are detected, it performs a multi-pass
     * resolution first. Otherwise, it executes a single-pass standard search.
     *
     * @param baseUrl The base URL of the Jira instance (e.g., https://jira.domain)
     * @param jql The query string which may contain custom or standard JQL functions
     * @param fields The list of fields requested by the user/UI (nullable)
     * @param maxResults The maximum number of results to return
     * @return The raw JSON response from the final search execution
     * @throws Exception if request execution fails
     */
    public String executeJql(String baseUrl, String jql, List<String> fields, int maxResults) throws Exception {
        if (jql == null || jql.trim().isEmpty()) {
            throw new IllegalArgumentException("JQL query cannot be empty.");
        }

        String currentJql = jql;
        boolean customTokenDetected = false;

        // Multi-pass parsing loop: Resolve one custom function token at a time until none remain
        while (true) {
            FunctionMatch match = findNextMatch(currentJql);
            if (match == null) {
                break;
            }

            customTokenDetected = true;

            // Step A: Matched strategy and inner JQL are extracted in FunctionMatch
            String innerJql = match.innerJql;
            CustomJqlFunction strategy = match.function;

            // Step B: Execute the first Jira API call using the inner JQL
            JSONObject firstPassPayload = new JSONObject();
            firstPassPayload.put("jql", innerJql);
            firstPassPayload.put("fields", strategy.getFirstPassFields());
            firstPassPayload.put("maxResults", 1000); // Request up to 1000 issues to resolve relationships fully

            String firstPassResponseRaw = apiService.executeRequest(
                baseUrl + "/rest/api/2/search",
                "POST",
                firstPassPayload.toString()
            );

            JSONObject firstPassJson = new JSONObject(firstPassResponseRaw);

            // Step C: Construct the final standard JQL fragment from first pass results
            String resolvedFragment = strategy.buildFinalJql(firstPassJson);

            // Replace the matched token expression in the current JQL
            String prefix = currentJql.substring(0, match.start);
            String suffix = currentJql.substring(match.end);
            currentJql = prefix + resolvedFragment + suffix;
        }

        // Step D: Execute final API call using the resolved JQL query
        JSONObject finalPayload = new JSONObject();
        finalPayload.put("jql", currentJql);
        if (fields != null && !fields.isEmpty()) {
            finalPayload.put("fields", fields);
        }
        finalPayload.put("maxResults", maxResults);

        return apiService.executeRequest(
            baseUrl + "/rest/api/2/search",
            "POST",
            finalPayload.toString()
        );
    }

    /**
     * Scans the query string to find the earliest custom function token call.
     */
    private FunctionMatch findNextMatch(String query) {
        String lowerQuery = query.toLowerCase();
        int earliestIndex = -1;
        CustomJqlFunction matchedFunction = null;

        for (CustomJqlFunction function : registeredFunctions) {
            String searchStr = function.getFunctionName().toLowerCase() + "(";
            int idx = lowerQuery.indexOf(searchStr);
            if (idx != -1) {
                if (earliestIndex == -1 || idx < earliestIndex) {
                    earliestIndex = idx;
                    matchedFunction = function;
                }
            }
        }

        if (matchedFunction == null) {
            return null;
        }

        // Parse content within matching parentheses
        int contentStartIdx = earliestIndex + matchedFunction.getFunctionName().length() + 1;
        int depth = 0;
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        int endIdx = -1;

        for (int i = contentStartIdx; i < query.length(); i++) {
            char c = query.charAt(i);

            // Skip escaped characters
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
            // Unclosed parenthesis: cannot resolve matching bracket
            return null;
        }

        // Extract inner JQL content and strip outer quotes if present
        String innerJql = query.substring(contentStartIdx, endIdx).trim();
        if ((innerJql.startsWith("\"") && innerJql.endsWith("\"")) || 
            (innerJql.startsWith("'") && innerJql.endsWith("'"))) {
            if (innerJql.length() >= 2) {
                innerJql = innerJql.substring(1, innerJql.length() - 1).trim();
            }
        }

        // Check for preceding "[field] in" pattern to replace it in full
        int replaceStart = earliestIndex;
        String prefix = query.substring(0, earliestIndex);
        Pattern prefixPattern = Pattern.compile("\\b(\\w+)\\s+(?i:in)\\s*$");
        Matcher m = prefixPattern.matcher(prefix);
        if (m.find()) {
            replaceStart = m.start();
        }

        return new FunctionMatch(replaceStart, endIdx + 1, innerJql, matchedFunction);
    }

    /**
     * Inner helper class storing the matched location and strategy details.
     */
    private static class FunctionMatch {
        final int start;
        final int end;
        final String innerJql;
        final CustomJqlFunction function;

        FunctionMatch(int start, int end, String innerJql, CustomJqlFunction function) {
            this.start = start;
            this.end = end;
            this.innerJql = innerJql;
            this.function = function;
        }
    }
}
