package tso.usmc.jira.workflow;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class WorkflowRecipe {
    private String recipeName;
    private String targetIssues = "";
    private List<WorkflowStep> steps = new ArrayList<>();
    private List<RecipeVariable> variables = new ArrayList<>();
    private JSONObject metadataSnapshot = new JSONObject();

    public String getRecipeName() { return recipeName; }
    public void setRecipeName(String recipeName) { this.recipeName = recipeName; }

    public String getTargetIssues() { return targetIssues != null ? targetIssues : ""; }
    public void setTargetIssues(String targetIssues) { this.targetIssues = targetIssues != null ? targetIssues : ""; }

    public boolean hasPredefinedIssues() {
        return targetIssues != null && !targetIssues.trim().isEmpty();
    }

    public String getJqlQuery() { return getTargetIssues(); }
    public void setJqlQuery(String jqlQuery) { setTargetIssues(jqlQuery); }

    public List<WorkflowStep> getSteps() { return steps; }
    public void setSteps(List<WorkflowStep> steps) { this.steps = steps; }
    public void addStep(WorkflowStep step) { this.steps.add(step); }

    public List<RecipeVariable> getVariables() { return variables; }
    public void setVariables(List<RecipeVariable> variables) { this.variables = variables != null ? variables : new ArrayList<>(); }
    public void addVariable(RecipeVariable var) { if (this.variables == null) this.variables = new ArrayList<>(); this.variables.add(var); }

    public JSONObject getMetadataSnapshot() { return metadataSnapshot; }
    public void setMetadataSnapshot(JSONObject metadataSnapshot) { this.metadataSnapshot = metadataSnapshot; }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("recipeName", recipeName);
        json.put("targetIssues", getTargetIssues());
        json.put("jqlQuery", getTargetIssues()); // Alias for backward compatibility
        json.put("metadataSnapshot", metadataSnapshot);
        
        JSONArray stepsArr = new JSONArray();
        for (WorkflowStep step : steps) {
            stepsArr.put(step.toJson());
        }
        json.put("steps", stepsArr);

        JSONArray varsArr = new JSONArray();
        if (variables != null) {
            for (RecipeVariable var : variables) {
                varsArr.put(var.toJson());
            }
        }
        json.put("variables", varsArr);

        return json;
    }

    public static WorkflowRecipe fromJson(String jsonStr) {
        JSONObject json = new JSONObject(jsonStr);
        WorkflowRecipe recipe = new WorkflowRecipe();
        recipe.setRecipeName(json.optString("recipeName"));
        if (json.has("targetIssues")) {
            recipe.setTargetIssues(json.optString("targetIssues"));
        } else {
            recipe.setTargetIssues(json.optString("jqlQuery"));
        }
        recipe.setMetadataSnapshot(json.optJSONObject("metadataSnapshot"));
        
        JSONArray stepsArr = json.optJSONArray("steps");
        if (stepsArr != null) {
            for (int i = 0; i < stepsArr.length(); i++) {
                recipe.addStep(WorkflowStep.fromJson(stepsArr.getJSONObject(i)));
            }
        }

        JSONArray varsArr = json.optJSONArray("variables");
        if (varsArr != null) {
            for (int i = 0; i < varsArr.length(); i++) {
                RecipeVariable var = RecipeVariable.fromJson(varsArr.getJSONObject(i));
                if (var != null) recipe.addVariable(var);
            }
        }

        return recipe;
    }
}
