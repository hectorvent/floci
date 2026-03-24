package io.github.hectorvent.floci.services.s3;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3RangeRequestIntegrationTest {

    private static final String BUCKET = "s3-range-request-test-bucket";
    private static final String KEY = "range-test.bin";
    // "Hello, World!" = 13 bytes
    private static final String CONTENT = "Hello, World!";

    @Test
    @Order(1)
    void setup() {
        given().when().put("/" + BUCKET).then().statusCode(200);
        given().body(CONTENT.getBytes()).contentType("application/octet-stream")
            .when().put("/" + BUCKET + "/" + KEY)
            .then().statusCode(200);
    }

    @Test
    @Order(2)
    void getFullObjectWithoutRangeHeader() {
        given()
        .when()
            .get("/" + BUCKET + "/" + KEY)
        .then()
            .statusCode(200)
            .body(equalTo(CONTENT))
            .header("Content-Length", String.valueOf(CONTENT.length()));
    }

    @Test
    @Order(3)
    void getRangeFromStart() {
        // bytes=0-4 → "Hello"
        given()
            .header("Range", "bytes=0-4")
        .when()
            .get("/" + BUCKET + "/" + KEY)
        .then()
            .statusCode(206)
            .body(equalTo("Hello"))
            .header("Content-Length", "5")
            .header("Content-Range", "bytes 0-4/13");
    }

    @Test
    @Order(4)
    void getRangeFromMiddle() {
        // bytes=7-11 → "World"
        given()
            .header("Range", "bytes=7-11")
        .when()
            .get("/" + BUCKET + "/" + KEY)
        .then()
            .statusCode(206)
            .body(equalTo("World"))
            .header("Content-Length", "5")
            .header("Content-Range", "bytes 7-11/13");
    }

    @Test
    @Order(5)
    void getSuffixRange() {
        // bytes=-6 → "World!" (last 6 bytes)
        given()
            .header("Range", "bytes=-6")
        .when()
            .get("/" + BUCKET + "/" + KEY)
        .then()
            .statusCode(206)
            .body(equalTo("World!"))
            .header("Content-Length", "6");
    }

    @Test
    @Order(6)
    void getOpenEndedRange() {
        // bytes=7- → "World!"
        given()
            .header("Range", "bytes=7-")
        .when()
            .get("/" + BUCKET + "/" + KEY)
        .then()
            .statusCode(206)
            .body(equalTo("World!"))
            .header("Content-Length", "6")
            .header("Content-Range", "bytes 7-12/13");
    }

    @Test
    @Order(7)
    void getRangeLastByte() {
        // bytes=-1 → "!" (last byte)
        given()
            .header("Range", "bytes=-1")
        .when()
            .get("/" + BUCKET + "/" + KEY)
        .then()
            .statusCode(206)
            .body(equalTo("!"))
            .header("Content-Length", "1");
    }

    @Test
    @Order(8)
    void getRangeBeyondEndIsClampedToFileSize() {
        // bytes=7-999 → should clamp to end of file
        given()
            .header("Range", "bytes=7-999")
        .when()
            .get("/" + BUCKET + "/" + KEY)
        .then()
            .statusCode(206)
            .body(equalTo("World!"))
            .header("Content-Length", "6")
            .header("Content-Range", "bytes 7-12/13");
    }

    @Test
    @Order(9)
    void headObjectReturnsAcceptRanges() {
        given()
        .when()
            .head("/" + BUCKET + "/" + KEY)
        .then()
            .statusCode(200)
            .header("Accept-Ranges", "bytes");
    }

    @Test
    @Order(10)
    void cleanUp() {
        given().when().delete("/" + BUCKET + "/" + KEY).then().statusCode(204);
        given().when().delete("/" + BUCKET).then().statusCode(204);
    }
}
