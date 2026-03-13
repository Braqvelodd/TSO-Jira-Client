package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AssetStep extends WorkflowStep {
    private boolean copyAttachments = true;
    private boolean copyLinks = true;
    private boolean copySubTasks = false;
    private boolean promptOptions = false;
    private List<String> subTaskFields = new ArrayList<>(Arrays.asList("summary", "description", "priority", "assignee", "reporter", "environment", "security"));
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

    public List<String> getSubTaskFieldsList() { return subTaskFields; }
    public void setSubTaskFieldsList(List<String> subTaskFields) { this.subTaskFields = subTaskFields; }

    /**
     * Legacy getter for backward compatibility with WorkflowEngine.
     */
    public String getSubTaskFields() {
        return String.join(",", subTaskFields);
    }

    /**
     * Legacy setter for backward compatibility with UI.
     */
    public void setSubTaskFields(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            this.subTaskFields = new ArrayList<>();
        } else {
            this.subTaskFields = new ArrayList<>(java.util.Arrays.asList(csv.split("\\s*,\\s*")));
        }
    }

    public String getSourceIssueToken() { return sourceIssueToken; }
    public void setSourceIssueToken(String sourceIssueToken) { this.sourceIssueToken = sourceIssueToken; }

    public String getTargetIssueToken() { return targetIssueToken; }
    public void setTargetIssueToken(String targetIssueToken) { this.targetIssueToken = targetIssueToken; }

    @Override
    public void validate() throws Exception {
        if (sourceIssueToken == null || sourceIssueToken.trim().isEmpty()) {
            throw new Exception("Asset Step: Source Issue Token is required.");
        }
        if (targetIssueToken == null || targetIssueToken.trim().isEmpty()) {
            throw new Exception("Asset Step: Target Issue Token is required.");
        }
    }

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
        json.put("subTaskFields", new JSONArray(subTaskFields));
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
        
        if (json.has("subTaskFields")) {
            Object obj = json.get("subTaskFields");
            if (obj instanceof JSONArray) {
                JSONArray arr = (JSONArray) obj;
                List<String> fields = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) fields.add(arr.getString(i));
                step.setSubTaskFieldsList(fields);
            } else if (obj instanceof String) {
                // Backward compatibility
                String csv = (String) obj;
                step.setSubTaskFieldsList(new ArrayList<>(Arrays.asList(csv.split(","))));
            }
        }
        
        step.setSourceIssueToken(json.optString("sourceIssueToken", "{{issue.key}}"));
        step.setTargetIssueToken(json.optString("targetIssueToken", "{{last_key}}"));
        return step;
    }
}
