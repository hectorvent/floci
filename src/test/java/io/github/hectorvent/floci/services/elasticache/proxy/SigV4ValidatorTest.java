package io.github.hectorvent.floci.services.elasticache.proxy;

import io.github.hectorvent.floci.services.iam.IamService;
import io.github.hectorvent.floci.testutil.IamServiceTestHelper;
import io.github.hectorvent.floci.testutil.SigV4TokenTestHelper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SigV4ValidatorTest {

    @Test
    void validateAcceptsTokenForMatchingReplicationGroup() throws Exception {
        IamService iamService = IamServiceTestHelper.iamServiceWithAccessKey("AKIDCACHE", "secret-cache");

        SigV4Validator validator = new SigV4Validator(iamService);
        String token = SigV4TokenTestHelper.createElastiCacheToken(
                "cache-cluster-01",
                "default",
                "AKIDCACHE",
                "secret-cache",
                Instant.now().minusSeconds(60),
                900
        );

        assertTrue(validator.validate(token, "cache-cluster-01"));
        assertTrue(validator.validate(token, "CACHE-CLUSTER-01"));
    }

    @Test
    void validateRejectsTokenForDifferentReplicationGroup() throws Exception {
        IamService iamService = IamServiceTestHelper.iamServiceWithAccessKey("AKIDCACHE", "secret-cache");

        SigV4Validator validator = new SigV4Validator(iamService);
        String token = SigV4TokenTestHelper.createElastiCacheToken(
                "cache-cluster-01",
                "default",
                "AKIDCACHE",
                "secret-cache",
                Instant.now().minusSeconds(60),
                900
        );

        assertFalse(validator.validate(token, "other-cluster"));
    }

    @Test
    void validateRejectsTamperedSignature() throws Exception {
        IamService iamService = IamServiceTestHelper.iamServiceWithAccessKey("AKIDCACHE", "secret-cache");

        SigV4Validator validator = new SigV4Validator(iamService);
        String validToken = SigV4TokenTestHelper.createElastiCacheToken(
                "cache-cluster-01",
                "default",
                "AKIDCACHE",
                "secret-cache",
                Instant.now().minusSeconds(60),
                900
        );
        String tamperedToken = validToken.replace("User=default", "User=other");

        assertFalse(validator.validate(tamperedToken, "cache-cluster-01"));
    }

    @Test
    void validateAcceptsTokenWhenExpectedGroupIsNull() throws Exception {
        IamService iamService = IamServiceTestHelper.iamServiceWithAccessKey("AKIDCACHE", "secret-cache");

        SigV4Validator validator = new SigV4Validator(iamService);
        String token = SigV4TokenTestHelper.createElastiCacheToken(
                "cache-cluster-01",
                "default",
                "AKIDCACHE",
                "secret-cache",
                Instant.now().minusSeconds(60),
                900
        );

        assertTrue(validator.validate(token, null));
    }

    @Test
    void validateRejectsExpiredToken() throws Exception {
        IamService iamService = IamServiceTestHelper.iamServiceWithAccessKey("AKIDCACHE", "secret-cache");

        SigV4Validator validator = new SigV4Validator(iamService);
        String token = SigV4TokenTestHelper.createElastiCacheToken(
                "cache-cluster-01",
                "default",
                "AKIDCACHE",
                "secret-cache",
                Instant.now().minusSeconds(1200),
                900
        );

        assertFalse(validator.validate(token, "cache-cluster-01"));
    }

    @Test
    void validateRejectsTokenWithUnknownAccessKey() throws Exception {
        IamService iamService = IamServiceTestHelper.iamServiceWithAccessKey("AKIDCACHE", "secret-cache");

        SigV4Validator validator = new SigV4Validator(iamService);
        String token = SigV4TokenTestHelper.createElastiCacheToken(
                "cache-cluster-01",
                "default",
                "AKIDUNKNOWN",
                "wrong-secret",
                Instant.now().minusSeconds(60),
                900
        );

        assertFalse(validator.validate(token, "cache-cluster-01"));
    }

    @Test
    void validateAcceptsTokenWithUrlEncodedUser() throws Exception {
        IamService iamService = IamServiceTestHelper.iamServiceWithAccessKey("AKIDCACHE", "secret-cache");

        SigV4Validator validator = new SigV4Validator(iamService);
        // Username with characters that require URL encoding exercises the
        // encoding path independently of the validator's decode logic
        String token = SigV4TokenTestHelper.createElastiCacheToken(
                "cache-cluster-01",
                "user+name@domain.com",
                "AKIDCACHE",
                "secret-cache",
                Instant.now().minusSeconds(60),
                900
        );

        assertTrue(validator.validate(token, "cache-cluster-01"));
    }

    @Test
    void validateRejectsTokenMissingActionParameter() throws Exception {
        IamService iamService = IamServiceTestHelper.iamServiceWithAccessKey("AKIDCACHE", "secret-cache");

        SigV4Validator validator = new SigV4Validator(iamService);
        String validToken = SigV4TokenTestHelper.createElastiCacheToken(
                "cache-cluster-01",
                "default",
                "AKIDCACHE",
                "secret-cache",
                Instant.now().minusSeconds(60),
                900
        );
        String withoutAction = validToken.replaceFirst("Action=connect&", "");

        assertFalse(validator.validate(withoutAction, "cache-cluster-01"));
    }

    @Test
    void validateRejectsTokenMissingSignatureParameter() throws Exception {
        IamService iamService = IamServiceTestHelper.iamServiceWithAccessKey("AKIDCACHE", "secret-cache");

        SigV4Validator validator = new SigV4Validator(iamService);
        String validToken = SigV4TokenTestHelper.createElastiCacheToken(
                "cache-cluster-01",
                "default",
                "AKIDCACHE",
                "secret-cache",
                Instant.now().minusSeconds(60),
                900
        );
        String withoutSignature = validToken.replaceFirst("&X-Amz-Signature=[0-9a-f]+", "");

        assertFalse(validator.validate(withoutSignature, "cache-cluster-01"));
    }
}
