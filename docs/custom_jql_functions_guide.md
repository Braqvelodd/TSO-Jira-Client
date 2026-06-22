# Guide: Creating and Using Custom JQL Functions

This guide explains how the dynamic, configuration-driven JQL engine works and how you can add your own custom relationship functions (e.g., `childrenOf`, `linkedIssuesOf`, or custom field lookups) without writing any Java code.

---

## 1. How the Engine Works (Multi-Pass Execution)

Standard Jira JQL only lets you search for issues using direct field matches. If you want to search based on **relationships** (like "find parents of issues that are in status Done"), standard JQL cannot do it in one query.

The Multi-Pass Execution Engine automates this workflow under the hood by coordinating two separate queries:

```mermaid
graph TD
    A[User enters query: project=PROJ AND parentsOf('status = Done')] --> B{Custom Token Detected?}
    B -->|Yes| C[Step 1: Matched strategy extracts inner JQL: 'status = Done']
    C --> D["Step 2: First-Pass API Query (jql='status = Done', fields='parent')"]
    D --> E[Step 3: Extract parent keys from JSON response]
    E --> F[Step 4: Build standard JQL: 'key in (KEY-1, KEY-2)']
    F --> G[Step 5: Replace token in original query]
    G --> H["Step 6: Second-Pass API Query (jql='project = PROJ AND key in (KEY-1, KEY-2)')"]
    H --> I[Return final search results to UI]
    B -->|No| J[Step 1: Execute single-pass legacy standard API call]
    J --> I
```

---

## 2. Anatomy of a Custom Function Definition

Every custom function is defined by a JSON object saved in `%USERPROFILE%\.JiraApiClient\custom_functions.json`. 

Here is an example definition:

```json
{
  "name": "parentsOf",
  "firstPassFields": ["parent"],
  "jsonPaths": ["issues[*].fields.parent.key"],
  "outputTemplate": "key in ({{KEYS}})"
}
```

### Properties Explained:

| Property | Type | Description | Example |
| :--- | :--- | :--- | :--- |
| `name` | String | The exact token name users will write in JQL. Case-insensitive. | `"parentsOf"` |
| `firstPassFields` | Array of Strings | The specific Jira API fields needed in the first query to resolve the relationship. | `["parent"]` |
| `jsonPaths` | Array of Strings | Dot-notation paths telling the engine how to find keys in the first-pass JSON payload. | `["issues[*].fields.parent.key"]` |
| `outputTemplate` | String | The format of the resolved standard JQL fragment. `{{KEYS}}` is replaced by the found keys. | `"key in ({{KEYS}})"` |

---

## 3. How to Write JSON Paths

The first-pass query returns a standard Jira search JSON payload. To find the issue keys you need, write a dot-separated traversal path:

*   **Objects:** Use a dot (`.`) to descend into child objects (e.g. `fields.parent.key`).
*   **Arrays:** Use `[*]` to traverse arrays and collect values from every element inside the array (e.g. `issues[*]`).

### Traversal Example:
Given the first-pass response:
```json
{
  "issues": [
    {
      "key": "TSO-999",
      "fields": {
        "parent": { "key": "PARENT-111" },
        "subtasks": [
          { "key": "TSO-1001" },
          { "key": "TSO-1002" }
        ]
      }
    }
  ]
}
```

*   Path `issues[*].fields.parent.key` returns: `["PARENT-111"]`
*   Path `issues[*].fields.subtasks[*].key` returns: `["TSO-1001", "TSO-1002"]`

---

## 4. Query Execution Modes & Usage Patterns

The Multi-Pass JQL engine provides extensive flexibility in how custom functions can be combined, overridden, negated, and even nested.

### Mode A: Shorthand Function Calls
If you don't prefix the custom function with a field name or operator, the engine automatically assumes the default output template (which defaults to `key in (...)`):
*   `parentsOf("status = Done")`
    resolves to: `key in (KEY-100, KEY-101)`

### Mode B: Embedded JQL Clauses
You can combine custom tokens with standard standard JQL filters using logical operators (`AND`, `OR`):
*   `project = PROJ AND assignee = admin AND parentsOf("status = Done")`
    resolves to: `project = PROJ AND assignee = admin AND key in (KEY-100, KEY-101)`

### Mode C: Field / Operator Overrides (Dynamic Rewriting)
If the engine detects a field and an operator preceding the custom function, it dynamically overrides the strategy's default template. It replaces the function call with the resolved key array while preserving your specified field and operator.
This supports:
1.  **Negating Operators (`not in`, `!=`):**
    *   `key not in parentsOf("status = Done")`
        resolves to: `key not in (KEY-100, KEY-101)`
    *   `key != parentsOf("key = TSO-123")`
        resolves to: `key != (PARENT-50)`
2.  **Custom Field References:**
    *   `issue in parentsOf("status = Done")`
        resolves to: `issue in (KEY-100, KEY-101)`
    *   `parent in childrenOf("key = TSO-50")`
        resolves to: `parent in (SUB-1, SUB-2)`

### Mode D: Multiple Custom Functions
You can use multiple different custom functions in a single query. The engine scans and resolves them sequentially (left-to-right) in multiple passes:
*   `parentsOf("status = Open") AND childrenOf("assignee = admin")`
    resolves to: `key in (PARENT-1) AND key in (CHILD-1, CHILD-2)`

### Mode E: Nested / Recursive Functions
Because the orchestrator evaluates queries recursively, you can nest custom functions inside each other. The inner query resolves first, and its output is fed into the outer query:
*   `parentsOf("key in childrenOf('status = Done')")`
    1.  First, the engine resolves `childrenOf('status = Done')` to get a list of subtask keys (e.g., `SUB-1`, `SUB-2`).
    2.  The query becomes `parentsOf("key in (SUB-1, SUB-2)")`.
    3.  Next, the engine queries those subtask issues to extract their parents, and resolves the outer query.
    4.  Final Result: `key in (PARENT-1)`

---

## 5. How to Add Custom Functions at Runtime

### Method A: Using the Graphical Interface (Recommended)

1. Open the application and go to the **JQL Runner** panel.
2. Click the **Actions** dropdown menu.
3. Select **Manage Custom JQL Functions...**
4. Click **New** to clear the form.
5. Fill in the parameters (see presets below).
6. Click **Save**.

The function is immediately active and registered in the engine without restarting the app!

---

### Method B: Editing the JSON File Directly

Open `%USERPROFILE%\.JiraApiClient\custom_functions.json` in a text editor, add your function configuration to the array, and save it. The engine re-reads this file every time a query is executed.

---

## 6. Useful Function Presets

Here are configurations you can add to implement common relationship queries:

### Presets:

#### 1. `childrenOf` (Find subtasks of issues matching a query)
*   **Name:** `childrenOf`
*   **Fields:** `subtasks`
*   **JSON Paths:** `issues[*].fields.subtasks[*].key`
*   **Template:** `key in ({{KEYS}})`
*   **Example Usage:** `childrenOf("status = 'In Progress'")`

#### 2. `linkedIssuesOf` (Find linked issues)
*   **Name:** `linkedIssuesOf`
*   **Fields:** `issuelinks`
*   **JSON Paths:** 
    *   `issues[*].fields.issuelinks[*].outwardIssue.key`
    *   `issues[*].fields.issuelinks[*].inwardIssue.key`
*   **Template:** `key in ({{KEYS}})`
*   **Example Usage:** `linkedIssuesOf("summary ~ 'Security'")`

#### 3. `epicsOf` (Find Epics of issues matching a query)
*   **Name:** `epicsOf`
*   **Fields:** `customfield_10008` (Replace with your Jira Epic Link custom field ID)
*   **JSON Paths:** `issues[*].fields.customfield_10008`
*   **Template:** `key in ({{KEYS}})`
*   **Example Usage:** `epicsOf("status = Done")`
