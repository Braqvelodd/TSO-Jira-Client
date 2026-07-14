# USMC TSO Jira Client - Instruction Context

This project is a specialized Java Swing application for automating and streamlining Jira Data Center workflows, specifically designed for the USMC TSO environment. It features secure CAC (Common Access Card) authentication, a local LLM-powered comment summarizer, and a sophisticated "Workflow Orchestrator" for complex task automation.

## Project Overview

*   **Type:** Java 8+ Swing Application (Desktop)
*   **Technologies:** Java (Swing/AWT), `org.json`, `llama.cpp` (embedded binaries), `SunMSCAPI` (for CAC authentication).
*   **Target Platform:** Windows (due to `SunMSCAPI` and `.bat` build scripts).
*   **Key Services:**
    *   `JiraApiService`: Core API interaction (mTLS via CAC).
    *   `EmbeddedLlmService`: Orchestrates local AI analysis via `llama-cli.exe` and GGUF models.
    *   `WorkflowManager`: Executes multi-step "Workflow Recipes" (Create, Update, Link, Transition, Clone).
    *   `TokenEngine`: Recursive token replacement (`{{key}}`, `{{fields.summary}}`, etc.) for dynamic data mapping.

## Repository Structure

*   `src/`: Organized by package:
    *   `tso.usmc.jira.app`: Main GUI (`JiraApiClientGui`).
    *   `tso.usmc.jira.service`: Core business logic and API/AI integration.
    *   `tso.usmc.jira.ui`: Modular Swing panels for each feature.
    *   `tso.usmc.jira.util`: Configuration, JSON, and common utilities.
    *   `tso.usmc.jira.workflow`: Engine for the "Workflow Orchestrator".
*   `lib/`: External dependencies (`json.jar`), AI model parts (`model.gguf.partXXX`), and AI runtime binaries (`llama-cli.exe`, DLLs).
*   `resources/`: Default `.ini` configuration and template files.
*   `embedding/`: Build staging area for binaries and reassembled models.
*   `bin/`: Compilation output directory.

## Building and Running

### Build Process
The project uses a Windows Batch script for building. It does not use Maven or Gradle.

1.  **Command:** `compile and build.bat`
2.  **JDK Required:** JDK 21+ (uses `--release 8` for compatibility).
3.  **What it does:**
    *   Reassembles split GGUF model parts (if `EMBED_MODEL=YES`).
    *   Extracts `org.json` classes into `bin/`.
    *   Compiles all source files under `src/` by automatically generating `sources.txt`.
    *   Bundles everything into `JiraApiClient.jar` with `JiraApiClientGui` as the entry point.

### Running
```cmd
java -jar JiraApiClient.jar
```
*   **Note:** Requires a CAC reader and card for most features.
*   **Config Location:** Settings are stored in `%USERPROFILE%\.JiraApiClient\JiraConfig.ini`.

## Development Conventions

*   **Swing GUI:** Uses the system look and feel (`UIManager.getSystemLookAndFeelClassName()`). Most UI components are organized into `JPanel` subclasses within the `ui` package.
*   **Configuration:** All settings must be manageable via `JiraConfig.java`. It supports live reloading via a `WatchService`.
*   **Authentication:** mTLS is strictly handled via `SunMSCAPI` using the "Windows-MY" keystore.
*   **Error Handling:** Use `JOptionPane` for user-facing errors. Core logic should throw exceptions to be caught by the UI layer.
*   **Source Management:** `sources.txt` is automatically generated dynamically by the build script (`compile and build.bat`). Developers do not need to manually edit it when adding new Java files.

## Key Features for Gemini to Support

*   **Workflow Recipe Generation:** Gemini can help design JSON-based recipes for the `Workflow Orchestrator`.
*   **JQL Drafting:** Assistance with complex Jira Query Language strings.
*   **API Template Creation:** Creating new `api_template.` entries for `jiratemplate.ini`.
*   **Token Mapping:** Helping users map Jira field paths to tokens used in the `Workflow Orchestrator`.
