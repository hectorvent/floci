package io.github.hectorvent.floci.services.ecs.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record ClusterSetting(String name, String value) {}
