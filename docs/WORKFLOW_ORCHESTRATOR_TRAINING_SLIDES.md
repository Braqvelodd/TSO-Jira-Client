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

> **[SCREENSHOT ANNOTATION: The vertical Step List in the Designer showing a variety of steps like 'Create', 'Transition', and 'Link' stacked together.]**

You can mix and match these 6 primary action types:

1.  **TRANSITION:** Moves an issue to a new status (e.g., "In Progress").
2.  **UPDATE:** Modifies fields on the current issue.
3.  **CREATE:** Spawns a new ticket. You can set Project, Type, and all required fields.
4.  **LINK:** Creates a relationship (e.g., "Relates to", "Blocks") between issues.
5.  **WORKLOG:** Logs time (e.g., `2h 30m`) and adds a comment to a ticket.
6.  **ASSET (CLONE):** Copies attachments, links, and even sub-tasks from a "Source" to a "Target."

---

## Slide 5: Field Mapping & Mapping Modes
**"Data Orchestration Made Simple"**

> **[SCREENSHOT ANNOTATION: Close-up of a specific field (like 'Summary' or 'Assignee') showing the three radio buttons for STATIC, VARIABLE, and PROMPT.]**

Each field in an **Update** or **Create** step has a "Mapping Mode":

### 1. STATIC
*   The value never changes.
*   *Example:* Setting "Priority" to "High" for every ticket created by this recipe.

### 2. VARIABLE (Tokens)
*   Copies data dynamically.
*   *Example:* `{{fields.summary}}` copies the summary from the original ticket.

### 3. PROMPT
*   Asks the user a question *once* before the batch starts.
*   *Example:* Question: "What is the release date?" -> The answer is applied to all processed tickets.

---

## Slide 6: The Token Engine
**"The Secret Sauce of Dynamic Data"**

> **[SCREENSHOT ANNOTATION: The 'Token Browser' sidebar on the far right of the Designer, showing a search result for a field and its corresponding {{token}} value.]**

Tokens are placeholders surrounded by `{{...}}`. They allow steps to talk to each other.

### Key Global Tokens:
*   `{{issue.key}}`: The key of the ticket currently being processed.
*   `{{fields.summary}}`: The summary of the current ticket.
*   `{{last_key}}`: The key of the ticket created in the *previous* "Create" step.
*   `{{now}}`: Current date and time.

### The Token Browser:
*   Located on the right sidebar of the Designer.
*   Search for any Jira field to find its token path.
*   **Double-click** to copy the token directly to your clipboard.

---

## Slide 7: Advanced Logic: Virtual Fields & Teams
**"Power User Features"**

> **[SCREENSHOT ANNOTATION: A comparison shot: Left side showing the 'teams_selection' field configured in the Designer; Right side showing how it appears as a searchable dropdown in the Runner.]**

### Virtual Fields (`teams_selection`)
If you add a PROMPT field named `teams_selection`, the Orchestrator enables "Team Logic":
*   It generates a searchable dropdown of teams defined in your `JiraConfig.ini`.
*   It **never** sends this field to Jira (preventing errors).
*   It unlocks team-specific tokens: `{{team.name}}`, `{{team.lead}}`, and `{{team.component}}`.

### Config Tags (`[config:...]`)
You can use `[config:key_name]` in any PROMPT field to pull a dynamic list of options from your local settings, ensuring your workflows stay up-to-date with your environment.

---

## Slide 8: Running & Monitoring
**"The Finish Line"**

> **[SCREENSHOT ANNOTATION: The Runner tab mid-execution: show the dynamic form at the top and the colorful execution log (Blue/Green/Red) scrolling at the bottom.]**

### The Runner Panel:
1.  **Select Recipe:** Load your saved automation.
2.  **Input Data:** If you defined PROMPTs, they appear as a form. Fill it out once.
3.  **Review JQL:** Confirm which tickets will be targeted.
4.  **Dry Run?** Check your JQL first to ensure the "Issue Count" matches your expectations.
5.  **Execution Log:**
    *   **Blue:** Informational (e.g., "Starting Step 1").
    *   **Green:** Success (e.g., "Created TFS-9999").
    *   **Red:** Errors (e.g., "Permission Denied").

### Post-Execution:
*   Click the **Issue Keys** in the log to open them directly in your browser for final validation.
