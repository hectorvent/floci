package io.github.hectorvent.floci.services.s3.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

@RegisterForReflection
public record QueueNotification(String id, String queueArn, List<String> events,
                                String filterPrefix, String filterSuffix) {}
