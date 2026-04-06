package io.github.hectorvent.floci.services.ecs.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
public class ServiceRevision {

    private String serviceRevisionArn;
    private String serviceArn;
    private String clusterArn;
    private String taskDefinition;
    private LaunchType launchType;
    private Instant createdAt;

    public String getServiceRevisionArn() { return serviceRevisionArn; }
    public void setServiceRevisionArn(String serviceRevisionArn) { this.serviceRevisionArn = serviceRevisionArn; }

    public String getServiceArn() { return serviceArn; }
    public void setServiceArn(String serviceArn) { this.serviceArn = serviceArn; }

    public String getClusterArn() { return clusterArn; }
    public void setClusterArn(String clusterArn) { this.clusterArn = clusterArn; }

    public String getTaskDefinition() { return taskDefinition; }
    public void setTaskDefinition(String taskDefinition) { this.taskDefinition = taskDefinition; }

    public LaunchType getLaunchType() { return launchType; }
    public void setLaunchType(LaunchType launchType) { this.launchType = launchType; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
