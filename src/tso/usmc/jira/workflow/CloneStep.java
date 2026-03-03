package tso.usmc.jira.workflow;

import org.json.JSONObject;

public class CloneStep extends WorkflowStep {
    private boolean copyAttachments = true;
    private boolean copyLinks = true;
    private String sourceIssueToken = "{{issue.key}}";
    private String targetIssueToken = "{{last_key}}";

    public CloneStep() {
        super(StepType.CLONE);
    }

    public boolean isCopyAttachments() { return copyAttachments; }
    public void setCopyAttachments(boolean copyAttachments) { this.copyAttachments = copyAttachments; }

    public boolean isCopyLinks() { return copyLinks; }
    public void setCopyLinks(boolean copyLinks) { this.copyLinks = copyLinks; }

    public String getSourceIssueToken() { return sourceIssueToken; }
    public void setSourceIssueToken(String sourceIssueToken) { this.sourceIssueToken = sourceIssueToken; }

    public String getTargetIssueToken() { return targetIssueToken; }
    public void setTargetIssueToken(String targetIssueToken) { this.targetIssueToken = targetIssueToken; }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("copyAttachments", copyAttachments);
        json.put("copyLinks", copyLinks);
        json.put("sourceIssueToken", sourceIssueToken);
        json.put("targetIssueToken", targetIssueToken);
        return json;
    }

    public static CloneStep fromJson(JSONObject json) {
        CloneStep step = new CloneStep();
        step.setCopyAttachments(json.optBoolean("copyAttachments", true));
        step.setCopyLinks(json.optBoolean("copyLinks", true));
        step.setSourceIssueToken(json.optString("sourceIssueToken", "{{issue.key}}"));
        step.setTargetIssueToken(json.optString("targetIssueToken", "{{last_key}}"));
        return step;
    }
}
