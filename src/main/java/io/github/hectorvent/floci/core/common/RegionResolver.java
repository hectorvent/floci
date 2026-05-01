package io.github.hectorvent.floci.core.common;

import io.github.hectorvent.floci.config.EmulatorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.HttpHeaders;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class RegionResolver {

    // Matches: Credential=AKID/20260215/us-west-2/s3/aws4_request
    private static final Pattern CREDENTIAL_REGION_PATTERN =
            Pattern.compile("Credential=\\S+/\\d{8}/([^/]+)/");

    private final String defaultRegion;
    private final String defaultAccountId;

    @Inject
    public RegionResolver(EmulatorConfig config) {
        this(config.defaultRegion(), config.defaultAccountId());
    }

    public RegionResolver(String defaultRegion, String defaultAccountId) {
        this.defaultRegion = defaultRegion;
        this.defaultAccountId = defaultAccountId;
    }

    public String resolveRegion(HttpHeaders headers) {
        if (headers == null) {
            return defaultRegion;
        }
        String auth = headers.getHeaderString("Authorization");
        if (auth == null || auth.isEmpty()) {
            return defaultRegion;
        }
        Matcher matcher = CREDENTIAL_REGION_PATTERN.matcher(auth);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return defaultRegion;
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public String getAccountId() {
        return defaultAccountId;
    }

    public String buildArn(String service, String region, String resource) {
        return AwsArnUtils.Arn.of(service, region, defaultAccountId, resource).toString();
    }
}
