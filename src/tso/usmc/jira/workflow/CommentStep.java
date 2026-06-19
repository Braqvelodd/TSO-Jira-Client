package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;

public class CommentStep extends WorkflowStep {
    private String targetIssueToken = "{{issue.key}}";
    private String commentBody = "";
    private boolean promptAtRuntime = false;
    private boolean promptPerIssue = false;

    public CommentStep() {
        super(StepType.COMMENT);
    }

    public String getTargetIssueToken() { return targetIssueToken; }
    public void setTargetIssueToken(String targetIssueToken) { this.targetIssueToken = targetIssueToken; }

    public String getCommentBody() { return commentBody; }
    public void setCommentBody(String commentBody) { this.commentBody = commentBody; }

    public boolean isPromptAtRuntime() { return promptAtRuntime; }
    public void setPromptAtRuntime(boolean promptAtRuntime) { this.promptAtRuntime = promptAtRuntime; }

    public boolean isPromptPerIssue() { return promptPerIssue; }
    public void setPromptPerIssue(boolean promptPerIssue) { this.promptPerIssue = promptPerIssue; }

    @Override
    public void validate() throws Exception {
        if (targetIssueToken == null || targetIssueToken.trim().isEmpty()) {
            throw new Exception("Comment Step: Target Issue Key/Token is required.");
        }
        if (!promptAtRuntime && (commentBody == null || commentBody.trim().isEmpty())) {
            throw new Exception("Comment Step: Comment Body is required when not prompting at runtime.");
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("targetIssueToken", targetIssueToken);
        json.put("commentBody", commentBody);
        json.put("promptAtRuntime", promptAtRuntime);
        json.put("promptPerIssue", promptPerIssue);

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

    public static CommentStep fromJson(JSONObject json) {
        CommentStep step = new CommentStep();
        step.setTargetIssueToken(json.optString("targetIssueToken", "{{issue.key}}"));
        step.setCommentBody(json.optString("commentBody", ""));
        step.setPromptAtRuntime(json.optBoolean("promptAtRuntime", false));
        step.setPromptPerIssue(json.optBoolean("promptPerIssue", false));
        return step;
    }
}
