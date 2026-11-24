package com.apigen.execution;

public class ExecutionException extends RuntimeException {

    private final int stepIndex;
    private final String stepName;

    public ExecutionException(int stepIndex, String stepName, String message, Throwable cause) {
        super(message, cause);
        this.stepIndex = stepIndex;
        this.stepName = stepName;
    }

    public int getStepIndex() {
        return stepIndex;
    }

    public String getStepName() {
        return stepName;
    }
}
