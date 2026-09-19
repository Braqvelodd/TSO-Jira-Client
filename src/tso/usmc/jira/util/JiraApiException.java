package tso.usmc.jira.util;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Base exception for Jira-related API failures.
 * Automatically parses Jira REST API error responses (field validation errors,
 * errorMessages, status descriptions) into clean, human-readable diagnostics.
 */
public class JiraApiException extends Exception {
    private final int statusCode;
    private final String responseBody;

    public JiraApiException(String message) {
        this(message, -1, null, null);
    }

    public JiraApiException(String message, Throwable cause) {
        this(message, -1, null, cause);
    }

    public JiraApiException(String message, int statusCode, String responseBody) {
        this(message, statusCode, responseBody, null);
    }

    public JiraApiException(String message, int statusCode, String responseBody, Throwable cause) {
        super(buildExceptionMessage(message, statusCode, responseBody), cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    private static String buildExceptionMessage(String baseMessage, int statusCode, String responseBody) {
        if (statusCode > 0 && responseBody != null && !responseBody.trim().isEmpty()) {
            String detailed = formatApiError(statusCode, responseBody);
            if (detailed != null && !detailed.isEmpty()) {
                return detailed;
            }
        }
        return baseMessage != null ? baseMessage : "Jira API request failed";
    }

    /**
     * Parses a Jira REST API HTTP error status and response payload into structured, readable text.
     */
    public static String formatApiError(int statusCode, String responseBody) {
        if (responseBody == null || responseBody.trim().isEmpty()) {
            String statusText = getStatusText(statusCode);
            return "Jira API request failed (HTTP " + statusCode + (statusText.isEmpty() ? "" : " " + statusText) + "). No response body returned.";
        }

        StringBuilder sb = new StringBuilder();
        String statusText = getStatusText(statusCode);
        sb.append("Jira API Error (HTTP ").append(statusCode);
        if (!statusText.isEmpty()) {
            sb.append(" ").append(statusText);
        }
        sb.append("):");

        String trimmed = responseBody.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JSONObject json = new JSONObject(trimmed);
                boolean hasSpecificErrors = false;

                // 1. General error messages list
                if (json.has("errorMessages")) {
                    JSONArray arr = json.optJSONArray("errorMessages");
                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            String msg = arr.optString(i);
                            if (msg != null && !msg.trim().isEmpty()) {
                                sb.append("\n  • ").append(msg.trim());
                                hasSpecificErrors = true;
                            }
                        }
                    }
                }

                // 2. Field-level specific validation errors
                if (json.has("errors")) {
                    JSONObject errs = json.optJSONObject("errors");
                    if (errs != null) {
                        for (String key : errs.keySet()) {
                            String reason = errs.optString(key);
                            sb.append("\n  • Field '").append(key).append("': ").append(reason != null ? reason.trim() : "Invalid value");
                            hasSpecificErrors = true;
                        }
                    }
                }

                // 3. Alternate error keys (message, errorMessage, error)
                if (!hasSpecificErrors) {
                    if (json.has("message") && !json.isNull("message")) {
                        sb.append("\n  • ").append(json.optString("message").trim());
                    } else if (json.has("errorMessage") && !json.isNull("errorMessage")) {
                        sb.append("\n  • ").append(json.optString("errorMessage").trim());
                    } else if (json.has("error") && !json.isNull("error")) {
                        sb.append("\n  • ").append(json.optString("error").trim());
                    } else {
                        sb.append("\n  • ").append(trimmed);
                    }
                }
                return sb.toString();
            } catch (Exception ignored) {
                // Not valid JSON, fall through
            }
        }

        // HTML or plain text handling
        if (trimmed.toLowerCase().contains("<html")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("<title>(.*?)</title>", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(trimmed);
            if (m.find()) {
                sb.append("\n  • Server Response: ").append(m.group(1).trim());
            } else {
                String plain = trimmed.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                if (plain.length() > 250) plain = plain.substring(0, 250) + "...";
                sb.append("\n  • ").append(plain);
            }
        } else {
            if (trimmed.length() > 300) trimmed = trimmed.substring(0, 300) + "...";
            sb.append("\n  • ").append(trimmed);
        }

        return sb.toString();
    }

    public static String getStatusText(int code) {
        switch (code) {
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 409: return "Conflict";
            case 415: return "Unsupported Media Type";
            case 429: return "Too Many Requests (Rate Limited)";
            case 500: return "Internal Server Error";
            case 502: return "Bad Gateway";
            case 503: return "Service Unavailable";
            case 504: return "Gateway Timeout";
            default: return "";
        }
    }
}
