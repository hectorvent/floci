package com.floci.test;

import org.junit.jupiter.api.*;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Cognito SRP Authentication (Issue #310)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CognitoSrpTest {

    private static final BigInteger G = BigInteger.valueOf(2);
    private static final BigInteger N = new BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
            "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
            "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
            "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
            "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
            "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
            "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
            "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
            "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
            "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
            "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64" +
            "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
            "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B" +
            "F12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
            "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31" +
            "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF", 16);

    private static CognitoIdentityProviderClient cognito;
    private static String poolId;
    private static String clientId;
    private static final String USERNAME = "srp-user-" + UUID.randomUUID();
    private static final String PASSWORD = "SrpPass1!";

    @BeforeAll
    static void setup() {
        cognito = TestFixtures.cognitoClient();
    }

    @AfterAll
    static void cleanup() {
        if (cognito == null) return;
        try {
            if (poolId != null) {
                cognito.deleteUserPool(b -> b.userPoolId(poolId));
            }
        } catch (Exception ignored) {}
        cognito.close();
    }

    @Test
    @Order(1)
    void createPoolAndClient() {
        CreateUserPoolResponse poolResp = cognito.createUserPool(b -> b.poolName("srp-test-pool"));
        poolId = poolResp.userPool().id();

        CreateUserPoolClientResponse clientResp = cognito.createUserPoolClient(b -> b
                .userPoolId(poolId)
                .clientName("srp-test-client")
                .explicitAuthFlows(ExplicitAuthFlowsType.ALLOW_USER_SRP_AUTH));
        clientId = clientResp.userPoolClient().clientId();

        assertThat(poolId).isNotBlank();
        assertThat(clientId).isNotBlank();
    }

    @Test
    @Order(2)
    void createUser() {
        cognito.adminCreateUser(b -> b
                .userPoolId(poolId)
                .username(USERNAME)
                .messageAction(MessageActionType.SUPPRESS));
        
        cognito.adminSetUserPassword(b -> b
                .userPoolId(poolId)
                .username(USERNAME)
                .password(PASSWORD)
                .permanent(true));
    }

    @Test
    @Order(3)
    @DisplayName("Verify USER_SRP_AUTH returns PASSWORD_VERIFIER challenge")
    void srpAuthReturnsChallenge() {
        BigInteger a = new BigInteger(256, new SecureRandom());
        BigInteger A = G.modPow(a, N);

        InitiateAuthResponse authResp = cognito.initiateAuth(b -> b
                .authFlow(AuthFlowType.USER_SRP_AUTH)
                .clientId(clientId)
                .authParameters(Map.of(
                        "USERNAME", USERNAME,
                        "SRP_A", A.toString(16)
                )));

        assertThat(authResp.challengeName()).isEqualTo(ChallengeNameType.PASSWORD_VERIFIER);
        Map<String, String> params = authResp.challengeParameters();
        assertThat(params).containsKey("SRP_B");
        assertThat(params).containsKey("SALT");
        assertThat(params).containsKey("SECRET_BLOCK");
    }
}
