package tso.usmc.jira.service;

import org.json.JSONArray;
import org.json.JSONObject;
import tso.usmc.jira.util.JiraConfig;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JqlAutocompleteService {
    private final JiraApiService apiService;
    private final String baseUrl;
    private final JiraConfig config;
    
    private List<String> fieldNames = new ArrayList<>();
    private List<String> functionNames = new ArrayList<>();
    private List<String> reservedWords = new ArrayList<>();
    private Map<String, List<String>> operatorsByField = new HashMap<>();
    
    private final Map<String, List<String>> suggestionCache = new ConcurrentHashMap<>();
    private long lastFetchTime = 0;
    private static final long CACHE_DURATION = 1000 * 60 * 60; // 1 hour

    public JqlAutocompleteService(JiraApiService apiService, String baseUrl, JiraConfig config) {
        this.apiService = apiService;
        this.baseUrl = baseUrl;
        this.config = config;
    }

    public synchronized void fetchDataIfNeeded() {
        if (System.currentTimeMillis() - lastFetchTime < CACHE_DURATION && !fieldNames.isEmpty()) {
            return;
        }
        
        try {
            String raw = apiService.getJqlAutoCompleteData(baseUrl);
            JSONObject json = new JSONObject(raw);
            
            fieldNames.clear();
            operatorsByField.clear();
            if (json.has("visibleFieldNames")) {
                JSONArray fields = json.getJSONArray("visibleFieldNames");
                for (int i = 0; i < fields.length(); i++) {
                    JSONObject f = fields.getJSONObject(i);
                    String val = f.getString("value");
                    fieldNames.add(val);
                    
                    if (f.has("operators")) {
                        JSONArray ops = f.getJSONArray("operators");
                        List<String> opList = new ArrayList<>();
                        for (int j = 0; j < ops.length(); j++) opList.add(ops.getString(j));
                        operatorsByField.put(val, opList);
                    }
                }
            }
            
            functionNames.clear();
            if (json.has("visibleFunctionNames")) {
                JSONArray funcs = json.getJSONArray("visibleFunctionNames");
                for (int i = 0; i < funcs.length(); i++) {
                    functionNames.add(funcs.getJSONObject(i).getString("value"));
                }
            }
            
            reservedWords.clear();
            if (json.has("jqlReservedWords")) {
                JSONArray words = json.getJSONArray("jqlReservedWords");
                for (int i = 0; i < words.length(); i++) reservedWords.add(words.getString(i));
            }
            
            lastFetchTime = System.currentTimeMillis();
        } catch (Exception e) {
            System.err.println("Error fetching JQL autocomplete data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<String> getFieldNames() { return fieldNames; }
    public List<String> getFunctionNames() { return functionNames; }
    public List<String> getReservedWords() { return reservedWords; }
    public List<String> getOperatorsForField(String field) { return operatorsByField.getOrDefault(field, Collections.emptyList()); }

    public List<String> getSuggestions(String fieldName, String userInput) {
        String cacheKey = fieldName + ":" + userInput;
        if (suggestionCache.containsKey(cacheKey)) return suggestionCache.get(cacheKey);
        
        try {
            String raw = apiService.getJqlSuggestions(baseUrl, fieldName, userInput);
            JSONObject json = new JSONObject(raw);
            List<String> suggestions = new ArrayList<>();
            if (json.has("results")) {
                JSONArray results = json.getJSONArray("results");
                for (int i = 0; i < results.length(); i++) {
                    suggestions.add(results.getJSONObject(i).getString("value"));
                }
            }
            suggestionCache.put(cacheKey, suggestions);
            return suggestions;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<String> getUserSuggestions(String userInput) {
        return getUserSuggestions(userInput, false);
    }

    public List<String> getUserSuggestions(String userInput, boolean includeTeams) {
        String cacheKey = "user:" + userInput + ":" + includeTeams;
        if (suggestionCache.containsKey(cacheKey)) return suggestionCache.get(cacheKey);

        List<String> suggestions = new ArrayList<>();

        if (includeTeams && config != null) {
            String[] teamKeys = config.getWorkflowTeamKeys();
            if (teamKeys != null) {
                String queryLower = userInput.toLowerCase();
                for (String key : teamKeys) {
                    String name = config.getTeamProperty(key, "name");
                    String nameLower = name != null ? name.toLowerCase() : "";
                    String keyLower = key.toLowerCase();
                    
                    if (keyLower.contains(queryLower) || nameLower.contains(queryLower)) {
                        suggestions.add("@" + key);
                        suggestions.add("team." + key);
                    }
                }
            }
        }

        try {
            String raw = apiService.searchUsers(baseUrl, userInput);
            JSONArray results = new JSONArray(raw);
            for (int i = 0; i < results.length(); i++) {
                JSONObject user = results.getJSONObject(i);
                suggestions.add(user.getString("name"));
            }
            suggestionCache.put(cacheKey, suggestions);
            return suggestions;
        } catch (Exception e) {
            if (!suggestions.isEmpty()) {
                suggestionCache.put(cacheKey, suggestions);
                return suggestions;
            }
            return Collections.emptyList();
        }
    }
}
