package tso.usmc.jira.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.swing.JOptionPane;

/**
 * Loads and provides access to configuration settings from the JiraConfig.ini file.
 */
public class JiraConfig {
    private static final String CURRENT_CONFIG_VERSION = "1.1";
    private final Properties properties = new Properties();
    private final File configFile;
    private final List<ConfigChangeListener> listeners = new ArrayList<>();
    private final Object lock = new Object();

    /**
     * Initializes the configuration loader.
     * @param configFilePath The path to the JiraConfig.ini file.
     */
    public JiraConfig() {
        // 1. Define the configuration path in a dedicated folder within the user's home directory.
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, ".JiraApiClient"); // Using a hidden folder is a common convention.
        this.configFile = new File(configDir, "JiraConfig.ini");

        // 2. Ensure the configuration file exists on the file system.
        ensureConfigFileExists();
        loadProperties();
        
        // 3. Check for version mismatch and upgrade if needed
        upgradeConfigIfNeeded();

        startFileWatcher();
    }

    private void upgradeConfigIfNeeded() {
        String existingVersion = getProperty("config_version");
        if (existingVersion == null || !existingVersion.equals(CURRENT_CONFIG_VERSION)) {
            System.out.println("Config version mismatch (Existing: " + existingVersion + ", Target: " + CURRENT_CONFIG_VERSION + "). Upgrading...");
            performUpgrade(existingVersion);
        }
    }

    private void performUpgrade(String oldVersion) {
        synchronized (lock) {
            try {
                // 1. Read existing lines
                List<String> existingLines = Files.readAllLines(configFile.toPath());
                
                // 2. Load default config lines from resources
                List<String> defaultLines = new ArrayList<>();
                try (InputStream in = JiraConfig.class.getResourceAsStream("/JiraConfig.ini")) {
                    if (in != null) {
                        java.util.Scanner scanner = new java.util.Scanner(in).useDelimiter("\\n");
                        while (scanner.hasNext()) {
                            defaultLines.add(scanner.next().replace("\r", ""));
                        }
                    }
                }

                if (defaultLines.isEmpty()) {
                    System.err.println("Could not load default config for comparison.");
                    return;
                }

                // 3. Map existing keys (both active and commented out)
                Map<String, String> existingKeyMap = new LinkedHashMap<>(); // key -> full line
                for (String line : existingLines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("# ")) continue; // Skip general comments
                    
                    if (trimmed.startsWith("#")) {
                        // Check if it's a commented out property: # key = value
                        String content = trimmed.substring(1).trim();
                        if (content.contains("=")) {
                            String key = content.split("=", 2)[0].trim();
                            if (!existingKeyMap.containsKey(key)) {
                                existingKeyMap.put(key, line);
                            }
                        }
                    } else if (trimmed.contains("=")) {
                        String key = trimmed.split("=", 2)[0].trim();
                        existingKeyMap.put(key, line);
                    }
                }

                // 4. Identify missing variables from default config
                List<String> toAdd = new ArrayList<>();
                for (String defLine : defaultLines) {
                    String trimmedDef = defLine.trim();
                    if (trimmedDef.isEmpty() || (trimmedDef.startsWith("#") && !trimmedDef.substring(1).trim().contains("="))) {
                        continue; 
                    }

                    String key;
                    if (trimmedDef.startsWith("#")) {
                        key = trimmedDef.substring(1).trim().split("=", 2)[0].trim();
                    } else if (trimmedDef.contains("=")) {
                        key = trimmedDef.split("=", 2)[0].trim();
                    } else {
                        continue;
                    }

                    if (!existingKeyMap.containsKey(key) && !key.equals("config_version")) {
                        toAdd.add(defLine);
                    }
                }

                // 5. Update version and write back
                boolean versionUpdated = false;
                for (int i = 0; i < existingLines.size(); i++) {
                    if (existingLines.get(i).trim().startsWith("config_version")) {
                        existingLines.set(i, "config_version = " + CURRENT_CONFIG_VERSION);
                        versionUpdated = true;
                        break;
                    }
                }
                if (!versionUpdated) {
                    existingLines.add(0, "config_version = " + CURRENT_CONFIG_VERSION);
                }

                if (!toAdd.isEmpty()) {
                    existingLines.add("");
                    existingLines.add("# Added missing variables from default config during upgrade to version " + CURRENT_CONFIG_VERSION);
                    existingLines.addAll(toAdd);
                }

                Files.write(configFile.toPath(), existingLines);
                loadProperties(); 
                System.out.println("Upgrade to version " + CURRENT_CONFIG_VERSION + " complete. Added " + toAdd.size() + " missing variables.");
            } catch (IOException e) {
                System.err.println("Failed to upgrade config file: " + e.getMessage());
            }
        }
    }
    private void ensureConfigFileExists() {
        if (!configFile.exists()) {
            try {
                // Create parent directories if they don't exist.
                File parentDir = configFile.getParentFile();
                if (!parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        throw new IOException("Could not create parent directory: " + parentDir.getAbsolutePath());
                    }
                }

                // Get the default config file from inside the JAR as a resource stream.
                try (InputStream in = JiraConfig.class.getResourceAsStream("/JiraConfig.ini");
                     OutputStream out = new FileOutputStream(configFile)) {

                    if (in == null) {
                        // This happens if JiraConfig.ini is not in the JAR's root resource folder.
                        throw new IOException("'JiraConfig.ini' not found in JAR resources. Ensure it's in your project's resource folder.");
                    }

                    // Write the default config from the JAR to the new file on the filesystem.
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                }
            } catch (IOException ex) {
                 String errorMessage = "Fatal Error: Could not create the initial configuration file at: " + configFile.getAbsolutePath()
                    + "\nPlease ensure the application has permission to write to this location.";
                 JOptionPane.showMessageDialog(null, errorMessage, "Configuration Setup Error", JOptionPane.ERROR_MESSAGE);
                 throw new RuntimeException(errorMessage, ex);
            }
        }
    }
    // NEW: Centralized method for loading properties
    private void loadProperties() {
        synchronized (lock) {
            try (InputStream input = new FileInputStream(this.configFile)) {
                properties.clear(); // Clear old properties before loading new ones
                properties.load(input);
                System.out.println("Configuration reloaded from " + configFile.getName());
            } catch (IOException ex) {
                System.err.println("Error reloading configuration: " + ex.getMessage());
            }
        }
    }
    public File getConfigFile() {
        return this.configFile;
    }
    // NEW: Method to start the file watcher background thread
    private void startFileWatcher() {
        Thread watcherThread = new Thread(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                Path path = this.configFile.getParentFile().toPath();
                path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                WatchKey key;
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        // Check if the modified file is our config file
                        if (event.context().toString().equals(this.configFile.getName())) {
                            // File has been modified, trigger reload
                            reload();
                        }
                    }
                    key.reset();
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        watcherThread.setDaemon(true); // Ensures the thread doesn't prevent JVM shutdown
        watcherThread.setName("JiraConfig-Watcher");
        watcherThread.start();
    }
    // NEW: Public method to manually trigger a reload and notify listeners
    public void reload() {
        loadProperties();
        // Notify all registered listeners
        for (ConfigChangeListener listener : listeners) {
            listener.onConfigChanged();
        }
    }

    /**
     * Gets a property value by its key.
     * @param key The property key.
     * @return The property value.
     */
    public String getProperty(String key) {
        synchronized (lock) {
            return properties.getProperty(key);
        }
    }
    // NEW: Methods to manage listeners
    public void addConfigChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    public void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Specifically retrieves the assignee ID for the unassigned backlog.
     * @return The assignee's JIRA user ID.
     */
    public String getUnassignedBacklogAssignee() {
        String assignee = getProperty("unassigned_backlog_assignee_id");
        if (assignee == null || assignee.trim().isEmpty()) {
            // This is a required field, so throw an error if it's missing.
            throw new IllegalStateException("The 'unassigned_backlog_assignee_id' is missing or empty in the JiraConfig.ini file.");
        }
        return assignee;
    }

    /**
     * Retrieves the JIRA base URL from the configuration.
     * @return The JIRA base URL.
     */
    public String getJiraBaseUrl() {
        String url = getProperty("jira_base_url");
        if (url == null || url.trim().isEmpty()) {
            return "https://tso-jira.mcw.usmc.mil"; // Default fallback
        }
        return url.trim();
    }

    public String getWorkflowJql() {
        String jql = getProperty("workflow_jql");
        if (jql == null || jql.trim().isEmpty()) {
            return "project in (JRS, MOD, MSMB, RFFKCI, TSO) AND status in (\"Incoming Requirements\", \"Submitted to TSO\")";
        }
        return jql.trim();
    }

    public String getWorkflowFySummaryIssue() {
        String key = getProperty("workflow_fy_summary_issue");
        if (key == null || key.trim().isEmpty()) {
            return "TFS-59109";
        }
        return key.trim();
    }

    public String[] getWorkflowTeamKeys() {
        return getKeysByPrefix("team.");
    }

    public String getTeamDetails(String key) {
        return getProperty("team." + key);
    }

    public String[] getTemplateKeys() {
        return getKeysByPrefix("template.");
    }

    public String getTemplateLabel(String key) {
        return getProperty("template." + key + ".label");
    }

    public String getTemplateText(String key) {
        String text = getProperty("template." + key + ".text");
        if (text != null) {
            // Handle escaped newlines
            return text.replace("\\n", "\n");
        }
        return text;
    }

    public String getLlamaCliPath() {
        String path = getProperty("llama_cli_path");
        if (path == null || path.trim().isEmpty()) {
            // Default to managed bin folder in user home
            return new File(configFile.getParentFile(), "bin/llama-cli.exe").getAbsolutePath();
        }
        return path;
    }

    public String getLlamaModelPath() {
        String path = getProperty("llama_model_path");
        if (path == null || path.trim().isEmpty()) {
            // Default to managed models folder in user home
            return new File(configFile.getParentFile(), "models/model.gguf").getAbsolutePath();
        }
        return path;
    }

    public String[] getRawApiTemplateKeys() {
        return getKeysByPrefix("api_template.");
    }

    public String getRawApiTemplate(String key) {
        return getProperty("api_template." + key);
    }

    /**
     * Helper to find all keys with a specific prefix in the config file, 
     * preserving the order they appear in.
     */
    private String[] getKeysByPrefix(String prefix) {
        List<String> keys = new ArrayList<>();
        try {
            // Read the file lines to preserve the order as they appear in the config
            List<String> lines = Files.readAllLines(configFile.toPath());
            for (String line : lines) {
                line = line.trim();
                // Ignore comments and look for our prefix
                if (!line.startsWith("#") && line.startsWith(prefix)) {
                    // Extract the key part before the '='
                    String fullKey = line.split("=", 2)[0].trim();
                    // Extract the identifier part after the prefix
                    String remainder = fullKey.substring(prefix.length());
                    // If it's template.key.label, we only want the 'key' part
                    String shortKey = remainder.contains(".") ? remainder.split("\\.")[0] : remainder;
                    
                    if (!keys.contains(shortKey)) {
                        keys.add(shortKey);
                    }
                }
            }
        } catch (IOException e) {
            // Fallback to the properties object if file reading fails (will be unordered)
            synchronized (lock) {
                for (Object keyObj : properties.keySet()) {
                    String key = keyObj.toString();
                    if (key.startsWith(prefix)) {
                        String remainder = key.substring(prefix.length());
                        String shortKey = remainder.contains(".") ? remainder.split("\\.")[0] : remainder;
                        if (!keys.contains(shortKey)) {
                            keys.add(shortKey);
                        }
                    }
                }
            }
        }
        return keys.toArray(new String[0]);
    }

    public boolean isTabEnabled(String tabName) {
        String propertyName = "tab." + tabName.replace(" ", "") + ".enabled";
        String value = getProperty(propertyName);
        if (value == null) {
            return false; // Default to disabled if the property is missing/commented out
        }
        return Boolean.parseBoolean(value.trim());
    }
}
