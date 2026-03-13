package tso.usmc.jira.util;

/**
 * Base exception for Jira-related API failures.
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
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
