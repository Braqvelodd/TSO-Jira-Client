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
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.*;
import tso.usmc.jira.util.JiraApiException;
import tso.usmc.jira.util.JiraConfig;

public class JiraApiService {
    private final JiraConfig config;
    private volatile String currentAlias;
    private volatile SSLContext sslContext;
    private boolean loggingEnabled = false;

    private Supplier<String> aliasSupplier;
    private Consumer<String> onAliasRefreshed;
    private Consumer<String> statusListener;
    private Runnable certReloader;

    public JiraApiService(JiraConfig config, String selectedAlias) throws Exception {
        this.config = config;
        this.currentAlias = selectedAlias;
        this.sslContext = createSslContext(selectedAlias);
    }

    public synchronized void updateSslContext(String selectedAlias) throws Exception {
        updateSslContext(selectedAlias, false);
    }

    public synchronized void updateSslContext(String selectedAlias, boolean force) throws Exception {
        if (force || (selectedAlias == null && this.currentAlias != null) || 
            (selectedAlias != null && !selectedAlias.equals(this.currentAlias)) ||
            this.sslContext == null) {
            this.currentAlias = selectedAlias;
            this.sslContext = createSslContext(selectedAlias);
        }
    }

    public synchronized void refreshSslContext() throws Exception {
        if (certReloader != null) {
            try { certReloader.run(); } catch (Exception ignored) {}
        }
        String targetAlias = this.currentAlias;
        if (aliasSupplier != null) {
            String supplied = aliasSupplier.get();
            if (supplied != null && !supplied.trim().isEmpty()) {
                targetAlias = supplied;
            }
        }
        this.currentAlias = targetAlias;
        this.sslContext = createSslContext(targetAlias);
    }

    public String getCurrentAlias() {
        return this.currentAlias;
    }

    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
    }

    public void setAliasSupplier(Supplier<String> aliasSupplier) {
        this.aliasSupplier = aliasSupplier;
    }

    public void setOnAliasRefreshed(Consumer<String> onAliasRefreshed) {
        this.onAliasRefreshed = onAliasRefreshed;
    }

    public void setStatusListener(Consumer<String> statusListener) {
        this.statusListener = statusListener;
    }

    public void setCertReloader(Runnable certReloader) {
        this.certReloader = certReloader;
    }

    public String executeRequest(String urlString, String method, String jsonBody) throws JiraApiException {
        int maxRetries = 5;
        int maxAuthRetries = 2; // Allow up to 2 recovery attempts on 401 or network/SSL failure
        int attempt = 0;
        int authAttempts = 0;
        long waitTime = 2000; // Start with 2s default wait for rate limit
        boolean forceNewConnection = false;

        while (true) {
            attempt++;
            try {
                String response = executeRequestInternal(urlString, method, jsonBody, forceNewConnection);
                if (authAttempts > 0 && statusListener != null) {
                    try {
                        statusListener.accept("Reconnected successfully.");
                    } catch (Exception ignored) {}
                }
                return response;
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
                int statusCode = e.getStatusCode();
                boolean isAuthError = (statusCode == 401);
                boolean isNetworkOrSslError = (e.getCause() instanceof IOException);
                
                String authMethod = config.getApiAuthMethod();
                boolean useCert = "mTLS".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);
                
                if ((isAuthError || isNetworkOrSslError) && authAttempts < maxAuthRetries) {
                    authAttempts++;
                    forceNewConnection = true;
                    
                    String reason = isAuthError ? "HTTP 401 Unauthorized" : "Connection/SSL error (" + e.getMessage() + ")";
                    String retryMsg = "[AUTO-RECONNECT] " + reason + ". Session or certificates may have gone stale. Refreshing credentials and SSL context (attempt " + authAttempts + " of " + maxAuthRetries + ")...";
                    System.err.println(retryMsg);
                    if (loggingEnabled) appendToFile("\n" + retryMsg + "\n");
                    
                    if (statusListener != null) {
                        try {
                            statusListener.accept("Reconnecting to Jira (refreshing credentials)...");
                        } catch (Exception ignored) {}
                    }
                    
                    if (useCert) {
                        try {
                            refreshSslContext();
                        } catch (Exception ex) {
                            System.err.println("[AUTO-RECONNECT] Failed to refresh SSL Context: " + ex.getMessage());
                            if (loggingEnabled) appendToFile("[AUTO-RECONNECT] Failed to refresh SSL Context: " + ex.getMessage() + "\n");
                        }
                    }
                    
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new JiraApiException("API request interrupted during reconnection backoff", ie);
                    }
                    continue;
                }
                
                if (authAttempts > 0 && statusListener != null) {
                    try {
                        statusListener.accept("Authentication failed after reconnect attempts.");
                    } catch (Exception ignored) {}
                }
                throw e;
            }
        }
    }

    public String executeRequestInternal(String urlString, String method, String jsonBody) throws JiraApiException, RateLimitException {
        return executeRequestInternal(urlString, method, jsonBody, false);
    }

    private String executeRequestInternal(String urlString, String method, String jsonBody, boolean forceNewConnection) throws JiraApiException, RateLimitException {
        if (loggingEnabled) {
            String logMsg = "\n[" + new java.util.Date() + "] [API REQUEST] " + method + " " + urlString + "\n";
            if (jsonBody != null) {
                logMsg += "[API REQUEST BODY]\n" + jsonBody + "\n";
            }
            appendToFile(logMsg);
        }

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            URLConnection urlConn = url.openConnection();
            if (urlConn instanceof HttpsURLConnection) {
                conn = (HttpsURLConnection) urlConn;
                ((HttpsURLConnection) conn).setSSLSocketFactory(this.sslContext.getSocketFactory());
            } else {
                conn = (HttpURLConnection) urlConn;
            }

            int timeoutMs = config.getApiTimeoutSeconds() * 1000;
            if (timeoutMs <= 0) timeoutMs = 30000;
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);

            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");

            if (forceNewConnection) {
                conn.setRequestProperty("Connection", "close");
            }

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

            if (code == 401) {
                String errorMsg = extractAuthDiagnostic(conn, "Jira API request failed with code 401 (Unauthorized)");
                try { conn.disconnect(); } catch (Exception ignored) {}
                throw new JiraApiException(errorMsg, 401, response);
            }

            if (code >= 300) {
                throw new JiraApiException("Jira API request failed with code " + code, code, response);
            }
            return response;
        } catch (IOException e) {
            if (conn != null) {
                try { conn.disconnect(); } catch (Exception ignored) {}
            }
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

    private static String extractAuthDiagnostic(HttpURLConnection conn, String defaultMessage) {
        if (conn == null) return defaultMessage;
        try {
            String seraphReason = conn.getHeaderField("X-Seraph-LoginReason");
            String deniedReason = conn.getHeaderField("X-Authentication-Denied-Reason");
            String wwwAuth = conn.getHeaderField("WWW-Authenticate");
            List<String> details = new java.util.ArrayList<>();
            if (seraphReason != null && !seraphReason.trim().isEmpty()) {
                details.add("Seraph: " + seraphReason.trim());
            }
            if (deniedReason != null && !deniedReason.trim().isEmpty()) {
                details.add("Denied Reason: " + deniedReason.trim());
            }
            if (wwwAuth != null && !wwwAuth.trim().isEmpty()) {
                details.add("Challenge: " + wwwAuth.trim());
            }
            if (!details.isEmpty()) {
                return defaultMessage + " [" + String.join(", ", details) + "]";
            }
        } catch (Exception ignored) {}
        return defaultMessage;
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
        int authAttempts = 0;
        boolean forceNewConnection = false;

        while (true) {
            attempt++;
            HttpURLConnection dlConn = null;
            try {
                URL downloadUrl = new URL(fileUrl);
                URLConnection urlConn = downloadUrl.openConnection();
                if (urlConn instanceof HttpsURLConnection) {
                    dlConn = (HttpsURLConnection) urlConn;
                    ((HttpsURLConnection) dlConn).setSSLSocketFactory(this.sslContext.getSocketFactory());
                } else {
                    dlConn = (HttpURLConnection) urlConn;
                }
                
                int timeoutMs = config.getApiTimeoutSeconds() * 1000;
                if (timeoutMs <= 0) timeoutMs = 30000;
                dlConn.setConnectTimeout(timeoutMs);
                dlConn.setReadTimeout(timeoutMs);

                if (forceNewConnection) {
                    dlConn.setRequestProperty("Connection", "close");
                }

                String authMethod = config.getApiAuthMethod();
                boolean sendPat = "PAT".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);
                if (sendPat) {
                    String patToken = config.getApiPatToken();
                    if (patToken != null && !patToken.isEmpty()) {
                        dlConn.setRequestProperty("Authorization", "Bearer " + patToken);
                    }
                }
                
                int code = dlConn.getResponseCode();
                if (code == 401) {
                    String errorMsg = extractAuthDiagnostic(dlConn, "Attachment download unauthorized (HTTP 401)");
                    try { dlConn.disconnect(); } catch (Exception ignored) {}
                    throw new JiraApiException(errorMsg, 401, null);
                }
                if (code >= 300) {
                    try { dlConn.disconnect(); } catch (Exception ignored) {}
                    throw new JiraApiException("Attachment download failed with HTTP " + code, code, null);
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
            } catch (JiraApiException | IOException e) {
                if (dlConn != null) {
                    try { dlConn.disconnect(); } catch (Exception ignored) {}
                }

                int statusCode = (e instanceof JiraApiException) ? ((JiraApiException) e).getStatusCode() : -1;
                boolean isAuthError = (statusCode == 401);
                boolean isNetworkOrSslError = (e instanceof IOException);

                String authMethod = config.getApiAuthMethod();
                boolean useCert = "mTLS".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);

                if ((isAuthError || isNetworkOrSslError) && authAttempts < 2) {
                    authAttempts++;
                    forceNewConnection = true;
                    String reason = isAuthError ? "HTTP 401 Unauthorized" : "Connection/SSL error (" + e.getMessage() + ")";
                    String retryMsg = "[AUTO-RECONNECT] Attachment download: " + reason + ". Refreshing credentials and SSL context (attempt " + authAttempts + " of 2)...";
                    System.err.println(retryMsg);
                    if (loggingEnabled) appendToFile("\n" + retryMsg + "\n");
                    
                    if (useCert) {
                        try {
                            refreshSslContext();
                        } catch (Exception ex) {
                            System.err.println("[AUTO-RECONNECT] Failed to refresh SSL Context: " + ex.getMessage());
                        }
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                if (e instanceof JiraApiException) {
                    throw (JiraApiException) e;
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
        int authAttempts = 0;
        boolean forceNewConnection = false;

        while (true) {
            attempt++;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlString);
                URLConnection urlConn = url.openConnection();
                if (urlConn instanceof HttpsURLConnection) {
                    conn = (HttpsURLConnection) urlConn;
                    ((HttpsURLConnection) conn).setSSLSocketFactory(this.sslContext.getSocketFactory());
                } else {
                    conn = (HttpURLConnection) urlConn;
                }

                int timeoutMs = config.getApiTimeoutSeconds() * 1000;
                if (timeoutMs <= 0) timeoutMs = 30000;
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);

                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestProperty("X-Atlassian-Token", "no-check");

                if (forceNewConnection) {
                    conn.setRequestProperty("Connection", "close");
                }

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
                if (code == 401) {
                    String errorMsg = extractAuthDiagnostic(conn, "Attachment upload unauthorized (HTTP 401)");
                    try { conn.disconnect(); } catch (Exception ignored) {}
                    throw new JiraApiException(errorMsg, 401, null);
                }

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
            } catch (JiraApiException | IOException e) {
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                }

                int statusCode = (e instanceof JiraApiException) ? ((JiraApiException) e).getStatusCode() : -1;
                boolean isAuthError = (statusCode == 401);
                boolean isNetworkOrSslError = (e instanceof IOException);

                String authMethod = config.getApiAuthMethod();
                boolean useCert = "mTLS".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);

                if ((isAuthError || isNetworkOrSslError) && authAttempts < 2) {
                    authAttempts++;
                    forceNewConnection = true;
                    String reason = isAuthError ? "HTTP 401 Unauthorized" : "Connection/SSL error (" + e.getMessage() + ")";
                    String retryMsg = "[AUTO-RECONNECT] Attachment upload: " + reason + ". Refreshing credentials and SSL context (attempt " + authAttempts + " of 2)...";
                    System.err.println(retryMsg);
                    if (loggingEnabled) appendToFile("\n" + retryMsg + "\n");

                    if (useCert) {
                        try {
                            refreshSslContext();
                        } catch (Exception ex) {
                            System.err.println("[AUTO-RECONNECT] Failed to refresh SSL Context: " + ex.getMessage());
                        }
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    continue;
                }
                if (e instanceof JiraApiException) {
                    throw (JiraApiException) e;
                }
                throw new JiraApiException("Network error during attachment upload", e);
            }
        }
    }

    public static String findClientAuthAlias(KeyStore ks) {
        final String CLIENT_AUTH_OID = "1.3.6.1.5.5.7.3.2";
        try {
            Enumeration<String> aliases = ks.aliases();
            String firstX509Alias = null;
            while (aliases.hasMoreElements()) {
                String a = aliases.nextElement();
                Certificate cert = ks.getCertificate(a);
                if (cert instanceof X509Certificate) {
                    if (firstX509Alias == null) firstX509Alias = a;
                    X509Certificate x509Cert = (X509Certificate) cert;
                    List<String> extendedKeyUsage = x509Cert.getExtendedKeyUsage();
                    if (extendedKeyUsage != null && extendedKeyUsage.contains(CLIENT_AUTH_OID)) {
                        return a;
                    }
                }
            }
            return firstX509Alias;
        } catch (Exception e) {
            return null;
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

        String effectiveAlias = alias;
        if (effectiveAlias == null || effectiveAlias.trim().isEmpty()) {
            if (aliasSupplier != null) {
                effectiveAlias = aliasSupplier.get();
            }
        }

        String authMethod = config.getApiAuthMethod();
        boolean useCert = "mTLS".equalsIgnoreCase(authMethod) || "mTLS+PAT".equalsIgnoreCase(authMethod);

        if (useCert) {
            KeyStore identityStore = KeyStore.getInstance("Windows-MY", "SunMSCAPI");
            identityStore.load(null, null);

            if (effectiveAlias != null && !effectiveAlias.trim().isEmpty()) {
                if (!identityStore.containsAlias(effectiveAlias)) {
                    String fallback = findClientAuthAlias(identityStore);
                    if (fallback != null) {
                        System.err.println("[SSL CONTEXT] Alias '" + effectiveAlias + "' not found in Windows-MY. Falling back to '" + fallback + "'");
                        effectiveAlias = fallback;
                        this.currentAlias = fallback;
                        if (onAliasRefreshed != null) {
                            try { onAliasRefreshed.accept(fallback); } catch (Exception ignored) {}
                        }
                    }
                }
            } else {
                String fallback = findClientAuthAlias(identityStore);
                if (fallback != null) {
                    effectiveAlias = fallback;
                    this.currentAlias = fallback;
                    if (onAliasRefreshed != null) {
                        try { onAliasRefreshed.accept(fallback); } catch (Exception ignored) {}
                    }
                }
            }

            if (effectiveAlias != null && !effectiveAlias.trim().isEmpty()) {
                final String chosenAlias = effectiveAlias;
                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(identityStore, null);

                KeyManager[] kms = kmf.getKeyManagers();
                if (kms != null && kms.length > 0 && kms[0] instanceof X509KeyManager) {
                    final X509KeyManager originalKeyManager = (X509KeyManager) kms[0];
                    X509ExtendedKeyManager customKeyManager = new X509ExtendedKeyManager() {
                        @Override
                        public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                            return chosenAlias;
                        }

                        @Override
                        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
                            return chosenAlias;
                        }

                        @Override
                        public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                            return originalKeyManager.chooseServerAlias(keyType, issuers, socket);
                        }

                        @Override
                        public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
                            if (originalKeyManager instanceof X509ExtendedKeyManager) {
                                return ((X509ExtendedKeyManager) originalKeyManager).chooseEngineServerAlias(keyType, issuers, engine);
                            }
                            return originalKeyManager.chooseServerAlias(keyType, issuers, null);
                        }

                        @Override
                        public X509Certificate[] getCertificateChain(String a) {
                            String target = chosenAlias;
                            if (a != null) {
                                try {
                                    if (identityStore.containsAlias(a)) {
                                        target = a;
                                    }
                                } catch (Exception ignored) {}
                            }
                            X509Certificate[] chain = null;
                            try {
                                chain = originalKeyManager.getCertificateChain(target);
                            } catch (Exception ignored) {}

                            if (chain == null || chain.length == 0) {
                                try {
                                    Certificate[] storeChain = identityStore.getCertificateChain(target);
                                    if (storeChain != null && storeChain.length > 0) {
                                        List<X509Certificate> list = new java.util.ArrayList<>();
                                        for (Certificate c : storeChain) {
                                            if (c instanceof X509Certificate) list.add((X509Certificate) c);
                                        }
                                        if (!list.isEmpty()) {
                                            chain = list.toArray(new X509Certificate[0]);
                                        }
                                    }
                                    if (chain == null || chain.length == 0) {
                                        Certificate cert = identityStore.getCertificate(target);
                                        if (cert instanceof X509Certificate) {
                                            chain = new X509Certificate[]{(X509Certificate) cert};
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                            return chain;
                        }

                        @Override
                        public String[] getClientAliases(String keyType, Principal[] issuers) {
                            String[] aliases = originalKeyManager.getClientAliases(keyType, issuers);
                            if (aliases == null || aliases.length == 0) {
                                return new String[]{chosenAlias};
                            }
                            boolean found = false;
                            for (String al : aliases) {
                                if (chosenAlias.equalsIgnoreCase(al)) {
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                String[] combined = new String[aliases.length + 1];
                                System.arraycopy(aliases, 0, combined, 0, aliases.length);
                                combined[aliases.length] = chosenAlias;
                                return combined;
                            }
                            return aliases;
                        }

                        @Override
                        public PrivateKey getPrivateKey(String a) {
                            String target = chosenAlias;
                            if (a != null) {
                                try {
                                    if (identityStore.containsAlias(a)) {
                                        target = a;
                                    }
                                } catch (Exception ignored) {}
                            }
                            PrivateKey pk = null;
                            try {
                                pk = originalKeyManager.getPrivateKey(target);
                            } catch (Exception ignored) {}

                            if (pk == null) {
                                try {
                                    java.security.Key k = identityStore.getKey(target, null);
                                    if (k instanceof PrivateKey) {
                                        pk = (PrivateKey) k;
                                    }
                                } catch (Exception ignored) {}
                            }
                            return pk;
                        }

                        @Override
                        public String[] getServerAliases(String keyType, Principal[] issuers) {
                            return originalKeyManager.getServerAliases(keyType, issuers);
                        }
                    };
                    ctx.init(new KeyManager[]{customKeyManager}, trustAllCerts, new SecureRandom());
                    return ctx;
                }
            }
        }

        ctx.init(null, trustAllCerts, new SecureRandom());
        return ctx;
    }
}
