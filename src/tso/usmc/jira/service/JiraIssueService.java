package tso.usmc.jira.service;

import org.json.JSONArray;
import org.json.JSONObject;
import tso.usmc.jira.util.JiraUtils;
import tso.usmc.jira.util.JiraConfig;
import java.util.List;

/**
 * Service for high-level Jira issue operations (Transition, Update, Link, Assign).
 * Decouples UI logic from Jira API specifics.
 */
public class JiraIssueService {
    private final JiraApiService apiService;
    private final String baseUrl;
    private final MetadataCacheService metadataService;
    private final JiraConfig config;

    public JiraIssueService(JiraApiService apiService, String baseUrl, MetadataCacheService metadataService, JiraConfig config) {
        this.apiService = apiService;
        this.baseUrl = baseUrl;
        this.metadataService = metadataService;
        this.config = config;
    }

    public JiraConfig getJiraConfig() {
        return config;
    }

    /**
     * Transitions an issue to a new status.
     * @param issueKey The Jira issue key (e.g., TSO-123)
     * @param transitionName The name of the transition (e.g., "Done")
     * @param fields Optional fields to set during transition (nullable)
     * @throws Exception if transition fails or is not found
     */
    public void transitionIssue(String issueKey, String transitionName, JSONObject fields) throws Exception {
        String transUrl = baseUrl + "/rest/api/2/issue/" + issueKey + "/transitions";
        String transMeta = apiService.executeRequest(transUrl, "GET", null);
        String tid = findTransitionIdByName(transMeta, transitionName);
        
        if (tid == null) {
            throw new Exception("Transition '" + transitionName + "' not found for issue " + issueKey);
        }

        JSONObject body = new JSONObject();
        body.put("transition", new JSONObject().put("id", tid));
        if (fields != null && fields.length() > 0) {
            JiraUtils.expandTeamMentionsInJson(fields, config);
            body.put("fields", fields);
        }

        apiService.executeRequest(transUrl, "POST", body.toString());
    }

    /**
     * Updates an issue's fields.
     */
    public void updateIssue(String issueKey, JSONObject fields) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey;
        if (fields != null) {
            JiraUtils.expandTeamMentionsInJson(fields, config);
        }
        JSONObject body = new JSONObject().put("fields", fields);
        apiService.executeRequest(url, "PUT", body.toString());
    }

    /**
     * Assigns an issue to a user.
     */
    public void assignIssue(String issueKey, String username) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/assignee";
        JSONObject body = new JSONObject().put("name", username != null ? username : JSONObject.NULL);
        apiService.executeRequest(url, "PUT", body.toString());
    }

    /**
     * Adds a comment to an issue.
     */
    public void addComment(String issueKey, String comment) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/comment";
        String expandedComment = JiraUtils.expandTeamMentions(comment, config);
        JSONObject body = new JSONObject().put("body", expandedComment);
        apiService.executeRequest(url, "POST", body.toString());
    }

    /**
     * Sends a Jira notification for an issue.
     */
    public void notifyIssue(String issueKey, String notifyPayloadJson) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/notify";
        apiService.executeRequest(url, "POST", notifyPayloadJson);
    }

    /**
     * Creates a link between two issues.
     */
    public void linkIssues(String inwardKey, String outwardKey, String linkType) throws Exception {
        String url = baseUrl + "/rest/api/2/issueLink";
        JSONObject body = new JSONObject();
        body.put("type", new JSONObject().put("name", linkType));
        body.put("inwardIssue", new JSONObject().put("key", inwardKey));
        body.put("outwardIssue", new JSONObject().put("key", outwardKey));
        apiService.executeRequest(url, "POST", body.toString());
    }

    /**
     * Creates a new Jira issue.
     * @return The response from Jira (contains 'key' and 'id')
     */
    public JSONObject createIssue(JSONObject fields) throws Exception {
        String url = baseUrl + "/rest/api/2/issue";
        if (fields != null) {
            JiraUtils.expandTeamMentionsInJson(fields, config);
        }
        String resp = apiService.executeRequest(url, "POST", new JSONObject().put("fields", fields).toString());
        return new JSONObject(resp);
    }

    /**
     * Helper to find a transition ID by its display name.
     */
    public String findTransitionIdByName(String jsonResponse, String transitionName) {
        if (jsonResponse == null || jsonResponse.trim().isEmpty()) return null;
        JSONObject response = new JSONObject(jsonResponse);
        if (!response.has("transitions")) return null;
        
        JSONArray transitions = response.getJSONArray("transitions");
        for (int i = 0; i < transitions.length(); i++) {
            JSONObject t = transitions.getJSONObject(i);
            if (t.getString("name").equalsIgnoreCase(transitionName)) {
                return t.getString("id");
            }
        }
        return null;
    }

    /**
     * Updates an issue using a raw JSON body (supports update block).
     */
    public void updateIssueRaw(String issueKey, JSONObject body) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey;
        apiService.executeRequest(url, "PUT", body.toString());
    }

    /**
     * Logs work on an issue.
     */
    public void logWork(String issueKey, String timeSpent, String comment) throws Exception {
        String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/worklog";
        JSONObject body = new JSONObject().put("timeSpent", timeSpent);
        if (comment != null && !comment.trim().isEmpty()) {
            body.put("comment", comment);
        }
        apiService.executeRequest(url, "POST", body.toString());
    }
}
