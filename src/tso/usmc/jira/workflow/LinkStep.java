package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class LinkStep extends WorkflowStep {
    private List<LinkAction> linkActions = new ArrayList<>();

    public LinkStep() {
        super(StepType.LINK);
    }

    public List<LinkAction> getLinkActions() { return linkActions; }
    public void setLinkActions(List<LinkAction> actions) { this.linkActions = actions; }
    public void addLinkAction(LinkAction action) { this.linkActions.add(action); }

    // Legacy support for migration
    private boolean remote;
    public boolean isRemote() { return remote; }
    public void setRemote(boolean remote) { this.remote = remote; }

    @Override
    public void validate() throws Exception {
        if (linkActions.isEmpty()) {
            throw new Exception("Link Step: At least one link action must be defined.");
        }
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("type", getType().toString());
        json.put("stepId", stepId);
        json.put("label", label);

        // Condition fields
        if (conditionToken != null) json.put("conditionToken", conditionToken);
        if (conditionOperator != null) json.put("conditionOperator", conditionOperator);
        if (conditionValue != null) json.put("conditionValue", conditionValue);

        JSONArray actionsArr = new JSONArray();
        for (LinkAction la : linkActions) {
            actionsArr.put(la.toJson());
        }
        json.put("linkActions", actionsArr);
        
        return json;
    }

    public static LinkStep fromJson(JSONObject json) {
        LinkStep step = new LinkStep();
        String legacyInward = json.optString("inwardIssueToken", "{{issue.key}}");
        
        if (json.has("linkActions")) {
            JSONArray arr = json.getJSONArray("linkActions");
            for (int i = 0; i < arr.length(); i++) {
                LinkAction la = LinkAction.fromJson(arr.getJSONObject(i));
                // If it doesn't have an inward token specifically set, use the legacy one from step level
                if (!arr.getJSONObject(i).has("inwardIssueToken")) {
                    la.setInwardIssueToken(legacyInward);
                }
                step.addLinkAction(la);
            }
        } else if (json.has("linkType") || json.has("url")) {
            // Backward compatibility for single link
            LinkAction la = new LinkAction();
            la.setInwardIssueToken(legacyInward);
            la.setRemote(json.optBoolean("remote", false));
            la.setLinkType(json.optString("linkType", "Relates"));
            la.setOutwardIssueToken(json.optString("outwardIssueToken", "{{last_key}}"));
            la.setUrl(json.optString("url", ""));
            la.setTitle(json.optString("title", ""));
            la.setSummary(json.optString("summary", ""));
            la.setRelationship(json.optString("relationship", "links to"));
            step.addLinkAction(la);
        }
        
        return step;
    }
}
