package io.github.hectorvent.floci.services.ses;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * Integration tests for SES V2 ConfigurationSet endpoints under /v2/email/configuration-sets.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SesConfigurationSetV2IntegrationTest {

    private static final String AUTH_HEADER =
            "AWS4-HMAC-SHA256 Credential=AKID/20260101/us-east-1/ses/aws4_request";

    @Test
    @Order(1)
    void createConfigurationSet() {
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("""
                {
                  "ConfigurationSetName": "v2-cs-alpha",
                  "Tags": [{"Key": "env", "Value": "test"}]
                }
                """)
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(2)
    void createConfigurationSet_duplicateRejected() {
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("""
                {"ConfigurationSetName": "v2-cs-alpha"}
                """)
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(400)
            .body("__type", equalTo("AlreadyExistsException"));
    }

    @Test
    @Order(3)
    void createConfigurationSet_tagsNotArray() {
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("""
                {
                  "ConfigurationSetName": "v2-cs-bad-tags",
                  "Tags": "not-an-array"
                }
                """)
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(400)
            .body("__type", equalTo("BadRequestException"));
    }

    @Test
    @Order(4)
    void createConfigurationSet_missingName() {
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("{}")
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(400)
            .body("__type", equalTo("BadRequestException"));
    }

    @Test
    @Order(5)
    void getConfigurationSet_returnsRoundTrip() {
        given()
            .header("Authorization", AUTH_HEADER)
        .when()
            .get("/v2/email/configuration-sets/v2-cs-alpha")
        .then()
            .statusCode(200)
            .body("ConfigurationSetName", equalTo("v2-cs-alpha"))
            .body("Tags[0].Key", equalTo("env"))
            .body("Tags[0].Value", equalTo("test"));
    }

    @Test
    @Order(6)
    void getConfigurationSet_unknownReturns404() {
        given()
            .header("Authorization", AUTH_HEADER)
        .when()
            .get("/v2/email/configuration-sets/v2-cs-ghost")
        .then()
            .statusCode(404)
            .body("__type", equalTo("NotFoundException"));
    }

    @Test
    @Order(7)
    void listConfigurationSets() {
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("""
                {"ConfigurationSetName": "v2-cs-beta"}
                """)
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", AUTH_HEADER)
        .when()
            .get("/v2/email/configuration-sets")
        .then()
            .statusCode(200)
            .body("ConfigurationSets", hasItem("v2-cs-alpha"))
            .body("ConfigurationSets", hasItem("v2-cs-beta"));
    }

    @Test
    @Order(8)
    void deleteConfigurationSet() {
        given()
            .header("Authorization", AUTH_HEADER)
        .when()
            .delete("/v2/email/configuration-sets/v2-cs-alpha")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", AUTH_HEADER)
        .when()
            .get("/v2/email/configuration-sets/v2-cs-alpha")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(9)
    void deleteConfigurationSet_unknownReturns404() {
        given()
            .header("Authorization", AUTH_HEADER)
        .when()
            .delete("/v2/email/configuration-sets/v2-cs-ghost")
        .then()
            .statusCode(404)
            .body("__type", equalTo("NotFoundException"));
    }

    @Test
    @Order(10)
    void createConfigurationSet_invalidNameCharacters() {
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("""
                {"ConfigurationSetName": "bad name!"}
                """)
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(400)
            .body("__type", equalTo("BadRequestException"));
    }

    @Test
    @Order(11)
    void createConfigurationSet_nameTooLong() {
        String longName = "a".repeat(65);
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("""
                {"ConfigurationSetName": "%s"}
                """.formatted(longName))
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(400)
            .body("__type", equalTo("BadRequestException"));
    }

    @Test
    @Order(12)
    void createConfigurationSet_tagWithMissingValue_roundTripsAsAbsent() {
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("""
                {
                  "ConfigurationSetName": "v2-cs-tag-no-value",
                  "Tags": [{"Key": "env"}]
                }
                """)
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(200);

        given()
            .header("Authorization", AUTH_HEADER)
        .when()
            .get("/v2/email/configuration-sets/v2-cs-tag-no-value")
        .then()
            .statusCode(200)
            .body("Tags[0].Key", equalTo("env"));
    }

    @Test
    @Order(13)
    void createConfigurationSet_tagWithMissingKey_returns400() {
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("""
                {
                  "ConfigurationSetName": "v2-cs-bad-tag-key",
                  "Tags": [{"Value": "v"}]
                }
                """)
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(400)
            .body("__type", equalTo("BadRequestException"));
    }

    @Test
    @Order(14)
    void createConfigurationSet_tagKeyTooLong() {
        String longKey = "k".repeat(129);
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("""
                {
                  "ConfigurationSetName": "v2-cs-long-tag-key",
                  "Tags": [{"Key": "%s", "Value": "v"}]
                }
                """.formatted(longKey))
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(400)
            .body("__type", equalTo("BadRequestException"));
    }

    @Test
    @Order(15)
    void createConfigurationSet_tagValueTooLong() {
        String longValue = "v".repeat(257);
        given()
            .contentType("application/json")
            .header("Authorization", AUTH_HEADER)
            .body("""
                {
                  "ConfigurationSetName": "v2-cs-long-tag-value",
                  "Tags": [{"Key": "k", "Value": "%s"}]
                }
                """.formatted(longValue))
        .when()
            .post("/v2/email/configuration-sets")
        .then()
            .statusCode(400)
            .body("__type", equalTo("BadRequestException"));
    }
}
