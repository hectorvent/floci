package io.github.hectorvent.floci.services.iam.model;

import java.util.List;

/**
 * A single parsed statement from an IAM policy document.
 */
public class PolicyStatement {

    private final String effect;          // "Allow" or "Deny"
    private final List<String> actions;   // IAM action patterns (may contain * and ?)
    private final List<String> resources; // resource ARN patterns (may contain * and ?)

    public PolicyStatement(String effect, List<String> actions, List<String> resources) {
        this.effect = effect;
        this.actions = actions;
        this.resources = resources;
    }

    public String getEffect() { return effect; }
    public List<String> getActions() { return actions; }
    public List<String> getResources() { return resources; }

    public boolean isDeny() { return "Deny".equalsIgnoreCase(effect); }
    public boolean isAllow() { return "Allow".equalsIgnoreCase(effect); }
}
