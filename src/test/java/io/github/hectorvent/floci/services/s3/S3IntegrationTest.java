package io.github.hectorvent.floci.services.s3;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Arrays;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3IntegrationTest {

    @Test
    @Order(1)
    void createBucket() {
        given()
        .when()
            .put("/test-bucket")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(2)
    void createDuplicateBucketFails() {
        given()
        .when()
            .put("/test-bucket")
        .then()
            .statusCode(409)
            .body(containsString("BucketAlreadyOwnedByYou"));
    }

    @Test
    @Order(3)
    void listBuckets() {
        given()
        .when()
            .get("/")
        .then()
            .statusCode(200)
            .body(containsString("test-bucket"));
    }

    @Test
    @Order(4)
    void putObject() {
        given()
            .contentType("text/plain")
            .body("Hello World from S3!")
        .when()
            .put("/test-bucket/greeting.txt")
        .then()
            .statusCode(200)
            .header("ETag", notNullValue());
    }

    @Test
    @Order(5)
    void getObject() {
        given()
        .when()
            .get("/test-bucket/greeting.txt")
        .then()
            .statusCode(200)
            .header("ETag", notNullValue())
            .header("Content-Length", notNullValue())
            .body(equalTo("Hello World from S3!"));
    }

    @Test
    @Order(6)
    void headObject() {
        given()
        .when()
            .head("/test-bucket/greeting.txt")
        .then()
            .statusCode(200)
            .header("ETag", notNullValue())
            .header("Content-Length", notNullValue());
    }

    @Test
    @Order(7)
    void getObjectNotFound() {
        given()
        .when()
            .get("/test-bucket/nonexistent.txt")
        .then()
            .statusCode(404)
            .body(containsString("NoSuchKey"));
    }

    @Test
    @Order(8)
    void putAnotherObject() {
        given()
            .contentType("application/json")
            .body("{\"key\": \"value\"}")
        .when()
            .put("/test-bucket/data/config.json")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(9)
    void listObjects() {
        given()
        .when()
            .get("/test-bucket")
        .then()
            .statusCode(200)
            .body(containsString("greeting.txt"))
            .body(containsString("data/config.json"));
    }

    @Test
    @Order(10)
    void listObjectsWithPrefix() {
        given()
            .queryParam("prefix", "data/")
        .when()
            .get("/test-bucket")
        .then()
            .statusCode(200)
            .body(containsString("data/config.json"))
            .body(not(containsString("greeting.txt")));
    }

    @Test
    @Order(11)
    void copyObject() {
        given()
            .header("x-amz-copy-source", "/test-bucket/greeting.txt")
        .when()
            .put("/test-bucket/greeting-copy.txt")
        .then()
            .statusCode(200)
            .body(containsString("CopyObjectResult"));

        // Verify the copy
        given()
        .when()
            .get("/test-bucket/greeting-copy.txt")
        .then()
            .statusCode(200)
            .body(equalTo("Hello World from S3!"));
    }

    @Test
    @Order(12)
    void deleteObject() {
        given()
        .when()
            .delete("/test-bucket/greeting-copy.txt")
        .then()
            .statusCode(204);

        // Verify it's gone
        given()
        .when()
            .get("/test-bucket/greeting-copy.txt")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(13)
    void deleteNonEmptyBucketFails() {
        given()
        .when()
            .delete("/test-bucket")
        .then()
            .statusCode(409)
            .body(containsString("BucketNotEmpty"));
    }

    @Test
    @Order(14)
    void cleanupAndDeleteBucket() {
        // Delete all objects
        given().delete("/test-bucket/greeting.txt");
        given().delete("/test-bucket/data/config.json");

        // Now delete bucket
        given()
        .when()
            .delete("/test-bucket")
        .then()
            .statusCode(204);
    }

    @Test
    @Order(15)
    void putLargeObject() {
        // 22 MB – exceeds the old Jackson 20 MB maxStringLength default
        byte[] largeBody = new byte[22 * 1024 * 1024];
        Arrays.fill(largeBody, (byte) 'A');

        // Create a dedicated bucket so this test is self-contained
        given()
        .when()
            .put("/large-object-bucket")
        .then()
            .statusCode(200);

        given()
            .contentType("application/octet-stream")
            .body(largeBody)
        .when()
            .put("/large-object-bucket/large-file.bin")
        .then()
            .statusCode(200)
            .header("ETag", notNullValue());

        // Verify we can retrieve the same number of bytes back
        given()
        .when()
            .get("/large-object-bucket/large-file.bin")
        .then()
            .statusCode(200)
            .header("Content-Length", String.valueOf(largeBody.length));

        // Cleanup
        given().delete("/large-object-bucket/large-file.bin");
        given().delete("/large-object-bucket");
    }

    @Test
    void getNonExistentBucket() {
        given()
        .when()
            .get("/nonexistent-bucket")
        .then()
            .statusCode(404)
            .body(containsString("NoSuchBucket"));
    }
}
