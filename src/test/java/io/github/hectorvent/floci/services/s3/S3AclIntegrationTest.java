package io.github.hectorvent.floci.services.s3;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3AclIntegrationTest {

    private static final String BUCKET = "acl-test-bucket";
    private static final String ALL_USERS_GROUP_URI = "http://acs.amazonaws.com/groups/global/AllUsers";
    private static final String AUTHENTICATED_USERS_GROUP_URI = "http://acs.amazonaws.com/groups/global/AuthenticatedUsers";
    private static String multipartUploadId;

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
    void putObjectAppliesPublicReadAcl() {
        given()
            .header("x-amz-acl", "public-read")
            .body("public body")
        .when()
            .put("/" + BUCKET + "/public.txt")
        .then()
            .statusCode(200);

        given()
        .when()
            .get("/" + BUCKET + "/public.txt?acl")
        .then()
            .statusCode(200)
            .body(containsString(ALL_USERS_GROUP_URI))
            .body(containsString("<Permission>READ</Permission>"))
            .body(containsString("<Permission>FULL_CONTROL</Permission>"));
    }

    @Test
    @Order(3)
    void copyObjectWithoutAclDefaultsToPrivateAcl() {
        given()
            .body("copy me")
        .when()
            .put("/" + BUCKET + "/copy-source.txt")
        .then()
            .statusCode(200);

        given()
            .header("x-amz-copy-source", "/" + BUCKET + "/copy-source.txt")
        .when()
            .put("/" + BUCKET + "/copy-default-private.txt")
        .then()
            .statusCode(200);

        given()
        .when()
            .get("/" + BUCKET + "/copy-default-private.txt?acl")
        .then()
            .statusCode(200)
            .body(not(containsString(ALL_USERS_GROUP_URI)))
            .body(containsString("<Permission>FULL_CONTROL</Permission>"));
    }

    @Test
    @Order(4)
    void copyObjectAppliesRequestedAuthenticatedReadAcl() {
        given()
            .header("x-amz-copy-source", "/" + BUCKET + "/copy-source.txt")
            .header("x-amz-acl", "authenticated-read")
        .when()
            .put("/" + BUCKET + "/copy-authenticated.txt")
        .then()
            .statusCode(200);

        given()
        .when()
            .get("/" + BUCKET + "/copy-authenticated.txt?acl")
        .then()
            .statusCode(200)
            .body(containsString(AUTHENTICATED_USERS_GROUP_URI))
            .body(containsString("<Permission>READ</Permission>"))
            .body(not(containsString(ALL_USERS_GROUP_URI)));
    }

    @Test
    @Order(5)
    void initiateMultipartUploadAppliesRequestedAclOnComplete() {
        multipartUploadId = given()
            .header("x-amz-acl", "public-read")
        .when()
            .post("/" + BUCKET + "/multipart-public.txt?uploads")
        .then()
            .statusCode(200)
            .extract().xmlPath().getString("InitiateMultipartUploadResult.UploadId");

        given()
            .body("part-one")
        .when()
            .put("/" + BUCKET + "/multipart-public.txt?uploadId=" + multipartUploadId + "&partNumber=1")
        .then()
            .statusCode(200);

        String completeXml = """
                <CompleteMultipartUpload>
                    <Part><PartNumber>1</PartNumber><ETag>etag1</ETag></Part>
                </CompleteMultipartUpload>""";

        given()
            .contentType("application/xml")
            .body(completeXml)
        .when()
            .post("/" + BUCKET + "/multipart-public.txt?uploadId=" + multipartUploadId)
        .then()
            .statusCode(200);

        given()
        .when()
            .get("/" + BUCKET + "/multipart-public.txt?acl")
        .then()
            .statusCode(200)
            .body(containsString(ALL_USERS_GROUP_URI))
            .body(containsString("<Permission>READ</Permission>"));
    }

    @Test
    @Order(6)
    void putObjectRejectsUnsupportedCannedAcl() {
        given()
            .header("x-amz-acl", "totally-unsupported")
            .body("bad acl")
        .when()
            .put("/" + BUCKET + "/invalid-acl.txt")
        .then()
            .statusCode(400)
            .body(containsString("InvalidArgument"))
            .body(containsString("Unsupported x-amz-acl value"));
    }

    @Test
    @Order(7)
    void initiateMultipartUploadRejectsUnsupportedCannedAcl() {
        given()
            .header("x-amz-acl", "totally-unsupported")
        .when()
            .post("/" + BUCKET + "/invalid-multipart.txt?uploads")
        .then()
            .statusCode(400)
            .body(containsString("InvalidArgument"))
            .body(containsString("Unsupported x-amz-acl value"));
    }
}
