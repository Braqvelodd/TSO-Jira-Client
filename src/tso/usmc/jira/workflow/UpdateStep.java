package tso.usmc.jira.workflow;

import org.json.JSONObject;

public class UpdateStep extends WorkflowStep {

    public UpdateStep() {
        super(StepType.UPDATE);
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        
        JSONObject fields = new JSONObject();
        for (String key : fieldActions.keySet()) {
            fields.put(key, fieldActions.get(key).toJson());
        }
        json.put("fields", fields);
        return json;
    }

    public static UpdateStep fromJson(JSONObject json) {
        return new UpdateStep();
    }
}
