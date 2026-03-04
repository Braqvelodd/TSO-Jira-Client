package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;

public class UpdateStep extends WorkflowStep {
    private String targetIssueToken = "{{issue.key}}";

    public UpdateStep() {
        super(StepType.UPDATE);
    }

    public String getTargetIssueToken() { return targetIssueToken; }
    public void setTargetIssueToken(String targetIssueToken) { this.targetIssueToken = targetIssueToken; }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("targetIssueToken", targetIssueToken);
        
        JSONArray fields = new JSONArray();
        for (FieldAction action : fieldActions.values()) {
            fields.put(action.toJson());
        }
        json.put("fields", fields);
        return json;
    }

    public static UpdateStep fromJson(JSONObject json) {
        UpdateStep step = new UpdateStep();
        step.setTargetIssueToken(json.optString("targetIssueToken", "{{issue.key}}"));
        return step;
    }
}
