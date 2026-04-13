package io.github.hectorvent.floci.services.msk.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import java.util.Map;

@RegisterForReflection
public class MskCluster {

    @JsonProperty("clusterArn")
    private String clusterArn;

    @JsonProperty("clusterName")
    private String clusterName;

    @JsonProperty("state")
    private ClusterState state;

    @JsonProperty("creationTime")
    private Instant creationTime;

    @JsonProperty("currentVersion")
    private String currentVersion;

    @JsonProperty("numberOfBrokerNodes")
    private int numberOfBrokerNodes;

    @JsonProperty("tags")
    private Map<String, String> tags;

    @JsonProperty("zookeeperConnectString")
    private String zookeeperConnectString;

    // Internal field, not directly in AWS response but needed for GetBootstrapBrokers
    private String bootstrapBrokers;
    
    // Docker container ID for mock=false
    private String containerId;

    public MskCluster() {}

    public MskCluster(String clusterArn, String clusterName) {
        this.clusterArn = clusterArn;
        this.clusterName = clusterName;
        this.state = ClusterState.CREATING;
        this.creationTime = Instant.now();
        this.currentVersion = "K3V6I1"; // Example version
        this.numberOfBrokerNodes = 1;
        this.zookeeperConnectString = "localhost:2181"; // Mock ZK
    }

    public String getClusterArn() { return clusterArn; }
    public void setClusterArn(String clusterArn) { this.clusterArn = clusterArn; }

    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }

    public ClusterState getState() { return state; }
    public void setState(ClusterState state) { this.state = state; }

    public Instant getCreationTime() { return creationTime; }
    public void setCreationTime(Instant creationTime) { this.creationTime = creationTime; }

    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }

    public int getNumberOfBrokerNodes() { return numberOfBrokerNodes; }
    public void setNumberOfBrokerNodes(int numberOfBrokerNodes) { this.numberOfBrokerNodes = numberOfBrokerNodes; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }

    public String getZookeeperConnectString() { return zookeeperConnectString; }
    public void setZookeeperConnectString(String zookeeperConnectString) { this.zookeeperConnectString = zookeeperConnectString; }

    public String getBootstrapBrokers() { return bootstrapBrokers; }
    public void setBootstrapBrokers(String bootstrapBrokers) { this.bootstrapBrokers = bootstrapBrokers; }

    public String getContainerId() { return containerId; }
    public void setContainerId(String containerId) { this.containerId = containerId; }
}
