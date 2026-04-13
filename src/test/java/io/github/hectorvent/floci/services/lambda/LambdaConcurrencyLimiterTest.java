package io.github.hectorvent.floci.services.lambda;

import io.github.hectorvent.floci.core.common.AwsException;
import io.github.hectorvent.floci.services.lambda.model.LambdaFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LambdaConcurrencyLimiterTest {

    private static final String ARN = "arn:aws:lambda:us-east-1:000000000000:function:fn";
    private static final String ARN2 = "arn:aws:lambda:us-east-1:000000000000:function:other";

    private LambdaFunction fn(String arn, Integer reserved) {
        LambdaFunction fn = new LambdaFunction();
        fn.setFunctionName("fn");
        fn.setFunctionArn(arn);
        fn.setReservedConcurrentExecutions(reserved);
        return fn;
    }

    private LambdaFunction fn(Integer reserved) {
        return fn(ARN, reserved);
    }

    @Test
    void unsetReserved_countsAgainstAccountPool() {
        LambdaConcurrencyLimiter limiter = new LambdaConcurrencyLimiter(2, 0);
        LambdaConcurrencyLimiter.Permit p1 = limiter.acquire(fn(null));
        LambdaConcurrencyLimiter.Permit p2 = limiter.acquire(fn(null));
        AwsException ex = assertThrows(AwsException.class, () -> limiter.acquire(fn(null)));
        assertEquals(429, ex.getHttpStatus());
        p1.close();
        p2.close();
        assertEquals(0, limiter.unreservedInflightCount());
    }

    @Test
    void reservedN_allowsUpToN_thenThrows() {
        LambdaConcurrencyLimiter limiter = new LambdaConcurrencyLimiter();
        LambdaFunction f = fn(2);
        LambdaConcurrencyLimiter.Permit p1 = limiter.acquire(f);
        LambdaConcurrencyLimiter.Permit p2 = limiter.acquire(f);

        AwsException ex = assertThrows(AwsException.class, () -> limiter.acquire(f));
        assertEquals("TooManyRequestsException", ex.getErrorCode());
        assertEquals(429, ex.getHttpStatus());

        p1.close();
        LambdaConcurrencyLimiter.Permit p3 = limiter.acquire(f);
        p2.close();
        p3.close();
        assertEquals(0, limiter.inflightCount(ARN));
    }

    @Test
    void reservedZero_throwsImmediately() {
        LambdaConcurrencyLimiter limiter = new LambdaConcurrencyLimiter();
        AwsException ex = assertThrows(AwsException.class, () -> limiter.acquire(fn(0)));
        assertEquals(429, ex.getHttpStatus());
    }

    @Test
    void reservedPool_doesNotConsumeUnreserved() {
        LambdaConcurrencyLimiter limiter = new LambdaConcurrencyLimiter(3, 0);
        limiter.setReserved(ARN, 2);
        // Reserved function consumes its own pool, not the account pool
        limiter.acquire(fn(ARN, 2));
        limiter.acquire(fn(ARN, 2));
        // Unreserved function can still use the full accountLimit - totalReserved = 1
        limiter.acquire(fn(ARN2, null));
        AwsException ex = assertThrows(AwsException.class, () -> limiter.acquire(fn(ARN2, null)));
        assertEquals(429, ex.getHttpStatus());
    }

    @Test
    void validatePut_rejectsWhenUnreservedMinViolated() {
        LambdaConcurrencyLimiter limiter = new LambdaConcurrencyLimiter(1000, 100);
        // totalReserved=0, max allowed for new function = 1000 - 100 = 900
        assertDoesNotThrow(() -> limiter.validateAndSetReserved(ARN, 900));
        AwsException ex = assertThrows(AwsException.class, () -> limiter.validateAndSetReserved(ARN, 901));
        assertEquals("LimitExceededException", ex.getErrorCode());
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void validatePut_excludesSelfWhenUpdating() {
        LambdaConcurrencyLimiter limiter = new LambdaConcurrencyLimiter(1000, 100);
        limiter.setReserved(ARN, 500);
        // Updating the same ARN to 900 should succeed (self is excluded from "other")
        assertDoesNotThrow(() -> limiter.validateAndSetReserved(ARN, 900));
    }

    @Test
    void validatePut_considersOtherFunctions() {
        LambdaConcurrencyLimiter limiter = new LambdaConcurrencyLimiter(1000, 100);
        limiter.setReserved(ARN2, 500);
        // otherReserved=500, max for ARN = 1000 - 100 - 500 = 400
        assertDoesNotThrow(() -> limiter.validateAndSetReserved(ARN, 400));
        assertThrows(AwsException.class, () -> limiter.validateAndSetReserved(ARN, 401));
    }

    @Test
    void reset_clearsInflightAndReserved() {
        LambdaConcurrencyLimiter limiter = new LambdaConcurrencyLimiter();
        LambdaFunction f = fn(1);
        limiter.setReserved(ARN, 1);
        limiter.acquire(f);
        limiter.reset(ARN);
        assertEquals(0, limiter.inflightCount(ARN));
        assertEquals(0, limiter.totalReserved());
    }
}
