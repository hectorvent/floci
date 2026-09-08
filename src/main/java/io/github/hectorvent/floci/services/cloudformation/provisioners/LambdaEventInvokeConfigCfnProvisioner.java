package io.github.hectorvent.floci.services.cloudformation.provisioners;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.services.cloudformation.model.StackResource;
import io.github.hectorvent.floci.services.lambda.LambdaService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provisions {@code AWS::Lambda::EventInvokeConfig}, the asynchronous invocation settings of one
 * function version or alias.
 *
 * <p>The physical id is the registry schema's composite primary identifier, {@code FunctionName}
 * and {@code Qualifier} joined by a pipe, which is what a real stack shows and all that
 * {@link #delete(String, String, String)} receives. Neither part can contain a pipe.
 *
 * <p>Both identifier parts are create-only. An update that keeps them applies the template's
 * settings to the existing configuration through the merge-style update call, as the AWS handler
 * does, so a setting the template drops keeps its stored value. An update that changes either part
 * creates the configuration for the new target and leaves the displaced one to the
 * {@link ReplacementCleanup} record, which deletes it once the stack update commits or puts it
 * back on rollback.
 *
 * <p>The schema declares no read-only properties, so there is nothing for {@code Fn::GetAtt}.
 */
@ApplicationScoped
public class LambdaEventInvokeConfigCfnProvisioner implements CfnResourceProvisioner {

    private static final String TYPE = "AWS::Lambda::EventInvokeConfig";
    private static final int MIN_RETRY_ATTEMPTS = 0;
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final int MIN_EVENT_AGE_SECONDS = 60;
    private static final int MAX_EVENT_AGE_SECONDS = 21600;

    private final LambdaService lambdaService;

    @Inject
    public LambdaEventInvokeConfigCfnProvisioner(LambdaService lambdaService) {
        this.lambdaService = lambdaService;
    }

    @Override
    public Set<String> resourceTypes() {
        return Set.of(TYPE);
    }

    @Override
    public void provision(StackResource r, JsonNode props, ProvisionContext ctx) {
        Map<String, String> attributesBefore = Map.copyOf(r.getAttributes());
        String functionName = required(ctx.resolveOptional(props, "FunctionName"), "FunctionName");
        String qualifier = required(ctx.resolveOptional(props, "Qualifier"), "Qualifier");
        Map<String, Object> settings = settings(props, ctx);

        String physicalId = functionName + "|" + qualifier;
        if (ctx.reusesPriorEntity(physicalId)) {
            lambdaService.updateEventInvokeConfig(ctx.region(), functionName, qualifier, settings);
        } else {
            lambdaService.putEventInvokeConfig(ctx.region(), functionName, qualifier, settings);
        }
        r.setPhysicalId(physicalId);
        ReplacementCleanup.record(r, ctx, attributesBefore);
    }

    /**
     * The settings the template names, in the request shape the Lambda service reads. Only named
     * settings are sent: on a create the service applies Lambda's defaults for the rest, and on an
     * in-place update the merge-style call leaves them as they are.
     */
    private Map<String, Object> settings(JsonNode props, ProvisionContext ctx) {
        Map<String, Object> settings = new LinkedHashMap<>();
        Integer retryAttempts = integerInRange(ctx.resolveOptional(props, "MaximumRetryAttempts"),
                "MaximumRetryAttempts", MIN_RETRY_ATTEMPTS, MAX_RETRY_ATTEMPTS);
        if (retryAttempts != null) {
            settings.put("MaximumRetryAttempts", retryAttempts);
        }
        Integer eventAge = integerInRange(ctx.resolveOptional(props, "MaximumEventAgeInSeconds"),
                "MaximumEventAgeInSeconds", MIN_EVENT_AGE_SECONDS, MAX_EVENT_AGE_SECONDS);
        if (eventAge != null) {
            settings.put("MaximumEventAgeInSeconds", eventAge);
        }
        if (props != null && props.hasNonNull("DestinationConfig")) {
            settings.put("DestinationConfig", destinationConfig(ctx.engine().resolveNode(props.get("DestinationConfig"))));
        }
        return settings;
    }

    private Map<String, Object> destinationConfig(JsonNode node) {
        Map<String, Object> config = new LinkedHashMap<>();
        for (String side : new String[] {"OnSuccess", "OnFailure"}) {
            if (node == null || !node.hasNonNull(side)) {
                continue;
            }
            String destination = node.get(side).path("Destination").asText(null);
            if (destination == null) {
                throw new AwsException("ValidationError",
                        TYPE + " DestinationConfig." + side + " requires Destination", 400);
            }
            config.put(side, Map.of("Destination", destination));
        }
        return config;
    }

    /**
     * Checks a numeric property against the bounds the registry schema declares. Real
     * CloudFormation validates the template against the schema before the handler runs, so an
     * out-of-range value fails the resource here rather than being stored.
     */
    private static Integer integerInRange(String value, String property, int min, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new AwsException("ValidationError",
                    TYPE + " " + property + " must be an integer, got '" + value + "'", 400);
        }
        if (parsed < min || parsed > max) {
            throw new AwsException("ValidationError",
                    TYPE + " " + property + " must be between " + min + " and " + max + ", got " + parsed, 400);
        }
        return parsed;
    }

    private static String required(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new AwsException("ValidationError", TYPE + " requires " + property, 400);
        }
        return value;
    }

    @Override
    public void delete(String resourceType, String physicalId, String region) {
        if (physicalId == null) {
            return;
        }
        int separator = physicalId.lastIndexOf('|');
        if (separator <= 0) {
            return;
        }
        String functionName = physicalId.substring(0, separator);
        String qualifier = physicalId.substring(separator + 1);
        // The service answers ResourceNotFoundException both for a missing configuration and
        // for a function that is already gone; either way there is nothing left to delete.
        CfnDeletes.safeDelete("Lambda event invoke config", physicalId,
                () -> lambdaService.deleteEventInvokeConfig(region, functionName, qualifier),
                "ResourceNotFoundException");
    }

    @Override
    public boolean hasReplacementUpdate(StackResource resource) {
        return ReplacementCleanup.hasReplacement(resource);
    }

    @Override
    public String updateCleanupPhysicalId(StackResource resource) {
        return ReplacementCleanup.cleanupPhysicalId(resource);
    }

    @Override
    public UpdateCleanupResult completeUpdate(StackResource resource) {
        return ReplacementCleanup.complete(resource, this::delete);
    }

    @Override
    public void clearUpdate(StackResource resource) {
        ReplacementCleanup.clear(resource);
    }

    /**
     * A replacement is undone through the cleanup record. An in-place update keeps no snapshot of
     * the previous settings, so the engine is told it was not rolled back.
     */
    @Override
    public boolean rollbackUpdate(StackResource resource) {
        return ReplacementCleanup.rollback(resource, this::delete);
    }
}
