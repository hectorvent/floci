package io.github.hectorvent.floci.services.s3.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
public class GetObjectAttributesParts {

    private boolean isTruncated;
    private int maxParts;
    private int nextPartNumberMarker;
    private int partNumberMarker;
    private int partsCount;
    private List<Part> parts = new ArrayList<>();

    public boolean isTruncated() { return isTruncated; }
    public void setTruncated(boolean truncated) { isTruncated = truncated; }

    public int getMaxParts() { return maxParts; }
    public void setMaxParts(int maxParts) { this.maxParts = maxParts; }

    public int getNextPartNumberMarker() { return nextPartNumberMarker; }
    public void setNextPartNumberMarker(int nextPartNumberMarker) { this.nextPartNumberMarker = nextPartNumberMarker; }

    public int getPartNumberMarker() { return partNumberMarker; }
    public void setPartNumberMarker(int partNumberMarker) { this.partNumberMarker = partNumberMarker; }

    public int getPartsCount() { return partsCount; }
    public void setPartsCount(int partsCount) { this.partsCount = partsCount; }

    public List<Part> getParts() { return parts; }
    public void setParts(List<Part> parts) { this.parts = parts; }
}
