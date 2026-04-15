package io.github.hectorvent.floci.services.s3;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3OwnershipControlsIntegrationTest {

    private static final String BUCKET = "ownership-controls-int-test";
    private static final String OWNERSHIP_CONTROLS_XML = """
            <OwnershipControls xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                <Rule>
                    <ObjectOwnership>BucketOwnerPreferred</ObjectOwnership>
                </Rule>
            </OwnershipControls>
            """;

    @Test
    @Order(1)
    void createBucket() {
        given()
        .when()
            .put("/" + BUCKET)
        .then()
            .statusCode(200);
    }

    @Test
    @Order(2)
    void getOwnershipControlsBeforePutReturns404() {
        given()
        .when()
            .get("/" + BUCKET + "?ownershipControls")
        .then()
            .statusCode(404)
            .body(containsString("OwnershipControlsNotFoundError"));
    }

    @Test
    @Order(3)
    void putOwnershipControlsReturns200() {
        given()
            .body(OWNERSHIP_CONTROLS_XML)
        .when()
            .put("/" + BUCKET + "?ownershipControls")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(4)
    void getOwnershipControlsReturnsStoredConfiguration() {
        given()
        .when()
            .get("/" + BUCKET + "?ownershipControls")
        .then()
            .statusCode(200)
            .body(containsString("<OwnershipControls"))
            .body(containsString("<ObjectOwnership>BucketOwnerPreferred</ObjectOwnership>"));
    }

    @Test
    @Order(5)
    void deleteOwnershipControlsReturns204() {
        given()
        .when()
            .delete("/" + BUCKET + "?ownershipControls")
        .then()
            .statusCode(204);
    }

    @Test
    @Order(6)
    void getOwnershipControlsAfterDeleteReturns404() {
        given()
        .when()
            .get("/" + BUCKET + "?ownershipControls")
        .then()
            .statusCode(404)
            .body(containsString("OwnershipControlsNotFoundError"));
    }
}
