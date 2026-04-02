package io.github.hectorvent.floci.services.opensearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusterConfig {

    @JsonProperty("InstanceType")
    private String instanceType = "m5.large.search";

    @JsonProperty("InstanceCount")
    private int instanceCount = 1;

    @JsonProperty("DedicatedMasterEnabled")
    private boolean dedicatedMasterEnabled = false;

    @JsonProperty("ZoneAwarenessEnabled")
    private boolean zoneAwarenessEnabled = false;

    public ClusterConfig() {}

    public String getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public void setInstanceCount(int instanceCount) {
        this.instanceCount = instanceCount;
    }

    public boolean isDedicatedMasterEnabled() {
        return dedicatedMasterEnabled;
    }

    public void setDedicatedMasterEnabled(boolean dedicatedMasterEnabled) {
        this.dedicatedMasterEnabled = dedicatedMasterEnabled;
    }

    public boolean isZoneAwarenessEnabled() {
        return zoneAwarenessEnabled;
    }

    public void setZoneAwarenessEnabled(boolean zoneAwarenessEnabled) {
        this.zoneAwarenessEnabled = zoneAwarenessEnabled;
    }
}
