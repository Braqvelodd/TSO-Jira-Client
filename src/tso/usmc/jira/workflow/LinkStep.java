package tso.usmc.jira.workflow;

import org.json.JSONObject;

public class LinkStep extends WorkflowStep {
    private String inwardIssueToken = "{{issue.key}}";
    private String outwardIssueToken = "{{last_key}}";
    private String linkType = "Relates";

    public LinkStep() {
        super(StepType.LINK);
    }

    public String getInwardIssueToken() { return inwardIssueToken; }
    public void setInwardIssueToken(String inwardIssueToken) { this.inwardIssueToken = inwardIssueToken; }

    public String getOutwardIssueToken() { return outwardIssueToken; }
    public void setOutwardIssueToken(String outwardIssueToken) { this.outwardIssueToken = outwardIssueToken; }

    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);
        json.put("inwardIssueToken", inwardIssueToken);
        json.put("outwardIssueToken", outwardIssueToken);
        json.put("linkType", linkType);
        return json;
    }

    public static LinkStep fromJson(JSONObject json) {
        LinkStep step = new LinkStep();
        step.setInwardIssueToken(json.optString("inwardIssueToken", "{{issue.key}}"));
        step.setOutwardIssueToken(json.optString("outwardIssueToken", "{{last_key}}"));
        step.setLinkType(json.optString("linkType", "Relates"));
        return step;
    }
}
