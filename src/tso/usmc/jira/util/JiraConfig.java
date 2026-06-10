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
    private static final String CURRENT_CONFIG_VERSION = "1.4";
    private static final String CURRENT_CONSTANTS_VERSION = "1.5";
    private final Properties properties = new Properties();
    private final File configFile;
    private final File templateFile;
    private final File constantsFile;
    private final List<ConfigChangeListener> listeners = new ArrayList<>();
    private final Object lock = new Object();
    private long lastReloadTime = 0;
    private static final long RELOAD_DEBOUNCE_MS = 500;

    /**
     * Initializes the configuration loader.
     * @param configFilePath The path to the JiraConfig.ini file.
     */
    public JiraConfig() {
        // 1. Define the configuration path in a dedicated folder within the user's home directory.
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, ".JiraApiClient"); // Using a hidden folder is a common convention.
        this.configFile = new File(configDir, "JiraConfig.ini");
        this.templateFile = new File(configDir, "jiratemplate.ini");
        this.constantsFile = new File(configDir, "constants.ini");

        // 2. Ensure the configuration files exist on the file system.
        ensureConfigFileExists();
        ensureTemplateFileExists();
        ensureConstantsFileExists();
        loadProperties();
        
        // 3. Check for version mismatch and upgrade if needed
        upgradeConfigIfNeeded();
        upgradeConstantsIfNeeded();

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

    private void upgradeConstantsIfNeeded() {
        String existingVersion = getProperty("constants_version");
        if (existingVersion == null || !existingVersion.equals(CURRENT_CONSTANTS_VERSION)) {
            System.out.println("Constants version mismatch (Existing: " + existingVersion + ", Target: " + CURRENT_CONSTANTS_VERSION + "). Upgrading...");
            performConstantsUpgrade(existingVersion);
        }
    }

    private void performConstantsUpgrade(String oldVersion) {
        synchronized (lock) {
            try {
                // 1. Read existing lines
                List<String> existingLines = Files.readAllLines(constantsFile.toPath());
                
                // 2. Load default constants lines from resources
                List<String> defaultLines = new ArrayList<>();
                try (InputStream in = JiraConfig.class.getResourceAsStream("/constants.ini")) {
                    if (in != null) {
                        java.util.Scanner scanner = new java.util.Scanner(in).useDelimiter("\\n");
                        while (scanner.hasNext()) {
                            defaultLines.add(scanner.next().replace("\r", ""));
                        }
                    }
                }

                if (defaultLines.isEmpty()) {
                    System.err.println("Could not load default constants for comparison.");
                    return;
                }

                // 3. Map existing keys (both active and commented out)
                Map<String, String> existingKeyMap = new LinkedHashMap<>(); // key -> full line
                for (String line : existingLines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("# ") || trimmed.startsWith("[")) continue; // Skip general comments and sections
                    
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

                // 4. Identify missing variables from default constants
                List<String> toAdd = new ArrayList<>();
                String currentSection = "";
                for (String defLine : defaultLines) {
                    String trimmedDef = defLine.trim();
                    if (trimmedDef.startsWith("[") && trimmedDef.endsWith("]")) {
                        currentSection = trimmedDef.substring(1, trimmedDef.length() - 1).trim();
                        // Add section headers if not present in file
                        boolean sectionExists = existingLines.stream().anyMatch(l -> l.trim().equals(trimmedDef));
                        if (!sectionExists) {
                            toAdd.add("");
                            toAdd.add(trimmedDef);
                        }
                        continue;
                    }
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

                    if (!existingKeyMap.containsKey(key) && !key.equals("constants_version")) {
                        toAdd.add(defLine);
                    }
                }

                // 5. Update version and write back
                boolean versionUpdated = false;
                for (int i = 0; i < existingLines.size(); i++) {
                    if (existingLines.get(i).trim().startsWith("constants_version")) {
                        existingLines.set(i, "constants_version = " + CURRENT_CONSTANTS_VERSION);
                        versionUpdated = true;
                        break;
                    }
                }
                if (!versionUpdated) {
                    existingLines.add(0, "constants_version = " + CURRENT_CONSTANTS_VERSION);
                }

                if (!toAdd.isEmpty()) {
                    existingLines.add("");
                    existingLines.add("# Added missing variables from default constants during upgrade to version " + CURRENT_CONSTANTS_VERSION);
                    existingLines.addAll(toAdd);
                }

                Files.write(constantsFile.toPath(), existingLines);
                loadProperties(); 
                System.out.println("Upgrade to version " + CURRENT_CONSTANTS_VERSION + " complete. Added " + toAdd.size() + " missing variables.");
            } catch (IOException e) {
                System.err.println("Failed to upgrade constants file: " + e.getMessage());
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
    private void ensureTemplateFileExists() {
        if (!templateFile.exists()) {
            try {
                // Create parent directories if they don't exist.
                File parentDir = templateFile.getParentFile();
                if (!parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        throw new IOException("Could not create parent directory: " + parentDir.getAbsolutePath());
                    }
                }

                // Get the default config file from inside the JAR as a resource stream.
                try (InputStream in = JiraConfig.class.getResourceAsStream("/jiratemplate.ini");
                     OutputStream out = new FileOutputStream(templateFile)) {

                    if (in == null) {
                        // Create an empty file if the resource is not found
                        templateFile.createNewFile();
                    } else {
                        // Write the default config from the JAR to the new file on the filesystem.
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = in.read(buffer)) > 0) {
                            out.write(buffer, 0, length);
                        }
                    }
                }
            } catch (IOException ex) {
                 System.err.println("Could not create the template file: " + ex.getMessage());
            }
        }
    }
    private void ensureConstantsFileExists() {
        if (!constantsFile.exists()) {
            try {
                File parentDir = constantsFile.getParentFile();
                if (!parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        throw new IOException("Could not create parent directory: " + parentDir.getAbsolutePath());
                    }
                }

                try (InputStream in = JiraConfig.class.getResourceAsStream("/constants.ini");
                     OutputStream out = new FileOutputStream(constantsFile)) {

                    if (in == null) {
                        throw new IOException("'constants.ini' not found in JAR resources.");
                    }

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                }
            } catch (IOException ex) {
                 System.err.println("Could not create the constants file: " + ex.getMessage());
            }
        }
    }
    // NEW: Centralized method for loading properties
    private void loadProperties() {
        synchronized (lock) {
            properties.clear(); // Clear old properties before loading new ones

            // 1. Load constants.ini first (contains system defaults & base settings)
            if (this.constantsFile != null && this.constantsFile.exists()) {
                try {
                    List<String> lines = Files.readAllLines(this.constantsFile.toPath());
                    String currentSection = "";
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                            continue;
                        }
                        if (line.startsWith("[") && line.endsWith("]")) {
                            currentSection = line.substring(1, line.length() - 1).trim();
                        } else if (line.contains("=")) {
                            String[] parts = line.split("=", 2);
                            String key = parts[0].trim();
                            String value = parts[1].trim();
                            String fullKey = (currentSection.isEmpty() || 
                                              currentSection.equals("Environment") || 
                                              currentSection.equals("Reconciliation") || 
                                              currentSection.equals("Teams")) ? key : currentSection + "." + key;
                            properties.setProperty(fullKey, value);
                        }
                    }
                    System.out.println("Constants loaded from " + constantsFile.getName());
                } catch (IOException ex) {
                    System.err.println("Error loading constants: " + ex.getMessage());
                }
            }

            // 2. Load jiratemplate.ini second (contains custom workflows, filters, and templates)
            if (this.templateFile.exists()) {
                try (InputStream input = new FileInputStream(this.templateFile)) {
                    Properties tempProps = new Properties();
                    tempProps.load(input);
                    // Only merge template, api_template, workflow, and jql_filter keys
                    for (String key : tempProps.stringPropertyNames()) {
                        if (key.startsWith("template.") || key.startsWith("api_template.") || 
                            key.startsWith("workflow.") || key.startsWith("jql_filter.")) {
                            properties.setProperty(key, tempProps.getProperty(key));
                        }
                    }
                    System.out.println("Templates loaded from " + templateFile.getName());
                } catch (IOException ex) {
                    System.err.println("Error loading templates: " + ex.getMessage());
                }
            }

            // 3. Load JiraConfig.ini last (contains user-specific overrides, settings, and themes)
            try (InputStream input = new FileInputStream(this.configFile)) {
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
    public File getTemplateFile() {
        return this.templateFile;
    }
    public void saveProperties(Map<String, String> newProps) {
        synchronized (lock) {
            try {
                Path path = configFile.toPath();
                List<String> lines = Files.readAllLines(path);
                Map<String, String> remaining = new LinkedHashMap<>(newProps);

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    
                    if (line.contains("=")) {
                        String key = line.split("=", 2)[0].trim();
                        if (remaining.containsKey(key)) {
                            lines.set(i, key + " = " + remaining.get(key));
                            remaining.remove(key);
                        }
                    }
                }

                // Append any new keys that weren't in the file already
                for (Map.Entry<String, String> entry : remaining.entrySet()) {
                    lines.add(entry.getKey() + " = " + entry.getValue());
                }

                Files.write(path, lines);
                reload();
            } catch (IOException e) {
                System.err.println("Error saving properties: " + e.getMessage());
            }
        }
    }

    public void saveProperty(String key, String value) {
        synchronized (lock) {
            try {
                Path path = configFile.toPath();
                List<String> lines = Files.readAllLines(path);
                String newLine = key + " = " + value;
                boolean found = false;

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (!line.startsWith("#") && (line.startsWith(key + "=") || line.startsWith(key + " "))) {
                        // Check if the key matches exactly before the =
                        String potentialKey = line.split("=", 2)[0].trim();
                        if (potentialKey.equals(key)) {
                            lines.set(i, newLine);
                            found = true;
                            break;
                        }
                    }
                }

                if (!found) {
                    lines.add(newLine);
                }

                Files.write(path, lines);
                reload();
            } catch (IOException e) {
                System.err.println("Error saving property " + key + ": " + e.getMessage());
            }
        }
    }

    // NEW: Public method to save a JQL filter to the template file
    public void saveJqlFilter(String name, String fields, String jql) {
        synchronized (lock) {
            try {
                Path path = templateFile.toPath();
                List<String> lines = templateFile.exists() ? Files.readAllLines(path) : new ArrayList<>();
                String key = "jql_filter." + name;
                String value = fields + "|" + jql;
                String newLine = key + " = " + value;
                
                boolean found = false;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (!line.startsWith("#") && line.startsWith(key + "=")) {
                        lines.set(i, newLine);
                        found = true;
                        break;
                    } else if (!line.startsWith("#") && line.startsWith(key + " ")) {
                        // Handle potential space before =
                        String potentialKey = line.split("=", 2)[0].trim();
                        if (potentialKey.equals(key)) {
                            lines.set(i, newLine);
                            found = true;
                            break;
                        }
                    }
                }
                
                if (!found) {
                    if (lines.isEmpty() || !lines.get(lines.size() - 1).trim().isEmpty()) {
                        lines.add(""); // Add newline before new section if not already there
                    }
                    if (lines.stream().noneMatch(l -> l.contains("# JQL Filters"))) {
                        lines.add("# JQL Filters (Format: fields|query)");
                    }
                    lines.add(newLine);
                }
                
                Files.write(path, lines);
                reload();
            } catch (IOException e) {
                System.err.println("Error saving JQL filter: " + e.getMessage());
            }
        }
    }

    public String[] getJqlFilterKeys() {
        return getKeysByPrefix("jql_filter.");
    }

    public String getJqlFilter(String key) {
        return getProperty("jql_filter." + key);
    }
    // NEW: Method to start the file watcher background thread
    private void startFileWatcher() {
        ExecutionService.submit(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                Path path = this.configFile.getParentFile().toPath();
                path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

                WatchKey key;
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        String fileName = event.context().toString();
                        // Check if the modified file is our config file, template file, or constants file
                        if (fileName.equals(this.configFile.getName()) || 
                            fileName.equals(this.templateFile.getName()) || 
                            (this.constantsFile != null && fileName.equals(this.constantsFile.getName()))) {
                            // File has been modified, trigger reload
                            reload();
                        }
                    }
                    key.reset();
                }
            } catch (IOException | InterruptedException e) {
                // Background watcher silent failure
            }
        });
    }
    // NEW: Public method to manually trigger a reload and notify listeners
    public void reload() {
        synchronized (lock) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastReloadTime < RELOAD_DEBOUNCE_MS) {
                return; // Ignore rapid-fire reload requests
            }
            lastReloadTime = currentTime;
            loadProperties();
        }
        // Notify all registered listeners outside the sync block to avoid deadlocks
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
        String assignee = getTeamProperty("unassigned", "lead");
        if (assignee == null) assignee = getProperty("unassigned_backlog_assignee_id");
        
        if (assignee == null || assignee.trim().isEmpty()) {
            return "LINCOLN.TODD.ALAN"; // Default fallback
        }
        return assignee.trim();
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

    public String getTeamProperty(String teamKey, String subKey) {
        return getProperty("team." + teamKey + "." + subKey);
    }

    public String getTeamDetails(String key) {
        String direct = getProperty("team." + key);
        if (direct != null) return direct;
        
        // If not found directly, try to reconstruct from hierarchical subkeys for backward compatibility
        String name = getTeamProperty(key, "name");
        String lead = getTeamProperty(key, "lead");
        String component = getTeamProperty(key, "component");
        String id = getTeamProperty(key, "id");
        
        if (name != null || lead != null || component != null || id != null) {
            return (name != null ? name : "") + "|" + 
                   (lead != null ? lead : "") + "|" + 
                   (component != null ? component : "") + "|" + 
                   (id != null ? id : "");
        }
        return null;
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

    public String[] getWorkflowRecipeKeys() {
        return getKeysByPrefix("workflow.");
    }

    /**
     * Helper to find all keys with a specific prefix in the config file, 
     * preserving the order they appear in.
     */
    private String[] getKeysByPrefix(String prefix) {
        List<String> keys = new ArrayList<>();
        // Helper to process a file and extract keys
        java.util.function.Consumer<File> processFile = (file) -> {
            if (file == null || !file.exists()) return;
            try {
                List<String> lines = Files.readAllLines(file.toPath());
                for (String line : lines) {
                    line = line.trim();
                    if (!line.startsWith("#") && line.startsWith(prefix)) {
                        String fullKey = line.split("=", 2)[0].trim();
                        String remainder = fullKey.substring(prefix.length());
                        String shortKey = remainder.contains(".") ? remainder.split("\\.")[0] : remainder;
                        if (!keys.contains(shortKey)) {
                            keys.add(shortKey);
                        }
                    }
                }
            } catch (IOException ignored) {}
        };

        processFile.accept(configFile);
        if (prefix.startsWith("template.") || prefix.startsWith("api_template.") || prefix.startsWith("workflow.") || prefix.startsWith("team.")) {
            processFile.accept(templateFile);
        }

        if (keys.isEmpty()) {
            // Fallback to the properties object if file reading fails or finds nothing (will be unordered)
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

    public int[] getIspwColumnBounds(String key, int[] defaultBounds) {
        String val = getProperty("recon.ispw." + key + ".bounds");
        if (val == null || !val.contains(",")) return defaultBounds;
        try {
            String[] parts = val.split(",");
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (Exception e) {
            return defaultBounds;
        }
    }

    public int[] getIspwActionBounds(int[] defaultBounds) {
        String val = getProperty("recon.ispw.action.bounds");
        if (val == null || !val.contains(",")) return defaultBounds;
        try {
            String[] parts = val.split(",");
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (Exception e) {
            return defaultBounds;
        }
    }

    public int getIspwMinLineLength(int defaultMin) {
        String val = getProperty("recon.ispw.min_line_length");
        if (val == null) return defaultMin;
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return defaultMin;
        }
    }

    public int getParallelThreads() {
        String val = getProperty("parallel_threads");
        if (val == null) return 5;
        try {
            int threads = Integer.parseInt(val.trim());
            return Math.max(1, Math.min(threads, 50)); // Safety cap at 50
        } catch (Exception e) {
            return 5;
        }
    }

    public int getLlmTimeoutMinutes() {
        String val = getProperty("llm_timeout_minutes");
        if (val == null) return 5;
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return 5;
        }
    }

    public int getApiTimeoutSeconds() {
        String val = getProperty("api_timeout_seconds");
        if (val == null) return 30;
        try {
            return Integer.parseInt(val.trim());
        } catch (Exception e) {
            return 30;
        }
    }

    public boolean isAutocompleteEnabled() {
        String value = getProperty("autocomplete.enabled");
        return value == null || Boolean.parseBoolean(value.trim());
    }

    public String getTheme() {
        String theme = getProperty("theme");
        if (theme == null || theme.trim().isEmpty()) {
            return "default";
        }
        return theme.trim().toLowerCase();
    }

    public void setTheme(String theme) {
        saveProperty("theme", theme);
    }

    public String getThemeAccentColor() {
        String color = getProperty("theme_accent_color");
        if (color == null || color.trim().isEmpty()) {
            return "#0078D7";
        }
        return color.trim();
    }

    public void setThemeAccentColor(String color) {
        saveProperty("theme_accent_color", color);
    }

    public String getThemeCssFilePath() {
        String path = getProperty("theme_css_file");
        if (path == null || path.trim().isEmpty()) {
            File parentDir = configFile.getParentFile();
            File cssFile = new File(parentDir, "custom_theme.css");
            ensureCssFileExists(cssFile);
            return cssFile.getAbsolutePath();
        }
        File cssFile = new File(path.trim());
        ensureCssFileExists(cssFile);
        return cssFile.getAbsolutePath();
    }

    private void ensureCssFileExists(File file) {
        if (!file.exists()) {
            try {
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                try (InputStream is = getClass().getResourceAsStream("/themes/custom.css")) {
                    if (is != null) {
                        Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        // Fallback in case resource is missing
                        List<String> defaultCss = new ArrayList<>();
                        defaultCss.add("/* Custom CSS Stylesheet for USMC TSO Jira Client */");
                        defaultCss.add(".root { -fx-font-family: \"Segoe UI\", Arial, sans-serif; }");
                        Files.write(file.toPath(), defaultCss);
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to create default CSS file: " + e.getMessage());
            }
        }
    }

    public File getConstantsFile() {
        return this.constantsFile;
    }

    public List<String> getCiTypes() {
        String prefixes = getProperty("CI_Types.prefixes");
        if (prefixes == null || prefixes.trim().isEmpty()) {
            return java.util.Arrays.asList("COB", "PROC", "JCL", "SYS", "ASM", "COPY", "DMGR", "DCLG", "CMAP");
        }
        List<String> list = new ArrayList<>();
        for (String p : prefixes.split(",")) {
            list.add(p.trim().toUpperCase());
        }
        return list;
    }

    public String getCustomFieldId(String key, String defaultValue) {
        String val = getProperty("Custom_Fields." + key);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        return val.trim();
    }

    public String getCloneProjectKey() {
        String val = getProperty("Defaults.clone_project_key");
        if (val == null || val.trim().isEmpty()) {
            return "TFS";
        }
        return val.trim().toUpperCase();
    }

    public List<String> getSubtaskTypes() {
        String types = getProperty("Defaults.subtask_types");
        if (types == null || types.trim().isEmpty()) {
            return java.util.Arrays.asList("Sub-task", "ST-PCU", "ST-Database", "ST-Interface");
        }
        List<String> list = new ArrayList<>();
        for (String t : types.split(",")) {
            list.add(t.trim());
        }
        return list;
    }

    public String getJqlDisplayFields() {
        String val = getProperty("Defaults.jql_display_fields");
        if (val == null || val.trim().isEmpty()) {
            return "key, summary, status, assignee, issuelinks";
        }
        return val.trim();
    }

    public String getJqlDefaultQuery() {
        String val = getProperty("Defaults.jql_default_query");
        if (val == null || val.trim().isEmpty()) {
            return "issuetype = Bug AND status = 'To Do' ORDER BY created DESC";
        }
        return val.trim();
    }

    public String getReconciliationParentKeys() {
        String val = getProperty("Defaults.reconciliation_parent_keys");
        if (val == null || val.trim().isEmpty()) {
            return "TFS-49439\nTFS-35035";
        }
        return val.replace(",", "\n").trim();
    }
}

