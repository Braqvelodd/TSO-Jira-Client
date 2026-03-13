package tso.usmc.jira.workflow;

/**
 * Interface for receiving updates during workflow execution.
 * Allows the WorkflowEngine to remain decoupled from the Swing UI.
 */
public interface WorkflowProgressListener {
    /**
     * Called when a log message should be displayed.
     */
    void onLog(String message);

    /**
     * Called when an error occurs.
     */
    void onError(String message, Exception ex);

    /**
     * Called when the entire workflow execution is complete.
     */
    void onComplete();
}
