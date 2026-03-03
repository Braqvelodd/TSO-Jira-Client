package tso.usmc.jira.workflow;

import org.json.JSONObject;

public class CreateStep extends WorkflowStep {
    private String projectKey;
    private String issueType;

    public CreateStep() {
        super(StepType.CREATE);
    }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("projectKey", projectKey);
        json.put("issueType", issueType);
        
        JSONObject fields = new JSONObject();
        for (String key : fieldActions.keySet()) {
            fields.put(key, fieldActions.get(key).toJson());
        }
        json.put("fields", fields);
        return json;
    }

    public static CreateStep fromJson(JSONObject json) {
        CreateStep step = new CreateStep();
        step.setProjectKey(json.optString("projectKey"));
        step.setIssueType(json.optString("issueType"));
        return step;
    }
}
