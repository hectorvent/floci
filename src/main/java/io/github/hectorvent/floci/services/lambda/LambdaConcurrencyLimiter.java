package io.github.hectorvent.floci.services.lambda;

import io.github.hectorvent.floci.config.EmulatorConfig;
import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.services.lambda.model.LambdaFunction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enforces Lambda concurrency limits at invocation time.
 *
 * <p>Two layers of enforcement:
 * <ul>
 *   <li><b>Reserved (per-function)</b>: when a function has a reserved value,
 *       inflight invocations are counted against that value and do not consume
 *       the account-wide pool.</li>
 *   <li><b>Unreserved (account-shared)</b>: functions without a reserved value
 *       share {@code accountLimit - Σreserved} permits.</li>
 * </ul>
 *
 * <p>{@link #validatePut(String, int)} rejects Put operations that would leave
 * less than {@code unreservedMin} (AWS default: 100) available for unreserved
 * functions, matching AWS's {@code LimitExceededException} behavior.
 */
@ApplicationScoped
public class LambdaConcurrencyLimiter {

    private final ConcurrentHashMap<String, AtomicInteger> inflight = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> reserved = new ConcurrentHashMap<>();
    private final AtomicInteger unreservedInflight = new AtomicInteger();
    /** Guards atomic validate-then-set on the reserved map (Put operations). */
    private final Object reservedLock = new Object();
    private final int accountLimit;
    private final int unreservedMin;

    @Inject
    public LambdaConcurrencyLimiter(EmulatorConfig config) {
        this(config.services().lambda().accountConcurrencyLimit(),
             config.services().lambda().unreservedConcurrencyMin());
    }

    /** Test-only constructor with explicit limits. */
    LambdaConcurrencyLimiter(int accountLimit, int unreservedMin) {
        this.accountLimit = accountLimit;
        this.unreservedMin = unreservedMin;
    }

    /** Test-only no-arg constructor using AWS defaults (1000 / 100). */
    LambdaConcurrencyLimiter() {
        this(1000, 100);
    }

    public Permit acquire(LambdaFunction fn) {
        Integer r = fn.getReservedConcurrentExecutions();
        if (r == null) {
            return acquireUnreserved();
        }
        return acquireReserved(fn.getFunctionArn(), r);
    }

    private Permit acquireReserved(String key, int limit) {
        AtomicInteger counter = inflight.computeIfAbsent(key, k -> new AtomicInteger());
        while (true) {
            int current = counter.get();
            if (current >= limit) {
                throw throttle();
            }
            if (counter.compareAndSet(current, current + 1)) {
                return counter::decrementAndGet;
            }
        }
    }

    private Permit acquireUnreserved() {
        while (true) {
            int current = unreservedInflight.get();
            // Recompute cap each spin so a concurrent setReserved is observed
            // promptly and we do not grant permits above the live pool.
            int cap = Math.max(0, accountLimit - totalReserved());
            if (current >= cap) {
                throw throttle();
            }
            if (unreservedInflight.compareAndSet(current, current + 1)) {
                return unreservedInflight::decrementAndGet;
            }
        }
    }

    /**
     * Register (or update) a function's reserved value without validation.
     * Intended for startup rehydration from persisted state.
     */
    public Integer setReserved(String functionArn, int value) {
        synchronized (reservedLock) {
            return reserved.put(functionArn, value);
        }
    }

    public Integer clearReserved(String functionArn) {
        synchronized (reservedLock) {
            return reserved.remove(functionArn);
        }
    }

    /**
     * Atomically validates and applies a reserved value. Two concurrent Puts for
     * different functions cannot each pass validation against stale totals and
     * then collectively push unreserved capacity below the minimum.
     *
     * @throws AwsException {@code LimitExceededException} if the value would
     *         drop unreserved below the minimum.
     */
    public void validateAndSetReserved(String functionArn, int target) {
        synchronized (reservedLock) {
            int otherReserved = totalReserved() - reserved.getOrDefault(functionArn, 0);
            int maxAllowed = accountLimit - unreservedMin - otherReserved;
            if (target > maxAllowed) {
                throw new AwsException("LimitExceededException",
                        "Specified ReservedConcurrentExecutions for function decreases account's "
                        + "UnreservedConcurrentExecution below its minimum value of ["
                        + unreservedMin + "].", 400);
            }
            reserved.put(functionArn, target);
        }
    }

    public void reset(String functionArn) {
        inflight.remove(functionArn);
        reserved.remove(functionArn);
    }

    public int totalReserved() {
        int sum = 0;
        for (Integer v : reserved.values()) {
            sum += v;
        }
        return sum;
    }

    public int availableUnreserved() {
        return Math.max(0, accountLimit - totalReserved() - unreservedInflight.get());
    }

    int inflightCount(String functionArn) {
        AtomicInteger counter = inflight.get(functionArn);
        return counter == null ? 0 : counter.get();
    }

    int unreservedInflightCount() {
        return unreservedInflight.get();
    }

    private static AwsException throttle() {
        return new AwsException("TooManyRequestsException", "Rate Exceeded.", 429);
    }

    @FunctionalInterface
    public interface Permit extends AutoCloseable {
        Permit NOOP = () -> { };

        @Override
        void close();
    }
}
