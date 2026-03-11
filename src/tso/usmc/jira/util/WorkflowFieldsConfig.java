package tso.usmc.jira.util;

import org.json.JSONObject;
import java.io.*;
import java.util.*;

/**
 * Manages the workflowfields.ini file which stores a global cache of Jira field metadata.
 * This avoids bloating individual workflow recipes with redundant metadata.
 */
public class WorkflowFieldsConfig {
    private final File fieldsFile;
    private final Map<String, JSONObject> fieldMetadata = new HashMap<>();
    private final Object lock = new Object();

    public WorkflowFieldsConfig() {
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, ".JiraApiClient");
        this.fieldsFile = new File(configDir, "workflowfields.ini");
        load();
    }

    public void load() {
        synchronized (lock) {
            fieldMetadata.clear();
            if (!fieldsFile.exists()) return;

            try (BufferedReader reader = new BufferedReader(new FileReader(fieldsFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    int eqIdx = line.indexOf('=');
                    if (eqIdx > 0) {
                        String fieldId = line.substring(0, eqIdx).trim();
                        String jsonStr = line.substring(eqIdx + 1).trim();
                        try {
                            fieldMetadata.put(fieldId, new JSONObject(jsonStr));
                        } catch (Exception e) {
                            System.err.println("Error parsing metadata for field " + fieldId + ": " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Error loading workflowfields.ini: " + e.getMessage());
            }
        }
    }

    public void save() {
        synchronized (lock) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(fieldsFile))) {
                writer.write("# Global Jira Field Metadata Cache\n");
                writer.write("# Format: fieldId = JSON_STRING\n\n");

                List<String> sortedKeys = new ArrayList<>(fieldMetadata.keySet());
                Collections.sort(sortedKeys);

                for (String key : sortedKeys) {
                    JSONObject meta = fieldMetadata.get(key);
                    writer.write(key + " = " + meta.toString() + "\n");
                }
            } catch (IOException e) {
                System.err.println("Error saving workflowfields.ini: " + e.getMessage());
            }
        }
    }

    public Map<String, JSONObject> getFieldMetadata() {
        synchronized (lock) {
            return new HashMap<>(fieldMetadata);
        }
    }

    public void updateMetadata(Map<String, JSONObject> newMeta) {
        synchronized (lock) {
            fieldMetadata.putAll(newMeta);
            save();
        }
    }

    public void replaceMetadata(Map<String, JSONObject> newMeta) {
        synchronized (lock) {
            fieldMetadata.clear();
            fieldMetadata.putAll(newMeta);
            save();
        }
    }

    public void addFieldMetadata(String fieldId, JSONObject meta) {
        synchronized (lock) {
            fieldMetadata.put(fieldId, meta);
            save();
        }
    }
}
