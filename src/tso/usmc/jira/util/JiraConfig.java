package tso.usmc.jira.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.swing.JOptionPane;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Loads, manages, and persists configuration settings across modular .properties files
 * and JSON template/filter files.
 */
public class JiraConfig {
    private static final String CURRENT_CONFIG_VERSION = "2.0";

    // In-memory property cache (case-preserving with lower-case fallback)
    private final Properties properties = new Properties();
    private final Map<String, String> lowerCasePropertyLookup = new HashMap<>();

    // Structured JSON caches
    private final Map<String, RawApiTemplateInfo> rawApiTemplates = new LinkedHashMap<>();
    private final Map<String, TaskTemplateInfo> taskTemplates = new LinkedHashMap<>();
    private final Map<String, JqlFilterInfo> jqlFilters = new LinkedHashMap<>();

    // File references in %USERPROFILE%\.JiraApiClient\
    private final File configDir;
    private final File connectionFile;
    private final File teamsFile;
    private final File uiFile;
    private final File defaultsFile;
    private final File companionFile;
    private final File llmFile;
    private final File reconciliationFile;

    // Subdirectories
    private final File filtersDir;
    private final File apiTemplatesDir;
    private final File taskTemplatesDir;
    private final File themesDir;
    private final File workflowsDir;

    // Legacy .ini files for auto-migration
    private final File legacyConfigFile;
    private final File legacyConstantsFile;
    private final File legacyTemplateFile;

    private final List<ConfigChangeListener> listeners = new ArrayList<>();
    private final Object lock = new Object();
    private long lastReloadTime = 0;
    private static final long RELOAD_DEBOUNCE_MS = 500;

    public static class RawApiTemplateInfo {
        public final String id;
        public final String label;
        public final String method;
        public final String endpoint;
        public final String body;

        public RawApiTemplateInfo(String id, String label, String method, String endpoint, String body) {
            this.id = id;
            this.label = label;
            this.method = method;
            this.endpoint = endpoint;
            this.body = body != null ? body : "";
        }
    }

    public static class TaskTemplateInfo {
        public final String name;
        public final String label;
        public final String text;

        public TaskTemplateInfo(String name, String label, String text) {
            this.name = name;
            this.label = label;
            if (text != null) {
                text = text.replace("\\\\n", "\n").replace("\\\n", "\n").replace("\\n", "\n");
                text = text.replaceAll("(?m)\\\\\\s*$", "");
            }
            this.text = text != null ? text : "";
        }
    }

    public static class JqlFilterInfo {
        public final String name;
        public final String fields;
        public final String jql;

        public JqlFilterInfo(String name, String fields, String jql) {
            this.name = name;
            this.fields = fields != null ? fields : "";
            this.jql = jql != null ? jql : "";
        }
    }

    public JiraConfig() {
        String userHome = System.getProperty("user.home");
        this.configDir = new File(userHome, ".JiraApiClient");

        this.connectionFile = new File(configDir, "connection.properties");
        this.teamsFile = new File(configDir, "teams.properties");
        this.uiFile = new File(configDir, "ui.properties");
        this.defaultsFile = new File(configDir, "defaults.properties");
        this.companionFile = new File(configDir, "companion.properties");
        this.llmFile = new File(configDir, "llm.properties");
        this.reconciliationFile = new File(configDir, "reconciliation.properties");

        this.filtersDir = new File(configDir, "filters");
        this.apiTemplatesDir = new File(configDir, "templates/api");
        this.taskTemplatesDir = new File(configDir, "templates/task");
        this.themesDir = new File(configDir, "themes");
        this.workflowsDir = new File(configDir, "workflows");

        this.legacyConfigFile = new File(configDir, "JiraConfig.ini");
        this.legacyConstantsFile = new File(configDir, "constants.ini");
        this.legacyTemplateFile = new File(configDir, "jiratemplate.ini");

        // 1. If legacy .ini files exist, migrate them to modular .properties and JSON
        checkAndPerformLegacyMigration();

        // 2. Ensure default configuration files and directories exist
        ensureDefaultFilesExist();

        // 3. Upgrade properties if newer application defaults add missing keys
        upgradeAllPropertiesIfNeeded();

        // 4. Load all properties and JSON records into memory
        loadProperties();

        // 5. Start file watcher for live reloading
        startFileWatcher();
    }

    /**
     * Determines which modular properties file a given key belongs to.
     */
    public File getTargetFileForKey(String key) {
        if (key == null) return defaultsFile;
        String lower = key.toLowerCase();

        if (lower.startsWith("team.") || lower.equals("unassigned_backlog_assignee_id")) {
            return teamsFile;
        }
        if (lower.equals("jira_base_url") || lower.equals("api_auth_method") ||
            lower.equals("api_pat_token") || lower.equals("api_timeout_seconds") ||
            lower.equals("verbose_api_logs")) {
            return connectionFile;
        }
        if (lower.startsWith("tab.") || lower.startsWith("keybind.") ||
            lower.startsWith("theme") || lower.equals("autocomplete.enabled")) {
            return uiFile;
        }
        if (lower.startsWith("companion.")) {
            return companionFile;
        }
        if (lower.startsWith("llama_") || lower.startsWith("llm_")) {
            return llmFile;
        }
        if (lower.startsWith("recon.ispw.")) {
            return reconciliationFile;
        }
        return defaultsFile;
    }

    private void ensureDefaultFilesExist() {
        if (!configDir.exists() && !configDir.mkdirs()) {
            String errorMsg = "Could not create application directory at " + configDir.getAbsolutePath();
            JOptionPane.showMessageDialog(null, errorMsg, "Configuration Error", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(errorMsg);
        }

        filtersDir.mkdirs();
        apiTemplatesDir.mkdirs();
        taskTemplatesDir.mkdirs();
        themesDir.mkdirs();
        workflowsDir.mkdirs();

        // 1. Properties files
        copyResourceIfMissing("/connection.properties", connectionFile);
        copyResourceIfMissing("/teams.properties", teamsFile);
        copyResourceIfMissing("/ui.properties", uiFile);
        copyResourceIfMissing("/defaults.properties", defaultsFile);
        copyResourceIfMissing("/companion.properties", companionFile);
        copyResourceIfMissing("/llm.properties", llmFile);
        copyResourceIfMissing("/reconciliation.properties", reconciliationFile);

        // 2. Default filter
        File defaultFilter = new File(filtersDir, "Default_Filter.json");
        if (!defaultFilter.exists()) {
            copyResourceIfMissing("/filters/Default_Filter.json", defaultFilter);
        }

        // 3. Default task template
        File defaultTask = new File(taskTemplatesDir, "release_mgmt.json");
        if (!defaultTask.exists()) {
            copyResourceIfMissing("/templates/task/release_mgmt.json", defaultTask);
        }

        // 4. Default API templates
        File[] existingApi = apiTemplatesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (existingApi == null || existingApi.length == 0) {
            try (InputStream in = JiraConfig.class.getResourceAsStream("/templates/api_templates.txt")) {
                if (in != null) {
                    java.util.Scanner s = new java.util.Scanner(in, "UTF-8");
                    while (s.hasNextLine()) {
                        String line = s.nextLine().trim();
                        if (!line.isEmpty()) {
                            File tFile = new File(apiTemplatesDir, line);
                            copyResourceIfMissing("/templates/api/" + line, tFile);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not extract API templates: " + e.getMessage());
            }
        }
    }

    private void copyResourceIfMissing(String resPath, File targetFile) {
        if (!targetFile.exists()) {
            try {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (InputStream in = JiraConfig.class.getResourceAsStream(resPath)) {
                    if (in != null) {
                        Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not copy resource " + resPath + " to " + targetFile.getName() + ": " + e.getMessage());
            }
        }
    }

    private void upgradeAllPropertiesIfNeeded() {
        upgradePropertiesFile(connectionFile, "/connection.properties");
        upgradePropertiesFile(teamsFile, "/teams.properties");
        upgradePropertiesFile(uiFile, "/ui.properties");
        upgradePropertiesFile(defaultsFile, "/defaults.properties");
        upgradePropertiesFile(companionFile, "/companion.properties");
        upgradePropertiesFile(llmFile, "/llm.properties");
        upgradePropertiesFile(reconciliationFile, "/reconciliation.properties");
    }

    private void upgradePropertiesFile(File userFile, String resourcePath) {
        if (!userFile.exists()) return;
        try {
            List<String> existingLines = Files.readAllLines(userFile.toPath(), StandardCharsets.UTF_8);
            Set<String> existingKeys = new HashSet<>();
            for (String line : existingLines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue;
                if (trimmed.contains("=")) {
                    existingKeys.add(trimmed.split("=", 2)[0].trim().toLowerCase());
                }
            }

            List<String> defaultLines = new ArrayList<>();
            try (InputStream in = JiraConfig.class.getResourceAsStream(resourcePath)) {
                if (in != null) {
                    java.util.Scanner scanner = new java.util.Scanner(in, "UTF-8").useDelimiter("\\n");
                    while (scanner.hasNext()) {
                        defaultLines.add(scanner.next().replace("\r", ""));
                    }
                }
            }
            if (defaultLines.isEmpty()) return;

            List<String> toAdd = new ArrayList<>();
            for (String defLine : defaultLines) {
                String trimmed = defLine.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) continue;
                if (trimmed.contains("=")) {
                    String key = trimmed.split("=", 2)[0].trim().toLowerCase();
                    if (!existingKeys.contains(key)) {
                        toAdd.add(defLine);
                        existingKeys.add(key);
                    }
                }
            }

            if (!toAdd.isEmpty()) {
                existingLines.add("");
                existingLines.add("# Added missing settings from application update");
                existingLines.addAll(toAdd);
                Files.write(userFile.toPath(), existingLines, StandardCharsets.UTF_8);
                System.out.println("Upgraded " + userFile.getName() + " with " + toAdd.size() + " new settings.");
            }
        } catch (Exception e) {
            System.err.println("Error checking upgrade for " + userFile.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Automatic seamless migration from legacy .ini files to modular .properties and JSON.
     */
    private void checkAndPerformLegacyMigration() {
        boolean hasLegacy = (legacyConfigFile != null && legacyConfigFile.exists()) ||
                            (legacyConstantsFile != null && legacyConstantsFile.exists()) ||
                            (legacyTemplateFile != null && legacyTemplateFile.exists());
        if (!hasLegacy) return;

        System.out.println("Legacy .ini configuration files detected. Migrating to modular .properties and JSON templates/filters...");
        try {
            ensureDefaultFilesExist();
            Map<String, String> migratedProps = new LinkedHashMap<>();

            // 1. Migrate constants.ini
            if (legacyConstantsFile.exists()) {
                List<String> lines = Files.readAllLines(legacyConstantsFile.toPath(), StandardCharsets.UTF_8);
                String currentSection = "";
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                    if (line.startsWith("[") && line.endsWith("]")) {
                        currentSection = line.substring(1, line.length() - 1).trim();
                    } else if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        String key = parts[0].trim();
                        String val = parts[1].trim();
                        String fullKey = (currentSection.isEmpty() ||
                                          currentSection.equalsIgnoreCase("Environment") ||
                                          currentSection.equalsIgnoreCase("Reconciliation") ||
                                          currentSection.equalsIgnoreCase("Companion") ||
                                          currentSection.equalsIgnoreCase("Teams")) ? key : currentSection + "." + key;
                        migratedProps.put(fullKey, val);
                    }
                }
            }

            // 2. Migrate JiraConfig.ini
            if (legacyConfigFile.exists()) {
                List<String> lines = Files.readAllLines(legacyConfigFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        migratedProps.put(parts[0].trim(), parts[1].trim());
                    }
                }
            }

            // 3. Migrate jiratemplate.ini
            if (legacyTemplateFile.exists()) {
                List<String> lines = Files.readAllLines(legacyTemplateFile.toPath(), StandardCharsets.UTF_8);
                Map<String, String> taskLabels = new LinkedHashMap<>();
                Map<String, String> taskTexts = new LinkedHashMap<>();

                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                    if (line.contains("=")) {
                        String[] parts = line.split("=", 2);
                        String key = parts[0].trim();
                        String val = parts[1].trim();

                        if (key.startsWith("template.") && key.endsWith(".label")) {
                            String name = key.substring("template.".length(), key.length() - ".label".length());
                            taskLabels.put(name, val);
                        } else if (key.startsWith("template.") && key.endsWith(".text")) {
                            String name = key.substring("template.".length(), key.length() - ".text".length());
                            taskTexts.put(name, val);
                        } else if (key.startsWith("api_template.")) {
                            String id = key.substring("api_template.".length());
                            String[] tokens = val.split("\\|", 4);
                            String label = tokens.length > 0 ? tokens[0] : id;
                            String method = tokens.length > 1 ? tokens[1] : "GET";
                            String endpoint = tokens.length > 2 ? tokens[2] : "";
                            String body = tokens.length > 3 ? tokens[3] : "";
                            saveRawApiTemplate(id, label, method, endpoint, body);
                        } else if (key.startsWith("jql_filter.")) {
                            String name = key.substring("jql_filter.".length());
                            String[] tokens = val.split("\\|", 2);
                            String fields = tokens.length > 0 ? tokens[0] : "";
                            String jql = tokens.length > 1 ? tokens[1] : "";
                            saveJqlFilter(name, fields, jql);
                        } else if (key.startsWith("workflow.")) {
                            String recipeName = key.substring("workflow.".length());
                            File wfFile = new File(workflowsDir, recipeName + ".json");
                            if (!wfFile.exists()) {
                                try {
                                    Files.write(wfFile.toPath(), val.getBytes(StandardCharsets.UTF_8));
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                for (String name : taskLabels.keySet()) {
                    String label = taskLabels.get(name);
                    String text = taskTexts.getOrDefault(name, "");
                    saveTaskTemplate(name, label, text);
                }
                for (String name : taskTexts.keySet()) {
                    if (!taskLabels.containsKey(name)) {
                        saveTaskTemplate(name, name, taskTexts.get(name));
                    }
                }
            }

            // Save non-template properties into their respective modular .properties files
            if (!migratedProps.isEmpty()) {
                savePropertiesInternal(migratedProps);
            }

            // Rename old files to .migrated
            renameToMigrated(legacyConfigFile);
            renameToMigrated(legacyConstantsFile);
            renameToMigrated(legacyTemplateFile);

            System.out.println("Migration complete! Legacy .ini files renamed to *.migrated.");
        } catch (Exception e) {
            System.err.println("Warning: Error during legacy .ini migration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void renameToMigrated(File f) {
        if (f != null && f.exists()) {
            File target = new File(f.getParentFile(), f.getName() + ".migrated");
            f.renameTo(target);
        }
    }

    private static String stripBom(String s) {
        if (s == null) return null;
        if (s.startsWith("\uFEFF")) {
            return s.substring(1);
        }
        return s;
    }

    /**
     * Centralized loader for all modular properties and JSON files.
     */
    private void loadProperties() {
        synchronized (lock) {
            properties.clear();
            lowerCasePropertyLookup.clear();
            rawApiTemplates.clear();
            taskTemplates.clear();
            jqlFilters.clear();

            // Load properties files in order
            File[] propFiles = new File[]{
                defaultsFile,
                teamsFile,
                connectionFile,
                uiFile,
                companionFile,
                llmFile,
                reconciliationFile
            };

            for (File file : propFiles) {
                if (file != null && file.exists()) {
                    try {
                        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                                continue;
                            }
                            if (trimmed.contains("=")) {
                                String[] parts = trimmed.split("=", 2);
                                String key = parts[0].trim();
                                String value = parts[1].trim();
                                properties.setProperty(key, value);
                                lowerCasePropertyLookup.put(key.toLowerCase(), value);
                            }
                        }
                    } catch (IOException ex) {
                        System.err.println("Error reading " + file.getName() + ": " + ex.getMessage());
                    }
                }
            }

            // Load Task Templates
            if (taskTemplatesDir.exists()) {
                File[] files = taskTemplatesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
                if (files != null) {
                    for (File f : files) {
                        try {
                            String content = stripBom(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
                            JSONObject json = new JSONObject(content);
                            String fileBase = f.getName().replace(".json", "");
                            String name = json.optString("name", fileBase);
                            String label = json.optString("label", name);
                            String text = json.optString("text", "");
                            TaskTemplateInfo info = new TaskTemplateInfo(name, label, text);
                            taskTemplates.put(name, info);

                            // Maintain compatibility with template.<name>.label / text lookups
                            properties.setProperty("template." + name + ".label", label);
                            properties.setProperty("template." + name + ".text", text);
                            lowerCasePropertyLookup.put(("template." + name + ".label").toLowerCase(), label);
                            lowerCasePropertyLookup.put(("template." + name + ".text").toLowerCase(), text);
                        } catch (Exception ex) {
                            System.err.println("Error loading task template " + f.getName() + ": " + ex.getMessage());
                        }
                    }
                }
            }

            // Load Raw API Templates
            if (apiTemplatesDir.exists()) {
                File[] files = apiTemplatesDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
                if (files != null) {
                    for (File f : files) {
                        try {
                            String content = stripBom(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
                            JSONObject json = new JSONObject(content);
                            String fileBase = f.getName().replace(".json", "");
                            String id = json.optString("id", fileBase);
                            String label = json.optString("label", id);
                            String method = json.optString("method", "GET");
                            String endpoint = json.optString("endpoint", "");
                            Object bodyObj = json.opt("body");
                            String body;
                            if (bodyObj instanceof JSONObject) {
                                body = ((JSONObject) bodyObj).toString(2);
                            } else if (bodyObj instanceof JSONArray) {
                                body = ((JSONArray) bodyObj).toString(2);
                            } else {
                                body = bodyObj != null ? bodyObj.toString() : "";
                            }

                            RawApiTemplateInfo info = new RawApiTemplateInfo(id, label, method, endpoint, body);
                            rawApiTemplates.put(id, info);

                            // Maintain compatibility with api_template.<key> lookups
                            String pipeFormat = label + "|" + method + "|" + endpoint + "|" + body;
                            properties.setProperty("api_template." + id, pipeFormat);
                            lowerCasePropertyLookup.put(("api_template." + id).toLowerCase(), pipeFormat);
                        } catch (Exception ex) {
                            System.err.println("Error loading API template " + f.getName() + ": " + ex.getMessage());
                        }
                    }
                }
            }

            // Load JQL Filters
            if (filtersDir.exists()) {
                File[] files = filtersDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
                if (files != null) {
                    for (File f : files) {
                        try {
                            String content = stripBom(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim());
                            JSONObject json = new JSONObject(content);
                            String fileBase = f.getName().replace(".json", "");
                            String name = json.optString("name", fileBase);
                            String fields = json.optString("fields", "");
                            String jql = json.optString("jql", "");
                            JqlFilterInfo info = new JqlFilterInfo(name, fields, jql);
                            jqlFilters.put(name, info);

                            // Maintain compatibility with jql_filter.<key> lookups
                            String pipeFormat = fields + "|" + jql;
                            properties.setProperty("jql_filter." + name, pipeFormat);
                            lowerCasePropertyLookup.put(("jql_filter." + name).toLowerCase(), pipeFormat);
                        } catch (Exception ex) {
                            System.err.println("Error loading JQL filter " + f.getName() + ": " + ex.getMessage());
                        }
                    }
                }
            }
        }
    }

    public File getConfigFile() {
        return this.defaultsFile;
    }

    public File getTemplateFile() {
        return this.taskTemplatesDir;
    }

    public File getConstantsFile() {
        return this.defaultsFile;
    }

    public File getConfigDir() {
        return this.configDir;
    }

    public void saveProperties(Map<String, String> newProps) {
        synchronized (lock) {
            savePropertiesInternal(newProps);
            reload(true);
        }
    }

    public void saveProperty(String key, String value) {
        synchronized (lock) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put(key, value);
            savePropertiesInternal(map);
            reload(true);
        }
    }

    private void savePropertiesInternal(Map<String, String> newProps) {
        Map<File, Map<String, String>> fileUpdates = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : newProps.entrySet()) {
            File targetFile = getTargetFileForKey(entry.getKey());
            fileUpdates.computeIfAbsent(targetFile, k -> new LinkedHashMap<>()).put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<File, Map<String, String>> fileEntry : fileUpdates.entrySet()) {
            File file = fileEntry.getKey();
            Map<String, String> updates = fileEntry.getValue();
            try {
                List<String> lines = file.exists()
                    ? Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)
                    : new ArrayList<>();
                Map<String, String> remaining = new LinkedHashMap<>(updates);

                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
                    if (line.contains("=")) {
                        String key = line.split("=", 2)[0].trim();
                        for (String updateKey : updates.keySet()) {
                            if (updateKey.equalsIgnoreCase(key)) {
                                lines.set(i, key + " = " + updates.get(updateKey));
                                remaining.remove(updateKey);
                                break;
                            }
                        }
                    }
                }

                // Append any new keys
                for (Map.Entry<String, String> entry : remaining.entrySet()) {
                    lines.add(entry.getKey() + " = " + entry.getValue());
                }

                Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                System.err.println("Error saving properties to " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public void saveJqlFilter(String name, String fields, String jql) {
        synchronized (lock) {
            try {
                if (!filtersDir.exists()) filtersDir.mkdirs();
                String safeName = sanitizeFileName(name);
                File file = new File(filtersDir, safeName + ".json");
                JSONObject json = new JSONObject();
                json.put("name", name);
                json.put("fields", fields != null ? fields : "");
                json.put("jql", jql != null ? jql : "");
                Files.write(file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
                reload(true);
            } catch (IOException e) {
                System.err.println("Error saving JQL filter " + name + ": " + e.getMessage());
            }
        }
    }

    public void saveRawApiTemplate(String id, String label, String method, String endpoint, String body) {
        synchronized (lock) {
            try {
                if (!apiTemplatesDir.exists()) apiTemplatesDir.mkdirs();
                String safeId = sanitizeFileName(id);
                File file = new File(apiTemplatesDir, safeId + ".json");
                JSONObject json = new JSONObject();
                json.put("id", id);
                json.put("label", label != null ? label : id);
                json.put("method", method != null ? method : "GET");
                json.put("endpoint", endpoint != null ? endpoint : "");
                if (body != null && !body.trim().isEmpty()) {
                    try {
                        String trimmed = body.trim();
                        if (trimmed.startsWith("{")) {
                            json.put("body", new JSONObject(trimmed));
                        } else if (trimmed.startsWith("[")) {
                            json.put("body", new JSONArray(trimmed));
                        } else {
                            json.put("body", body);
                        }
                    } catch (Exception ex) {
                        json.put("body", body);
                    }
                } else {
                    json.put("body", "");
                }
                Files.write(file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
                reload(true);
            } catch (IOException e) {
                System.err.println("Error saving raw API template " + id + ": " + e.getMessage());
            }
        }
    }

    public void saveTaskTemplate(String name, String label, String text) {
        synchronized (lock) {
            try {
                if (!taskTemplatesDir.exists()) taskTemplatesDir.mkdirs();
                String safeName = sanitizeFileName(name);
                File file = new File(taskTemplatesDir, safeName + ".json");
                JSONObject json = new JSONObject();
                json.put("name", name);
                json.put("label", label != null ? label : name);
                json.put("text", text != null ? text.replace("\\n", "\n") : "");
                Files.write(file.toPath(), json.toString(2).getBytes(StandardCharsets.UTF_8));
                reload(true);
            } catch (IOException e) {
                System.err.println("Error saving task template " + name + ": " + e.getMessage());
            }
        }
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) return "unnamed";
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    public String[] getJqlFilterKeys() {
        synchronized (lock) {
            return jqlFilters.keySet().toArray(new String[0]);
        }
    }

    public String getJqlFilter(String key) {
        synchronized (lock) {
            JqlFilterInfo info = jqlFilters.get(key);
            if (info != null) {
                return info.fields + "|" + info.jql;
            }
            return getProperty("jql_filter." + key);
        }
    }

    public JqlFilterInfo getJqlFilterInfo(String key) {
        synchronized (lock) {
            return jqlFilters.get(key);
        }
    }

    private void startFileWatcher() {
        ExecutionService.submit(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                configDir.toPath().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                if (filtersDir.exists()) {
                    filtersDir.toPath().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                }
                if (apiTemplatesDir.exists()) {
                    apiTemplatesDir.toPath().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                }
                if (taskTemplatesDir.exists()) {
                    taskTemplatesDir.toPath().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
                }

                WatchKey key;
                while ((key = watchService.take()) != null) {
                    boolean trigger = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        String name = event.context().toString().toLowerCase();
                        if (name.endsWith(".properties") || name.endsWith(".json")) {
                            trigger = true;
                        }
                    }
                    if (trigger) {
                        reload();
                    }
                    key.reset();
                }
            } catch (IOException | InterruptedException ignored) {}
        });
    }

    public void reload() {
        reload(false);
    }

    public void reload(boolean force) {
        synchronized (lock) {
            long currentTime = System.currentTimeMillis();
            if (!force && (currentTime - lastReloadTime < RELOAD_DEBOUNCE_MS)) {
                return;
            }
            lastReloadTime = currentTime;
            loadProperties();
        }
        for (ConfigChangeListener listener : listeners) {
            listener.onConfigChanged();
        }
    }

    public String getProperty(String key) {
        if (key == null) return null;
        synchronized (lock) {
            String val = properties.getProperty(key);
            if (val != null) return val;
            return lowerCasePropertyLookup.get(key.toLowerCase());
        }
    }

    public void addConfigChangeListener(ConfigChangeListener listener) {
        listeners.add(listener);
    }

    public void removeConfigChangeListener(ConfigChangeListener listener) {
        listeners.remove(listener);
    }

    public String getUnassignedBacklogAssignee() {
        String assignee = getTeamProperty("unassigned", "lead");
        if (assignee == null) assignee = getProperty("unassigned_backlog_assignee_id");
        if (assignee == null || assignee.trim().isEmpty()) {
            return "LINCOLN.TODD.ALAN";
        }
        return assignee.trim();
    }

    public String getJiraBaseUrl() {
        String url = getProperty("jira_base_url");
        if (url == null || url.trim().isEmpty()) {
            return "https://tso-jira.mcw.usmc.mil";
        }
        return url.trim();
    }

    public String getApiAuthMethod() {
        String method = getProperty("api_auth_method");
        if (method == null || method.trim().isEmpty()) {
            return "mTLS";
        }
        return method.trim();
    }

    public String getApiPatToken() {
        String token = getProperty("api_pat_token");
        return token != null ? token.trim() : "";
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
        synchronized (lock) {
            if (!taskTemplates.isEmpty()) {
                return taskTemplates.keySet().toArray(new String[0]);
            }
            return getKeysByPrefix("template.");
        }
    }

    public String getTemplateLabel(String key) {
        synchronized (lock) {
            TaskTemplateInfo info = taskTemplates.get(key);
            if (info != null) return info.label;
            return getProperty("template." + key + ".label");
        }
    }

    public String getTemplateText(String key) {
        synchronized (lock) {
            TaskTemplateInfo info = taskTemplates.get(key);
            if (info != null) return info.text;
            String text = getProperty("template." + key + ".text");
            return text != null ? text.replace("\\n", "\n") : null;
        }
    }

    public String getLlamaCliPath() {
        String path = getProperty("llama_cli_path");
        if (path == null || path.trim().isEmpty()) {
            return new File(configDir, "bin/llama-cli.exe").getAbsolutePath();
        }
        return path;
    }

    public String getLlamaModelPath() {
        String path = getProperty("llama_model_path");
        if (path == null || path.trim().isEmpty()) {
            return new File(configDir, "models/model.gguf").getAbsolutePath();
        }
        return path;
    }

    public String[] getRawApiTemplateKeys() {
        synchronized (lock) {
            if (!rawApiTemplates.isEmpty()) {
                return rawApiTemplates.keySet().toArray(new String[0]);
            }
            return getKeysByPrefix("api_template.");
        }
    }

    public String getRawApiTemplate(String key) {
        synchronized (lock) {
            RawApiTemplateInfo info = rawApiTemplates.get(key);
            if (info != null) {
                return info.label + "|" + info.method + "|" + info.endpoint + "|" + info.body;
            }
            return getProperty("api_template." + key);
        }
    }

    public RawApiTemplateInfo getRawApiTemplateInfo(String key) {
        synchronized (lock) {
            return rawApiTemplates.get(key);
        }
    }

    public String[] getWorkflowRecipeKeys() {
        tso.usmc.jira.workflow.WorkflowManager wm = new tso.usmc.jira.workflow.WorkflowManager();
        List<String> list = wm.listWorkflows();
        return list.toArray(new String[0]);
    }

    /**
     * Helper to find distinct keys with a given prefix, preserving appearance order.
     */
    private String[] getKeysByPrefix(String prefix) {
        List<String> keys = new ArrayList<>();
        File targetFile = getTargetFileForKey(prefix);

        if (targetFile != null && targetFile.exists()) {
            try {
                List<String> lines = Files.readAllLines(targetFile.toPath(), StandardCharsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    if (!line.startsWith("#") && !line.startsWith(";") && line.startsWith(prefix)) {
                        String fullKey = line.split("=", 2)[0].trim();
                        String remainder = fullKey.substring(prefix.length());
                        String shortKey = remainder.contains(".") ? remainder.split("\\.")[0] : remainder;
                        if (!keys.contains(shortKey)) {
                            keys.add(shortKey);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }

        if (keys.isEmpty()) {
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
        return value != null && Boolean.parseBoolean(value.trim());
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

    public int[] getIspwEnvLvlBounds(int[] defaultBounds) {
        return getIspwColumnBounds("env_lvl", defaultBounds);
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
            return Math.max(1, Math.min(threads, 50));
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
        if (!themesDir.exists()) {
            themesDir.mkdirs();
        }

        if (path == null || path.trim().isEmpty()) {
            File cssFile = new File(themesDir, "custom_theme.css");
            ensureCssFileExists(cssFile);
            return cssFile.getAbsolutePath();
        }

        File cssFile = new File(path.trim());
        if (!cssFile.exists()) {
            cssFile = new File(themesDir, "custom_theme.css");
        }

        // Migration check
        if (cssFile.getParentFile() != null && cssFile.getParentFile().equals(configDir)) {
            File migratedFile = new File(themesDir, cssFile.getName());
            if (!migratedFile.exists() && cssFile.exists()) {
                try {
                    Files.copy(cssFile.toPath(), migratedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {}
            }
            cssFile = migratedFile;
            setThemeCssFilePath(cssFile.getAbsolutePath());
        }

        ensureCssFileExists(cssFile);
        return cssFile.getAbsolutePath();
    }

    public void setThemeCssFilePath(String path) {
        if (path != null) {
            path = path.replace("\\", "/");
        }
        saveProperty("theme_css_file", path);
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
                        List<String> defaultCss = new ArrayList<>();
                        defaultCss.add("/* Custom CSS Stylesheet for USMC TSO Jira Client */");
                        defaultCss.add(".root { -fx-font-family: \"Segoe UI\", Arial, sans-serif; }");
                        Files.write(file.toPath(), defaultCss, StandardCharsets.UTF_8);
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to create default CSS file: " + e.getMessage());
            }
        }
    }

    public List<String> getCiTypes() {
        String prefixes = getProperty("ci_types.prefixes");
        if (prefixes == null) prefixes = getProperty("CI_Types.prefixes");
        if (prefixes == null || prefixes.trim().isEmpty()) {
            return Arrays.asList("COB", "PROC", "JCL", "SYS", "ASM", "COPY", "DMGR", "DCLG", "CMAP");
        }
        List<String> list = new ArrayList<>();
        for (String p : prefixes.split(",")) {
            list.add(p.trim().toUpperCase());
        }
        return list;
    }

    public String getCustomFieldId(String key, String defaultValue) {
        String val = getProperty("custom_fields." + key);
        if (val == null) val = getProperty("Custom_Fields." + key);
        if (val == null || val.trim().isEmpty()) {
            return defaultValue;
        }
        return val.trim();
    }

    public String getCloneProjectKey() {
        String val = getProperty("defaults.clone_project_key");
        if (val == null) val = getProperty("Defaults.clone_project_key");
        if (val == null || val.trim().isEmpty()) {
            return "TFS";
        }
        return val.trim().toUpperCase();
    }

    public List<String> getSubtaskTypes() {
        String types = getProperty("defaults.subtask_types");
        if (types == null) types = getProperty("Defaults.subtask_types");
        if (types == null || types.trim().isEmpty()) {
            return Arrays.asList("Sub-task", "ST-PCU", "ST-Database", "ST-Interface");
        }
        List<String> list = new ArrayList<>();
        for (String t : types.split(",")) {
            list.add(t.trim());
        }
        return list;
    }

    public String getJqlDisplayFields() {
        String val = getProperty("defaults.jql_display_fields");
        if (val == null) val = getProperty("Defaults.jql_display_fields");
        if (val == null || val.trim().isEmpty()) {
            return "key, summary, status, assignee, issuelinks";
        }
        return val.trim();
    }

    public String getJqlDefaultQuery() {
        String val = getProperty("defaults.jql_default_query");
        if (val == null) val = getProperty("Defaults.jql_default_query");
        if (val == null || val.trim().isEmpty()) {
            return "issuetype = Bug AND status = 'To Do' ORDER BY created DESC";
        }
        return val.trim();
    }

    public String getReconciliationParentKeys() {
        String val = getProperty("defaults.reconciliation_parent_keys");
        if (val == null) val = getProperty("Defaults.reconciliation_parent_keys");
        if (val == null || val.trim().isEmpty()) {
            return "TFS-49439\nTFS-35035";
        }
        return val.replace(",", "\n").trim();
    }

    public int getJqlMaxResults() {
        String val = getProperty("jql.max_results");
        if (val == null || val.trim().isEmpty()) {
            return 500;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return 500;
        }
    }

    // Companion Topaz Mainframe Extractor Settings
    public boolean isCompanionTopazEnabled() {
        String val = getProperty("companion.topaz.enabled");
        return val == null || Boolean.parseBoolean(val.trim());
    }

    public String getCompanionTopazPath() {
        String val = getProperty("companion.topaz.path");
        return val != null ? val.trim() : "";
    }

    public String getCompanionTopazAuthMode() {
        String val = getProperty("companion.topaz.auth_mode");
        return val != null && !val.trim().isEmpty() ? val.trim() : "CERTIFICATE";
    }

    public String getCompanionTopazUser() {
        String val = getProperty("companion.topaz.user");
        return val != null ? val.trim() : "";
    }

    public String getCompanionTopazPassword() {
        String val = getProperty("companion.topaz.password");
        return val != null ? val.trim() : "";
    }

    public String getCompanionTopazFetchMode() {
        String val = getProperty("companion.topaz.fetch_mode");
        return val != null && !val.trim().isEmpty() ? val.trim() : "DATASET";
    }

    public String getCompanionTopazDataset() {
        String val = getProperty("companion.topaz.dataset");
        return val != null ? val.trim() : "";
    }

    public String getCompanionTopazJobId() {
        String val = getProperty("companion.topaz.job_id");
        return val != null ? val.trim() : "";
    }

    public String getCompanionTopazJobDd() {
        String val = getProperty("companion.topaz.job_dd");
        return val != null && !val.trim().isEmpty() ? val.trim() : "SORTOUT";
    }

    public String getCompanionTopazJclSource() {
        String val = getProperty("companion.topaz.jcl_source");
        return val != null ? val.trim() : "";
    }

    public int getCompanionTopazTimeoutSec() {
        String val = getProperty("companion.topaz.timeout_sec");
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 120;
    }

    public static void main(String[] args) {
        System.out.println("Testing JiraConfig initialization and migration...");
        JiraConfig config = new JiraConfig();
        System.out.println("Config loaded successfully!");
        System.out.println("Base URL: " + config.getJiraBaseUrl());
        System.out.println("Teams: " + String.join(", ", config.getWorkflowTeamKeys()));
        System.out.println("Raw API templates: " + config.getRawApiTemplateKeys().length);
        System.out.println("Task templates: " + config.getTemplateKeys().length);
        System.out.println("Filters: " + String.join(", ", config.getJqlFilterKeys()));
        System.out.println("Theme: " + config.getTheme());
        System.out.println("Companion Topaz Path: " + config.getCompanionTopazPath());
    }
}
