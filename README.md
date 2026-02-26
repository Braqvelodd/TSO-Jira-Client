# USMC TSO Jira Client (Refactor API)

[![Java Version](https://img.shields.io/badge/Java-8%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html)
[![License](https://img.shields.io/badge/License-Proprietary-red.svg)](#license)

A specialized, GUI-driven Java application designed for the USMC TSO (Technology Services Orginization) to automate and streamline complex Jira Data Center workflows. This tool integrates secure CAC (Common Access Card) authentication with a comprehensive suite of developer productivity tools and local AI capabilities.

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
*   **Raw API Sandbox:** 
    *   Dropdown selector with **50+ predefined Data Center API templates** (Issues, Projects, Users, Worklogs, etc.).
    *   Dynamic discovery: Add new `api_template.` keys to the config to automatically expand the menu.
    *   Automatic JSON pretty-printing and smart execution buttons.
*   **Enhanced Bulk Actions:** Perform mass updates including Transitions, Assignee changes, Comments, Labels, Priority, and Issue Linking.
*   **Workflow Automation:** Automates the 5-step "Issue Processing" workflow including cloning, attachment migration, and SMARTS linking.
*   **Task Builder:** Rapidly generate sub-tasks from templates. Includes a dynamic context menu in the JQL Runner.
*   **Data Reconciliation:** Tools for comparing Jira sub-tasks against ISPW reports to ensure development synchronization.

---

## 🛠️ Technical Stack

*   **Language:** Java 8+
*   **GUI:** Java Swing (System Look & Feel)
*   **AI Engine:** llama.cpp (llama-cli) + GGUF Models
*   **JSON Handling:** `org.json`
*   **Authentication:** mTLS via Windows-MY (SunMSCAPI)
*   **Build System:** Windows Batch Script (`compile and build.bat`)

---

## ⚙️ Configuration

The application is fully configurable via `%USERPROFILE%\.JiraApiClient\JiraConfig.ini`.

*   **Dynamic Discovery:** Teams, Task Templates, and API Templates are automatically discovered from the config file.
*   **Feature Toggles:** Enable or disable optional tabs by toggling `tab.[Name].enabled = true`.
*   **Auto-Upgrade:** The app detects the `config_version` and automatically appends missing required keys during updates.
*   **Live Reloading:** Most settings refresh automatically in the GUI when the `.ini` file is saved.

---

## 📄 License

This software is **Proprietary** and intended for official use within the USMC TSO. All rights reserved. 
