package io.github.hectorvent.floci.services.appconfig;

import io.github.hectorvent.floci.core.common.AwsArnUtils;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.TagHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

/**
 * {@link TagHandler} implementation for AppConfig.
 *
 * <p>Supported ARN formats:
 * <ul>
 *   <li>{@code arn:aws:appconfig:<region>:<account>:application/<appId>}
 *   <li>{@code arn:aws:appconfig:<region>:<account>:application/<appId>/environment/<envId>}
 *   <li>{@code arn:aws:appconfig:<region>:<account>:application/<appId>/configurationprofile/<profileId>}
 * </ul>
 * Only application-level tags are stored; environment and configurationprofile tag calls
 * are accepted (no-op) to satisfy Terraform provider reads.
 */
@ApplicationScoped
public class AppConfigTagHandler implements TagHandler {

    private final AppConfigService service;

    @Inject
    public AppConfigTagHandler(AppConfigService service) {
        this.service = service;
    }

    @Override
    public String serviceKey() {
        return "appconfig";
    }

    @Override
    public Map<String, String> listTags(String region, String arn) {
        ResourceRef ref = parseArn(arn);
        return switch (ref.type()) {
            case "application" -> service.getApplicationTags(ref.id());
            default -> Map.of();
        };
    }

    @Override
    public void tagResource(String region, String arn, Map<String, String> tags) {
        ResourceRef ref = parseArn(arn);
        if ("application".equals(ref.type())) {
            service.tagApplication(ref.id(), tags);
        }
    }

    @Override
    public void untagResource(String region, String arn, List<String> tagKeys) {
        ResourceRef ref = parseArn(arn);
        if ("application".equals(ref.type())) {
            service.untagApplication(ref.id(), tagKeys);
        }
    }

    private record ResourceRef(String type, String id) {}

    private static ResourceRef parseArn(String arn) {
        // arn:aws:appconfig:<region>:<account>:<resource>
        String resource;
        try {
            resource = AwsArnUtils.parse(arn).resource();
        } catch (IllegalArgumentException e) {
            throw new AwsException("BadRequestException", "Invalid resource ARN: " + arn, 400);
        }
        String[] parts = resource.split("/");
        if (parts.length >= 2 && "application".equals(parts[0])) {
            // application/<appId>
            if (parts.length == 2) return new ResourceRef("application", parts[1]);
            // application/<appId>/environment/<envId>
            // application/<appId>/configurationprofile/<profileId>
            if (parts.length == 4) return new ResourceRef(parts[2], parts[3]);
            // application/<appId>/environment/<envId>/deployment/<num>
            if (parts.length == 6) return new ResourceRef(parts[4], parts[5]);
        }
        throw new AwsException("BadRequestException", "Invalid resource ARN: " + arn, 400);
    }
}
