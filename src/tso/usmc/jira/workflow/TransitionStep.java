package tso.usmc.jira.workflow;

import org.json.JSONObject;

public class TransitionStep extends WorkflowStep {
    private String targetStatus;

    public TransitionStep() {
        super(StepType.TRANSITION);
    }

    public String getTargetStatus() { return targetStatus; }
    public void setTargetStatus(String targetStatus) { this.targetStatus = targetStatus; }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("targetStatus", targetStatus);
        
        JSONObject fields = new JSONObject();
        for (String key : fieldActions.keySet()) {
            fields.put(key, fieldActions.get(key).toJson());
        }
        json.put("fields", fields);
        return json;
    }

    public static TransitionStep fromJson(JSONObject json) {
        TransitionStep step = new TransitionStep();
        step.setTargetStatus(json.optString("targetStatus"));
        // Fields and common properties are populated by the caller (WorkflowStep.fromJson) or we can do it here if we want cleaner code, 
        // but since fromJson is static in the parent, the parent usually orchestrates.
        // Actually, the parent factory calls this, so we return the instance. 
        // We rely on the parent logic to fill common fields or we duplicate it.
        // Let's rely on the parent to fill the fields after creation.
        return step;
    }
}
