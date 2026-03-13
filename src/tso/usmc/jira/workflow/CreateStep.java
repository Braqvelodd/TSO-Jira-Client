package tso.usmc.jira.workflow;

import org.json.JSONArray;
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
    public void validate() throws Exception {
        if (projectKey == null || projectKey.trim().isEmpty()) {
            throw new Exception("Create Step: Project Key is required.");
        }
        if (issueType == null || issueType.trim().isEmpty()) {
            throw new Exception("Create Step: Issue Type is required.");
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("projectKey", projectKey);
        json.put("issueType", issueType);

        // Condition fields
        if (conditionToken != null) json.put("conditionToken", conditionToken);
        if (conditionOperator != null) json.put("conditionOperator", conditionOperator);
        if (conditionValue != null) json.put("conditionValue", conditionValue);
        
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
        return step;
    }
}
