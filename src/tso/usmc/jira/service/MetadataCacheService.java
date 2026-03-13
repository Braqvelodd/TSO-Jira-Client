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

    public List<JSONObject> getIssueTypesForProject(String projectKey) throws Exception {
        String cacheKey = "issuetypes:" + projectKey;
        JSONObject json = getOrFetch(cacheKey, () -> {
            String url = baseUrl + "/rest/api/2/issue/createmeta/" + projectKey + "/issuetypes";
            return new JSONObject(apiService.executeRequest(url, "GET", null));
        });

        List<JSONObject> types = new ArrayList<>();
        if (json.has("values")) {
            JSONArray values = json.getJSONArray("values");
            for (int i = 0; i < values.length(); i++) {
                types.add(values.getJSONObject(i));
            }
        }
        return types;
    }

    /**
     * Fetches creation metadata for specific project and issue type.
     * Supports pagination for fields.
     */
    public Map<String, JSONObject> getCreateMetadata(String projectKey, String issueTypeName) throws Exception {
        String cacheKey = "createmeta:" + projectKey + ":" + issueTypeName;
        
        // Find the type ID first
        List<JSONObject> types = getIssueTypesForProject(projectKey);
        String typeId = null;
        for (JSONObject type : types) {
            if (type.getString("name").equalsIgnoreCase(issueTypeName)) {
                typeId = type.getString("id");
                break;
            }
        }
        if (typeId == null) throw new Exception("Issue type '" + issueTypeName + "' not found in " + projectKey);

        final String finalTypeId = typeId;
        JSONObject json = getOrFetch(cacheKey, () -> {
            // Paginated fetch of fields
            JSONArray allValues = new JSONArray();
            int startAt = 0;
            boolean isLast = false;
            
            do {
                String fieldsUrl = baseUrl + "/rest/api/2/issue/createmeta/" + projectKey + "/issuetypes/" + finalTypeId + "?startAt=" + startAt;
                JSONObject page = new JSONObject(apiService.executeRequest(fieldsUrl, "GET", null));
                
                if (page.has("values")) {
                    JSONArray values = page.getJSONArray("values");
                    for (int i = 0; i < values.length(); i++) {
                        allValues.put(values.get(i));
                    }
                    startAt += values.length();
                }
                
                isLast = page.optBoolean("isLast", true);
                // If total is present and we've hit it, we're done
                if (page.has("total") && startAt >= page.getInt("total")) isLast = true;
                // Safety break for older Jira versions that might not support pagination params but return all
                if (!page.has("values") || page.getJSONArray("values").length() == 0) isLast = true;

            } while (!isLast);

            return new JSONObject().put("values", allValues);
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

    public List<JSONObject> getIssueLinkTypes() throws Exception {
        String cacheKey = "linktypes:all";
        JSONObject json = getOrFetch(cacheKey, () -> {
            String url = baseUrl + "/rest/api/2/issueLinkType";
            return new JSONObject(apiService.executeRequest(url, "GET", null));
        });

        List<JSONObject> types = new ArrayList<>();
        if (json.has("issueLinkTypes")) {
            JSONArray arr = json.getJSONArray("issueLinkTypes");
            for (int i = 0; i < arr.length(); i++) {
                types.add(arr.getJSONObject(i));
            }
        }
        return types;
    }

    public List<JSONObject> getAllFields() throws Exception {
        String cacheKey = "fields:all";
        JSONObject json = getOrFetch(cacheKey, () -> {
            String url = baseUrl + "/rest/api/2/field";
            String resp = apiService.executeRequest(url, "GET", null);
            return new JSONObject().put("fields", new JSONArray(resp));
        });

        List<JSONObject> fields = new ArrayList<>();
        if (json.has("fields")) {
            JSONArray arr = json.getJSONArray("fields");
            for (int i = 0; i < arr.length(); i++) {
                fields.add(arr.getJSONObject(i));
            }
        }
        return fields;
    }

    public List<JSONObject> getIssueLinkTypes() throws Exception {
        String cacheKey = "linktypes:all";
        JSONObject json = getOrFetch(cacheKey, () -> {
            String url = baseUrl + "/rest/api/2/issueLinkType";
            return new JSONObject(apiService.executeRequest(url, "GET", null));
        });

        List<JSONObject> types = new ArrayList<>();
        if (json.has("issueLinkTypes")) {
            JSONArray arr = json.getJSONArray("issueLinkTypes");
            for (int i = 0; i < arr.length(); i++) {
                types.add(arr.getJSONObject(i));
            }
        }
        return types;
    }

    public List<JSONObject> getAllFields() throws Exception {
        String cacheKey = "fields:all";
        JSONObject json = getOrFetch(cacheKey, () -> {
            String url = baseUrl + "/rest/api/2/field";
            String resp = apiService.executeRequest(url, "GET", null);
            return new JSONObject().put("fields", new JSONArray(resp));
        });

        List<JSONObject> fields = new ArrayList<>();
        if (json.has("fields")) {
            JSONArray arr = json.getJSONArray("fields");
            for (int i = 0; i < arr.length(); i++) {
                fields.add(arr.getJSONObject(i));
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
                    if (cacheData.get(key) instanceof JSONObject) {
                        cache.put(key, cacheData.getJSONObject(key));
                        lastFetchTime.put(key, timesData.getLong(key));
                    }
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
