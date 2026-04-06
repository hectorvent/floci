package io.github.hectorvent.floci.services.ecs.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.HashMap;
import java.util.Map;

@RegisterForReflection
public class CapacityProvider {

    private String capacityProviderArn;
    private String name;
    private String status;
    private Map<String, Object> autoScalingGroupProvider;
    private Map<String, String> tags = new HashMap<>();

    public String getCapacityProviderArn() { return capacityProviderArn; }
    public void setCapacityProviderArn(String capacityProviderArn) { this.capacityProviderArn = capacityProviderArn; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Map<String, Object> getAutoScalingGroupProvider() { return autoScalingGroupProvider; }
    public void setAutoScalingGroupProvider(Map<String, Object> autoScalingGroupProvider) {
        this.autoScalingGroupProvider = autoScalingGroupProvider;
    }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
}
