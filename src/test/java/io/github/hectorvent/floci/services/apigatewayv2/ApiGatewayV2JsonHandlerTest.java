package io.github.hectorvent.floci.services.apigatewayv2;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests for API Gateway v2 fixes:
 * - createDeployment stageName auto-deploy
 * - GetDeployment, DeleteDeployment, DeleteIntegration via REST path
 *
 * The JSON 1.1 handler's PascalCase normalization and missing switch cases
 * delegate to the same service methods tested here.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiGatewayV2JsonHandlerTest {

    private static String apiId;
    private static String integrationId;
    private static String deploymentId;

    @Test
    @Order(1)
    void createApi() {
        apiId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"v2-handler-test","protocolType":"HTTP"}
                        """)
                .when().post("/v2/apis")
                .then()
                .statusCode(201)
                .body("apiId", notNullValue())
                .extract().path("apiId");
    }

    @Test
    @Order(2)
    void createIntegration() {
        integrationId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"integrationType":"AWS_PROXY","integrationUri":"arn:aws:lambda:us-east-1:000000000000:function:test","payloadFormatVersion":"2.0"}
                        """)
                .when().post("/v2/apis/" + apiId + "/integrations")
                .then()
                .statusCode(201)
                .body("integrationId", notNullValue())
                .extract().path("integrationId");
    }

    @Test
    @Order(3)
    void createDeploymentAndStage() {
        deploymentId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"description":"initial"}
                        """)
                .when().post("/v2/apis/" + apiId + "/deployments")
                .then()
                .statusCode(201)
                .body("deploymentId", notNullValue())
                .extract().path("deploymentId");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"stageName":"prod","deploymentId":"%s"}
                        """.formatted(deploymentId))
                .when().post("/v2/apis/" + apiId + "/stages")
                .then()
                .statusCode(201)
                .body("stageName", equalTo("prod"))
                .body("deploymentId", equalTo(deploymentId));
    }

    @Test
    @Order(4)
    void getDeploymentReturnsCreatedDeployment() {
        given()
                .when().get("/v2/apis/" + apiId + "/deployments/" + deploymentId)
                .then()
                .statusCode(200)
                .body("deploymentId", equalTo(deploymentId))
                .body("description", equalTo("initial"));
    }

    @Test
    @Order(5)
    void createDeploymentWithStageNameAutoDeploysToStage() {
        String newDeploymentId = given()
                .contentType(ContentType.JSON)
                .body("""
                        {"description":"auto-deploy","stageName":"prod"}
                        """)
                .when().post("/v2/apis/" + apiId + "/deployments")
                .then()
                .statusCode(201)
                .body("deploymentId", notNullValue())
                .extract().path("deploymentId");

        // Verify the stage's deploymentId was updated
        given()
                .when().get("/v2/apis/" + apiId + "/stages/prod")
                .then()
                .statusCode(200)
                .body("deploymentId", equalTo(newDeploymentId))
                .body("deploymentId", not(equalTo(deploymentId)));
    }

    @Test
    @Order(6)
    void createDeploymentWithMissingStageName404s() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"description":"bad stage","stageName":"nonexistent"}
                        """)
                .when().post("/v2/apis/" + apiId + "/deployments")
                .then()
                .statusCode(404);
    }

    @Test
    @Order(7)
    void deleteIntegrationRemovesIntegration() {
        given().when().delete("/v2/apis/" + apiId + "/integrations/" + integrationId)
                .then().statusCode(204);

        given().when().get("/v2/apis/" + apiId + "/integrations/" + integrationId)
                .then().statusCode(404);
    }

    @Test
    @Order(8)
    void deleteDeploymentRemovesDeployment() {
        given().when().delete("/v2/apis/" + apiId + "/deployments/" + deploymentId)
                .then().statusCode(204);

        given().when().get("/v2/apis/" + apiId + "/deployments/" + deploymentId)
                .then().statusCode(404);
    }

    @Test
    @Order(9)
    void cleanup() {
        given().when().delete("/v2/apis/" + apiId + "/stages/prod").then().statusCode(204);
        given().when().delete("/v2/apis/" + apiId).then().statusCode(204);
    }
}
