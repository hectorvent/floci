package io.github.hectorvent.floci.services.elbv2.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;

@RegisterForReflection
@JsonIgnoreProperties(ignoreUnknown = true)
public class Action {

    private String type;
    private Integer order;

    // forward (simple)
    private String targetGroupArn;

    // forward (weighted)
    private List<TargetGroupTuple> targetGroups = new ArrayList<>();
    private Boolean stickinessEnabled;
    private Integer stickinessDurationSeconds;

    // redirect
    private String redirectProtocol;
    private String redirectPort;
    private String redirectHost;
    private String redirectPath;
    private String redirectQuery;
    private String redirectStatusCode;

    // fixed-response
    private String fixedResponseStatusCode;
    private String fixedResponseContentType;
    private String fixedResponseMessageBody;

    public Action() {}

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }

    public String getTargetGroupArn() { return targetGroupArn; }
    public void setTargetGroupArn(String targetGroupArn) { this.targetGroupArn = targetGroupArn; }

    public List<TargetGroupTuple> getTargetGroups() { return targetGroups; }
    public void setTargetGroups(List<TargetGroupTuple> targetGroups) { this.targetGroups = targetGroups; }

    public Boolean getStickinessEnabled() { return stickinessEnabled; }
    public void setStickinessEnabled(Boolean stickinessEnabled) { this.stickinessEnabled = stickinessEnabled; }

    public Integer getStickinessDurationSeconds() { return stickinessDurationSeconds; }
    public void setStickinessDurationSeconds(Integer stickinessDurationSeconds) { this.stickinessDurationSeconds = stickinessDurationSeconds; }

    public String getRedirectProtocol() { return redirectProtocol; }
    public void setRedirectProtocol(String redirectProtocol) { this.redirectProtocol = redirectProtocol; }

    public String getRedirectPort() { return redirectPort; }
    public void setRedirectPort(String redirectPort) { this.redirectPort = redirectPort; }

    public String getRedirectHost() { return redirectHost; }
    public void setRedirectHost(String redirectHost) { this.redirectHost = redirectHost; }

    public String getRedirectPath() { return redirectPath; }
    public void setRedirectPath(String redirectPath) { this.redirectPath = redirectPath; }

    public String getRedirectQuery() { return redirectQuery; }
    public void setRedirectQuery(String redirectQuery) { this.redirectQuery = redirectQuery; }

    public String getRedirectStatusCode() { return redirectStatusCode; }
    public void setRedirectStatusCode(String redirectStatusCode) { this.redirectStatusCode = redirectStatusCode; }

    public String getFixedResponseStatusCode() { return fixedResponseStatusCode; }
    public void setFixedResponseStatusCode(String fixedResponseStatusCode) { this.fixedResponseStatusCode = fixedResponseStatusCode; }

    public String getFixedResponseContentType() { return fixedResponseContentType; }
    public void setFixedResponseContentType(String fixedResponseContentType) { this.fixedResponseContentType = fixedResponseContentType; }

    public String getFixedResponseMessageBody() { return fixedResponseMessageBody; }
    public void setFixedResponseMessageBody(String fixedResponseMessageBody) { this.fixedResponseMessageBody = fixedResponseMessageBody; }

    @RegisterForReflection
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TargetGroupTuple {
        private String targetGroupArn;
        private Integer weight;

        public TargetGroupTuple() {}

        public String getTargetGroupArn() { return targetGroupArn; }
        public void setTargetGroupArn(String targetGroupArn) { this.targetGroupArn = targetGroupArn; }

        public Integer getWeight() { return weight; }
        public void setWeight(Integer weight) { this.weight = weight; }
    }
}
