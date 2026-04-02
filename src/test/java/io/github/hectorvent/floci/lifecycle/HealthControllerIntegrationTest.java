package io.github.hectorvent.floci.lifecycle;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class HealthControllerIntegrationTest {

    @Test
    void healthEndpoint_returnsVersionAndServices() {
        given()
        .when()
            .get("/_floci/health")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("version", notNullValue())
            .body("edition", equalTo("floci-open-source"))
            .body("services", notNullValue())
            .body("services.sqs", anyOf(equalTo("running"), equalTo("available")))
            .body("services.s3", anyOf(equalTo("running"), equalTo("available")))
            .body("services.dynamodb", anyOf(equalTo("running"), equalTo("available")))
            .body("services.ssm", anyOf(equalTo("running"), equalTo("available")))
            .body("services.sns", anyOf(equalTo("running"), equalTo("available")))
            .body("services.lambda", anyOf(equalTo("running"), equalTo("available")))
            .body("services.iam", anyOf(equalTo("running"), equalTo("available")))
            .body("services.kms", anyOf(equalTo("running"), equalTo("available")))
            .body("services.secretsmanager", anyOf(equalTo("running"), equalTo("available")))
            .body("services.elasticache", anyOf(equalTo("running"), equalTo("available")))
            .body("services.rds", anyOf(equalTo("running"), equalTo("available")));
    }

    @Test
    void healthEndpoint_localstackCompatPath() {
        given()
        .when()
            .get("/_localstack/health")
        .then()
            .statusCode(200)
            .contentType("application/json")
            .body("version", notNullValue())
            .body("edition", equalTo("floci-open-source"))
            .body("services", notNullValue());
    }
}
