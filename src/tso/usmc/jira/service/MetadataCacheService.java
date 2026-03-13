package tso.usmc.jira.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified service for fetching and caching Jira metadata (projects, issue types, fields, transitions).
 * Combines logic from JiraMetadataHelper and WorkflowFieldsConfig with in-memory and disk persistence.
 */
public class MetadataCacheService {
    private final JiraApiService apiService;
    private final String baseUrl;
    private final File cacheFile;
    private final Map<String, JSONObject> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastFetchTime = new ConcurrentHashMap<>();
    private static final long DEFAULT_TTL = 3600000; // 1 hour in milliseconds

    public MetadataCacheService(JiraApiService apiService, String baseUrl) {
        this.apiService = apiService;
        this.baseUrl = baseUrl;
        
        String userHome = System.getProperty("user.home");
        File configDir = new File(userHome, ".JiraApiClient");
        if (!configDir.exists()) configDir.mkdirs();
        this.cacheFile = new File(configDir, "metadata_cache.json");
        
        loadFromDisk();
    }

    /**
     * Get metadata for a specific key, fetching from API if not cached or expired.
     */
    private JSONObject getOrFetch(String key, ApiFetcher fetcher) throws Exception {
        long now = System.currentTimeMillis();
        if (cache.containsKey(key) && (now - lastFetchTime.getOrDefault(key, 0L) < DEFAULT_TTL)) {
            return cache.get(key);
        }

        JSONObject data = fetcher.fetch();
        cache.put(key, data);
        lastFetchTime.put(key, now);
        saveToDisk();
        return data;
    }

    private interface ApiFetcher {
        JSONObject fetch() throws Exception;
    }

    // --- High-level Metadata Operations ---

    public Map<String, JSONObject> getEditMetadata(String issueKey) throws Exception {
        String cacheKey = "editmeta:" + issueKey;
        JSONObject json = getOrFetch(cacheKey, () -> {
            String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/editmeta";
            return new JSONObject(apiService.executeRequest(url, "GET", null));
        });

        Map<String, JSONObject> fields = new HashMap<>();
        if (json.has("fields")) {
            JSONObject fieldsJson = json.getJSONObject("fields");
            for (String k : fieldsJson.keySet()) {
                fields.put(k, fieldsJson.getJSONObject(k));
            }
        }
        return fields;
    }

    public List<JSONObject> getTransitions(String issueKey) throws Exception {
        String cacheKey = "transitions:" + issueKey;
        JSONObject json = getOrFetch(cacheKey, () -> {
            String url = baseUrl + "/rest/api/2/issue/" + issueKey + "/transitions?expand=transitions.fields";
            return new JSONObject(apiService.executeRequest(url, "GET", null));
        });

        List<JSONObject> transitions = new ArrayList<>();
        if (json.has("transitions")) {
            JSONArray transArray = json.getJSONArray("transitions");
            for (int i = 0; i < transArray.length(); i++) {
                transitions.add(transArray.getJSONObject(i));
            }
        }
        return transitions;
    }

    public Map<String, JSONObject> getCreateMetadata(String projectKey, String issueTypeName) throws Exception {
        String cacheKey = "createmeta:" + projectKey + ":" + issueTypeName;
        JSONObject json = getOrFetch(cacheKey, () -> {
            // Step 1: Get issue type ID
            String typesUrl = baseUrl + "/rest/api/2/issue/createmeta/" + projectKey + "/issuetypes";
            JSONObject typesJson = new JSONObject(apiService.executeRequest(typesUrl, "GET", null));
            String typeId = null;
            if (typesJson.has("values")) {
                JSONArray values = typesJson.getJSONArray("values");
                for (int i = 0; i < values.length(); i++) {
                    JSONObject type = values.getJSONObject(i);
                    if (type.getString("name").equalsIgnoreCase(issueTypeName)) {
                        typeId = type.getString("id");
                        break;
                    }
                }
            }
            if (typeId == null) throw new Exception("Issue type '" + issueTypeName + "' not found.");

            // Step 2: Get fields
            String fieldsUrl = baseUrl + "/rest/api/2/issue/createmeta/" + projectKey + "/issuetypes/" + typeId;
            return new JSONObject(apiService.executeRequest(fieldsUrl, "GET", null));
        });

        Map<String, JSONObject> fields = new HashMap<>();
        if (json.has("values")) {
            JSONArray values = json.getJSONArray("values");
            for (int i = 0; i < values.length(); i++) {
                JSONObject f = values.getJSONObject(i);
                if (f.has("fieldId")) fields.put(f.getString("fieldId"), f);
            }
        }
        return fields;
    }

    public List<String> getProjectKeys() throws Exception {
        String cacheKey = "projects:all";
        JSONObject json = getOrFetch(cacheKey, () -> {
            String resp = apiService.executeRequest(baseUrl + "/rest/api/2/project", "GET", null);
            return new JSONObject().put("projects", new JSONArray(resp));
        });

        List<String> keys = new ArrayList<>();
        JSONArray arr = json.getJSONArray("projects");
        for (int i = 0; i < arr.length(); i++) {
            keys.add(arr.getJSONObject(i).getString("key"));
        }
        return keys;
    }

    // --- Persistence ---

    private synchronized void saveToDisk() {
        try (PrintWriter out = new PrintWriter(new FileWriter(cacheFile))) {
            JSONObject root = new JSONObject();
            JSONObject cacheData = new JSONObject();
            JSONObject timesData = new JSONObject();
            
            for (String key : cache.keySet()) {
                cacheData.put(key, cache.get(key));
                timesData.put(key, lastFetchTime.get(key));
            }
            
            root.put("cache", cacheData);
            root.put("lastFetchTime", timesData);
            out.print(root.toString(4));
        } catch (IOException e) {
            System.err.println("Failed to save metadata cache: " + e.getMessage());
        }
    }

    private synchronized void loadFromDisk() {
        if (!cacheFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(cacheFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            
            JSONObject root = new JSONObject(sb.toString());
            if (root.has("cache") && root.has("lastFetchTime")) {
                JSONObject cacheData = root.getJSONObject("cache");
                JSONObject timesData = root.getJSONObject("lastFetchTime");
                
                for (String key : cacheData.keySet()) {
                    cache.put(key, cacheData.getJSONObject(key));
                    lastFetchTime.put(key, timesData.getLong(key));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load metadata cache: " + e.getMessage());
        }
    }

    public void clearCache() {
        cache.clear();
        lastFetchTime.clear();
        if (cacheFile.exists()) cacheFile.delete();
    }
}
