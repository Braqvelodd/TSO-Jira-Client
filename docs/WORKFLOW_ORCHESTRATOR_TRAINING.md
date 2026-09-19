# Training Guide: Workflow Orchestrator Panel

## Overview
The **Workflow Orchestrator** is a high-level automation engine designed to build, manage, and execute complex Jira task sequences. It allows you to transform one issue into another, clone assets between tickets, log work, post comments, send notifications, and automate status transitions with a single click.

---

## 1. The Designer Tab
The Designer is where you build "Recipes" (automation templates).

### Header Controls
*   **Recipe Name:** The unique ID for this automation (saved as a `.json` file in `%USERPROFILE%\.JiraApiClient\workflows\`).
*   **Default Target Issues:** Optional default issue key(s) (e.g. `TSO-101, TSO-102`) saved with this recipe. If left blank, you can launch this recipe against any selected tickets directly from the JQL Runner or enter keys manually in the Runner.
*   **Context Issue:** Enter a real Jira Key (e.g., `TSO-123`) and click **Fetch Metadata**. This populates the field dropdowns and the **Token Browser** with real data from your Jira instance.
*   **Double-Click to Expand:** All text fields (Recipe Name, Target Issues, etc.) can be double-clicked to open a large multi-line editor for easier viewing of long strings.

### Step Types
You can stack multiple steps in a single recipe. The 9 supported step types are:

1.  **Transition:** Moves the issue to a new status (e.g., "In Progress"). Can optionally set issue fields during the transition.
2.  **Update:** Modifies fields on the current issue (or any target issue key/token).
3.  **Create:** Spawns a brand new issue. You can define the Project, Issue Type, Parent Issue Key (for sub-tasks), and map fields.
4.  **Link:** Creates standard Jira links (e.g., "Relates to", "Blocks") or **Remote Links** (hyperlinks pointing to external URLs with custom titles, summaries, and relationships).
5.  **Worklog:** Logs time spent (e.g., `1h 30m`), a worklog comment, and a start time (defaults to `{{now}}`). Supports runtime prompting.
6.  **Asset (Clone):** Copies attachments, links, and/or sub-tasks from a "Source" issue to a "Target" issue. Can prompt for options at runtime.
7.  **Attachment:** Uploads a file from a specified file path (supports tokens) or prompts the operator to select a file at runtime.
8.  **Comment:** Posts a comment to the target issue. Can prompt for comment text once for the entire batch or once per issue.
9.  **Notify:** Sends a Jira email notification to specific recipients (Assignee, Reporter, Watchers, Voters, custom users, and custom groups). Supports runtime prompting of subjects/bodies/recipients and features automatic `@team` membership expansion.

---

## 2. Field Mapping & Mapping Modes
Each field in an **Update** or **Create** step can be mapped in the following ways:

### A. STATIC (Hard-coded Values)
*   **Usage:** Fixed values that do not change between issues.
*   **Automatic Formatting:** For select lists/dropdown fields, just type the option name (e.g., `Design`). The client automatically wraps it in Jira's required structure (e.g. `{"value": "Design"}`).
*   **Multi-Select:** Type a comma-separated list (e.g., `Option A, Option B`) and the client converts it into a JSON array of options automatically.
*   **Power User JSON Mode:** If your value starts with `{` or `[`, the engine treats it as raw JSON and sends it directly to Jira.

### B. VARIABLE (Tokens)
*   **Usage:** Injects data dynamically from the issue being processed or from previous steps.
*   **Token Browser:** Located on the right sidebar of the Designer. Search for any Jira field to find its token, then double-click to copy it.

### C. PROMPT (Runtime Operator Input)
*   **Usage:** Asks the operator for input in the **Runner** tab before the workflow starts.
*   **Question Field:** What the user sees as the field label (e.g., "Enter Deadline").
*   **Opts (Options) Field:** 
    *   *Empty:* Displays a standard text input field.
    *   *Comma List:* Creates a dropdown list (e.g., `High, Medium, Low`).
    *   *Prompt Tags:* Dynamic tags to pull options from metadata cache or config (see Section 5).

---

## 3. The Token Engine
Tokens are placeholders surrounded by `{{...}}`. They resolve to real values during execution.

### Key Global Tokens:
*   `{{issue.key}}` (or `{{key}}`): The key of the issue currently being processed.
*   `{{fields.summary}}` (or `{{summary}}`): The summary of the current issue. (Paths nested in `fields` can omit the `fields.` prefix as a fallback).
*   `{{last.key}}` (or `{{last_key}}`): The key of the issue created or modified in a previous step.
*   `{{last.id}}`: The internal database ID of the last created issue.
*   `{{last.fields.summary}}`: The summary of the last created issue.
*   `{{today}}`: Current date in `YYYY-MM-DD` format.
*   `{{now}}`: ISO-8601 timestamp (`yyyy-MM-dd'T'HH:mm:ss.SSSZ`).

### Recipe Variables (Custom Tokens)
You can define custom tokens in the **Recipe Variables** section of the recipe designer:
*   **Custom Syntax:** Usable in any field as `{{variable.name}}` (e.g., `{{cycle.date}}`, `{{release}}`, `{{environment}}`).
*   **Prompt at Runtime:** When checked, the user is prompted for this value once before executing the recipe.
*   **Static Constants / Dynamic Defaults:** Can have fixed values or reference built-in tokens like `{{today}}` or `{{now}}`.
*   **Dropdown Options:** Provide an optional comma-separated list of values (e.g. `R1, R2, R3`) to create a dropdown choice at runtime.
*   *Example:* Defining `cycle.date` prompted as `20260918` allows you to write `{{cycle.date}} R2 cycle request` in Summary and `Request R2 cycle support for {{cycle.date}}:` in Description.

### Referencing Field Prompt Values
You can reference any field prompt value in later steps or fields using the syntax `{{Prompt Question.value}}` (using the exact text from the prompt's question field).
*   *Example:* If a step has a prompt with the question "Target Release Version", you can inject the answer into another field using `{{Target Release Version.value}}`.

### Logical Functions
*   **`COALESCE(arg1, arg2, ...)`**: Evaluates arguments from left to right and returns the first non-null, non-empty value. Literals must be quoted in single or double quotes.
    *   *Example:* `{{COALESCE(fields.assignee.name, 'Unassigned')}}`
    *   *Example:* `{{COALESCE(fields.customfield_10020, fields.summary, "Default Title")}}`

---

## 4. Conditional Step Execution (Conditional Branching)
Every step in a recipe supports conditional execution. If a step's condition evaluates to `false`, that step is skipped for the current issue, and the engine continues to the next step.

### Condition Configuration Fields
*   **Condition Token:** The token to evaluate (e.g., `{{fields.status.name}}` or `{{fields.priority.name}}`).
*   **Condition Operator:** How to compare the token. Supported operators:
    *   `ALWAYS` (default - always executes)
    *   `EQUALS` (case-insensitive match)
    *   `NOT_EQUALS` (case-insensitive mismatch)
    *   `CONTAINS` (case-insensitive substring search)
    *   `NOT_CONTAINS` (case-insensitive substring absence)
    *   `EMPTY` (checks if token evaluates to an empty string)
    *   `NOT_EMPTY` (checks if token contains any text)
*   **Condition Value:** The expected value to compare against (e.g., `Critical` or `In Progress`).

---

## 5. Dynamic Prompt Tags
You can place special tags in the **Prompt Question** or **Opts** field of a prompt to generate dynamic controls:

| Tag | UI Control / Behavior |
| :--- | :--- |
| `[allowed:fieldId]` | Queries the metadata cache for valid values of `fieldId`. Renders checkboxes for multi-select, or a searchable autocomplete box for single-select. |
| `[choice:A,B,{{summary}}]` | Generates a dropdown with custom options. Tokens inside options are resolved at runtime. |
| `[config:YOUR_KEY]` | Turns any comma-separated list defined in your local `JiraConfig.ini` into a dropdown. |
| `[config:teams]` | Dropdown of local teams; returns the selected team's **Lead Username**. |
| `[config:teams:component]` | Dropdown of local teams; returns the team's associated **Component Name**. |
| `[config:teams:id]` | Dropdown of local teams; returns the team's internal **Jira ID**. |
| `[config:fy_summary]` | Returns the `workflow_fy_summary_issue` key from local config. |

---

## 6. Advanced Team Logic
The Orchestrator features built-in team logic using the local `JiraConfig.ini` configurations.

### The Virtual Field: `teams_selection`
If you add a field with the ID `teams_selection` and set its mode to **PROMPT** with the `[config:teams]` tag, it behaves as a team selector:
1.  It generates a searchable team dropdown in the Runner.
2.  It is marked as **Virtual**, meaning it is never sent to Jira (preventing scheme errors).
3.  It populates team-specific tokens for use in any subsequent fields.

### Team Tokens
Once a team is selected, these tokens become active:
*   `{{team.name}}`: The display name of the selected team (e.g., `Team Decisive`).
*   `{{team.lead}}`: The team lead's Jira username (e.g., `SMITH.JOHN.A`).
*   `{{team.component}}`: The component name associated with that team.
*   `{{team.id}}`: The internal team ID number.

### Notification Team Expansion
In **Notify** steps, entering `@team.Alpha` or `team.Alpha` in the **To Users** field automatically expands to the user accounts listed under that team's `members` property in `JiraConfig.ini`.

---

## 7. The Runner Tab
The Runner is where you execute your recipes.

1.  **Select Recipe:** Choose your automation template.
2.  **Target Issues:** Enter comma/space-separated issue keys (e.g., `TSO-101, TSO-102`) or click **Load Saved** to populate the recipe's saved keys. You can also paste multi-line ticket lists directly (they are auto-formatted into clean keys).
    *   *Seamless JQL Runner Integration:* You can also right-click any issue (or select multiple issues and use the bulk menu) in the **JQL Runner** to run any recipe directly or transfer keys to the Runner tab.
3.  **Dynamic Inputs & Prompts:** If your recipe contains prompts (variables or step inputs), input fields or dropdowns are automatically generated. Fill them out before clicking Run.
4.  **Dry Run (Validate Only):** Check this to run the workflow in simulation mode. The engine checks transitions, validates fields, logs what actions *would* occur, but makes no changes to Jira.
5.  **Verbose API Logs:** Check to see detailed REST request and response payloads in the log.
6.  **Run Workflow:** Starts the process across the target issues using background threads so the GUI remains responsive.
7.  **Log & Reporting:** Real-time execution log. You can also click **Export Report (CSV)** when complete to download an execution report.

---

## 8. Pro Tips
*   **Metadata is King:** Always use "Fetch Metadata" with a context issue when designing. It allows the system to inspect the exact fields Jira expects for that issue type.
*   **Order of Operations:** Steps run top-to-bottom. If step 2 requires the key of the issue created in step 1, make sure the **Create** step is placed above the **Link** or **Asset** step.
*   **Dry Run First:** Always do a Dry Run before running a new recipe on a batch of tickets to ensure field mappings, transitions, and permissions are valid.
