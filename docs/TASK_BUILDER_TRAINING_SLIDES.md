# USMC TSO Jira Client: Task Builder Training

---

## Slide 1: Introduction to Task Builder
**"Rapid Sub-task Generation & Bulk Creation"**

### What is it?
The **Task Builder** is a specialized tool for quickly drafting and creating multiple Jira tasks (usually sub-tasks) in one go. It uses a "text-first" approach, allowing you to type or paste a list of tasks and instantly turn them into real Jira issues.

### Key Benefits:
*   **Drafting Speed:** Type your task summaries and descriptions as a continuous text block.
*   **Intelligent Parsing:** The system automatically detects task boundaries and properties.
*   **Bulk Actions:** Create, transition, and notify users for 20+ tasks in seconds.
*   **Templates:** Save common task sets (e.g., "Standard PCU Testing") to reuse later.

---

## Slide 2: The Default Configuration
**"Set Once, Apply to All"**

> **[SCREENSHOT ANNOTATION: The 'Defaults' panel at the top left, showing Parent, Type, Assignee, Component, and Transition fields.]**

Before you start typing, set your global defaults. Any task you draft will automatically inherit these values unless you specifically override them.

*   **Parent:** The Jira Key (e.g., `TSO-555`) all sub-tasks will be linked to.
*   **Type:** The default issue type (Sub-task, ST-PCU, etc.).
*   **Assignee:** Who should be assigned to these tasks by default.
*   **Component:** The Jira component to apply.
*   **Transition:** The status to move the tasks to immediately after they are created.

---

## Slide 3: Drafting Tasks (The Syntax)
**"Simple Text, Powerful Results"**

> **[SCREENSHOT ANNOTATION: The 'Input Area' on the left, showing a few tasks separated by '***' lines, with some lines starting with '--' as comments.]**

Tasks are separated by three or more asterisks (`***`).

### The Basic Format:
*   **First Line:** This becomes the **Summary**.
*   **Following Lines:** These become the **Description**.
*   **Comments:** Lines starting with `--` are ignored (great for notes!).

### In-Line Sync:
As you change the "Defaults" fields at the top, the Task Builder automatically injects `DEFAULT_...` lines into your text area to keep your draft in sync.

---

## Slide 4: Task Overrides & Context Menu
**"Fine-Tuning Individual Tasks"**

> **[SCREENSHOT ANNOTATION: A right-click context menu open over a task in the Input Area, showing options like 'Set Assignee...', 'Set Component', and 'Set Transition'.]**

Need one specific task to go to a different person? You can "Override" defaults for any task block.

### How to Override:
1.  **Right-Click** anywhere inside a task block.
2.  Select an override (e.g., **Set Assignee**).
3.  The system will insert a special tag (e.g., `assignee: smith_j`) into that block.

### Supported Overrides:
`assignee:`, `component:`, `issue-type:`, `transition:`, `parent:`, `duedate:`, and `notify:`.

---

## Slide 5: The Task List & Validation
**"Visualizing Your Batch"**

> **[SCREENSHOT ANNOTATION: The 'Task List' on the right side, showing the parsed summaries. Highlight how double-clicking a task in the list scrolls the text area to that task.]**

The right-hand panel shows you exactly how the system has parsed your text.

*   **Checkbox List:** Only the tasks you select here will be created when you run the execution.
*   **Double-Click Navigation:** Double-click a task in the list to jump the text editor directly to that block.
*   **Status Indicators:** If you've applied a transition override, it will show up in red next to the summary (e.g., `[DONE]`).

---

## Slide 6: Execution & Real-Time Monitoring
**"Watch Your Workflow in Action"**

> **[SCREENSHOT ANNOTATION: The 'Results Table' at the bottom, showing summaries, status messages, and clickable Jira links.]**

Once you click **Execute Selected Tasks**, the engine takes over:

1.  **Bulk Creation:** It sends all tasks to Jira in a single high-speed request.
2.  **Parallel Processing:** It then performs transitions and notifications in parallel to maximize speed.
3.  **Clickable Links:** As tasks are created, their keys appear in the results table. Click any link to open the ticket in your browser.

### Pro-Tip:
*   Use **Mock Mode** (if enabled) to test your templates and overrides without actually creating tickets in Jira.
