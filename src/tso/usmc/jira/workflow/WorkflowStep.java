package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class WorkflowStep {
    public enum StepType { TRANSITION, UPDATE, ASSET, CREATE, LINK, WORKLOG }

    protected String stepId;
    protected String label;
    protected StepType type;
    protected Map<String, FieldAction> fieldActions = new LinkedHashMap<>();

    // NEW: Conditional Branching Fields
    protected String conditionToken;    // e.g., "{{issue.fields.status.name}}"
    protected String conditionOperator; // EQUALS, CONTAINS, etc.
    protected String conditionValue;    // The value to compare against

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

    public String getConditionToken() { return conditionToken; }
    public void setConditionToken(String conditionToken) { this.conditionToken = conditionToken; }

    public String getConditionOperator() { return conditionOperator; }
    public void setConditionOperator(String conditionOperator) { this.conditionOperator = conditionOperator; }

    public String getConditionValue() { return conditionValue; }
    public void setConditionValue(String conditionValue) { this.conditionValue = conditionValue; }

    public abstract JSONObject toJson();

    /**
     * Validates step properties before execution.
     * @throws Exception with user-friendly message if validation fails.
     */
    public abstract void validate() throws Exception;

    public static WorkflowStep fromJson(JSONObject json) {
        // Use the registry to create the correct subclass instance
        WorkflowStep step = WorkflowStepRegistry.createStep(json);
        
        // Populate common fields
        if (json.has("stepId")) step.setStepId(json.getString("stepId"));
        if (json.has("label")) step.setLabel(json.getString("label"));
        
        // Populate condition fields
        if (json.has("conditionToken")) step.setConditionToken(json.getString("conditionToken"));
        if (json.has("conditionOperator")) step.setConditionOperator(json.getString("conditionOperator"));
        if (json.has("conditionValue")) step.setConditionValue(json.getString("conditionValue"));
        
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
