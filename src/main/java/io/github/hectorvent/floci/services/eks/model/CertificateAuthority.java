package io.github.hectorvent.floci.services.eks.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class CertificateAuthority {

    @JsonProperty("data")
    private String data;

    public CertificateAuthority() {}

    public CertificateAuthority(String data) {
        this.data = data;
    }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
