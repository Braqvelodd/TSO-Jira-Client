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
     * Resolves the Topaz companion JAR file from configuration, with backwards compatibility
     * for configurations that pointed to run.bat or the project root directory.
     */
    public static File resolveJarFile(String configuredPath) {
        if (configuredPath == null || configuredPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Topaz companion JAR path is not configured. Please set companion.topaz.path in companion.properties.");
        }

        File target = new File(configuredPath.trim());

        // If target exists and is a JAR file, return it
        if (target.exists() && target.isFile() && target.getName().toLowerCase().endsWith(".jar")) {
            return target;
        }

        // If target points to a .bat / .cmd script, check for dist/topaz-pds-reader.jar or topaz-pds-reader.jar in same directory tree
        if (target.getName().toLowerCase().endsWith(".bat") || target.getName().toLowerCase().endsWith(".cmd")) {
            File parent = target.getParentFile();
            if (parent != null) {
                File distJar = new File(parent, "dist" + File.separator + "topaz-pds-reader.jar");
                if (distJar.exists()) {
                    return distJar;
                }
                File localJar = new File(parent, "topaz-pds-reader.jar");
                if (localJar.exists()) {
                    return localJar;
                }
            }
        }

        // If target is a directory, check for dist/topaz-pds-reader.jar or topaz-pds-reader.jar
        if (target.isDirectory()) {
            File distJar = new File(target, "dist" + File.separator + "topaz-pds-reader.jar");
            if (distJar.exists()) {
                return distJar;
            }
            File localJar = new File(target, "topaz-pds-reader.jar");
            if (localJar.exists()) {
                return localJar;
            }
        }

        if (target.exists() && target.isFile()) {
            return target;
        }

        // Check fallback locations relative to working dir or user home
        File fallback1 = new File("../Topaz-file-read/dist/topaz-pds-reader.jar");
        if (fallback1.exists()) {
            return fallback1;
        }
        File fallback2 = new File(System.getProperty("user.home"), "Documents/projects/Topaz-file-read/dist/topaz-pds-reader.jar");
        if (fallback2.exists()) {
            return fallback2;
        }

        throw new IllegalArgumentException("Topaz companion JAR not found: " + target.getAbsolutePath()
                + "\nPlease configure companion.topaz.path to point to topaz-pds-reader.jar.");
    }

    /**
     * Resolves the Java runtime executable path (preferring Java 21+ for Topaz compatibility).
     */
    public static String resolveJavaExecutable() {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String exeName = isWindows ? "java.exe" : "java";

        // 1. Check custom JAVA21_HOME or JAVA_21_HOME environment variables
        String java21Home = System.getenv("JAVA21_HOME");
        if (java21Home == null || java21Home.trim().isEmpty()) {
            java21Home = System.getenv("JAVA_21_HOME");
        }
        if (java21Home != null && !java21Home.trim().isEmpty()) {
            File java21Bin = new File(java21Home.trim(), "bin" + File.separator + exeName);
            if (java21Bin.exists()) {
                return java21Bin.getAbsolutePath();
            }
        }

        // 2. Check standard USMC TSO JDK 21 installation path
        File tsoJdk21 = new File("C:\\Program Files\\Java\\jdk21\\TSO\\bin" + File.separator + exeName);
        if (tsoJdk21.exists()) {
            return tsoJdk21.getAbsolutePath();
        }

        // 3. Check C:\Program Files\Java\latest\jdk-21\bin\java.exe
        File latestJdk21 = new File("C:\\Program Files\\Java\\latest\\jdk-21\\bin" + File.separator + exeName);
        if (latestJdk21.exists()) {
            return latestJdk21.getAbsolutePath();
        }

        // 4. Scan C:\Program Files\Java for jdk21* or modern JDKs
        File javaDir = new File("C:\\Program Files\\Java");
        if (javaDir.exists() && javaDir.isDirectory()) {
            File[] files = javaDir.listFiles();
            if (files != null) {
                // Priority: JDK 21+ directories
                for (File dir : files) {
                    String name = dir.getName().toLowerCase();
                    if (name.startsWith("jdk21") || name.startsWith("jdk-21") || name.startsWith("jdk-25") || name.startsWith("jdk25")) {
                        File exe = new File(dir, "bin" + File.separator + exeName);
                        if (exe.exists()) return exe.getAbsolutePath();
                        File tsoExe = new File(dir, "TSO" + File.separator + "bin" + File.separator + exeName);
                        if (tsoExe.exists()) return tsoExe.getAbsolutePath();
                    }
                }
                // Secondary: JDK 17+ directories
                for (File dir : files) {
                    String name = dir.getName().toLowerCase();
                    if (name.startsWith("jdk17") || name.startsWith("jdk-17") || name.startsWith("jdk11") || name.startsWith("jdk-11")) {
                        File exe = new File(dir, "bin" + File.separator + exeName);
                        if (exe.exists()) return exe.getAbsolutePath();
                        File tsoExe = new File(dir, "TSO" + File.separator + "bin" + File.separator + exeName);
                        if (tsoExe.exists()) return tsoExe.getAbsolutePath();
                    }
                }
            }
        }

        // 5. Check JAVA_HOME
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.trim().isEmpty()) {
            File exe = new File(javaHome.trim(), "bin" + File.separator + exeName);
            if (exe.exists()) {
                return exe.getAbsolutePath();
            }
        }

        // 6. Check current JVM java.home
        String currentJavaHome = System.getProperty("java.home");
        if (currentJavaHome != null && !currentJavaHome.trim().isEmpty()) {
            File exe = new File(currentJavaHome.trim(), "bin" + File.separator + exeName);
            if (exe.exists()) {
                return exe.getAbsolutePath();
            }
        }

        // 7. System fallback
        return "java";
    }

    /**
     * Executes the Topaz companion application synchronously.
     */
    public static CompanionResult executeTopazFetch(JiraConfig config, String certAlias, Consumer<String> statusLogger) throws Exception {
        File jarFile = resolveJarFile(config.getCompanionTopazPath());
        String javaExe = resolveJavaExecutable();

        File tempOutputFile = File.createTempFile("topaz_ispw_report_", ".txt");
        tempOutputFile.deleteOnExit();

        List<String> command = new ArrayList<>();
        command.add(javaExe);
        command.add("-jar");
        command.add(jarFile.getAbsolutePath());
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
            statusLogger.accept("Launching Topaz companion process (" + mode + " mode) via " + jarFile.getName() + "...");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        File workDir = jarFile.getParentFile();
        if (workDir != null && "dist".equalsIgnoreCase(workDir.getName()) && workDir.getParentFile() != null) {
            workDir = workDir.getParentFile();
        }
        if (workDir != null && workDir.exists()) {
            pb.directory(workDir);
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
