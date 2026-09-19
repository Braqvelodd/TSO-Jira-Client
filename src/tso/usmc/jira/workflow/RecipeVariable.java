package tso.usmc.jira.workflow;

import org.json.JSONObject;

/**
 * Represents a custom variable (token) defined at the recipe level.
 * Can be configured as a static constant or prompted at execution time.
 */
public class RecipeVariable {
    private String name = "";
    private String label = "";
    private String defaultValue = "";
    private boolean prompt = true;
    private String options = "";

    public RecipeVariable() {}

    public RecipeVariable(String name, String label, String defaultValue, boolean prompt, String options) {
        setName(name);
        this.label = label != null ? label : "";
        this.defaultValue = defaultValue != null ? defaultValue : "";
        this.prompt = prompt;
        this.options = options != null ? options : "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null) {
            // Strip any accidental curly braces or spaces e.g. {{cycle.date}} -> cycle.date
            this.name = name.replaceAll("[{}]", "").trim();
        } else {
            this.name = "";
        }
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label != null ? label : "";
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue != null ? defaultValue : "";
    }

    public boolean isPrompt() {
        return prompt;
    }

    public void setPrompt(boolean prompt) {
        this.prompt = prompt;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options != null ? options : "";
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("name", name);
        json.put("label", label);
        json.put("defaultValue", defaultValue);
        json.put("prompt", prompt);
        json.put("options", options);
        return json;
    }

    public static RecipeVariable fromJson(JSONObject json) {
        if (json == null) return null;
        RecipeVariable var = new RecipeVariable();
        var.setName(json.optString("name"));
        var.setLabel(json.optString("label"));
        var.setDefaultValue(json.optString("defaultValue"));
        var.setPrompt(json.optBoolean("prompt", true));
        var.setOptions(json.optString("options"));
        return var;
    }
}
