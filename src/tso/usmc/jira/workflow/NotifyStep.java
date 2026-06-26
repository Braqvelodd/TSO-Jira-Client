package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;

public class NotifyStep extends WorkflowStep {
    private String targetIssueToken = "{{issue.key}}";
    private String subject = "";
    private String textBody = "";
    private String toUsers = "";
    private String toGroups = "";
    private boolean toAssignee = false;
    private boolean toReporter = false;
    private boolean toWatchers = false;
    private boolean toVoters = false;
    private boolean promptAtRuntime = false;
    private boolean promptPerIssue = false;

    // Specific prompt fields
    private boolean promptSubject = false;
    private boolean promptBody = false;
    private boolean promptUsers = false;
    private boolean promptGroups = false;

    public NotifyStep() {
        super(StepType.NOTIFY);
    }

    public String getTargetIssueToken() { return targetIssueToken; }
    public void setTargetIssueToken(String targetIssueToken) { this.targetIssueToken = targetIssueToken; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTextBody() { return textBody; }
    public void setTextBody(String textBody) { this.textBody = textBody; }

    public String getToUsers() { return toUsers; }
    public void setToUsers(String toUsers) { this.toUsers = toUsers; }

    public String getToGroups() { return toGroups; }
    public void setToGroups(String toGroups) { this.toGroups = toGroups; }

    public boolean isToAssignee() { return toAssignee; }
    public void setToAssignee(boolean toAssignee) { this.toAssignee = toAssignee; }

    public boolean isToReporter() { return toReporter; }
    public void setToReporter(boolean toReporter) { this.toReporter = toReporter; }

    public boolean isToWatchers() { return toWatchers; }
    public void setToWatchers(boolean toWatchers) { this.toWatchers = toWatchers; }

    public boolean isToVoters() { return toVoters; }
    public void setToVoters(boolean toVoters) { this.toVoters = toVoters; }

    public boolean isPromptAtRuntime() { return promptAtRuntime; }
    public void setPromptAtRuntime(boolean promptAtRuntime) { this.promptAtRuntime = promptAtRuntime; }

    public boolean isPromptPerIssue() { return promptPerIssue; }
    public void setPromptPerIssue(boolean promptPerIssue) { this.promptPerIssue = promptPerIssue; }

    public boolean isPromptSubject() { return promptSubject; }
    public void setPromptSubject(boolean promptSubject) { this.promptSubject = promptSubject; }

    public boolean isPromptBody() { return promptBody; }
    public void setPromptBody(boolean promptBody) { this.promptBody = promptBody; }

    public boolean isPromptUsers() { return promptUsers; }
    public void setPromptUsers(boolean promptUsers) { this.promptUsers = promptUsers; }

    public boolean isPromptGroups() { return promptGroups; }
    public void setPromptGroups(boolean promptGroups) { this.promptGroups = promptGroups; }

    @Override
    public void validate() throws Exception {
        if (targetIssueToken == null || targetIssueToken.trim().isEmpty()) {
            throw new Exception("Notify Step: Target Issue Key/Token is required.");
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("targetIssueToken", targetIssueToken);
        json.put("subject", subject);
        json.put("textBody", textBody);
        json.put("toUsers", toUsers);
        json.put("toGroups", toGroups);
        json.put("toAssignee", toAssignee);
        json.put("toReporter", toReporter);
        json.put("toWatchers", toWatchers);
        json.put("toVoters", toVoters);
        json.put("promptAtRuntime", promptAtRuntime);
        json.put("promptPerIssue", promptPerIssue);
        json.put("promptSubject", promptSubject);
        json.put("promptBody", promptBody);
        json.put("promptUsers", promptUsers);
        json.put("promptGroups", promptGroups);

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

    public static NotifyStep fromJson(JSONObject json) {
        NotifyStep step = new NotifyStep();
        step.setTargetIssueToken(json.optString("targetIssueToken", "{{issue.key}}"));
        step.setSubject(json.optString("subject", ""));
        step.setTextBody(json.optString("textBody", ""));
        step.setToUsers(json.optString("toUsers", ""));
        step.setToGroups(json.optString("toGroups", ""));
        step.setToAssignee(json.optBoolean("toAssignee", false));
        step.setToReporter(json.optBoolean("toReporter", false));
        step.setToWatchers(json.optBoolean("toWatchers", false));
        step.setToVoters(json.optBoolean("toVoters", false));
        step.setPromptAtRuntime(json.optBoolean("promptAtRuntime", false));
        step.setPromptPerIssue(json.optBoolean("promptPerIssue", false));
        step.setPromptSubject(json.optBoolean("promptSubject", false));
        step.setPromptBody(json.optBoolean("promptBody", false));
        step.setPromptUsers(json.optBoolean("promptUsers", false));
        step.setPromptGroups(json.optBoolean("promptGroups", false));
        return step;
    }
}
