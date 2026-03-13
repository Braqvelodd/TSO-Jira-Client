package tso.usmc.jira.util;

/**
 * Exception thrown when a workflow execution step fails.
 */
public class WorkflowException extends Exception {
    private final String stepLabel;

    public WorkflowException(String message, String stepLabel) {
        super(message);
        this.stepLabel = stepLabel;
    }

    public WorkflowException(String message, String stepLabel, Throwable cause) {
        super(message, cause);
        this.stepLabel = stepLabel;
    }

    public String getStepLabel() {
        return stepLabel;
    }
}
