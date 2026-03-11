package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class WorkflowRecipe {
    private String recipeName;
    private String jqlQuery;
    private List<WorkflowStep> steps = new ArrayList<>();
    private JSONObject metadataSnapshot = new JSONObject();

    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }

    public String getJqlQuery() { return jqlQuery; }
    public void setJqlQuery(String jqlQuery) { this.jqlQuery = jqlQuery; }

    public List<WorkflowStep> getSteps() { return steps; }
    public void setSteps(List<WorkflowStep> steps) { this.steps = steps; }
    public void addStep(WorkflowStep step) { this.steps.add(step); }

    public JSONObject getMetadataSnapshot() { return metadataSnapshot; }
    public void setMetadataSnapshot(JSONObject metadataSnapshot) { this.metadataSnapshot = metadataSnapshot; }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("recipeName", recipeName);
        json.put("jqlQuery", jqlQuery);
        json.put("metadataSnapshot", metadataSnapshot);
        
        JSONArray stepsArr = new JSONArray();
        for (WorkflowStep step : steps) {
            stepsArr.put(step.toJson());
        }
        json.put("steps", stepsArr);
        return json;
    }

    public static WorkflowRecipe fromJson(String jsonStr) {
        JSONObject json = new JSONObject(jsonStr);
        WorkflowRecipe recipe = new WorkflowRecipe();
        recipe.setRecipeName(json.optString("recipeName"));
        recipe.setJqlQuery(json.optString("jqlQuery"));
        recipe.setMetadataSnapshot(json.optJSONObject("metadataSnapshot"));
        
        JSONArray stepsArr = json.optJSONArray("steps");
        if (stepsArr != null) {
            for (int i = 0; i < stepsArr.length(); i++) {
                recipe.addStep(WorkflowStep.fromJson(stepsArr.getJSONObject(i)));
            }
        }
        return recipe;
    }
}
