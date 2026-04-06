package io.github.hectorvent.floci.services.ecs.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public class Container {

    private String containerArn;
    private String taskArn;
    private String name;
    private String image;
    private String lastStatus;
    private Integer exitCode;
    private String reason;
    private List<NetworkBinding> networkBindings;

    // transient — not persisted
    private transient String dockerId;

    public String getContainerArn() { return containerArn; }
    public void setContainerArn(String containerArn) { this.containerArn = containerArn; }

    public String getTaskArn() { return taskArn; }
    public void setTaskArn(String taskArn) { this.taskArn = taskArn; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public String getLastStatus() { return lastStatus; }
    public void setLastStatus(String lastStatus) { this.lastStatus = lastStatus; }

    public Integer getExitCode() { return exitCode; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public List<NetworkBinding> getNetworkBindings() { return networkBindings; }
    public void setNetworkBindings(List<NetworkBinding> networkBindings) { this.networkBindings = networkBindings; }

    public String getDockerId() { return dockerId; }
    public void setDockerId(String dockerId) { this.dockerId = dockerId; }
}
