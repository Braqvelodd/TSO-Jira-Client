package tso.usmc.jira.ui;

// A simple data class to hold all the info for our assignee dropdown.
public class AssigneeOption {
    private final String displayName; // Text shown in the dropdown (e.g., "Team Lifeline")
    private final String assigneeJiraId;  // The JIRA username (e.g., "HULL.JAMES.DOUGLAS")
    private final String componentName; // The component/team name, null for Unassigned
    private final String teamId;

    public AssigneeOption(String displayName, String assigneeJiraId, String componentName, String teamId) {
        this.displayName = displayName;
        this.assigneeJiraId = assigneeJiraId;
        this.componentName = componentName;
        this.teamId = teamId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAssigneeJiraId() {
        return assigneeJiraId;
    }

    public String getComponentName() {
        return componentName;
    }
    public String getTeamId() { 
        return teamId; 
    }

    // This is what JComboBox will display by default. We'll override this with a renderer.
    @Override
    public String toString() {
        return displayName;
    }
}
