# USMC TSO Jira Client: Workflow Orchestrator Training

---

## Slide 1: Introduction to Workflow Orchestrator
**"Automate the Boring, Master the Complex"**

### What is it?
The **Workflow Orchestrator** is a multi-step automation engine that allows users to chain Jira actions together into a single "Recipe."

### Key Benefits:
*   **Consistency:** Ensure every ticket is updated, linked, and transitioned exactly the same way every time.
*   **Efficiency:** Perform 10+ Jira actions (Create, Update, Link, Transition) with one click.
*   **Power:** Dynamically map data between tickets using a robust Token Engine.
*   **Self-Service:** Non-technical users can run complex workflows designed by power users.

---

## Slide 2: The Workflow Lifecycle
**"From Concept to Completion"**

> **[SCREENSHOT ANNOTATION: Full view of the Workflow Orchestrator panel, highlighting the 'Designer' and 'Runner' tabs at the top.]**

A workflow moves through three distinct phases:

1.  **DESIGN (The Designer Tab):**
    *   Define your **Recipe Name** and **JQL Query**.
    *   Add **Steps** (Actions) in the order you want them to run.
    *   Use **Fetch Metadata** to see exactly what Jira expects for your issue types.

2.  **CONFIGURE (Field Mapping):**
    *   Set fields as **STATIC** (always the same), **VARIABLE** (copied from another ticket), or **PROMPT** (asked to the user at runtime).

3.  **EXECUTE (The Runner Tab):**
    *   Select your recipe.
    *   Fill in any dynamic prompts.
    *   Click **Run** and watch the logs in real-time.

---

## Slide 3: Anatomy of the Designer
**"Building Your Automation Blueprint"**

> **[SCREENSHOT ANNOTATION: Close-up of the top section of the Designer tab: Recipe Name, JQL Query box, and the 'Fetch Metadata' button next to the Context Issue field.]**

### Essential UI Controls:
*   **Recipe Name:** A unique identifier (e.g., `create-deployment-subtask`).
*   **JQL Query:** The search filter that finds the "Source" issues (e.g., `project = TFS AND status = "Ready for Test"`).
*   **Context Issue:** Enter a real Jira Key (e.g., `TFS-1234`) to "prime" the designer with real field data.
*   **Step List:** The vertical stack of actions. Drag-and-drop or use "Move Up/Down" to change execution order.

### Pro-Tip:
*   **Double-Click** any text field (like the JQL box) to open a large-format editor for easier typing.

---

## Slide 4: Step Types (The Building Blocks)
**"What can your workflow actually DO?"**

> **[SCREENSHOT ANNOTATION: The vertical Step List in the Designer showing a variety of steps stacked together.]**

You can mix and match these 9 primary action types:

1.  **TRANSITION:** Moves an issue to a new status (e.g., "In Progress"). Can set fields during status change.
2.  **UPDATE:** Modifies custom or standard fields on the target issue.
3.  **CREATE:** Spawns a new ticket (with optional parent/sub-task linking).
4.  **LINK:** Binds issues via standard links or external **Remote Links** (URL bookmarks).
5.  **WORKLOG:** Logs time (e.g., `2h 30m`), starting timestamp, and descriptions.
6.  **ASSET (CLONE):** Copies attachments, links, and sub-tasks from source to target.
7.  **ATTACHMENT:** Uploads local files or prompts the user for paths at runtime.
8.  **COMMENT:** Posts comments to the ticket's history feed.
9.  **NOTIFY:** Sends custom Jira notifications to users, groups, or team member lists.

---

## Slide 5: Field Mapping & Mapping Modes
**"Data Orchestration Made Simple"**

> **[SCREENSHOT ANNOTATION: Close-up of a specific field showing the radio buttons for STATIC, VARIABLE, and PROMPT mapping.]**

Each field in an **Update** or **Create** step has a "Mapping Mode":

### 1. STATIC
*   The value never changes.
*   *Example:* Setting "Priority" to "High" for every ticket created.
*   *Note:* Array fields accept comma-separated inputs; JSON structure is handled automatically.

### 2. VARIABLE (Tokens)
*   Copies data dynamically using tokens.
*   *Example:* `{{fields.summary}}` copies the summary from the original ticket.

### 3. PROMPT
*   Asks the user a question *once* before the batch starts.
*   *Example:* Question: "What is the release date?" -> The answer is applied to all processed tickets.

---

## Slide 6: The Token Engine
**"The Secret Sauce of Dynamic Data"**

> **[SCREENSHOT ANNOTATION: The 'Token Browser' sidebar on the far right of the Designer, showing a search result for a field and its corresponding {{token}} value.]**

Tokens are placeholders surrounded by `{{...}}`. They allow steps to share data and resolve dynamic paths.

### Key Global Tokens:
*   `{{issue.key}}` (or `{{key}}`): Key of the current ticket in the loop.
*   `{{fields.summary}}` (or `{{summary}}`): Summary of the current ticket.
*   `{{last_key}}` (or `{{last.key}}`): Key of the ticket created in a prior step.
*   `{{last.id}}`: Internal database ID of the last created ticket.
*   `{{now}}` / `{{today}}`: Current timestamp / date.

### Advanced Tokens & Fallbacks:
*   **Prompt Reference:** Reference operator answers in subsequent steps using `{{Prompt Question.value}}`.
*   **COALESCE:** Returns the first non-empty value: `{{COALESCE(fields.assignee.name, 'Unassigned')}}`.

---

## Slide 7: Advanced Logic: Virtual Fields, Teams, and Tags
**"Power User Features"**

> **[SCREENSHOT ANNOTATION: A comparison shot: Left side showing the 'teams_selection' field configured in the Designer; Right side showing how it appears as a searchable dropdown in the Runner.]**

### Virtual Fields (`teams_selection`)
If you add a PROMPT field named `teams_selection`, the Orchestrator enables "Team Logic":
*   It generates a searchable dropdown of teams defined in your `JiraConfig.ini`.
*   It **never** sends this field to Jira (preventing scheme errors).
*   It unlocks team-specific tokens: `{{team.name}}`, `{{team.lead}}`, `{{team.component}}`, and `{{team.id}}`.

### Config & Custom Tags (`[...]`)
Use tags in prompt opts or questions to generate dynamic widgets:
*   `[allowed:fieldId]`: Generates checkbox lists or autocomplete fields based on valid values.
*   `[choice:A,B,C]`: Hardcodes custom dropdown choice options.
*   `[config:key_name]`: Pulls list options from local settings (e.g. `[config:teams]`).

---

## Slide 8: Conditional Step Execution
**"Branching Logic Without Scripting"**

> **[SCREENSHOT ANNOTATION: Close-up of the 'Condition' section of a Step in the Designer, showing the token, operator, and expected value fields.]**

Every step in a recipe supports conditional checks. If the condition evaluates to false, that step is skipped.

### Conditional Fields:
*   **Condition Token:** The token to evaluate (e.g., `{{fields.priority.name}}`).
*   **Condition Operator:** The comparison method (`ALWAYS`, `EQUALS`, `NOT_EQUALS`, `CONTAINS`, `NOT_CONTAINS`, `EMPTY`, `NOT_EMPTY`).
*   **Condition Value:** The expected value to compare against (e.g., `Critical`).

### Example Case:
*   Add a step that only updates the assignee if the current issue is unassigned (`conditionToken: {{fields.assignee.name}}`, `conditionOperator: EMPTY`).

---

## Slide 9: Running & Monitoring
**"The Finish Line"**

> **[SCREENSHOT ANNOTATION: The Runner tab mid-execution: show the dynamic form at the top and the colorful execution log (Blue/Green/Red) scrolling at the bottom.]**

### The Runner Panel:
1.  **Select Recipe:** Load your saved automation.
2.  **Input Data:** If you defined PROMPTs, they appear as a form. Fill it out once.
3.  **Review JQL:** Confirm which tickets will be targeted.
4.  **Dry Run?** Check your JQL first to ensure the "Issue Count" matches your expectations.
5.  **Execution Log:**
    *   **Blue:** Informational (e.g., "Starting Step 1", Dry Run reports).
    *   **Green:** Success (e.g., "Created TFS-9999").
    *   **Red:** Errors (e.g., "Permission Denied").

### Post-Execution:
*   Click the **Issue Keys** in the log to open them directly in your browser for final validation.
