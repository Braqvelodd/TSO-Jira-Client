package tso.usmc.jira.service;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

public class JiraApiService {
    private SSLContext sslContext;
    private boolean loggingEnabled = false;

    public JiraApiService(String selectedAlias) throws Exception {
        this.sslContext = createSslContext(selectedAlias);
    }

    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
    }

    public String executeRequest(String urlString, String method, String jsonBody) throws Exception {
        int maxRetries = 5;
        int attempt = 0;
        long waitTime = 2000; // Start with 2s default wait

        while (true) {
            attempt++;
            try {
                return executeRequestInternal(urlString, method, jsonBody);
            } catch (RateLimitException e) {
                if (attempt >= maxRetries) {
                    throw new Exception("Jira API Rate Limit exceeded. Failed after " + maxRetries + " attempts. Last error: " + e.getMessage());
                }
                
                long sleepTime = e.getRetryAfterSeconds() > 0 ? e.getRetryAfterSeconds() * 1000L : waitTime;
                String retryMsg = "[RATE LIMIT] Attempt " + attempt + " failed. Retrying in " + (sleepTime / 1000.0) + " seconds...";
                System.out.println(retryMsg);
                if (loggingEnabled) appendToFile("\n" + retryMsg + "\n");
                
                Thread.sleep(sleepTime);
                waitTime *= 2; // Exponential backoff for next time
            }
        }
    }

    private String executeRequestInternal(String urlString, String method, String jsonBody) throws Exception {
        if (loggingEnabled) {
            String logMsg = "\n[" + new java.util.Date() + "] [API REQUEST] " + method + " " + urlString + "\n";
            if (jsonBody != null) {
                logMsg += "[API REQUEST BODY]\n" + jsonBody + "\n";
            }
            appendToFile(logMsg);
        }

        URL url = new URL(urlString);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(this.sslContext.getSocketFactory());
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

        if (("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) && jsonBody != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }
        }

        int code = conn.getResponseCode();
        
        if (code == 429) {
            int retryAfter = conn.getHeaderFieldInt("Retry-After", -1);
            throw new RateLimitException("Rate limit hit", retryAfter);
        }

        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }
        }

        String response = sb.toString();
        if (loggingEnabled) {
            String respLog = "[API RESPONSE CODE] " + code + "\n";
            if (response != null && !response.isEmpty()) {
                respLog += "[API RESPONSE BODY]\n" + response + "\n";
            }
            appendToFile(respLog);
        }

        if (code >= 300) {
            throw new Exception("Jira API request failed with code " + code + ": " + response);
        }
        return response;
    }

    private static class RateLimitException extends Exception {
        private final int retryAfterSeconds;
        public RateLimitException(String message, int retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public int getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    public String getJqlAutoCompleteData(String baseUrl) throws Exception {
        String url = baseUrl + "/rest/api/2/jql/autocompletedata";
        return executeRequest(url, "GET", null);
    }

    public String getJqlSuggestions(String baseUrl, String fieldName, String fieldValue) throws Exception {
        String url = baseUrl + "/rest/api/2/jql/autocompletedata/suggestions?fieldName=" + 
                     java.net.URLEncoder.encode(fieldName, "UTF-8") + 
                     "&fieldValue=" + java.net.URLEncoder.encode(fieldValue, "UTF-8");
        return executeRequest(url, "GET", null);
    }

    public String searchUsers(String baseUrl, String query) throws Exception {
        String url = baseUrl + "/rest/api/2/user/search?username=" + java.net.URLEncoder.encode(query, "UTF-8");
        return executeRequest(url, "GET", null);
    }

    private void appendToFile(String msg) {
        if (!loggingEnabled) return;
        try {
            String userHome = System.getProperty("user.home");
            File logDir = new File(userHome, ".JiraApiClient/logs");
            if (!logDir.exists()) logDir.mkdirs();
            
            String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
            File logFile = new File(logDir, "jira_api_" + dateStr + ".log");
            
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(msg);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public File downloadAttachmentToTempFile(String fileUrl, String originalFilename) throws Exception {
        if (loggingEnabled) appendToFile("\n[" + new java.util.Date() + "] [API ATTACHMENT DOWNLOAD] " + fileUrl);
        URL downloadUrl = new URL(fileUrl);
        HttpsURLConnection dlConn = (HttpsURLConnection) downloadUrl.openConnection();
        
        // Use the SSLContext that was configured when this service was created.
        dlConn.setSSLSocketFactory(this.sslContext.getSocketFactory());
        String suffix = ".tmp"; // Default fallback
            int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
           suffix = originalFilename.substring(dotIndex);
        }
        // Create a temporary file to store the download.
        File tempFile = File.createTempFile("jira-attachment-", ".tmp");
        
        // Use try-with-resources to ensure streams are closed automatically.
        try (InputStream in = dlConn.getInputStream(); FileOutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192]; // Use a slightly larger buffer
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        if (loggingEnabled) appendToFile("[API ATTACHMENT DOWNLOAD] Success: " + originalFilename + " -> " + tempFile.getAbsolutePath());
        
        // Return the handle to the downloaded temporary file.
        return tempFile;
    }
    public String uploadAttachment(String urlString, File fileToUpload, String originalFilename) throws Exception {
        if (loggingEnabled) appendToFile("\n[" + new java.util.Date() + "] [API ATTACHMENT UPLOAD] POST " + urlString + " (File: " + originalFilename + ")");
        String boundary = "---" + System.currentTimeMillis() + "---";
        URL url = new URL(urlString);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        conn.setSSLSocketFactory(this.sslContext.getSocketFactory());
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        
        // Set headers for multipart form data
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("X-Atlassian-Token", "no-check"); // Required for API uploads

        try (OutputStream os = conn.getOutputStream(); FileInputStream fis = new FileInputStream(fileToUpload)) {
            // Write the file part
            os.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
            os.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + originalFilename + "\"\r\n").getBytes("UTF-8"));
            os.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes("UTF-8"));

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
            
            os.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
        }

        String response = sb.toString();
        if (loggingEnabled) appendToFile("[API RESPONSE CODE] " + code + "\n[API RESPONSE BODY]\n" + response);

        if (code >= 300) {
            throw new Exception("Jira API request failed with code " + code + ": " + response);
        }

        return response;
    }

    private SSLContext createSslContext(final String alias) throws Exception {

    KeyStore identityStore = KeyStore.getInstance("Windows-MY", "SunMSCAPI");
    identityStore.load(null, null);

    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(identityStore, null);
    final X509KeyManager originalKeyManager = (X509KeyManager) kmf.getKeyManagers()[0];

    X509KeyManager customKeyManager = new X509KeyManager() {
        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
            return alias; // Always choose the alias provided.
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
            return originalKeyManager.chooseServerAlias(keyType, issuers, socket);
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return originalKeyManager.getCertificateChain(alias);
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return originalKeyManager.getClientAliases(keyType, issuers);
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return originalKeyManager.getPrivateKey(alias);
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return originalKeyManager.getServerAliases(keyType, issuers);
        }
    }; // <-- The missing semicolon likely went here!

    TrustManager[] trustAllCerts = new TrustManager[] {
        new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null; // Trust all issuers
            }
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                // Do nothing
            }
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                // Do nothing
            }
        }
    };

    SSLContext ctx = SSLContext.getInstance("TLSv1.2");
    ctx.init(new KeyManager[]{customKeyManager}, trustAllCerts, new SecureRandom());
    return ctx;
}
}