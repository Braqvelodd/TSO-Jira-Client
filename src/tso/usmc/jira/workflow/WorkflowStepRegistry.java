package tso.usmc.jira.workflow;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry for WorkflowStep types.
 * Allows new step types to be added without modifying the base WorkflowStep class.
 */
public class WorkflowStepRegistry {
    private static final Map<String, Function<JSONObject, WorkflowStep>> registry = new HashMap<>();

    static {
        // Register standard steps
        register("TRANSITION", TransitionStep::fromJson);
        register("UPDATE", UpdateStep::fromJson);
        register("CREATE", CreateStep::fromJson);
        register("LINK", LinkStep::fromJson);
        register("WORKLOG", WorklogStep::fromJson);
        register("ASSET", AssetStep::fromJson);
        register("CLONE", AssetStep::fromJson); // Legacy alias
        
        // Custom handling for legacy REMOTE_LINK format
        register("REMOTE_LINK", json -> {
            LinkStep ls = new LinkStep();
            LinkAction la = LinkAction.fromJson(json);
            la.setRemote(true);
            if (json.has("targetIssueToken")) la.setInwardIssueToken(json.getString("targetIssueToken"));
            ls.addLinkAction(la);
            return ls;
        });
    }

    public static void register(String type, Function<JSONObject, WorkflowStep> creator) {
        registry.put(type.toUpperCase(), creator);
    }

    public static WorkflowStep createStep(JSONObject json) {
        String type = json.optString("type", "UPDATE").toUpperCase();
        Function<JSONObject, WorkflowStep> creator = registry.get(type);
        if (creator != null) {
            return creator.apply(json);
        }
        return new UpdateStep(); // Fallback
    }
}
