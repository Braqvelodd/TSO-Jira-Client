# Training Guide: Workflow Orchestrator Panel

## Overview
The **Workflow Orchestrator** is a high-level automation engine designed to build, manage, and execute complex Jira task sequences. It allows you to transform one issue into another, clone assets between tickets, and automate repetitive status transitions with a single click.

---

## 1. The Designer Tab
The Designer is where you build "Recipes" (automation templates).

### Header Controls
*   **Recipe Name:** The unique ID for this automation.
*   **JQL Query:** The default search filter used to find issues when this recipe is selected in the Runner.
*   **Context Issue:** Enter a real Jira Key (e.g., `TSO-123`) and click **Fetch Metadata**. This populates the field dropdowns and the **Token Browser** with real data from your Jira instance.
*   **Double-Click to Expand:** All text fields (Recipe Name, JQL, etc.) can be double-clicked to open a large multi-line editor for easier viewing of long strings.

### Step Types
You can stack multiple steps in a single recipe:
1.  **Transition:** Moves the issue to a new status (e.g., "In Progress").
2.  **Update:** Modifies fields on the *current* issue being processed.
3.  **Create:** Spawns a brand new issue. You can define the Project and Type.
4.  **Link:** Creates a Jira link (e.g., "Relates to") between two issues.
5.  **Clone:** Copies attachments and/or issue links from a "Source" issue to a "Target" issue.

---

## 2. Field Mapping Modes
Each field in an **Update** or **Create** step can be mapped in three ways:

### A. STATIC
*   **Usage:** Hard-coded values.
*   **Formatting:** For select lists, just type the name (e.g., `Design`). The system automatically wraps it in Jira's required `{"value": "Design"}` format.
*   **Multi-Select:** Type a comma-separated list (e.g., `Option A, Option B`) and the system will handle the JSON array conversion automatically.

### B. VARIABLE (Tokens)
*   **Usage:** Injects data from the issue being processed or from previous steps.
*   **Common Tokens:**
    *   `{{key}}`: The Jira key of the issue you are currently processing.
    *   `{{fields.summary}}`: The summary of the original issue.
    *   `{{last_key}}`: The Jira key of the issue created in a *previous* "Create" step.
*   **Token Browser:** Use the sidebar on the right to search for tokens. Double-click any token to copy it to your clipboard.

### C. PROMPT
*   **Usage:** Asks the operator for input *once* before the workflow starts.
*   **Question Field:** What the user sees (e.g., "Enter Deadline").
*   **Opts (Options) Field:** 
    *   **Empty:** Standard text input.
    *   **Comma List:** Creates a dropdown (e.g., `High, Medium, Low`).
    *   **Config Tag:** Uses values from `JiraConfig.ini` (see Section 4).

---

## 3. Advanced Team Logic
The Orchestrator has specialized support for team-based assignments.

### The Virtual Field: `teams_selection`
If you add a field with the ID `teams_selection` and set it to **PROMPT** mode with the `[config:teams]` tag, it acts as a "Variable Setter."
*   It generates a team dropdown in the Runner.
*   It is **NOT** sent to Jira, so it won't cause "Invalid Field" errors.
*   It populates team-specific tokens for use in other fields.

### Team Tokens
Once a team is selected, these tokens become active:
*   `{{team.name}}`: The display name (e.g., Team Decisive).
*   `{{team.lead}}`: The team lead's Jira ID (for Assignee fields).
*   `{{team.component}}`: The Jira component name associated with that team.
*   `{{team.id}}`: The internal team ID number.

---

## 4. Configuration Tags (`[config:...]`)
You can use values from your `JiraConfig.ini` to drive dropdowns by placing tags in the **Prompt Question** or **Opts** field.

| Tag | Result |
| :--- | :--- |
| `[config:teams]` | List of teams; returns the **Lead ID**. |
| `[config:teams:component]` | List of teams; returns the **Component Name**. |
| `[config:teams:id]` | List of teams; returns the **Team ID**. |
| `[config:fy_summary]` | Returns the `workflow_fy_summary_issue` from config. |
| `[config:YOUR_KEY]` | Turns any comma-separated list in the INI into a dropdown. |

---

## 5. The Runner Tab
The Runner is where you execute your recipes.

1.  **Select Recipe:** Choose your automation.
2.  **Dynamic Inputs:** If your recipe has PROMPTs, a form is automatically generated here. Fill this out **once**; these values apply to all issues found.
3.  **Override JQL:** Defaults to the recipe's JQL. You can enter a new query or even a single issue key (e.g., `TFS-5555`).
4.  **Run Workflow:** Starts the process. The execution is threaded so the UI won't freeze.
5.  **Log:** Displays real-time progress, including keys of created issues and any errors encountered.

---

## 6. Pro Tips
*   **Metadata is King:** Always use "Fetch Metadata" with a context issue when designing. It allows the system to "see" what Jira expects for that specific ticket type.
*   **JSON Mode:** If you are a power user, you can type raw JSON (starting with `{` or `[`) into a **STATIC** field, and the engine will send it exactly as written.
*   **Order Matters:** Steps run top-to-bottom. If you want to create a ticket and then link it to the original, ensure the **Create** step is above the **Link** step.
