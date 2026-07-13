package tso.usmc.jira.service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import tso.usmc.jira.util.JiraApiException;
import tso.usmc.jira.util.JiraConfig;

public class JiraApiService {
    private final JiraConfig config;
    private String currentAlias;
    private SSLContext sslContext;
    private boolean loggingEnabled = false;

    public JiraApiService(JiraConfig config, String selectedAlias) throws Exception {
        this.config = config;
        this.currentAlias = selectedAlias;
        this.sslContext = createSslContext(selectedAlias);
    }

    public void updateSslContext(String selectedAlias) throws Exception {
        if ((selectedAlias == null && this.currentAlias != null) || 
            (selectedAlias != null && !selectedAlias.equals(this.currentAlias))) {
            this.currentAlias = selectedAlias;
            this.sslContext = createSslContext(selectedAlias);
        }
    }

    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
    }

    public String executeRequest(String urlString, String method, String jsonBody) throws JiraApiException {
        int maxRetries = 5;
        int attempt = 0;
        long waitTime = 2000; // Start with 2s default wait

        while (true) {
            attempt++;
            try {
                return executeRequestInternal(urlString, method, jsonBody);
            } catch (RateLimitException e) {
                if (attempt >= maxRetries) {
                    throw new JiraApiException("Jira API Rate Limit exceeded. Failed after " + maxRetries + " attempts.", e);
                }
                
                long sleepTime = e.getRetryAfterSeconds() > 0 ? e.getRetryAfterSeconds() * 1000L : waitTime;
                String retryMsg = "[RATE LIMIT] Attempt " + attempt + " failed. Retrying in " + (sleepTime / 1000.0) + " seconds...";
                System.out.println(retryMsg);
                if (loggingEnabled) appendToFile("\n" + retryMsg + "\n");
                
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new JiraApiException("API request interrupted during rate limit backoff", ie);
                }
                waitTime *= 2; // Exponential backoff for next time
            } catch (JiraApiException e) {
                boolean isNetworkOrSslError = e.getCause() instanceof IOException;
                String authMethod = config.getApiAuthMethod();
                boolean useCert = "mTLS".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);
                
                if (isNetworkOrSslError && useCert && attempt < 3) {
                    String retryMsg = "[SSL/NETWORK ERROR] Connection error: " + e.getMessage() + ". Re-initializing SSL Context (attempt " + attempt + ")...";
                    System.err.println(retryMsg);
                    if (loggingEnabled) appendToFile("\n" + retryMsg + "\n");
                    try {
                        this.sslContext = createSslContext(this.currentAlias);
                    } catch (Exception ex) {
                        System.err.println("Failed to re-initialize SSL Context: " + ex.getMessage());
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new JiraApiException("API request interrupted during SSL recovery backoff", ie);
                    }
                    continue;
                }
                throw e;
            }
        }
    }

    private String executeRequestInternal(String urlString, String method, String jsonBody) throws JiraApiException, RateLimitException {
        if (loggingEnabled) {
            String logMsg = "\n[" + new java.util.Date() + "] [API REQUEST] " + method + " " + urlString + "\n";
            if (jsonBody != null) {
                logMsg += "[API REQUEST BODY]\n" + jsonBody + "\n";
            }
            appendToFile(logMsg);
        }

        try {
            URL url = new URL(urlString);
            URLConnection urlConn = url.openConnection();
            HttpURLConnection conn;
            if (urlConn instanceof HttpsURLConnection) {
                conn = (HttpsURLConnection) urlConn;
                ((HttpsURLConnection) conn).setSSLSocketFactory(this.sslContext.getSocketFactory());
            } else {
                conn = (HttpURLConnection) urlConn;
            }
            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            String authMethod = config.getApiAuthMethod();
            boolean sendPat = "PAT".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);
            if (sendPat) {
                String patToken = config.getApiPatToken();
                if (patToken != null && !patToken.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + patToken);
                }
            }

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
                throw new JiraApiException("Jira API request failed with code " + code, code, response);
            }
            return response;
        } catch (IOException e) {
            throw new JiraApiException("Network error during Jira API call: " + e.getMessage(), e);
        }
    }

    private static class RateLimitException extends Exception {
        private final int retryAfterSeconds;
        public RateLimitException(String message, int retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public int getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    public String getJqlAutoCompleteData(String baseUrl) throws JiraApiException {
        String url = baseUrl + "/rest/api/2/jql/autocompletedata";
        return executeRequest(url, "GET", null);
    }

    public String getJqlSuggestions(String baseUrl, String fieldName, String fieldValue) throws JiraApiException, UnsupportedEncodingException {
        String url = baseUrl + "/rest/api/2/jql/autocompletedata/suggestions?fieldName=" + 
                     java.net.URLEncoder.encode(fieldName, "UTF-8") + 
                     "&fieldValue=" + java.net.URLEncoder.encode(fieldValue, "UTF-8");
        return executeRequest(url, "GET", null);
    }

    public String searchUsers(String baseUrl, String query) throws JiraApiException, UnsupportedEncodingException {
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

    public File downloadAttachmentToTempFile(String fileUrl, String originalFilename) throws JiraApiException {
        if (loggingEnabled) appendToFile("\n[" + new java.util.Date() + "] [API ATTACHMENT DOWNLOAD] " + fileUrl);
        int maxRetries = 3;
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                URL downloadUrl = new URL(fileUrl);
                URLConnection urlConn = downloadUrl.openConnection();
                HttpURLConnection dlConn;
                if (urlConn instanceof HttpsURLConnection) {
                    dlConn = (HttpsURLConnection) urlConn;
                    ((HttpsURLConnection) dlConn).setSSLSocketFactory(this.sslContext.getSocketFactory());
                } else {
                    dlConn = (HttpURLConnection) urlConn;
                }
                
                String authMethod = config.getApiAuthMethod();
                boolean sendPat = "PAT".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);
                if (sendPat) {
                    String patToken = config.getApiPatToken();
                    if (patToken != null && !patToken.isEmpty()) {
                        dlConn.setRequestProperty("Authorization", "Bearer " + patToken);
                    }
                }
                
                File tempFile = File.createTempFile("jira-attachment-", ".tmp");
                try (InputStream in = dlConn.getInputStream(); FileOutputStream out = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                if (loggingEnabled) appendToFile("[API ATTACHMENT DOWNLOAD] Success: " + originalFilename + " -> " + tempFile.getAbsolutePath());
                return tempFile;
            } catch (IOException e) {
                String authMethod = config.getApiAuthMethod();
                boolean useCert = "mTLS".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);
                if (useCert && attempt < maxRetries) {
                    System.err.println("[SSL/NETWORK ERROR] Attachment download failed: " + e.getMessage() + ". Re-initializing SSL Context...");
                    try {
                        this.sslContext = createSslContext(this.currentAlias);
                    } catch (Exception ex) {}
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                throw new JiraApiException("Failed to download attachment: " + originalFilename, e);
            }
        }
    }

    public String uploadAttachment(String urlString, File fileToUpload, String originalFilename) throws JiraApiException {
        if (loggingEnabled) appendToFile("\n[" + new java.util.Date() + "] [API ATTACHMENT UPLOAD] POST " + urlString + " (File: " + originalFilename + ")");
        String boundary = "---" + System.currentTimeMillis() + "---";
        int maxRetries = 3;
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                URL url = new URL(urlString);
                URLConnection urlConn = url.openConnection();
                HttpURLConnection conn;
                if (urlConn instanceof HttpsURLConnection) {
                    conn = (HttpsURLConnection) urlConn;
                    ((HttpsURLConnection) conn).setSSLSocketFactory(this.sslContext.getSocketFactory());
                } else {
                    conn = (HttpURLConnection) urlConn;
                }
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("X-Atlassian-Token", "no-check");

                String authMethod = config.getApiAuthMethod();
                boolean sendPat = "PAT".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);
                if (sendPat) {
                    String patToken = config.getApiPatToken();
                    if (patToken != null && !patToken.isEmpty()) {
                        conn.setRequestProperty("Authorization", "Bearer " + patToken);
                    }
                }

                try (OutputStream os = conn.getOutputStream(); FileInputStream fis = new FileInputStream(fileToUpload)) {
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
                    throw new JiraApiException("Jira API upload failed with code " + code, code, response);
                }
                return response;
            } catch (IOException e) {
                String authMethod = config.getApiAuthMethod();
                boolean useCert = "mTLS".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);
                if (useCert && attempt < maxRetries) {
                    System.err.println("[SSL/NETWORK ERROR] Attachment upload failed: " + e.getMessage() + ". Re-initializing SSL Context...");
                    try {
                        this.sslContext = createSslContext(this.currentAlias);
                    } catch (Exception ex) {}
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                throw new JiraApiException("Network error during attachment upload", e);
            }
        }
    }

    private SSLContext createSslContext(final String alias) throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        SSLContext ctx = SSLContext.getInstance("TLSv1.2");

        if (alias != null && !alias.trim().isEmpty()) {
            KeyStore identityStore = KeyStore.getInstance("Windows-MY", "SunMSCAPI");
            identityStore.load(null, null);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(identityStore, null);

            KeyManager[] kms = kmf.getKeyManagers();
            if (kms != null && kms.length > 0 && kms[0] instanceof X509KeyManager) {
                final X509KeyManager originalKeyManager = (X509KeyManager) kms[0];
                X509KeyManager customKeyManager = new X509KeyManager() {
                    @Override public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) { return alias; }
                    @Override public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) { return originalKeyManager.chooseServerAlias(keyType, issuers, socket); }
                    @Override public X509Certificate[] getCertificateChain(String alias) { return originalKeyManager.getCertificateChain(alias); }
                    @Override public String[] getClientAliases(String keyType, Principal[] issuers) { return originalKeyManager.getClientAliases(keyType, issuers); }
                    @Override public PrivateKey getPrivateKey(String alias) { return originalKeyManager.getPrivateKey(alias); }
                    @Override public String[] getServerAliases(String keyType, Principal[] issuers) { return originalKeyManager.getServerAliases(keyType, issuers); }
                };
                ctx.init(new KeyManager[]{customKeyManager}, trustAllCerts, new SecureRandom());
                return ctx;
            }
        }

        ctx.init(null, trustAllCerts, new SecureRandom());
        return ctx;
    }
}
