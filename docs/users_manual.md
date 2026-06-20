# USMC TSO Jira Client - Comprehensive User's Manual

This manual provides an in-depth operational and functional guide for the USMC TSO JIRA Client desktop application. It details every user interface panel, configuration files, authentication setups, offline local AI summarization, workflow orchestrator recipe scripting, task builder formatting syntax, and a keyboard shortcut quick reference.

---

## 1. System Overview & Architecture

The **USMC TSO JIRA Client** is a standalone JavaFX-based desktop application designed for secure, automated JIRA Data Center interaction within the USMC TSO enterprise environment. 

### Key Design Principles:
*   **CAC-First Authentication:** Communicates securely via mTLS leveraging your Common Access Card (CAC) and the native Windows Certificate Store.
*   **Zero-Network Local AI:** Summarizes JIRA comments offline using an embedded CPU-optimized LLM, avoiding data leakage outside the local workspace.
*   **Multi-Step Automation:** Empowers users to chain actions (create, link, update, transition) into reproducible, multi-step automation scripts (Recipes).

---

## 2. Getting Started & Directory Structure

Upon first execution, the application initializes a settings directory in the current user's profile path:
`C:\Users\<Username>\.JiraApiClient\`

### Core Configuration Files:

1.  **`JiraConfig.ini`:**
    *   Controls general client configurations: timeouts, active panels, parallel thread counts, and local LLM runtime parameters.
2.  **`constants.ini`:**
    *   Defines environment variables, custom JIRA field mappings (e.g. Epic Link, Source Key), default sub-task types, and team structure entries (used for the dynamic Team Selector).
3.  **`jiratemplate.ini`:**
    *   Stores reusable task blocks for the **Task Builder**, raw endpoint templates for the **Raw API Call** panel, and JSON-based **Orchestrator Recipes**.

### Live Settings Reloading
The client runs a background NIO daemon that listens for file modifications within `%USERPROFILE%\.JiraApiClient\`. You can open any config file in **Notepad**, save changes, and the client will instantly apply them (e.g., loading new teams, toggling visible tabs, or updating active UI themes) without requiring a restart.

---

## 3. CAC / PKI Authentication & Connection Guide

The client utilizes the standard Java Cryptography Architecture (JCA) with the `SunMSCAPI` provider to interface directly with the **Windows-MY** certificate store.

### Connecting to JIRA:
1.  Plug in your external CAC card reader and fully insert your USMC CAC.
2.  Launch the client.
3.  Select your active cryptographic email certificate from the **Select CAC Certificate** dropdown.
4.  If the dropdown is empty, click **Refresh Certs**.
5.  Verify that the **Jira Base URL** is pointing to your environment's gateway (default: `https://tso-jira.mcw.usmc.mil`).
6.  The client automatically intercepts HTTPS connections, negotiating a client-side certificate handshake (mTLS) with JIRA's server.

### Authentication Troubleshooting:

| Symptom / Error | Potential Cause | Troubleshooting Steps |
| :--- | :--- | :--- |
| **Empty Certificate Dropdown** | Card reader unrecognized or CAC not fully inserted. | Check USB connection; re-insert CAC; verify card is recognized in the Windows system tray. |
| **"Certificate Expired" or "Untrusted"** | Outdated or retired certificate chosen. | Check certificate details in the dropdown; choose the most recent active signature certificate. |
| **"Keystore Load Failed" or "Provider Exception"** | Smartcard driver (ActiveClient) is blocked or hanging. | Close the client, re-insert the card, restart the ActiveClient service or reboot Windows. |
| **"API Request Failed - Code 401/403"** | Certificate is valid but is not registered to your JIRA user account. | Verify that you have logged into the JIRA portal at least once in your web browser using that specific CAC certificate. |
| **"Network Error / Socket Timeout"** | Disconnected VPN or firewall blockage. | Ensure you are connected to the USMC MCW network or enterprise VPN before attempting connections. |

---

## 4. Offline Local AI (Comment Summarizer)

The **Comment Summarizer** extracts and processes JIRA ticket comments locally. It runs offline on CPU cores using a custom-wrapped `llama.cpp` runtime, maintaining strict data privacy guidelines.

### Setup & Reassembly:
Due to maximum repository file size constraints, the GGUF model is distributed in split chunks within the `lib/` directory:
*   Chunks: `lib/model.gguf.part001` through `lib/model.gguf.part021`.
*   During compilation, the batch script (`compile and build.bat`) automatically reassembles these parts into `embedding/models/model.gguf` using a binary copy (`copy /b`).
*   **Manual Setup:** If downloading model parts separately, place the chunks in a folder, run `copy /b model.gguf.part* model.gguf` in the Windows command prompt, and copy the reassembled file to `%USERPROFILE%\.JiraApiClient\models\model.gguf`.

### Runtime Execution & missing DLL errors:
The AI panel requires `llama-cli.exe` and its supporting system DLLs (extracted from the JAR to `%USERPROFILE%\.JiraApiClient\bin\`).

> [!WARNING]
> If running the summarizer fails with **Exit Code -1073741515**, it indicates that your Windows environment is missing required C++ runtimes.
> *   **Resolution:** Download and install the standard **Microsoft Visual C++ Redistributable 2015-2022 (x64)**.

### Resource Allocation:
*   **Timeout Limit:** Controlled by `llm_timeout_minutes` in `JiraConfig.ini` (default is 5 minutes).
*   **CPU Threads:** Calculated dynamically using `Math.max(1, Available_Cores - 2)` to avoid locking the host system's primary user interface threads.

---

## 5. Task Builder Syntax & Templating

The **Task Builder** provides a rapid "markup" interface to compile and execute batch sub-tasks. Rather than creating tickets one-by-one, you write a text block and hit execute.

### Syntax Specifications:

1.  **Block Separation:** Use a line containing six or more asterisks (`******`) to demarcate individual task blocks.
2.  **Comments:** Prefix any line with double hyphens (`-- `) to mark it as a comment. The parser ignores these lines completely.
3.  **Global Sync Flags:** These instruct the parser to select specific values in the UI controls at the top-left of the panel:
    *   `PARENT_TICKET: <JIRA-KEY>` (sets the global parent key)
    *   `DEFAULT_TYPE: <IssueType>` (sets default issue type, e.g. Sub-task)
    *   `DEFAULT_ASSIGNEE: <UserID>` (sets default assignee username)
    *   `DEFAULT_COMPONENT: <Component>` (sets default component)
    *   `DEFAULT_TRANSITION: <Status>` (sets default post-creation transition)
4.  **Task Overrides:** Placing these tags in a task block overrides the defaults for *that task block only*:
    *   `parent: <JIRA-KEY>`
    *   `issue-type: <Type>`
    *   `assignee: <UserID>` (use `noassignee:` to force unassigned)
    *   `component: <Component>` (use `nocomponent:` to clear component)
    *   `transition: <Status>` (use `notransition:` to suppress state advancement)
    *   `duedate: <YYYY-MM-DD>`
    *   `notify: <UserID>` (comma-separated list of usernames to send alerts)

### Parsing Rules:
*   The first non-comment, non-flag line of a task block becomes the **Summary**.
*   All subsequent lines in the block are parsed as the **Description**.

### Syntax Example:
```text
PARENT_TICKET: TFS-50402
DEFAULT_TYPE: Sub-task
DEFAULT_COMPONENT: Team Renegades

-- This is a comment block describing the execution
Setup PCU Testing Environment
This task involves preparing database migrations and verifying container mounts.
assignee: CROSSETT.OLIVIA
duedate: 2026-07-15

******

Verify Integration Pipelines
Run regression tests against the MOD sandbox environment.
assignee: SMITH.JOHN.A
transition: TO: In Progress
```

### Visual Validation:
*   As you type, the right-hand **Task List** updates live. Red labels (`[DONE]`, `[IN PROGRESS]`) show planned transition overrides.
*   **Navigation:** Double-clicking any task in the right-hand list scrolls the text editor straight to that task's block.
*   **Context Menu:** Right-click anywhere in the text block to automatically insert override strings.

---

## 6. Workflow Orchestrator Recipes

The **Workflow Orchestrator** is the core automation center of the client. Power users can define execution sequences (Recipes) in JIRA-compliant JSON, allowing operators to run structured workflows on JQL query results.

### Step Mapping Modes:
When a field is updated or created in a step, it uses one of three modes:
*   `STATIC`: Sends a hardcoded value (e.g. `High`). Array fields like components or multiple select lists can use comma-separated values (e.g. `App, DB`), which are converted automatically into the required JIRA JSON formats.  References contextual tokens to dynamically inject data.
*   `PROMPT`: Requests user input once in the **Runner** tab before executing the batch.

### Dynamic Prompt Tags:
To build interactive prompt forms, add these tags to the **Prompt Question** or the step's options:
*   `[allowed:fieldId]`
    *   Automatically queries the cache for valid values of `fieldId`. If it's a multi-select field, the runner displays a checkable `ListView`. If single, it displays a searchable `AutocompleteTextField`.
*   `[choice:optionA,optionB,{{fields.customfield_123}}]`
    *   Displays a dropdown list populated with the specified options (tokens within choices are resolved at runtime).
*   `[config:key]`
    *   Retrieves variables from `JiraConfig` settings.
    *   `[config:teams]` displays a list of teams mapped in `constants.ini`, outputting the **Lead ID**.
    *   `[config:teams:component]` outputs the team's associated component name.
    *   `[config:teams:id]` outputs the internal JIRA team ID value.
    *   `[config:fy_summary]` outputs the fiscal year parent issue key.

### Virtual Fields & Team Mappings
Defining a prompt field with the exact ID `teams_selection` enables advanced team tokens:
1.  It renders a searchable dropdown list of your organization's teams.
2.  It is marked as "Virtual", meaning it is never sent to JIRA (preventing API scheme mismatches).
3.  It populates these dynamic team tokens for all downstream fields:
    *   `{{team.name}}` -> Display Name (e.g. Team Fidelis)
    *   `{{team.lead}}` -> Team Lead Username (e.g. JOHNSON.TONY.E)
    *   `{{team.component}}` -> JIRA Component (e.g. Team Fidelis)
    *   `{{team.id}}` -> Internal ID value (e.g. 149)

### Token Engine Reference:
Tokens are wrapped in double-braces `{{...}}`.

*   **Global Variables:**
    *   `{{key}}` or `{{issue.key}}`: Key of the current ticket in the loop.
    *   `{{fields.summary}}` (or just `{{summary}}`): Current ticket summary.
    *   `{{last.key}}` (or `{{last_key}}`): Key of the ticket created in a prior step.
    *   `{{last.id}}`: Internal database ID of the last created ticket.
    *   `{{last.fields.summary}}`: Summary of the last created ticket.
    *   `{{today}}`: Current date in `YYYY-MM-DD` format.
    *   `{{now}}`: ISO timestamp `yyyy-MM-dd'T'HH:mm:ss.SSSZ`.
*   **Logical Functions:**
    *   `{{COALESCE(arg1, arg2, 'Fallback')}}`: Evaluates arguments left-to-right, returning the first non-empty, non-null value (literals must be surrounded in single or double quotes).

---

## 7. JSON Recipe Reference Specification

Recipes are stored in the user directory or embedded within `jiratemplate.ini` as a JSON block.

### Step Types & Parameters:

#### 1. TRANSITION
Moves the ticket status and optionally adds transition details.
```json
{
  "type": "TRANSITION",
  "label": "Progress Status",
  "targetStatus": "TO: In Progress",
  "targetIssueToken": "{{issue.key}}"
}
```

#### 2. UPDATE
Modifies custom or standard fields on a target ticket.
```json
{
  "type": "UPDATE",
  "label": "Mark Done",
  "targetIssueToken": "{{issue.key}}",
  "fields": [
    {
      "fieldId": "customfield_10519",
      "mode": "STATIC",
      "value": "null"
    }
  ]
}
```

#### 3. CREATE
Creates a new JIRA issue, optionally setting parent linkage.
```json
{
  "type": "CREATE",
  "label": "Create Sub-task",
  "projectKey": "TFS",
  "issueType": "Sub-task",
  "parentIssueKey": "{{issue.key}}",
  "fields": [
    {
      "fieldId": "summary",
      "mode": "VARIABLE",
      "value": "Sub: {{fields.summary}}"
    }
  ]
}
```

#### 4. LINK
Creates JIRA relationship links or attaches external URL bookmarks.
```json
{
  "type": "LINK",
  "label": "Relate Tickets",
  "linkActions": [
    {
      "remote": false,
      "linkType": "Relates",
      "inwardIssueToken": "{{issue.key}}",
      "outwardIssueToken": "{{last_key}}"
    },
    {
      "remote": true,
      "inwardIssueToken": "{{issue.key}}",
      "url": "https://wiki.tso.usmc.mil/docs/{{issue.key}}",
      "title": "TSO Documentation Hub",
      "summary": "Project wiki reference portal",
      "relationship": "documentation"
    }
  ]
}
```

#### 5. WORKLOG
Logs time spent on the issue.
```json
{
  "type": "WORKLOG",
  "label": "Track Execution Time",
  "targetIssueToken": "{{issue.key}}",
  "timeSpent": "1h 30m",
  "comment": "Completed integration validation tests.",
  "started": "{{today}}T08:00:00.000+0000",
  "promptAtRuntime": false,
  "promptPerIssue": false
}
```

#### 6. ASSET
Clones links, attachments, or sub-tasks from a source issue to a target.
```json
{
  "type": "ASSET",
  "label": "Clone Deliverables",
  "sourceIssueToken": "{{issue.key}}",
  "targetIssueToken": "{{last_key}}",
  "copyAttachments": true,
  "copyLinks": true,
  "copySubTasks": false
}
```

#### 7. COMMENT
Adds plain text comments to the issue history feed.
```json
{
  "type": "COMMENT",
  "label": "Post Complete Status",
  "targetIssueToken": "{{issue.key}}",
  "commentBody": "Workflow automated script finished executing at {{now}}.",
  "promptAtRuntime": false
}
```

### Conditional Branching (Logical Conditions):
Every step subclass inherits three conditional matching fields. If configured, the engine evaluates the condition check. If it evaluates to `false`, the step is skipped.
*   `conditionToken`: The token to evaluate (e.g. `{{fields.status.name}}`).
*   `conditionOperator`: The matching condition operator. Supported operators:
    *   `ALWAYS` (default)
    *   `EQUALS`
    *   `NOT_EQUALS`
    *   `CONTAINS`
    *   `NOT_CONTAINS`
    *   `EMPTY`
    *   `NOT_EMPTY`
*   `conditionValue`: The value compared against (e.g. `Incoming Requirements`).

```json
{
  "type": "UPDATE",
  "label": "Patch Urgent Status",
  "conditionToken": "{{fields.priority.name}}",
  "conditionOperator": "EQUALS",
  "conditionValue": "Critical",
  "fields": [
    {
      "fieldId": "comment",
      "mode": "STATIC",
      "value": "Marking this critical issue for immediate reviewer escalation."
    }
  ]
}
```

---

## 8. Complete Recipe Example

This JSON recipe moves incoming requirements, creates a linked PTR task in the TFS project, prompts for assignment, and clones attachments.

```json
{
  "recipeName": "Requirement_Ingestion",
  "jqlQuery": "project = TSO AND status = 'Incoming Requirements'",
  "steps": [
    {
      "type": "TRANSITION",
      "label": "Move status to active",
      "targetStatus": "TO: In Progress",
      "targetIssueToken": "{{issue.key}}"
    },
    {
      "type": "CREATE",
      "label": "Create Linked PTR",
      "projectKey": "TFS",
      "issueType": "PTR",
      "fields": [
        {
          "fieldId": "summary",
          "mode": "VARIABLE",
          "value": "[PTR] {{key}} - {{fields.summary}}"
        },
        {
          "fieldId": "description",
          "mode": "VARIABLE",
          "value": "Automated ingestion. Original ticket details:\n\n{{fields.description}}"
        },
        {
          "fieldId": "customfield_10400",
          "mode": "VARIABLE",
          "value": "{{key}}"
        }
      ]
    },
    {
      "type": "UPDATE",
      "label": "Assign New PTR",
      "targetIssueToken": "{{last_key}}",
      "fields": [
        {
          "fieldId": "teams_selection",
          "mode": "PROMPT",
          "promptLabel": "Select Owning Team [config:teams]"
        },
        {
          "fieldId": "assignee",
          "mode": "VARIABLE",
          "value": "{{team.lead}}"
        },
        {
          "fieldId": "components",
          "mode": "VARIABLE",
          "value": "{{team.component}}"
        }
      ]
    },
    {
      "type": "LINK",
      "label": "Link PTR to Requirement",
      "linkActions": [
        {
          "remote": false,
          "linkType": "Relates",
          "inwardIssueToken": "{{key}}",
          "outwardIssueToken": "{{last_key}}"
        }
      ]
    },
    {
      "type": "ASSET",
      "label": "Clone Original Attachments",
      "sourceIssueToken": "{{key}}",
      "targetIssueToken": "{{last_key}}",
      "copyAttachments": true,
      "copyLinks": true,
      "copySubTasks": false
    }
  ]
}
```

---

## 9. Keyboard Shortcuts & Quick Reference

For power users, the following keyboard mappings are active:

| Area / Panel | Shortcut | Action | Description |
| :--- | :--- | :--- | :--- |
| **Global UI** | `Alt + E` (on settings buttons) | Edit Configuration / Templates | Instantly opens configuration `.ini` files in Notepad. |
| **Task Builder Editor** | `Ctrl + Alt + Down` | Duplicate Lines Down | Copies selected rows and appends them immediately below. |
| **Task Builder Editor** | `Ctrl + Alt + Up` | Duplicate Lines Up | Copies selected rows and prepends them immediately above. |
| **Task Builder Editor** | `Alt + Down` | Move Lines Down | Shifts selected lines downward one row. |
| **Task Builder Editor** | `Alt + Up` | Move Lines Up | Shifts selected lines upward one row. |
| **Task Builder Editor** | `Ctrl + /` | Toggle Comment | Prepends or strips `-- ` on the selected lines. |
| **Task Builder Editor** | `Ctrl + D` | Delete Lines | Deletes selected lines from the text block. |
| **Task Builder Editor** | `Ctrl + B` | Bold Formatting | Surrounds selected text with JIRA bold syntax (`*`). |
| **Task Builder Editor** | `Ctrl + I` | Italics Formatting | Surrounds selected text with JIRA italic syntax (`{_}`). |
| **JQL Runner Table** | `Ctrl + C` | Copy Rows | Copies the selected keys/summary values into clipboard. |
| **Reconciliation Tables** | `Ctrl + C` | Copy Selection | Copies rows matching the current filter results. |
| **Reconciliation Tables** | `Ctrl + A` | Select All | Highlights all items in the reconciliation table. |
| **Auto-Complete Boxes** | `Down Arrow` / `Up Arrow` | Navigate Suggestions | Moves selection down/up inside the suggestion popup. |
| **Auto-Complete Boxes** | `Enter` / `Tab` | Accept Suggestion | Injects selected value into the text component. |
| **Auto-Complete Boxes** | `Escape` | Close Suggestions | Dismisses the autocompletion dropdown menu. |
