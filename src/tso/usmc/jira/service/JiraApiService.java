// package tso.usmc.jira.service;

// import java.io.*;
// import java.net.URL;
// import java.security.SecureRandom;
// import java.security.cert.X509Certificate;
// import javax.net.ssl.HttpsURLConnection;
// import javax.net.ssl.SSLContext;
// import javax.net.ssl.TrustManager;
// import javax.net.ssl.X509TrustManager;

// public class JiraApiService {

//     private final String personalAccessToken;
//     private final SSLContext sslContext;

//     /**
//      * Initializes the service with a PAT. This version uses a "Trust All"
//      * SSL Context and is INSECURE. For development/testing only.
//      * @param personalAccessToken Your Jira PAT.
//      * @throws Exception if the SSL context cannot be created.
//      */
//     public JiraApiService(String personalAccessToken) throws Exception {
//         if (personalAccessToken == null || personalAccessToken.trim().isEmpty()) {
//             throw new IllegalArgumentException("NDMyODAwNjYyMDQxOuahrRmZjL1nGVfKFUkpJT4O5dBJ");
//         }
//         this.personalAccessToken = personalAccessToken;
//         this.sslContext = createTrustAllSslContext(); // Use the trust-all context
//     }

//     /**
//      * Creates an SSL Context that trusts all server certificates.
//      * WARNING: This is insecure and vulnerable to Man-in-the-Middle attacks.
//      */
//     private SSLContext createTrustAllSslContext() throws Exception {
//         // Create a trust manager that does not validate certificate chains
//         TrustManager[] trustAllCerts = new TrustManager[] {
//             new X509TrustManager() {
//                 public X509Certificate[] getAcceptedIssuers() {
//                     return null; // Trust all issuers
//                 }
//                 public void checkClientTrusted(X509Certificate[] certs, String authType) {
//                     // Do nothing, trust the client
//                 }
//                 public void checkServerTrusted(X509Certificate[] certs, String authType) {
//                     // Do nothing, trust the server
//                 }
//             }
//         };

//         // Install the all-trusting trust manager
//         SSLContext sc = SSLContext.getInstance("TLSv1.2");
//         // Initialize with null KeyManagers and our custom TrustManager
//         sc.init(null, trustAllCerts, new SecureRandom());
//         return sc;
//     }

//     public String executeRequest(String urlString, String method, String jsonBody) throws Exception {
//         URL url = new URL(urlString);
//         HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

//         // Apply the "Trust All" SSL Socket Factory
//         conn.setSSLSocketFactory(this.sslContext.getSocketFactory());
        
//         // (Optional) If your server uses a self-signed cert with a mismatched hostname, you might also need this.
//         // conn.setHostnameVerifier((hostname, session) -> true);

//         // --- Use PAT for authentication ---
//         conn.setRequestProperty("Authorization", "Bearer " + this.personalAccessToken);

//         // Set other standard properties
//         conn.setRequestMethod(method);
//         conn.setRequestProperty("Content-Type", "application/json");
//         conn.setRequestProperty("Accept", "application/json");

//         if ("POST".equalsIgnoreCase(method) && jsonBody != null) {
//             conn.setDoOutput(true);
//             try (OutputStream os = conn.getOutputStream()) {
//                 os.write(jsonBody.getBytes("UTF-8"));
//             }
//         }

//         int code = conn.getResponseCode();
//         InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

//         StringBuilder sb = new StringBuilder();
//         if (is != null) {
//             try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
//                 String line;
//                 while ((line = reader.readLine()) != null) {
//                     sb.append(line).append(System.lineSeparator());
//                 }
//             }
//         }

//         if (code >= 300) {
//             throw new IOException("Jira request failed with code " + code + ": " + sb.toString());
//         }

//         return sb.toString();
//     }
// }

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

    public JiraApiService(String selectedAlias) throws Exception {
        this.sslContext = createSslContext(selectedAlias);
    }

    public String executeRequest(String urlString, String method, String jsonBody) throws Exception {
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
        if (code >= 300) {
            throw new Exception("Jira API request failed with code " + code + ": " + sb.toString());
        }
        return sb.toString();
    }
    public File downloadAttachmentToTempFile(String fileUrl, String originalFilename) throws Exception {
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
        
        // Return the handle to the downloaded temporary file.
        return tempFile;
    }
    public String uploadAttachment(String urlString, File fileToUpload, String originalFilename) throws Exception {
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

        if (code >= 300) {
            throw new Exception("Jira API request failed with code " + code + ": " + sb.toString());
        }

        return sb.toString();
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