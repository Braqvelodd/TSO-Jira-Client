package tso.usmc.jira.util;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A utility class for common Jira-related helper functions.
 */
public class JiraUtils {

    /**
     * Searches a JSON string containing an array of transitions and returns the ID of the
     * transition that matches the given name (case-insensitive).
     *
     * @param transitionsJson The JSON string response from a /transitions API call.
     * @param transitionName  The display name of the transition to find (e.g., "In Progress", "Done").
     * @return The string ID of the found transition, or null if no match is found.
     */
    public static String findTransitionIdByName(String transitionsJson, String transitionName) {
        // Guard against null or empty input
        if (transitionsJson == null || transitionsJson.isEmpty() || transitionName == null) {
            return null;
        }

        JSONObject response = new JSONObject(transitionsJson);

        // Check if the 'transitions' key exists and is an array
        if (!response.has("transitions")) {
            return null;
        }

        JSONArray transitions = response.getJSONArray("transitions");
        for (int i = 0; i < transitions.length(); i++) {
            JSONObject t = transitions.getJSONObject(i);
            if (t.has("name") && t.getString("name").equalsIgnoreCase(transitionName)) {
                return t.getString("id");
            }
        }

        // Return null if no transition with that name was found
        return null;
    }

    // You can add other static utility methods here in the future
}
