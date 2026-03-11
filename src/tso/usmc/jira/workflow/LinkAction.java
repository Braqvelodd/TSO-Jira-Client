package tso.usmc.jira.workflow;

import org.json.JSONObject;

public class LinkAction {
    private boolean remote = false;
    private String inwardIssueToken = "{{issue.key}}";
    
    // Jira Link fields
    private String linkType = "Relates";
    private String outwardIssueToken = "{{last_key}}";
    
    // Remote Link fields
    private String url = "";
    private String title = "";
    private String summary = "";
    private String relationship = "links to";

    public LinkAction() {}

    public String getInwardIssueToken() { return inwardIssueToken; }
    public void setInwardIssueToken(String token) { this.inwardIssueToken = token; }

    public boolean isRemote() { return remote; }
    public void setRemote(boolean remote) { this.remote = remote; }

    public String getLinkType() { return linkType; }
    public void setLinkType(String linkType) { this.linkType = linkType; }

    public String getOutwardIssueToken() { return outwardIssueToken; }
    public void setOutwardIssueToken(String outwardIssueToken) { this.outwardIssueToken = outwardIssueToken; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("remote", remote);
        json.put("inwardIssueToken", inwardIssueToken);
        if (remote) {
            json.put("url", url);
            json.put("title", title);
            json.put("summary", summary);
            json.put("relationship", relationship);
        } else {
            json.put("linkType", linkType);
            json.put("outwardIssueToken", outwardIssueToken);
        }
        return json;
    }

    public static LinkAction fromJson(JSONObject json) {
        LinkAction la = new LinkAction();
        la.setRemote(json.optBoolean("remote", false));
        la.setInwardIssueToken(json.optString("inwardIssueToken", "{{issue.key}}"));
        la.setLinkType(json.optString("linkType", "Relates"));
        la.setOutwardIssueToken(json.optString("outwardIssueToken", "{{last_key}}"));
        la.setUrl(json.optString("url", ""));
        la.setTitle(json.optString("title", ""));
        la.setSummary(json.optString("summary", ""));
        la.setRelationship(json.optString("relationship", "links to"));
        return la;
    }
}
