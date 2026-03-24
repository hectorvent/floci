package io.github.hectorvent.floci.services.s3;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3MultipartIntegrationTest {

    private static final String BUCKET = "multipart-test-bucket";
    private static final String KEY = "large-file.bin";
    private static String uploadId;

    @Test
    @Order(1)
    void createBucket() {
        given()
            .when().put("/" + BUCKET)
            .then().statusCode(200);
    }

    @Test
    @Order(2)
    void initiateMultipartUpload() {
        uploadId = given()
            .contentType("application/octet-stream")
            .header("x-amz-meta-owner", "team-a")
            .header("x-amz-storage-class", "STANDARD_IA")
        .when()
            .post("/" + BUCKET + "/" + KEY + "?uploads")
        .then()
            .statusCode(200)
            .body(containsString("<UploadId>"))
            .body(containsString("<Bucket>" + BUCKET + "</Bucket>"))
            .body(containsString("<Key>" + KEY + "</Key>"))
            .extract().xmlPath().getString(
                "InitiateMultipartUploadResult.UploadId");
    }

    @Test
    @Order(3)
    void uploadPart1() {
        given()
            .body("Part1Data-Hello")
        .when()
            .put("/" + BUCKET + "/" + KEY + "?uploadId=" + uploadId + "&partNumber=1")
        .then()
            .statusCode(200)
            .header("ETag", notNullValue());
    }

    @Test
    @Order(4)
    void uploadPart2() {
        given()
            .body("Part2Data-World")
        .when()
            .put("/" + BUCKET + "/" + KEY + "?uploadId=" + uploadId + "&partNumber=2")
        .then()
            .statusCode(200)
            .header("ETag", notNullValue());
    }

    @Test
    @Order(5)
    void listMultipartUploads() {
        given()
        .when()
            .get("/" + BUCKET + "?uploads")
        .then()
            .statusCode(200)
            .body(containsString("<UploadId>" + uploadId + "</UploadId>"))
            .body(containsString("<Key>" + KEY + "</Key>"));
    }

    @Test
    @Order(6)
    void completeMultipartUpload() {
        String completeXml = """
                <CompleteMultipartUpload>
                    <Part><PartNumber>1</PartNumber><ETag>etag1</ETag></Part>
                    <Part><PartNumber>2</PartNumber><ETag>etag2</ETag></Part>
                </CompleteMultipartUpload>""";

        given()
            .contentType("application/xml")
            .body(completeXml)
        .when()
            .post("/" + BUCKET + "/" + KEY + "?uploadId=" + uploadId)
        .then()
            .statusCode(200)
            .body(containsString("<CompleteMultipartUploadResult"))
            .body(containsString("<ETag>"))
            .body(containsString("-2")); // Composite ETag ends with -2
    }

    @Test
    @Order(7)
    void getCompletedObject() {
        given()
        .when()
            .get("/" + BUCKET + "/" + KEY)
        .then()
            .statusCode(200)
            .header("x-amz-meta-owner", equalTo("team-a"))
            .header("x-amz-storage-class", equalTo("STANDARD_IA"))
            .body(equalTo("Part1Data-HelloPart2Data-World"));
    }

    @Test
    @Order(8)
    void getMultipartObjectAttributes() {
        given()
            .header("x-amz-object-attributes", "ObjectParts,Checksum,StorageClass")
            .header("x-amz-max-parts", 1)
        .when()
            .get("/" + BUCKET + "/" + KEY + "?attributes")
        .then()
            .statusCode(200)
            .body(containsString("<GetObjectAttributesResponse"))
            .body(containsString("<StorageClass>STANDARD_IA</StorageClass>"))
            .body(containsString("<ObjectParts>"))
            .body(containsString("<PartsCount>2</PartsCount>"))
            .body(containsString("<ChecksumSHA256>"));
    }

    @Test
    @Order(9)
    void multipartUploadNoLongerListed() {
        given()
        .when()
            .get("/" + BUCKET + "?uploads")
        .then()
            .statusCode(200)
            .body(not(containsString("<UploadId>")));
    }

    @Test
    @Order(10)
    void abortMultipartUpload() {
        // Initiate new upload
        String newUploadId = given()
            .when()
                .post("/" + BUCKET + "/abort-test.bin?uploads")
            .then()
                .statusCode(200)
                .extract().xmlPath().getString("InitiateMultipartUploadResult.UploadId");

        // Upload a part
        given()
            .body("some data")
        .when()
            .put("/" + BUCKET + "/abort-test.bin?uploadId=" + newUploadId + "&partNumber=1")
        .then()
            .statusCode(200);

        // Abort
        given()
        .when()
            .delete("/" + BUCKET + "/abort-test.bin?uploadId=" + newUploadId)
        .then()
            .statusCode(204);

        // Verify upload is gone
        given()
        .when()
            .get("/" + BUCKET + "?uploads")
        .then()
            .statusCode(200)
            .body(not(containsString(newUploadId)));
    }

    @Test
    @Order(11)
    void cleanUp() {
        given().when().delete("/" + BUCKET + "/" + KEY).then().statusCode(204);
        given().when().delete("/" + BUCKET).then().statusCode(204);
    }
}
