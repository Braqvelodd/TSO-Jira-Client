package tso.usmc.jira.service;

import tso.usmc.jira.util.ExecutionService;
import tso.usmc.jira.util.JiraConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Service to execute companion external tools (e.g. Topaz-file-read)
 * to retrieve mainframe ISPW reports headlessly.
 */
public class CompanionRunner {

    public enum FetchMode {
        DATASET,
        JOB_SPOOL,
        SUBMIT
    }

    public static class CompanionResult {
        public final boolean success;
        public final int exitCode;
        public final String outputContent;
        public final String standardOutput;
        public final String errorMessage;

        public CompanionResult(boolean success, int exitCode, String outputContent, String stdout, String error) {
            this.success = success;
            this.exitCode = exitCode;
            this.outputContent = outputContent;
            this.standardOutput = stdout;
            this.errorMessage = error;
        }
    }

    /**
     * Executes the Topaz companion application asynchronously and delivers result to onComplete callback.
     */
    public static void executeTopazFetchAsync(JiraConfig config, String certAlias, Consumer<String> statusLogger, Consumer<CompanionResult> onComplete) {
        ExecutionService.submit(() -> {
            try {
                CompanionResult result = executeTopazFetch(config, certAlias, statusLogger);
                if (onComplete != null) {
                    onComplete.accept(result);
                }
            } catch (Exception ex) {
                if (onComplete != null) {
                    onComplete.accept(new CompanionResult(false, -1, null, "", ex.getMessage()));
                }
            }
        });
    }

    /**
     * Executes the Topaz companion application synchronously.
     */
    public static CompanionResult executeTopazFetch(JiraConfig config, String certAlias, Consumer<String> statusLogger) throws Exception {
        String scriptPath = config.getCompanionTopazPath();
        if (scriptPath == null || scriptPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Topaz companion script path is not configured. Please set companion.topaz.path in companion.properties.");
        }

        File scriptFile = new File(scriptPath.trim());
        if (!scriptFile.exists()) {
            throw new IllegalArgumentException("Topaz companion script not found: " + scriptFile.getAbsolutePath());
        }

        File tempOutputFile = File.createTempFile("topaz_ispw_report_", ".txt");
        tempOutputFile.deleteOnExit();

        List<String> command = new ArrayList<>();
        command.add("cmd.exe");
        command.add("/c");
        command.add(scriptFile.getAbsolutePath());
        command.add("--batch");

        // 1. Configure Authentication
        String authMode = config.getCompanionTopazAuthMode();
        if ("USER_PASSWORD".equalsIgnoreCase(authMode) || "PASSWORD".equalsIgnoreCase(authMode)) {
            String user = config.getCompanionTopazUser();
            String pass = config.getCompanionTopazPassword();
            if (user != null && !user.trim().isEmpty()) {
                command.add("--user");
                command.add(user.trim());
            }
            if (pass != null && !pass.isEmpty()) {
                command.add("--pass");
                command.add(pass);
            }
        } else if ("MTLS".equalsIgnoreCase(authMode)) {
            command.add("--mtls");
            if (certAlias != null && !certAlias.trim().isEmpty()) {
                command.add("--cert-alias");
                command.add(certAlias.trim());
            }
        } else {
            // Default: X.509 CERTIFICATE authentication from Windows-MY / CAC
            command.add("--cert");
            if (certAlias != null && !certAlias.trim().isEmpty()) {
                command.add("--cert-alias");
                command.add(certAlias.trim());
            }
        }

        // 2. Configure Operation
        String fetchModeStr = config.getCompanionTopazFetchMode();
        FetchMode mode = FetchMode.DATASET;
        try {
            if (fetchModeStr != null) {
                mode = FetchMode.valueOf(fetchModeStr.trim().toUpperCase());
            }
        } catch (IllegalArgumentException ignored) {}

        switch (mode) {
            case SUBMIT:
                String jcl = config.getCompanionTopazJclSource();
                if (jcl == null || jcl.trim().isEmpty()) {
                    throw new IllegalArgumentException("Mainframe JCL source is not configured for SUBMIT mode.");
                }
                command.add("--submit");
                command.add(jcl.trim());
                command.add("--wait");
                String dd = config.getCompanionTopazJobDd();
                if (dd != null && !dd.trim().isEmpty()) {
                    command.add("--dd");
                    command.add(dd.trim());
                }
                int waitTimeout = config.getCompanionTopazTimeoutSec();
                command.add("--wait-timeout");
                command.add(String.valueOf(waitTimeout > 0 ? waitTimeout : 0));
                break;

            case JOB_SPOOL:
                String jobId = config.getCompanionTopazJobId();
                if (jobId == null || jobId.trim().isEmpty()) {
                    throw new IllegalArgumentException("Mainframe Job ID is not configured for JOB_SPOOL mode.");
                }
                command.add("--job");
                command.add(jobId.trim());
                String spoolDd = config.getCompanionTopazJobDd();
                if (spoolDd != null && !spoolDd.trim().isEmpty()) {
                    command.add("--dd");
                    command.add(spoolDd.trim());
                }
                break;

            case DATASET:
            default:
                String dataset = config.getCompanionTopazDataset();
                if (dataset == null || dataset.trim().isEmpty()) {
                    throw new IllegalArgumentException("Mainframe sequential dataset is not configured for DATASET mode. Please configure dataset name.");
                }
                command.add("--seq");
                command.add("--dataset");
                command.add(dataset.trim());
                break;
        }

        // 3. Output destination
        command.add("--output");
        command.add(tempOutputFile.getAbsolutePath());

        if (statusLogger != null) {
            statusLogger.accept("Launching Topaz companion process (" + mode + " mode)...");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (scriptFile.getParentFile() != null) {
            pb.directory(scriptFile.getParentFile());
        }
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder stdoutBuilder = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdoutBuilder.append(line).append("\n");
                if (statusLogger != null && (line.contains("[SUCCESS]") || line.contains("Connecting") || line.contains("Job submitted"))) {
                    statusLogger.accept(line.trim());
                }
            }
        }

        int timeoutSec = config.getCompanionTopazTimeoutSec();
        boolean completed;
        if (timeoutSec <= 0) {
            process.waitFor();
            completed = true;
        } else {
            completed = process.waitFor(timeoutSec, TimeUnit.SECONDS);
        }

        if (!completed) {
            process.destroyForcibly();
            return new CompanionResult(false, -1, null, stdoutBuilder.toString(),
                    "Topaz companion process timed out after " + timeoutSec + " seconds.");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            return new CompanionResult(false, exitCode, null, stdoutBuilder.toString(),
                    "Topaz companion exited with error code " + exitCode + ":\n" + stdoutBuilder.toString());
        }

        if (!tempOutputFile.exists() || tempOutputFile.length() == 0) {
            return new CompanionResult(false, exitCode, null, stdoutBuilder.toString(),
                    "Topaz companion completed but output report file is empty: " + tempOutputFile.getAbsolutePath());
        }

        String content = new String(Files.readAllBytes(tempOutputFile.toPath()), StandardCharsets.UTF_8);
        return new CompanionResult(true, 0, content, stdoutBuilder.toString(), null);
    }
}
