# Companion App Integration Plan: Jira Client & Topaz Mainframe Extractor

This document tracks the phased implementation of the **Companion Architecture** connecting **TSO-Jira-Client** to **Topaz-file-read** for automated mainframe ISPW report retrieval and Jira reconciliation.

---

## Architecture Summary
- **TSO-Jira-Client**: Java 8+ Swing/JavaFX client for Jira workflows. Delegates mainframe actions to Topaz via headless CLI subprocess (`ProcessBuilder`).
- **Topaz-file-read**: Java 21 client utilizing Compuware/BMC Topaz API (`zos-connection.jar`) to interact with z/OS via HCI.
- **Authentication**:
  - Primary: CAC X.509 Certificate (`Windows-MY` store -> Base64 encoded cert -> Topaz HCI SAF mapping).
  - Fallback: RACF User ID & Password (`--user <id> --pass <password>`).

---

## Phase Checklist & Status

### Phase 1: Update & Harden `Topaz-file-read`
- [x] **1.1 Inspect Existing CLI & Auth Modes**
  - Verify headless batch mode non-blocking behavior (Scanner prompt guarded against headless subprocesses).
  - Verify CAC X.509 (`--cert`) and User/Pass (`--user`, `--pass`) parameter handling.
- [x] **1.2 Add JCL Job Submission Capability (`--submit`)**
  - Add `OperationMode.SUBMIT_JOB` in `AppConfig.java`.
  - Add CLI arguments: `--submit <jcl_file_or_member>`, `--wait`, `--dd <dd_name>`, `--wait-timeout <sec>`.
  - Implement `submitJobAndExtractOutput()` in `TopazConnectionManager.java` and `TopazClient.java` using `IJESCommandProvider.submit()`.
  - Add polling loop waiting for job execution status `ON_OUTPUT_QUEUE` / completion.
  - Extract target spool DD (e.g. `SORTOUT`) to output file.
- [x] **1.3 Build and Verify `Topaz-file-read`**
  - Run `compileandbuild.bat` in `Topaz-file-read` (Built successfully targeting Java 21).
  - Verified `run.bat --help` command execution.

### Phase 2: Companion Integration Framework in `TSO-Jira-Client`
- [x] **2.1 Configuration Extensions**
  - Add `companion.topaz.*` properties in `resources/companion.properties`.
  - Add companion getter/setter methods in `JiraConfig.java`.
- [x] **2.2 Build `CompanionRunner.java` Service**
  - Direct execution of `topaz-pds-reader.jar` via detected Java 21+ runtime (`java -jar <jar> --batch`).
  - Automatic fallback and backwards compatibility for legacy paths pointing to `run.bat` or directories.
  - Implement async subprocess execution with timeout handling.
  - Support mode dispatch: `DATASET` (sequential), `JOB_SPOOL` (spool fetch), and `SUBMIT` (JCL submission).
  - Handle CAC cert alias alignment and user/pass fallback.
  - Capture stdout/stderr with clean error diagnosis.

### Phase 3: Reconciliation Panel UI Integration
- [x] **3.1 UI Actions in `ReconciliationPanel.java`**
  - Add `[Fetch from Mainframe]` action in Card 2 header (next to `[Configure Columns...]`).
  - Add companion settings dialog/popup to quickly review/configure dataset, job, or JCL parameters.
- [x] **3.2 Automated Flow**
  - Asynchronously fetch the ISPW report and populate `ispwReportArea`.
  - Seamlessly link with the `Compare Jira & ISPW` reconciliation pipeline.

### Phase 4: Testing & Verification
- [ ] **4.1 Test CAC X.509 Authentication**
- [ ] **4.2 Test User / Password Fallback**
- [ ] **4.3 Test Error Handling (Timeouts, Abends, Auth Failures)**
- [x] **4.4 Final Build & Verification**
  - Verified compilation of both `Topaz-file-read` and `TSO-Jira-Client`.

---

## Current Progress Notes
- **State**: Phases 1, 2, and 3 are COMPLETED.
- **Active Task**: Ready to conduct Phase 4 runtime testing with mainframe datasets/jobs.

---

## Future Architectural Roadmap: Standalone Topaz Microservice & Multi-Threaded Fetcher

### Core Concept: Service / UI Decoupling
* **Decoupled Architecture**: Break out the raw BMC Topaz Java API calls (`zos-connection.jar`) into an independent headless service / microservice module. 
* **Front-Ends as Pure UIs**: 
  * `Topaz-file-read` becomes a dedicated UI consumer that calls this underlying service.
  * Other companion apps (e.g. `TSO-Jira-Client`, or a future multi-threaded batch mainframe fetcher) can call the exact same service.

### Invisible Managed Sidecar Pattern
* **Single-Click User Experience**: Desktop applications launch the service invisibly on `127.0.0.1` (loopback) on-demand (lazy-loaded). Users never see a second window or command prompt.
* **Pure In-Memory API**: Communication is strictly via local REST/JSON HTTP calls with **zero temporary files or disk footprint**.
* **High-Throughput Multi-Threading**: The service can maintain persistent, authenticated HCI sessions / connection pools, enabling companion applications to execute concurrent mainframe fetches across multiple worker threads with near-zero latency.
* **Auto-Teardown**: Controlled via application shutdown hooks (`Runtime.getRuntime().addShutdownHook`) to ensure zero orphan background processes upon closing the parent application.

