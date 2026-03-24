package io.github.hectorvent.floci.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

/**
 * Raises Jackson's default {@code StreamReadConstraints.maxStringLength} from the built-in 20 MB
 * to the value configured via {@code floci.services.s3.max-object-size-mb} (default 512 MB).
 *
 * <p>Jackson 2.15+ introduced a hard cap of 20 000 000 characters per string token
 * ({@link StreamReadConstraints#DEFAULT_MAX_STRING_LEN}). When the AWS SDK sends large binary
 * payloads whose chunked-encoding wrapper is converted to a {@code String} inside
 * {@code S3Controller#decodeAwsChunked}, or when any other request path routes a large body
 * through a Jackson reader, the cap causes Jackson to throw a
 * {@code StreamConstraintsException}, which in turn surfaces as HTTP 413 Content Too Large.
 *
 * <p>This customizer applies the new limit both to the CDI-managed {@link ObjectMapper} (via its
 * {@link com.fasterxml.jackson.core.JsonFactory}) and globally via
 * {@link StreamReadConstraints#overrideDefaultStreamReadConstraints}, so every
 * {@code ObjectMapper} created anywhere in the JVM (including the ones inside
 * {@code HybridStorage} and {@code PersistentStorage}) inherits the higher limit.
 */
@Singleton
public class JacksonStreamConstraintsCustomizer implements ObjectMapperCustomizer {

    private static final Logger LOG = Logger.getLogger(JacksonStreamConstraintsCustomizer.class);

    private final EmulatorConfig config;

    @Inject
    public JacksonStreamConstraintsCustomizer(EmulatorConfig config) {
        this.config = config;
    }

    @Override
    public void customize(ObjectMapper objectMapper) {
        int maxMb = config.services().s3().maxObjectSizeMb();
        // Guard against overflow: Integer.MAX_VALUE is ~2 GB, well above any sane upload size.
        long maxBytes = (long) maxMb * 1024L * 1024L;
        int maxBytesInt = (int) Math.min(maxBytes, Integer.MAX_VALUE);

        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxStringLength(maxBytesInt)
                .build();

        // Override the JVM-wide default so every ObjectMapper instance that is created
        // after this point (including non-CDI ones) inherits the higher limit.
        StreamReadConstraints.overrideDefaultStreamReadConstraints(constraints);

        // Also update the JsonFactory that backs the CDI-managed ObjectMapper directly,
        // in case it was already initialised before the global override took effect.
        objectMapper.getFactory().setStreamReadConstraints(constraints);

        LOG.infov("Jackson maxStringLength set to {0} MB ({1} bytes)", maxMb, maxBytesInt);
    }
}