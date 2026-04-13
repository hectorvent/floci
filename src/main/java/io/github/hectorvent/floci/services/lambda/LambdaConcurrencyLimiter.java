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
        int cap = Math.max(0, accountLimit - totalReserved());
        while (true) {
            int current = unreservedInflight.get();
            if (current >= cap) {
                throw throttle();
            }
            if (unreservedInflight.compareAndSet(current, current + 1)) {
                return unreservedInflight::decrementAndGet;
            }
        }
    }

    /**
     * Register (or update) a function's reserved value. Returns the previous value,
     * or {@code null} if none was set.
     */
    public Integer setReserved(String functionArn, int value) {
        return reserved.put(functionArn, value);
    }

    public Integer clearReserved(String functionArn) {
        return reserved.remove(functionArn);
    }

    /**
     * Validates that setting {@code target} for {@code functionArn} leaves at least
     * {@code unreservedMin} unreserved capacity.
     *
     * @throws AwsException {@code LimitExceededException} if the value would
     *         drop unreserved below the minimum.
     */
    public void validatePut(String functionArn, int target) {
        int otherReserved = totalReserved() - reserved.getOrDefault(functionArn, 0);
        int maxAllowed = accountLimit - unreservedMin - otherReserved;
        if (target > maxAllowed) {
            throw new AwsException("LimitExceededException",
                    "Specified ReservedConcurrentExecutions for function decreases account's "
                    + "UnreservedConcurrentExecution below its minimum value of ["
                    + unreservedMin + "].", 400);
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
