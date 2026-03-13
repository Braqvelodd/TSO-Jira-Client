package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;

public class WorklogStep extends WorkflowStep {
    private String targetIssueToken = "{{issue.key}}";
    private String timeSpent;
    private String comment;
    private String started = "{{now}}";

    public WorklogStep() {
        super(StepType.WORKLOG);
    }

    public String getTargetIssueToken() { return targetIssueToken; }
    public void setTargetIssueToken(String targetIssueToken) { this.targetIssueToken = targetIssueToken; }

    public String getTimeSpent() { return timeSpent; }
    public void setTimeSpent(String timeSpent) { this.timeSpent = timeSpent; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getStarted() { return started; }
    public void setStarted(String started) { this.started = started; }

    @Override
    public void validate() throws Exception {
        if (targetIssueToken == null || targetIssueToken.trim().isEmpty()) {
            throw new Exception("Worklog Step: Target Issue Key/Token is required.");
        }
        
        if (timeSpent == null || timeSpent.trim().isEmpty()) {
            throw new Exception("Worklog Step: Time Spent is required.");
        }

        // Only validate format if it's a literal value, not a token/choice
        String ts = timeSpent.trim();
        if (!ts.contains("{{") && !ts.contains("[") && !ts.contains(",")) {
            // Pattern for Jira time tracking: e.g., "1h 30m", "4d", "20m"
            if (!ts.matches("^(\\d+[wdhm]\\s*)+$")) {
                throw new Exception("Worklog Step: Invalid Time Spent format '" + ts + "'. Use Jira format like '1h 30m', '4d', or '20m'.");
            }
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("targetIssueToken", targetIssueToken);
        json.put("timeSpent", timeSpent);
        json.put("comment", comment);
        json.put("started", started);
        
        JSONArray fields = new JSONArray();
        for (FieldAction action : fieldActions.values()) {
            fields.put(action.toJson());
        }
        json.put("fields", fields);
        return json;
    }

    public static WorklogStep fromJson(JSONObject json) {
        WorklogStep step = new WorklogStep();
        step.setTargetIssueToken(json.optString("targetIssueToken", "{{issue.key}}"));
        step.setTimeSpent(json.optString("timeSpent"));
        step.setComment(json.optString("comment"));
        step.setStarted(json.optString("started"));
        return step;
    }
}
