package io.github.hectorvent.floci.core.common;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jboss.logging.Logger;

import java.security.Security;

/**
 * Ensures BouncyCastle security provider is registered before any CDI beans are injected.
 *
 * <p>This class uses a static initializer block to guarantee the provider is registered
 * during class loading, which happens before CDI injection. The {@code @Startup} annotation
 * ensures this class is loaded early in the application lifecycle.</p>
 *
 * <p>Design decision: Using static initializer + @Startup instead of @Observes StartupEvent
 * because StartupEvent observers run after CDI context is ready, which may be too late if
 * other beans depend on BouncyCastle during their construction.</p>
 *
 * @see <a href="https://quarkus.io/guides/cdi#startup-event">Quarkus CDI Startup</a>
 */
@ApplicationScoped
@Startup
public class BouncyCastleInitializer {

    private static final Logger LOG = Logger.getLogger(BouncyCastleInitializer.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
            LOG.info("Registered BouncyCastle security provider (static initializer)");
        }
    }

    /**
     * No-op constructor. The actual initialization happens in the static block above.
     * This bean exists solely to trigger class loading via {@code @Startup}.
     */
    public BouncyCastleInitializer() {
        // Static initializer has already run by the time this constructor is called
    }
}
