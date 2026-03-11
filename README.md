# USMC TSO Jira Client

[![Java Version](https://img.shields.io/badge/Java-8%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html)
[![License](https://img.shields.io/badge/License-Internal_Use_Only-orange.svg)](#license)

A specialized, GUI-driven Java application designed for the USMC TSO (Technology Services Organization) to automate and streamline complex Jira Data Center workflows. This tool integrates secure CAC (Common Access Card) authentication with a comprehensive suite of developer productivity tools and local AI capabilities.

---

## 📂 Repository Contents

*   **`src/`**: Full Java source code organized by package (app, service, ui, util).
*   **`lib/`**: External dependencies, including `json-20231013.jar` and the split parts of the Llama-3 AI model.
*   **`resources/`**: Application resources and default configuration templates.
*   **`embedding/`**: Staging area for AI binaries and models during the build process.
*   **`compile and build.bat`**: Universal build script for both Home and Work environments.
*   **`sources.txt`**: Unified manifest of all Java source files required for compilation.

---

## 🚀 Getting Started

### 1. Prerequisites
*   **Java Development Kit (JDK):** JDK 21+ is recommended for building (supports `--release 8`).
*   **Windows Environment:** The build script and CAC authentication (`SunMSCAPI`) are Windows-specific.
*   **CAC Hardware:** A functional CAC reader and valid USMC CAC card.

### 2. Prepare the AI Model
Due to file size limits, the `model.gguf` model is stored in the `lib/` directory as multiple 95MB split parts (`.part001`, `.part002`, etc.). 
*   **Automatic:** The `compile and build.bat` script automatically reassembles these parts into `embedding/models/model.gguf` during the build process.

### 3. Build the Application
Open `compile and build.bat` and ensure your environment is set correctly (`set ENV=HOME` or `set ENV=WORK`) and your Java JDK path. Then run:
```batch
"compile and build.bat"
```
This script reassembles the model, extracts libraries, compiles the source via `sources.txt`, and generates the `JiraApiClient.jar`.

### 4. Run the Application
```batch
java -jar JiraApiClient.jar
```
Or by double click

*On first run, the app extracts the AI runtime and creates a default config at `%USERPROFILE%\.JiraApiClient\JiraConfig.ini`.*

---

## 🚀 Key Features

### 🔐 Secure CAC Authentication
*   **Integrated Windows-MY Support:** Directly leverages the Windows Certificate Store (`SunMSCAPI`) to access CAC certificates for Mutual TLS (mTLS) authentication.
*   **Purpose-Based Filtering:** Automatically filters the certificate list to only show valid "Client Authentication" certificates.

### 🤖 Offline AI Comment Summarizer
*   **Local LLM Runtime:** Features an embedded **llama.cpp** runtime (`llama-cli.exe`) to process data entirely offline.
*   **Actionable Summaries:** Fetches all comments for a specific Jira issue and generates a concise summary of key actions and decisions using local GGUF models.
*   **Privacy-First:** Sensitive USMC data never leaves the controlled environment; all AI analysis is performed on your local machine.

### 🛠️ Specialized Toolset
*   **JQL Runner & Advanced Search:**
    *   **Real-time Autocomplete:** Intelligent popup suggestions for fields, functions, and reserved words as you type.
    *   **Value Suggestions:** Context-aware suggestions for field values (e.g., suggesting user names after `assignee =` or statuses after `status IN`).
    *   **Saved Filters:** Save, load, and manage named JQL filters directly from the UI for quick access to frequent queries.
*   **Raw API Sandbox:** 
    *   Dropdown selector with **50+ predefined Data Center API templates** (Issues, Projects, Users, Worklogs, Link Types, etc.).
    *   Dynamic discovery: Add new `api_template.` keys to the config to automatically expand the menu.
    *   Automatic JSON pretty-printing and smart execution buttons.
*   **Enhanced Bulk Actions:** Perform mass updates including Transitions, Assignee changes, Comments, Labels, Priority, and Issue Linking.
*   **Workflow Automation:** Automates the 5-step "Issue Processing" workflow including cloning, attachment migration, and SMARTS linking.
*   **Workflow Orchestrator (v3.0):** Build, save, and execute complex "Workflow Recipes" with enhanced multi-action support.
    *   **Designer Tab:** A visual builder to stack automated steps.
        *   **Dynamic Metadata:** Fetch live Jira field definitions to populate step editors with real field names.
        *   **Mapping Modes:** Every field supports **Static** values, **Variable** tokens, or **Runtime Prompts**.
    *   **Enhanced LINK Step:**
        *   **Multi-Action Support:** A single Link step can now perform multiple link operations simultaneously.
        *   **Unified Link Types:** Seamlessly mix standard Jira issue links and Remote URL links in one step.
        *   **Editable Link Types:** Use a searchable dropdown or type in custom link types directly.
        *   **Flexible Inward Tokens:** Specify the source issue (Inward key) per action, allowing you to link multiple different issues in a single step.
    *   **Smart Token Engine:** Use recursive tokens like `{{fields.summary}}`, `{{fields.reporter.name}}`, or capture keys via `{{last_key}}`.
    *   **Persistent Recipes:** Recipes are stored as JSON in `%USERPROFILE%\.JiraApiClient\workflows\` for easy sharing and re-use.
*   **Task Builder:** Rapidly generate sub-tasks from templates.
    *   **Mention-Style Autocomplete:** Trigger real-time Jira user suggestions by typing an `@` symbol on `Assignee:`, `DEFAULT_ASSIGNEE:`, or `notify:` lines. The `@` is automatically stripped upon selection.
    *   **Integrated Defaults:** Full user autocomplete support in the "Defaults" configuration area.
    *   **Dynamic Parsing:** Real-time parsing of task blocks separated by `***`.
    *   **Interactive List:** Select specific tasks for execution; double-click a task to jump to its definition in the editor.
    *   **Advanced Syntax:** Supports `transition:`, `notify:`, `duedate:`, and field overrides.
    *   **Editor Shortcuts:** `Ctrl + Alt + ↑/↓` (Duplicate), `Alt + ↑/↓` (Move), `Ctrl + /` (Comment), `Ctrl + D` (Delete).
*   **Data Reconciliation:** Tools for comparing Jira sub-tasks against ISPW reports to ensure development synchronization.

---

## 🛠️ Technical Stack

*   **Language:** Java 8+ (JDK 21+ for building)
*   **GUI:** Java Swing (System Look & Feel)
*   **AI Engine:** llama.cpp (llama-cli) + GGUF Models
*   **Jira API:** Data Center REST API (v2) with JQL Autocomplete Metadata support.
*   **Authentication:** mTLS via Windows-MY (SunMSCAPI)
*   **Build System:** Windows Batch Script (`compile and build.bat`)

---

## ⚙️ Configuration

The application is fully configurable via files located in `%USERPROFILE%\.JiraApiClient\`.

*   **`JiraConfig.ini`**: Main configuration for URLs, Teams, and Feature Toggles.
*   **`jiratemplate.ini`**: Dedicated storage for Task Templates, Raw API Templates, and "baked-in" Workflow Recipes.
*   **`workflows/`**: A directory containing custom Workflow Recipes saved via the Orchestrator.
*   **Live Reloading:** Most settings refresh automatically in the GUI when the `.ini` file is saved.

---

## 📄 License

This software is for **Internal Use Only** within the USMC TSO. All rights reserved. 
