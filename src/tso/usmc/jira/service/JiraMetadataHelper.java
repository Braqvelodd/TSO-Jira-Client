package tso.usmc.jira.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class JiraMetadataHelper {

    private final JiraApiService apiService;
    private final String baseUrl;

    public JiraMetadataHelper(JiraApiService apiService, String baseUrl) {
        this.apiService = apiService;
        this.baseUrl = baseUrl;
    }

    /**
     * Fetches available fields for editing an issue.
     * Use an issue key to get context-specific fields.
     */
    public Map<String, JSONObject> getEditMetadata(String issueKey) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/editmeta";
        String response = apiService.executeRequest(url, "GET", null);
        JSONObject json = new JSONObject(response);
        Map<String, JSONObject> fields = new HashMap<>();
        
        if (json.has("fields")) {
            JSONObject fieldsJson = json.getJSONObject("fields");
            for (String key : fieldsJson.keySet()) {
                fields.put(key, fieldsJson.getJSONObject(key));
            }
        }
        return fields;
    }

    /**
     * Fetches possible transitions for an issue.
     */
    public List<JSONObject> getTransitions(String issueKey) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/transitions";
        String response = apiService.executeRequest(url, "GET", null);
        JSONObject json = new JSONObject(response);
        List<JSONObject> transitions = new ArrayList<>();
        
        if (json.has("transitions")) {
            JSONArray transArray = json.getJSONArray("transitions");
            for (int i = 0; i < transArray.length(); i++) {
                transitions.add(transArray.getJSONObject(i));
            }
        }
        return transitions;
    }

    /**
     * Helper to extract allowed values from a field metadata object.
     */
    public List<String> getAllowedValues(JSONObject fieldMeta) {
        List<String> values = new ArrayList<>();
        if (fieldMeta.has("allowedValues")) {
            JSONArray allowed = fieldMeta.getJSONArray("allowedValues");
            for (int i = 0; i < allowed.length(); i++) {
                JSONObject opt = allowed.getJSONObject(i);
                if (opt.has("value")) {
                    values.add(opt.getString("value"));
                } else if (opt.has("name")) {
                    values.add(opt.getString("name"));
                } else if (opt.has("key")) {
                    values.add(opt.getString("key"));
                }
            }
        }
        return values;
    }
}
