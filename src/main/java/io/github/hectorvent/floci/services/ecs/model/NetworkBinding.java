package io.github.hectorvent.floci.services.ecs.model;

public record NetworkBinding(String bindIP, int containerPort, int hostPort, String protocol) {
}
