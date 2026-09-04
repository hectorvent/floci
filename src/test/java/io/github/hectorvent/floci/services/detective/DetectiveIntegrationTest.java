package io.github.hectorvent.floci.services.detective;

import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@QuarkusTest
class DetectiveIntegrationTest {
    private static final String AUTH = "AWS4-HMAC-SHA256 Credential=AKID/20260904/us-east-1/detective/aws4_request";

    @BeforeAll
    static void configureRestAssured() { RestAssuredJsonUtils.configureAwsContentTypes(); }

    @Test
    void organizationGraphAndMemberLifecycle() {
        post("/orgs/enableAdminAccount", "{\"AccountId\":\"000000000000\"}").statusCode(200);
        String graphArn = post("/graphs/list", "{}").statusCode(200).body("GraphList", hasSize(1)).extract().path("GraphList[0].Arn");
        post("/orgs/updateOrganizationConfiguration", "{\"GraphArn\":\"" + graphArn + "\",\"AutoEnable\":true}").statusCode(200);
        post("/orgs/describeOrganizationConfiguration", "{\"GraphArn\":\"" + graphArn + "\"}").statusCode(200).body("AutoEnable", equalTo(true));
        post("/graph/members", "{\"GraphArn\":\"" + graphArn + "\",\"Accounts\":[{\"AccountId\":\"111111111111\",\"EmailAddress\":\"security@example.com\"}]}")
                .statusCode(200).body("Members[0].Status", equalTo("ACCEPTED_BUT_DISABLED"));
        post("/graph/member/monitoringstate", "{\"GraphArn\":\"" + graphArn + "\",\"AccountId\":\"111111111111\"}").statusCode(200);
        post("/graph/members/list", "{\"GraphArn\":\"" + graphArn + "\"}").statusCode(200).body("MemberDetails[0].Status", equalTo("ENABLED"));
    }

    @Test
    void invalidAdminAccountIdReturnsValidationError() {
        post("/orgs/enableAdminAccount", "{\"AccountId\":\"bad\"}")
                .statusCode(400).body("__type", equalTo("ValidationException"));
    }

    private static io.restassured.response.ValidatableResponse post(String path, String body) {
        return given().contentType("application/json").header("Authorization", AUTH).body(body).post(path).then();
    }
}
