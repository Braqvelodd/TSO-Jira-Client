package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;

public class AttachmentStep extends WorkflowStep {
    private String targetIssueToken = "{{issue.key}}";
    private String filePath = "";
    private boolean promptAtRuntime = false;

    public AttachmentStep() {
        super(StepType.ATTACHMENT);
    }

    public String getTargetIssueToken() { return targetIssueToken; }
    public void setTargetIssueToken(String targetIssueToken) { this.targetIssueToken = targetIssueToken; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public boolean isPromptAtRuntime() { return promptAtRuntime; }
    public void setPromptAtRuntime(boolean promptAtRuntime) { this.promptAtRuntime = promptAtRuntime; }

    @Override
    public void validate() throws Exception {
        if (targetIssueToken == null || targetIssueToken.trim().isEmpty()) {
            throw new Exception("Attachment Step: Target Issue Key/Token is required.");
        }
        if (!promptAtRuntime && (filePath == null || filePath.trim().isEmpty())) {
            throw new Exception("Attachment Step: File Path is required when not prompting at runtime.");
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("targetIssueToken", targetIssueToken);
        json.put("filePath", filePath);
        json.put("promptAtRuntime", promptAtRuntime);

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

    public static AttachmentStep fromJson(JSONObject json) {
        AttachmentStep step = new AttachmentStep();
        step.setTargetIssueToken(json.optString("targetIssueToken", "{{issue.key}}"));
        step.setFilePath(json.optString("filePath", ""));
        step.setPromptAtRuntime(json.optBoolean("promptAtRuntime", false));
        return step;
    }
}
