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
     * Fetches possible transitions for an issue, optionally expanding to see fields.
     */
    public List<JSONObject> getTransitions(String issueKey, boolean expandFields) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/transitions";
        if (expandFields) url += "?expand=transitions.fields";
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
     * Fetches fields required/available for a specific transition ID.
     */
    public Map<String, JSONObject> getTransitionMetadata(String issueKey, String transitionId) throws Exception {
        List<JSONObject> transitions = getTransitions(issueKey, true);
        Map<String, JSONObject> fields = new HashMap<>();
        
        for (JSONObject trans : transitions) {
            if (trans.getString("id").equals(transitionId) && trans.has("fields")) {
                JSONObject fieldsJson = trans.getJSONObject("fields");
                for (String key : fieldsJson.keySet()) {
                    fields.put(key, fieldsJson.getJSONObject(key));
                }
            }
        }
        return fields;
    }

    /**
     * Fetches creation metadata for specific project and issue type.
     */
    public Map<String, JSONObject> getCreateMetadata(String projectKey, String issueTypeName) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/createmeta?expand=projects.issuetypes.fields";
        if (projectKey != null && !projectKey.isEmpty() && !projectKey.contains("{{")) {
            url += "&projectKeys=" + projectKey;
        }
        if (issueTypeName != null && !issueTypeName.isEmpty() && !issueTypeName.contains("{{")) {
            url += "&issueTypeNames=" + java.net.URLEncoder.encode(issueTypeName, "UTF-8");
        }
        
        String response = apiService.executeRequest(url, "GET", null);
        JSONObject json = new JSONObject(response);
        Map<String, JSONObject> fields = new HashMap<>();
        
        if (json.has("projects")) {
            JSONArray projects = json.getJSONArray("projects");
            for (int i = 0; i < projects.length(); i++) {
                JSONObject proj = projects.getJSONObject(i);
                JSONArray types = proj.getJSONArray("issuetypes");
                for (int j = 0; j < types.length(); j++) {
                    JSONObject type = types.getJSONObject(j);
                    if (type.has("fields")) {
                        JSONObject fieldsJson = type.getJSONObject("fields");
                        for (String key : fieldsJson.keySet()) {
                            fields.put(key, fieldsJson.getJSONObject(key));
                        }
                    }
                }
            }
        }
        return fields;
    }

    /**
     * Fetches all available issue link types.
     */
    public List<JSONObject> getIssueLinkTypes() throws Exception {
        String url = baseUrl + "/rest/api/2/issueLinkType";
        String response = apiService.executeRequest(url, "GET", null);
        JSONObject json = new JSONObject(response);
        List<JSONObject> linkTypes = new ArrayList<>();
        
        if (json.has("issueLinkTypes")) {
            JSONArray arr = json.getJSONArray("issueLinkTypes");
            for (int i = 0; i < arr.length(); i++) {
                linkTypes.add(arr.getJSONObject(i));
            }
        }
        return linkTypes;
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
