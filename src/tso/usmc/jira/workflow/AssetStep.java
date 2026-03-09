package tso.usmc.jira.workflow;

import org.json.JSONObject;

public class AssetStep extends WorkflowStep {
    private boolean copyAttachments = true;
    private boolean copyLinks = true;
    private boolean copySubTasks = false;
    private boolean promptOptions = false;
    private String subTaskFields = "summary,description,priority,assignee,reporter,environment,security";
    private String sourceIssueToken = "{{issue.key}}";
    private String targetIssueToken = "{{last_key}}";

    public AssetStep() {
        super(StepType.ASSET);
    }

    public boolean isCopyAttachments() { return copyAttachments; }
    public void setCopyAttachments(boolean copyAttachments) { this.copyAttachments = copyAttachments; }

    public boolean isCopyLinks() { return copyLinks; }
    public void setCopyLinks(boolean copyLinks) { this.copyLinks = copyLinks; }

    public boolean isCopySubTasks() { return copySubTasks; }
    public void setCopySubTasks(boolean copySubTasks) { this.copySubTasks = copySubTasks; }

    public boolean isPromptOptions() { return promptOptions; }
    public void setPromptOptions(boolean promptOptions) { this.promptOptions = promptOptions; }

    public String getSubTaskFields() { return subTaskFields; }
    public void setSubTaskFields(String subTaskFields) { this.subTaskFields = subTaskFields; }

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
        json.put("copySubTasks", copySubTasks);
        json.put("promptOptions", promptOptions);
        json.put("subTaskFields", subTaskFields);
        json.put("sourceIssueToken", sourceIssueToken);
        json.put("targetIssueToken", targetIssueToken);
        return json;
    }

    public static AssetStep fromJson(JSONObject json) {
        AssetStep step = new AssetStep();
        step.setCopyAttachments(json.optBoolean("copyAttachments", true));
        step.setCopyLinks(json.optBoolean("copyLinks", true));
        step.setCopySubTasks(json.optBoolean("copySubTasks", false));
        step.setPromptOptions(json.optBoolean("promptOptions", false));
        step.setSubTaskFields(json.optString("subTaskFields", "summary,description,priority,assignee,reporter,environment,security"));
        step.setSourceIssueToken(json.optString("sourceIssueToken", "{{issue.key}}"));
        step.setTargetIssueToken(json.optString("targetIssueToken", "{{last_key}}"));
        return step;
    }
}
