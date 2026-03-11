package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

public class TransitionStep extends WorkflowStep {
    private String targetStatus;
    private String targetIssueToken = "{{issue.key}}";

    public TransitionStep() {
        super(StepType.TRANSITION);
    }

    public String getTargetStatus() { return targetStatus; }
    public void setTargetStatus(String targetStatus) { this.targetStatus = targetStatus; }

    public String getTargetIssueToken() { return targetIssueToken; }
    public void setTargetIssueToken(String targetIssueToken) { this.targetIssueToken = targetIssueToken; }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("targetStatus", targetStatus);
        json.put("targetIssueToken", targetIssueToken);
        
        JSONArray fields = new JSONArray();
        for (FieldAction action : fieldActions.values()) {
            fields.put(action.toJson());
        }
        json.put("fields", fields);
        return json;
    }

    public static TransitionStep fromJson(JSONObject json) {
        TransitionStep step = new TransitionStep();
        step.setStepId(json.optString("stepId", UUID.randomUUID().toString()));
        step.setLabel(json.optString("label", "Transition Step"));
        step.setTargetStatus(json.optString("targetStatus"));
        step.setTargetIssueToken(json.optString("targetIssueToken", "{{issue.key}}"));
        return step;
    }
}
