package com.apigen.execution;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contexte mutable transmis d'étape en étape.
 * <p>Racine : request, trigger, payload, steps, output.</p>
 */
public class ExecutionContext {

    private final Map<String, Object> root = new LinkedHashMap<>();
    private final Map<String, Object> steps = new LinkedHashMap<>();
    private Object output;

    public ExecutionContext() {
        root.put("steps", steps);
        root.put("output", null);
    }

    public void setInitial(Map<String, Object> initial) {
        if (initial != null) {
            root.putAll(initial);
        }
        root.put("steps", steps);
        root.put("output", output);
    }

    public void setRootValue(String key, Object value) {
        root.put(key, value);
    }

    public Object getRootValue(String key) {
        return root.get(key);
    }

    public Map<String, Object> getRoot() {
        return root;
    }

    public Map<String, Object> getSteps() {
        return steps;
    }

    public Object getOutput() {
        return output;
    }

    public void setOutput(Object output) {
        this.output = output;
        root.put("output", output);
    }

    public void recordStep(String key, Object result) {
        steps.put(key, result);
        setOutput(result);
    }
}
