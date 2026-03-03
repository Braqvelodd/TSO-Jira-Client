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
    public static void setupExpandedView(javax.swing.JTextField field) {
        field.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    javax.swing.JTextArea area = new javax.swing.JTextArea(15, 50);
                    area.setText(field.getText());
                    area.setLineWrap(true);
                    area.setWrapStyleWord(true);
                    int result = javax.swing.JOptionPane.showConfirmDialog(field, new javax.swing.JScrollPane(area), 
                        "Expanded Input", javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE);
                    if (result == javax.swing.JOptionPane.OK_OPTION) {
                        field.setText(area.getText());
                    }
                }
            }
        });
        field.setToolTipText("Double-click to expand");
    }
}
