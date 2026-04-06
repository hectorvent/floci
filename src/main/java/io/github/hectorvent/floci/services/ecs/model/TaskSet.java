package io.github.hectorvent.floci.services.ecs.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RegisterForReflection
public class TaskSet {

    private String id;
    private String taskSetArn;
    private String serviceArn;
    private String clusterArn;
    private String taskDefinition;
    private String status;
    private int computedDesiredCount;
    private int pendingCount;
    private int runningCount;
    private double scaleValue;
    private String scaleUnit;
    private String stabilityStatus;
    private LaunchType launchType;
    private String externalId;
    private Instant createdAt;
    private Instant updatedAt;
    private Map<String, String> tags = new HashMap<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTaskSetArn() { return taskSetArn; }
    public void setTaskSetArn(String taskSetArn) { this.taskSetArn = taskSetArn; }

    public String getServiceArn() { return serviceArn; }
    public void setServiceArn(String serviceArn) { this.serviceArn = serviceArn; }

    public String getClusterArn() { return clusterArn; }
    public void setClusterArn(String clusterArn) { this.clusterArn = clusterArn; }

    public String getTaskDefinition() { return taskDefinition; }
    public void setTaskDefinition(String taskDefinition) { this.taskDefinition = taskDefinition; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getComputedDesiredCount() { return computedDesiredCount; }
    public void setComputedDesiredCount(int computedDesiredCount) { this.computedDesiredCount = computedDesiredCount; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getRunningCount() { return runningCount; }
    public void setRunningCount(int runningCount) { this.runningCount = runningCount; }

    public double getScaleValue() { return scaleValue; }
    public void setScaleValue(double scaleValue) { this.scaleValue = scaleValue; }

    public String getScaleUnit() { return scaleUnit; }
    public void setScaleUnit(String scaleUnit) { this.scaleUnit = scaleUnit; }

    public String getStabilityStatus() { return stabilityStatus; }
    public void setStabilityStatus(String stabilityStatus) { this.stabilityStatus = stabilityStatus; }

    public LaunchType getLaunchType() { return launchType; }
    public void setLaunchType(LaunchType launchType) { this.launchType = launchType; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
}
