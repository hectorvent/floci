package io.github.hectorvent.floci.services.ecs.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;

@RegisterForReflection
public record ProtectedTask(String taskArn, boolean protectionEnabled, Instant expirationDate) {}
