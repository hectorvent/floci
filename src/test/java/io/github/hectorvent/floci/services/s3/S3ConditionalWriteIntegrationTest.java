package io.github.hectorvent.floci.services.s3;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class S3ConditionalWriteIntegrationTest {

    @Test
    void putObject_ifNoneMatchStar_succeedsWhenKeyMissing() {
        String bucket = createBucket("put-if-none-missing");

        given()
            .header("If-None-Match", "*")
            .body("first")
        .when()
            .put("/" + bucket + "/object.txt")
        .then()
            .statusCode(200)
            .header("ETag", notNullValue());

        assertObjectBody(bucket, "object.txt", "first");
    }

    @Test
    void putObject_ifNoneMatchStar_412WhenKeyExistsAndDoesNotOverwrite() {
        String bucket = createBucket("put-if-none-existing");
        putObject(bucket, "object.txt", "first");

        given()
            .header("If-None-Match", "*")
            .body("second")
        .when()
            .put("/" + bucket + "/object.txt")
        .then()
            .statusCode(412)
            .body(containsString("PreconditionFailed"));

        assertObjectBody(bucket, "object.txt", "first");
    }

    @Test
    void putObject_ifNoneMatchEtag_succeedsWhenEtagDiffers() {
        String bucket = createBucket("put-if-none-different");
        putObject(bucket, "object.txt", "first");

        given()
            .header("If-None-Match", "\"not-the-current-etag\"")
            .body("second")
        .when()
            .put("/" + bucket + "/object.txt")
        .then()
            .statusCode(200);

        assertObjectBody(bucket, "object.txt", "second");
    }

    @Test
    void putObject_ifNoneMatchEtag_412WhenEtagMatches() {
        String bucket = createBucket("put-if-none-match");
        String eTag = putObject(bucket, "object.txt", "first");

        given()
            .header("If-None-Match", eTag)
            .body("second")
        .when()
            .put("/" + bucket + "/object.txt")
        .then()
            .statusCode(412)
            .body(containsString("PreconditionFailed"));

        assertObjectBody(bucket, "object.txt", "first");
    }

    @Test
    void putObject_ifMatch_succeedsOnMatch() {
        String bucket = createBucket("put-if-match");
        String eTag = putObject(bucket, "object.txt", "first");

        given()
            .header("If-Match", eTag)
            .body("second")
        .when()
            .put("/" + bucket + "/object.txt")
        .then()
            .statusCode(200);

        assertObjectBody(bucket, "object.txt", "second");
    }

    @Test
    void putObject_ifMatch_412OnMismatch() {
        String bucket = createBucket("put-if-match-wrong");
        putObject(bucket, "object.txt", "first");

        given()
            .header("If-Match", "\"not-the-current-etag\"")
            .body("second")
        .when()
            .put("/" + bucket + "/object.txt")
        .then()
            .statusCode(412)
            .body(containsString("PreconditionFailed"));

        assertObjectBody(bucket, "object.txt", "first");
    }

    @Test
    void putObject_headerValueWithAndWithoutQuotes_bothHonoured() {
        String bucket = createBucket("put-quotes");
        String eTag = putObject(bucket, "object.txt", "first");

        given()
            .header("If-Match", stripQuotes(eTag))
            .body("second")
        .when()
            .put("/" + bucket + "/object.txt")
        .then()
            .statusCode(200);

        String currentETag = given()
            .when()
                .head("/" + bucket + "/object.txt")
            .then()
                .statusCode(200)
                .extract().header("ETag");

        given()
            .header("If-None-Match", stripQuotes(currentETag))
            .body("third")
        .when()
            .put("/" + bucket + "/object.txt")
        .then()
            .statusCode(412)
            .body(containsString("PreconditionFailed"));

        given()
            .header("If-None-Match", "\"*\"")
            .body("third")
        .when()
            .put("/" + bucket + "/object.txt")
        .then()
            .statusCode(412)
            .body(containsString("PreconditionFailed"));

        assertObjectBody(bucket, "object.txt", "second");
    }

    @Test
    void completeMultipartUpload_ifNoneMatchStar_412WhenKeyExists() {
        String bucket = createBucket("mpu-if-none-existing");
        putObject(bucket, "object.txt", "first");
        String uploadId = initiateMultipartUpload(bucket, "object.txt");
        uploadPart(bucket, "object.txt", uploadId, 1, "second");

        given()
            .contentType("application/xml")
            .header("If-None-Match", "*")
            .body(completeMultipartXml(1))
        .when()
            .post("/" + bucket + "/object.txt?uploadId=" + uploadId)
        .then()
            .statusCode(412)
            .body(containsString("PreconditionFailed"));

        assertObjectBody(bucket, "object.txt", "first");
    }

    @Test
    void completeMultipartUpload_ifMatch_succeedsOnMatch() {
        String bucket = createBucket("mpu-if-match");
        String eTag = putObject(bucket, "object.txt", "first");
        String uploadId = initiateMultipartUpload(bucket, "object.txt");
        uploadPart(bucket, "object.txt", uploadId, 1, "second");

        given()
            .contentType("application/xml")
            .header("If-Match", eTag)
            .body(completeMultipartXml(1))
        .when()
            .post("/" + bucket + "/object.txt?uploadId=" + uploadId)
        .then()
            .statusCode(200)
            .body(containsString("<CompleteMultipartUploadResult"));

        assertObjectBody(bucket, "object.txt", "second");
    }

    @Test
    void completeMultipartUpload_ifMatch_412OnMismatchAndDoesNotOverwrite() {
        String bucket = createBucket("mpu-if-match-wrong");
        putObject(bucket, "object.txt", "first");
        String uploadId = initiateMultipartUpload(bucket, "object.txt");
        uploadPart(bucket, "object.txt", uploadId, 1, "second");

        given()
            .contentType("application/xml")
            .header("If-Match", "\"not-the-current-etag\"")
            .body(completeMultipartXml(1))
        .when()
            .post("/" + bucket + "/object.txt?uploadId=" + uploadId)
        .then()
            .statusCode(412)
            .body(containsString("PreconditionFailed"));

        assertObjectBody(bucket, "object.txt", "first");
    }

    private static String createBucket(String label) {
        String bucket = "cond-" + label + "-" + UUID.randomUUID().toString().substring(0, 8);
        given()
        .when()
            .put("/" + bucket)
        .then()
            .statusCode(200);
        return bucket;
    }

    private static String putObject(String bucket, String key, String body) {
        return given()
            .body(body)
        .when()
            .put("/" + bucket + "/" + key)
        .then()
            .statusCode(200)
            .extract().header("ETag");
    }

    private static void assertObjectBody(String bucket, String key, String body) {
        given()
        .when()
            .get("/" + bucket + "/" + key)
        .then()
            .statusCode(200)
            .body(equalTo(body));
    }

    private static String initiateMultipartUpload(String bucket, String key) {
        return given()
            .contentType("application/octet-stream")
        .when()
            .post("/" + bucket + "/" + key + "?uploads")
        .then()
            .statusCode(200)
            .extract().xmlPath().getString("InitiateMultipartUploadResult.UploadId");
    }

    private static void uploadPart(String bucket, String key, String uploadId, int partNumber, String body) {
        given()
            .body(body)
        .when()
            .put("/" + bucket + "/" + key + "?uploadId=" + uploadId + "&partNumber=" + partNumber)
        .then()
            .statusCode(200)
            .header("ETag", notNullValue());
    }

    private static String completeMultipartXml(int partNumber) {
        return """
                <CompleteMultipartUpload>
                    <Part><PartNumber>%d</PartNumber><ETag>etag</ETag></Part>
                </CompleteMultipartUpload>""".formatted(partNumber);
    }

    private static String stripQuotes(String eTag) {
        return eTag.replace("\"", "");
    }
}
