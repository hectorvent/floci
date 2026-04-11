package io.github.hectorvent.floci.services.apigateway;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.core.common.TagHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

/**
 * {@link TagHandler} implementation for API Gateway.
 *
 * <p>ARN format: {@code arn:aws:apigateway:<region>::/restapis/<apiId>}.
 * The {@code apiId} is the canonical identifier the underlying {@link ApiGatewayService}
 * uses for its tag store.
 */
@ApplicationScoped
public class ApiGatewayTagHandler implements TagHandler {

    private final ApiGatewayService service;

    @Inject
    public ApiGatewayTagHandler(ApiGatewayService service) {
        this.service = service;
    }

    @Override
    public String serviceKey() {
        return "apigateway";
    }

    @Override
    public Map<String, String> listTags(String region, String arn) {
        return service.getTags(region, apiIdFromArn(arn));
    }

    @Override
    public void tagResource(String region, String arn, Map<String, String> tags) {
        service.tagResource(region, apiIdFromArn(arn), tags);
    }

    @Override
    public void untagResource(String region, String arn, List<String> tagKeys) {
        service.untagResource(region, apiIdFromArn(arn), tagKeys);
    }

    private static String apiIdFromArn(String arn) {
        String[] parts = arn.split("/restapis/");
        if (parts.length < 2) {
            throw new AwsException("BadRequestException", "Invalid resource ARN: " + arn, 400);
        }
        return parts[1].split("/")[0];
    }
}
