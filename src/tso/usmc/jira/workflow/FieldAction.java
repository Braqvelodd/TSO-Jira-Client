package tso.usmc.jira.workflow;

import org.json.JSONObject;

public class FieldAction {
    public enum MappingMode {
        SET,      // Value is set (can contain tokens or literals)
        PROMPT    // User is asked at runtime
    }

    private String fieldId;
    private MappingMode mode;
    private Object value; // The static value or variable token
    private String promptLabel; // The question to ask if mode is PROMPT
    private String description; // Optional description for the UI

    public FieldAction() {}

    public FieldAction(String fieldId, MappingMode mode, Object value, String promptLabel) {
        this.fieldId = fieldId;
        this.mode = mode;
        this.value = value;
        this.promptLabel = promptLabel;
    }

    public String getFieldId() { return fieldId; }
    public void setFieldId(String fieldId) { this.fieldId = fieldId; }

    public MappingMode getMode() { return mode; }
    public void setMode(MappingMode mode) { this.mode = mode; }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    public String getPromptLabel() { return promptLabel; }
    public void setPromptLabel(String promptLabel) { this.promptLabel = promptLabel; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("fieldId", fieldId);
        json.put("mode", mode.toString());
        json.put("value", value);
        json.put("promptLabel", promptLabel);
        json.put("description", description);
        return json;
    }

    public static FieldAction fromJson(JSONObject json) {
        FieldAction fa = new FieldAction();
        String rawFieldId = "";
        if (json.has("fieldId")) {
            rawFieldId = json.getString("fieldId");
        }
        
        String fieldId = rawFieldId;
        String tokenVal = null;
        if (rawFieldId != null && rawFieldId.contains("{{")) {
            String token = rawFieldId.trim();
            if (token.startsWith("{{") && token.endsWith("}}")) {
                String clean = token.substring(2, token.length() - 2).trim();
                if (clean.startsWith("issue.fields.")) {
                    clean = clean.substring("issue.fields.".length()).trim();
                }
                fieldId = clean;
                tokenVal = token;
            }
        }
        
        fa.setFieldId(fieldId);
        
        String modeStr = json.getString("mode");
        if (modeStr.equals("STATIC") || modeStr.equals("VARIABLE")) {
            fa.setMode(MappingMode.SET);
        } else {
            fa.setMode(MappingMode.valueOf(modeStr));
        }
        
        if (json.has("value")) {
            fa.setValue(json.get("value"));
        }
        
        if (json.has("promptLabel")) fa.setPromptLabel(json.optString("promptLabel"));
        if (json.has("description")) fa.setDescription(json.optString("description"));
        return fa;
    }
}
