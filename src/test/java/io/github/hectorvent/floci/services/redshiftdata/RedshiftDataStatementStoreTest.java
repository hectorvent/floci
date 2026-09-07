package io.github.hectorvent.floci.services.redshiftdata;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RedshiftDataStatementStoreTest {

    private static RedshiftDataStatementStore.StoredStatement statement(String id, Instant createdAt) {
        RedshiftDataStatementStore.StoredStatement s = new RedshiftDataStatementStore.StoredStatement();
        s.id = id;
        s.createdAt = createdAt;
        s.updatedAt = createdAt;
        s.status = RedshiftDataStatementStore.Status.FINISHED;
        return s;
    }

    @Test
    void putThenGet() {
        RedshiftDataStatementStore store = new RedshiftDataStatementStore(24, Clock.systemUTC());
        store.put(statement("a", Instant.now()));
        assertNotNull(store.get("a"));
        assertNull(store.get("missing"));
    }

    @Test
    void sweepEvictsEntriesOlderThanTtl() {
        Instant now = Instant.parse("2026-09-07T00:00:00Z");
        Clock fixed = Clock.fixed(now, ZoneOffset.UTC);
        RedshiftDataStatementStore store = new RedshiftDataStatementStore(24, fixed);
        store.put(statement("old", now.minus(Duration.ofHours(25))));
        store.put(statement("fresh", now.minus(Duration.ofHours(1))));

        store.sweep();

        assertNull(store.get("old"));
        assertNotNull(store.get("fresh"));
    }

    @Test
    void clearEmptiesStore() {
        RedshiftDataStatementStore store = new RedshiftDataStatementStore(24, Clock.systemUTC());
        store.put(statement("a", Instant.now()));
        store.clear();
        assertNull(store.get("a"));
    }
}
