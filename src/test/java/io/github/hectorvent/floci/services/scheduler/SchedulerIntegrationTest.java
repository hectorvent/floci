package io.github.hectorvent.floci.services.scheduler;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchedulerIntegrationTest {

    @Test
    @Order(1)
    void createScheduleGroup() {
        given()
            .contentType("application/json")
            .body("{\"ClientToken\":\"ct-1\"}")
        .when()
            .post("/schedule-groups/my-group")
        .then()
            .statusCode(200)
            .body("ScheduleGroupArn", containsString("schedule-group/my-group"))
            .body("ScheduleGroupArn", containsString(":scheduler:"));
    }

    @Test
    @Order(2)
    void createScheduleGroupWithTags() {
        given()
            .contentType("application/json")
            .body("""
                {
                    "ClientToken": "ct-2",
                    "Tags": [
                        {"Key": "env", "Value": "test"},
                        {"Key": "team", "Value": "platform"}
                    ]
                }
                """)
        .when()
            .post("/schedule-groups/tagged-group")
        .then()
            .statusCode(200)
            .body("ScheduleGroupArn", containsString("schedule-group/tagged-group"));
    }

    @Test
    @Order(3)
    void createScheduleGroupDuplicateReturns409() {
        given()
            .contentType("application/json")
            .body("{}")
        .when()
            .post("/schedule-groups/my-group")
        .then()
            .statusCode(409);
    }

    @Test
    @Order(4)
    void createScheduleGroupReservedDefaultNameReturns409() {
        given()
            .contentType("application/json")
            .body("{}")
        .when()
            .post("/schedule-groups/default")
        .then()
            .statusCode(409);
    }

    @Test
    @Order(5)
    void getScheduleGroup() {
        given()
        .when()
            .get("/schedule-groups/my-group")
        .then()
            .statusCode(200)
            .body("Name", equalTo("my-group"))
            .body("State", equalTo("ACTIVE"))
            .body("Arn", containsString("schedule-group/my-group"))
            .body("CreationDate", notNullValue())
            .body("LastModificationDate", notNullValue());
    }

    @Test
    @Order(6)
    void getDefaultScheduleGroupIsAutoCreated() {
        given()
        .when()
            .get("/schedule-groups/default")
        .then()
            .statusCode(200)
            .body("Name", equalTo("default"))
            .body("State", equalTo("ACTIVE"));
    }

    @Test
    @Order(7)
    void getScheduleGroupNotFoundReturns404() {
        given()
        .when()
            .get("/schedule-groups/nonexistent-group")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(8)
    void listScheduleGroupsIncludesDefault() {
        given()
        .when()
            .get("/schedule-groups")
        .then()
            .statusCode(200)
            .body("ScheduleGroups.Name", hasItem("default"))
            .body("ScheduleGroups.Name", hasItem("my-group"))
            .body("ScheduleGroups.Name", hasItem("tagged-group"));
    }

    @Test
    @Order(9)
    void listScheduleGroupsWithPrefix() {
        given()
            .queryParam("NamePrefix", "tag")
        .when()
            .get("/schedule-groups")
        .then()
            .statusCode(200)
            .body("ScheduleGroups.Name", hasItem("tagged-group"))
            .body("ScheduleGroups.Name", not(hasItem("my-group")))
            .body("ScheduleGroups.Name", not(hasItem("default")));
    }

    @Test
    @Order(10)
    void deleteScheduleGroup() {
        given()
        .when()
            .delete("/schedule-groups/my-group")
        .then()
            .statusCode(200);

        given()
        .when()
            .get("/schedule-groups/my-group")
        .then()
            .statusCode(404);
    }

    @Test
    @Order(11)
    void deleteDefaultScheduleGroupReturns400() {
        given()
        .when()
            .delete("/schedule-groups/default")
        .then()
            .statusCode(400);
    }

    @Test
    @Order(12)
    void deleteScheduleGroupNotFoundReturns404() {
        given()
        .when()
            .delete("/schedule-groups/already-gone")
        .then()
            .statusCode(404);
    }
}
