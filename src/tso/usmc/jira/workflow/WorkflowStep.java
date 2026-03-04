package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class WorkflowStep {
    public enum StepType { TRANSITION, UPDATE, CLONE, CREATE, LINK }

    protected String stepId;
    protected String label;
    protected StepType type;
    protected Map<String, FieldAction> fieldActions = new LinkedHashMap<>();

    public WorkflowStep(StepType type) {
        this.type = type;
        this.stepId = java.util.UUID.randomUUID().toString();
    }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public StepType getType() { return type; }

    public Map<String, FieldAction> getFieldActions() { return fieldActions; }
    public void setFieldActions(Map<String, FieldAction> actions) { this.fieldActions = actions; }
    public void addFieldAction(FieldAction action) { this.fieldActions.put(action.getFieldId(), action); }

    public abstract JSONObject toJson();

    public static WorkflowStep fromJson(JSONObject json) {
        StepType type = StepType.valueOf(json.getString("type"));
        WorkflowStep step = null;
        if (type == StepType.TRANSITION) {
            step = TransitionStep.fromJson(json);
        } else if (type == StepType.UPDATE) {
            step = UpdateStep.fromJson(json);
        } else if (type == StepType.CLONE) {
            step = CloneStep.fromJson(json);
        } else if (type == StepType.CREATE) {
            step = CreateStep.fromJson(json);
        } else if (type == StepType.LINK) {
            step = LinkStep.fromJson(json);
        } else {
            step = new UpdateStep(); 
        }
        
        // Populate common fields
        if (json.has("stepId")) step.setStepId(json.getString("stepId"));
        if (json.has("label")) step.setLabel(json.getString("label"));
        
        if (json.has("fields")) {
            Object fieldsObj = json.get("fields");
            if (fieldsObj instanceof JSONArray) {
                JSONArray fieldsArr = (JSONArray) fieldsObj;
                for (int i = 0; i < fieldsArr.length(); i++) {
                    FieldAction fa = FieldAction.fromJson(fieldsArr.getJSONObject(i));
                    step.addFieldAction(fa);
                }
            } else if (fieldsObj instanceof JSONObject) {
                JSONObject fields = (JSONObject) fieldsObj;
                for (String key : fields.keySet()) {
                    FieldAction fa = FieldAction.fromJson(fields.getJSONObject(key));
                    if (fa.getFieldId() == null || fa.getFieldId().isEmpty()) {
                        fa.setFieldId(key);
                    }
                    step.addFieldAction(fa);
                }
            }
        }
        return step;
    }
}
