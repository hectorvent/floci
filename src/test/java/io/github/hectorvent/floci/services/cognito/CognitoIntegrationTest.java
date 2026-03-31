package io.github.hectorvent.floci.services.cognito;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.EncoderConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CognitoIntegrationTest {

    private static final String COGNITO_CONTENT_TYPE = "application/x-amz-json-1.1";
    private static final String TARGET_PREFIX = "AWSCognitoIdentityProviderService.";

    private static String userPoolId;
    private static String clientId;

    @BeforeAll
    static void configureRestAssured() {
        RestAssured.config = RestAssured.config().encoderConfig(
                EncoderConfig.encoderConfig()
                        .encodeContentTypeAs(COGNITO_CONTENT_TYPE, ContentType.TEXT));
    }

    @Test
    @Order(1)
    void createUserPool() {
        userPoolId = given()
            .header("X-Amz-Target", TARGET_PREFIX + "CreateUserPool")
            .contentType(COGNITO_CONTENT_TYPE)
            .body("{\"PoolName\":\"TestPool\"}")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .extract().path("UserPool.Id");
    }

    @Test
    @Order(2)
    void createUserPoolClient() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"ClientName\":\"test-client\"}", userPoolId);

        clientId = given()
            .header("X-Amz-Target", TARGET_PREFIX + "CreateUserPoolClient")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .extract().path("UserPoolClient.ClientId");
    }

    @Test
    @Order(3)
    void adminCreateUser() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"Username\":\"alice\",\"TemporaryPassword\":\"TempPass1!\","
                + "\"UserAttributes\":[{\"Name\":\"email\",\"Value\":\"alice@example.com\"}]}",
                userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "AdminCreateUser")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(4)
    void adminSetUserPassword() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"Username\":\"alice\",\"Password\":\"Perm1234!\",\"Permanent\":true}",
                userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "AdminSetUserPassword")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(10)
    void createGroup() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"GroupName\":\"admin\",\"Description\":\"Admin group\",\"Precedence\":1}",
                userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "CreateGroup")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Group.GroupName", equalTo("admin"))
            .body("Group.UserPoolId", equalTo(userPoolId))
            .body("Group.Description", equalTo("Admin group"))
            .body("Group.Precedence", equalTo(1));
    }

    @Test
    @Order(11)
    void createGroupDuplicate() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"GroupName\":\"admin\",\"Description\":\"Admin group\",\"Precedence\":1}",
                userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "CreateGroup")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(400);
    }

    @Test
    @Order(12)
    void getGroup() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"GroupName\":\"admin\"}", userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "GetGroup")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Group.GroupName", equalTo("admin"));
    }

    @Test
    @Order(13)
    void listGroups() {
        String body = String.format("{\"UserPoolId\":\"%s\"}", userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "ListGroups")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Groups.size()", equalTo(1))
            .body("Groups[0].GroupName", equalTo("admin"));
    }

    @Test
    @Order(14)
    void adminAddUserToGroup() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"GroupName\":\"admin\",\"Username\":\"alice\"}", userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "AdminAddUserToGroup")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(15)
    void adminListGroupsForUser() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"Username\":\"alice\"}", userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "AdminListGroupsForUser")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Groups.size()", equalTo(1))
            .body("Groups[0].GroupName", equalTo("admin"));
    }

    @Test
    @Order(16)
    void authenticateAndVerifyGroupsInToken() {
        String body = String.format(
                "{\"AuthFlow\":\"USER_PASSWORD_AUTH\",\"ClientId\":\"%s\","
                + "\"AuthParameters\":{\"USERNAME\":\"alice\",\"PASSWORD\":\"Perm1234!\"}}",
                clientId);

        String accessToken = given()
            .header("X-Amz-Target", TARGET_PREFIX + "InitiateAuth")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .extract().path("AuthenticationResult.AccessToken");

        String[] parts = accessToken.split("\\.");
        String payload = new String(
                Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

        Assertions.assertTrue(payload.contains("\"cognito:groups\""),
                "JWT payload should contain cognito:groups claim");
        Assertions.assertTrue(payload.contains("\"admin\""),
                "JWT payload should contain admin group");
    }

    @Test
    @Order(17)
    void adminRemoveUserFromGroup() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"GroupName\":\"admin\",\"Username\":\"alice\"}", userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "AdminRemoveUserFromGroup")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(18)
    void adminListGroupsForUserEmpty() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"Username\":\"alice\"}", userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "AdminListGroupsForUser")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body("Groups.size()", equalTo(0));
    }

    @Test
    @Order(19)
    void deleteGroup() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"GroupName\":\"admin\"}", userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "DeleteGroup")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(200);
    }

    @Test
    @Order(20)
    void getGroupNotFound() {
        String body = String.format(
                "{\"UserPoolId\":\"%s\",\"GroupName\":\"admin\"}", userPoolId);

        given()
            .header("X-Amz-Target", TARGET_PREFIX + "GetGroup")
            .contentType(COGNITO_CONTENT_TYPE)
            .body(body)
        .when()
            .post("/")
        .then()
            .statusCode(404);
    }
}
