package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;

public class CreateStep extends WorkflowStep {
    private String projectKey;
    private String issueType;
    private String parentIssueToken;

    public CreateStep() {
        super(StepType.CREATE);
    }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    public String getParentIssueToken() { return parentIssueToken; }
    public void setParentIssueToken(String parentIssueToken) { this.parentIssueToken = parentIssueToken; }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("projectKey", projectKey);
        json.put("issueType", issueType);
        json.put("parentIssueToken", parentIssueToken);
        
        JSONArray fields = new JSONArray();
        for (FieldAction action : fieldActions.values()) {
            fields.put(action.toJson());
        }
        json.put("fields", fields);
        return json;
    }

    public static CreateStep fromJson(JSONObject json) {
        CreateStep step = new CreateStep();
        step.setProjectKey(json.optString("projectKey"));
        step.setIssueType(json.optString("issueType"));
        step.setParentIssueToken(json.optString("parentIssueToken"));
        return step;
    }
}
