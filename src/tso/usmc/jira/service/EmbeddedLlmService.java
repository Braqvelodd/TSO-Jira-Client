package tso.usmc.jira.service;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import tso.usmc.jira.util.JiraConfig;

/**
 * Service to interact with an offline LLM using llama.cpp (llama-cli).
 * Handles automatic extraction of embedded resources from the JAR.
 */
public class EmbeddedLlmService {

    public interface ProgressListener {
        void onProgress(String task, int percent);
    }

    private final String cliPath;
    private final String modelPath;

    public EmbeddedLlmService(JiraConfig config, ProgressListener listener) throws IOException {
        this.cliPath = config.getLlamaCliPath();
        this.modelPath = config.getLlamaModelPath();

        // 1. Ensure resources are extracted
        ensureResourceExtracted("/bin/llama-cli.exe", cliPath, listener);
        
        // Extract required DLLs
        String binDir = new File(cliPath).getParent();
        String[] dlls = {
            "llama.dll", "ggml.dll", "ggml-base.dll", "mtmd.dll", "ggml-rpc.dll",
            "ggml-cpu-x64.dll", "ggml-cpu-sse42.dll", "libomp140.x86_64.dll",
            "ggml-cpu-alderlake.dll", "ggml-cpu-cannonlake.dll", "ggml-cpu-cascadelake.dll",
            "ggml-cpu-cooperlake.dll", "ggml-cpu-haswell.dll", "ggml-cpu-icelake.dll",
            "ggml-cpu-ivybridge.dll", "ggml-cpu-piledriver.dll", "ggml-cpu-sandybridge.dll",
            "ggml-cpu-sapphirerapids.dll", "ggml-cpu-skylakex.dll", "ggml-cpu-zen4.dll"
        };
        
        for (String dll : dlls) {
            ensureResourceExtracted("/bin/" + dll, new File(binDir, dll).getAbsolutePath(), null);
        }

        ensureResourceExtracted("/models/llama-3-8b.gguf", modelPath, listener);

        // 2. Validate paths
        File cliFile = new File(cliPath);
        if (!cliFile.exists()) {
            throw new FileNotFoundException("Llama CLI not found at: " + cliPath);
        }

        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            throw new FileNotFoundException("Model file not found at: " + modelPath);
        }
    }

    private void ensureResourceExtracted(String resourcePath, String targetPath, ProgressListener listener) throws IOException {
        File targetFile = new File(targetPath);
        if (targetFile.exists()) {
            return;
        }

        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        URL resourceUrl = getClass().getResource(resourcePath);
        if (resourceUrl == null) {
            System.out.println("Resource " + resourcePath + " not found in JAR. Skipping extraction.");
            return;
        }

        URLConnection connection = resourceUrl.openConnection();
        long totalSize = connection.getContentLengthLong();
        String taskName = "Extracting " + new File(resourcePath).getName();

        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(targetFile)) {
            
            byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
            long bytesReadTotal = 0;
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                bytesReadTotal += bytesRead;
                
                if (listener != null && totalSize > 0) {
                    int percent = (int) ((bytesReadTotal * 100) / totalSize);
                    listener.onProgress(taskName, percent);
                }
            }
            targetFile.setExecutable(true);
        }
    }

    public String summarizeActions(String text, ProgressListener listener) throws Exception {
        File tempPromptFile = File.createTempFile("llama_prompt_", ".txt");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(tempPromptFile), StandardCharsets.UTF_8))) {
            pw.println("You are a helpful assistant that summarizes Jira ticket comments.");
            pw.println("Please provide a concise summary of the key actions and decisions mentioned in the following comments:");
            pw.println("\n--- COMMENTS START ---");
            pw.println(text);
            pw.println("--- COMMENTS END ---");
            pw.println("\nSummary:");
        }

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
        
        ProcessBuilder pb = new ProcessBuilder(
            cliPath,
            "-m", modelPath,
            "-f", tempPromptFile.getAbsolutePath(),
            "--temp", "0.1",
            "-n", "512",
            "-t", String.valueOf(threads),
            "--no-display-prompt"
        );
        pb.redirectErrorStream(true); // Combine stdout and stderr to capture all output

        if (listener != null) listener.onProgress("Starting LLM process...", 0);
        
        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                // Many LLM logs go to stderr (now combined). We filter for display.
                if (line.contains("load_model") || line.contains("system_info")) {
                    if (listener != null) listener.onProgress("AI Engine: Loading model into memory...", -1);
                } else if (line.contains("compute_buffer")) {
                    if (listener != null) listener.onProgress("AI Engine: Preparing buffers...", -1);
                } else if (!line.startsWith("llm_load") && !line.startsWith("llama_")) {
                    output.append(line).append("\n");
                    lineCount++;
                    if (listener != null && lineCount % 5 == 0) {
                        listener.onProgress("AI Engine: Generating summary (" + lineCount + " lines)...", -1);
                    }
                }
            }
        }

        boolean finished = process.waitFor(5, TimeUnit.MINUTES); 
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("LLM summarization timed out after 5 minutes.");
        }

        tempPromptFile.delete();

        if (process.exitValue() != 0) {
            int exitCode = process.exitValue();
            if (exitCode == -1073741515) {
                throw new Exception("LLM process failed (Exit Code: -1073741515). This usually means a required system DLL is missing. \n\n" +
                                    "Please try installing the 'Microsoft Visual C++ Redistributable 2015-2022' (x64) or ensure your environment has the necessary MinGW runtimes.");
            }
            throw new Exception("LLM process failed with exit code: " + exitCode);
        }

        return output.toString().trim();
    }

    public String summarizeActions(String text) throws Exception {
        return summarizeActions(text, null);
    }

    public void close() {}
}
