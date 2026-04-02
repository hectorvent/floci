package io.github.hectorvent.floci.services.opensearch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class EbsOptions {

    @JsonProperty("EBSEnabled")
    private boolean ebsEnabled = true;

    @JsonProperty("VolumeType")
    private String volumeType = "gp2";

    @JsonProperty("VolumeSize")
    private int volumeSize = 10;

    public EbsOptions() {}

    public boolean isEbsEnabled() {
        return ebsEnabled;
    }

    public void setEbsEnabled(boolean ebsEnabled) {
        this.ebsEnabled = ebsEnabled;
    }

    public String getVolumeType() {
        return volumeType;
    }

    public void setVolumeType(String volumeType) {
        this.volumeType = volumeType;
    }

    public int getVolumeSize() {
        return volumeSize;
    }

    public void setVolumeSize(int volumeSize) {
        this.volumeSize = volumeSize;
    }
}
