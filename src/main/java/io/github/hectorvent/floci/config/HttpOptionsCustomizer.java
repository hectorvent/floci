package io.github.hectorvent.floci.config;

import io.quarkus.vertx.http.HttpServerOptionsCustomizer;
import io.vertx.core.http.HttpServerOptions;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Removes the 8 KB per-attribute limit imposed by Vert.x's default
 * {@code HttpServerOptions.maxFormAttributeSize}.
 *
 * Without this, any request that arrives with
 * {@code Content-Type: application/x-www-form-urlencoded} and a single
 * attribute value larger than ~8 KB is rejected by Netty's form decoder
 * with "Size exceed allowed maximum capacity". This hits real AWS APIs
 * that use the Query Protocol: CloudFormation templates, EC2 UserData
 * (base64-encoded), large IAM policies, etc.
 *
 * The overall request body size is still bounded by
 * {@code quarkus.http.limits.max-body-size} (default 512 MB), so raising
 * the per-attribute limit to unlimited is safe.
 */
@ApplicationScoped
public class HttpOptionsCustomizer implements HttpServerOptionsCustomizer {

    @Override
    public void customizeHttpServer(HttpServerOptions options) {
        options.setMaxFormAttributeSize(-1);
    }

    @Override
    public void customizeHttpsServer(HttpServerOptions options) {
        options.setMaxFormAttributeSize(-1);
    }
}
