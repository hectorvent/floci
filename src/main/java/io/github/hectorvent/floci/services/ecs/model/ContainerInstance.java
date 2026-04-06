package io.github.hectorvent.floci.services.ecs.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RegisterForReflection
public class ContainerInstance {

    private String containerInstanceArn;
    private String ec2InstanceId;
    private String status;
    private int runningTasksCount;
    private int pendingTasksCount;
    private String agentVersion;
    private boolean agentConnected;
    private List<Attribute> attributes = new ArrayList<>();
    private Map<String, String> tags = new HashMap<>();

    public String getContainerInstanceArn() { return containerInstanceArn; }
    public void setContainerInstanceArn(String containerInstanceArn) { this.containerInstanceArn = containerInstanceArn; }

    public String getEc2InstanceId() { return ec2InstanceId; }
    public void setEc2InstanceId(String ec2InstanceId) { this.ec2InstanceId = ec2InstanceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRunningTasksCount() { return runningTasksCount; }
    public void setRunningTasksCount(int runningTasksCount) { this.runningTasksCount = runningTasksCount; }

    public int getPendingTasksCount() { return pendingTasksCount; }
    public void setPendingTasksCount(int pendingTasksCount) { this.pendingTasksCount = pendingTasksCount; }

    public String getAgentVersion() { return agentVersion; }
    public void setAgentVersion(String agentVersion) { this.agentVersion = agentVersion; }

    public boolean isAgentConnected() { return agentConnected; }
    public void setAgentConnected(boolean agentConnected) { this.agentConnected = agentConnected; }

    public List<Attribute> getAttributes() { return attributes; }
    public void setAttributes(List<Attribute> attributes) { this.attributes = attributes; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }
}
