package io.github.hectorvent.floci.services.redshiftdata;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.hectorvent.floci.config.EmulatorConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
class RedshiftDataStatementStore {

    private static final Logger LOG = Logger.getLogger(RedshiftDataStatementStore.class);

    enum Status { PICKED, STARTED, FINISHED, FAILED, ABORTED }

    private final ConcurrentMap<String, StoredStatement> statements = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;
    // Created by the CDI lifecycle in start(); the test constructor never starts it, so unit
    // tests that build the store directly drive sweep() by hand and spawn no background thread.
    private ScheduledExecutorService sweeper;

    @Inject
    RedshiftDataStatementStore(EmulatorConfig config) {
        this(config.services().redshiftData().resultTtlHours(), Clock.systemUTC());
    }

    RedshiftDataStatementStore(int resultTtlHours, Clock clock) {
        this.ttl = Duration.ofHours(Math.max(1, resultTtlHours));
        this.clock = clock;
    }

    @PostConstruct
    void start() {
        sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "redshift-data-statement-sweep");
            t.setDaemon(true);
            return t;
        });
        sweeper.scheduleAtFixedRate(this::sweepSafely, 30, 30, TimeUnit.MINUTES);
    }

    @PreDestroy
    void stop() {
        if (sweeper != null) {
            sweeper.shutdownNow();
        }
    }

    void put(StoredStatement statement) {
        statements.put(statement.id, statement);
    }

    StoredStatement get(String id) {
        return statements.get(id);
    }

    Collection<StoredStatement> values() {
        return statements.values();
    }

    void clear() {
        statements.clear();
    }

    void sweep() {
        Instant cutoff = Instant.now(clock).minus(ttl);
        statements.values().removeIf(s -> s.createdAt.isBefore(cutoff));
    }

    private void sweepSafely() {
        try {
            sweep();
        } catch (Exception e) {
            LOG.warn("Failed to sweep expired Redshift Data API statements", e);
        }
    }

    static final class StoredStatement {
        String id;
        String sql;
        List<String> sqls;
        boolean batch;
        String statementName;
        String clusterIdentifier;
        String database;
        String dbUser;
        String resultFormat = "JSON";
        Status status;
        String error;
        Instant createdAt;
        Instant updatedAt;
        long durationNanos;
        boolean hasResultSet;
        long resultRows;
        long resultSize;
        ArrayNode columnMetadata;
        List<ArrayNode> rows;
        List<StoredStatement> subStatements;
    }
}
