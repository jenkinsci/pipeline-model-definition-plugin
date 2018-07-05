package org.jenkinsci.plugins.pipeline.modeldefinition.model;

/**
 * Represent result of the process stage restart
 */
public class StageRestartResult {
    private String message;
    private boolean success;

    public StageRestartResult(String message, boolean success) {
        this.message = message;
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
