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
class S3NotificationIntegrationTest {

    private static final String BUCKET = "notif-test-bucket";

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
    void getNotificationReturnsEmptyByDefault() {
        given()
            .queryParam("notification", "")
        .when()
            .get("/" + BUCKET)
        .then()
            .statusCode(200)
            .body(containsString("NotificationConfiguration"))
            .body(not(containsString("QueueConfiguration")))
            .body(not(containsString("TopicConfiguration")));
    }

    @Test
    @Order(3)
    void putQueueNotificationWithoutFilter() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Id>no-filter</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:plain-queue</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;
        given()
            .queryParam("notification", "")
            .contentType("application/xml")
            .body(xml)
        .when()
            .put("/" + BUCKET)
        .then()
            .statusCode(200);

        given()
            .queryParam("notification", "")
        .when()
            .get("/" + BUCKET)
        .then()
            .statusCode(200)
            .body(containsString("<Id>no-filter</Id>"))
            .body(containsString("plain-queue"))
            .body(containsString("s3:ObjectCreated:*"))
            .body(not(containsString("<Filter>")));
    }

    @Test
    @Order(4)
    void putQueueNotificationWithPrefixFilter() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Id>prefix-only</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:prefix-queue</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>prefix</Name>
                          <Value>images/</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;
        given()
            .queryParam("notification", "")
            .contentType("application/xml")
            .body(xml)
        .when()
            .put("/" + BUCKET)
        .then()
            .statusCode(200);

        given()
            .queryParam("notification", "")
        .when()
            .get("/" + BUCKET)
        .then()
            .statusCode(200)
            .body(containsString("<Id>prefix-only</Id>"))
            .body(containsString("<Filter>"))
            .body(containsString("<Name>prefix</Name>"))
            .body(containsString("<Value>images/</Value>"))
            .body(not(containsString("<Name>suffix</Name>")));
    }

    @Test
    @Order(5)
    void putQueueNotificationWithSuffixFilter() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Id>suffix-only</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:suffix-queue</Queue>
                    <Event>s3:ObjectRemoved:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>suffix</Name>
                          <Value>.jpg</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;
        given()
            .queryParam("notification", "")
            .contentType("application/xml")
            .body(xml)
        .when()
            .put("/" + BUCKET)
        .then()
            .statusCode(200);

        given()
            .queryParam("notification", "")
        .when()
            .get("/" + BUCKET)
        .then()
            .statusCode(200)
            .body(containsString("<Id>suffix-only</Id>"))
            .body(containsString("<Name>suffix</Name>"))
            .body(containsString("<Value>.jpg</Value>"))
            .body(not(containsString("<Name>prefix</Name>")));
    }

    @Test
    @Order(6)
    void putQueueNotificationWithBothPrefixAndSuffix() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Id>both-filters</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:both-queue</Queue>
                    <Event>s3:ObjectCreated:Put</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>prefix</Name>
                          <Value>uploads/</Value>
                        </FilterRule>
                        <FilterRule>
                          <Name>suffix</Name>
                          <Value>.png</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;
        given()
            .queryParam("notification", "")
            .contentType("application/xml")
            .body(xml)
        .when()
            .put("/" + BUCKET)
        .then()
            .statusCode(200);

        given()
            .queryParam("notification", "")
        .when()
            .get("/" + BUCKET)
        .then()
            .statusCode(200)
            .body(containsString("<Id>both-filters</Id>"))
            .body(containsString("<Name>prefix</Name>"))
            .body(containsString("<Value>uploads/</Value>"))
            .body(containsString("<Name>suffix</Name>"))
            .body(containsString("<Value>.png</Value>"));
    }

    @Test
    @Order(7)
    void putMultipleQueueConfigsWithDifferentFilters() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Id>config-a</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:queue-a</Queue>
                    <Event>s3:ObjectCreated:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>prefix</Name>
                          <Value>logs/</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                  <QueueConfiguration>
                    <Id>config-b</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:queue-b</Queue>
                    <Event>s3:ObjectRemoved:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>suffix</Name>
                          <Value>.csv</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;
        given()
            .queryParam("notification", "")
            .contentType("application/xml")
            .body(xml)
        .when()
            .put("/" + BUCKET)
        .then()
            .statusCode(200);

        String body = given()
            .queryParam("notification", "")
        .when()
            .get("/" + BUCKET)
        .then()
            .statusCode(200)
            .body(containsString("config-a"))
            .body(containsString("config-b"))
            .body(containsString("queue-a"))
            .body(containsString("queue-b"))
            .extract().body().asString();

        // Verify both configs are present with their respective filters
        assert body.contains("logs/") : "Expected prefix filter logs/";
        assert body.contains(".csv") : "Expected suffix filter .csv";
    }

    @Test
    @Order(8)
    void putTopicNotificationWithFilter() {
        String xml = """
                <NotificationConfiguration>
                  <TopicConfiguration>
                    <Id>topic-filter</Id>
                    <Topic>arn:aws:sns:us-east-1:000000000000:my-topic</Topic>
                    <Event>s3:ObjectCreated:*</Event>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>prefix</Name>
                          <Value>data/</Value>
                        </FilterRule>
                        <FilterRule>
                          <Name>suffix</Name>
                          <Value>.json</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                  </TopicConfiguration>
                </NotificationConfiguration>
                """;
        given()
            .queryParam("notification", "")
            .contentType("application/xml")
            .body(xml)
        .when()
            .put("/" + BUCKET)
        .then()
            .statusCode(200);

        given()
            .queryParam("notification", "")
        .when()
            .get("/" + BUCKET)
        .then()
            .statusCode(200)
            .body(containsString("<TopicConfiguration>"))
            .body(containsString("<Id>topic-filter</Id>"))
            .body(containsString("my-topic"))
            .body(containsString("<Name>prefix</Name>"))
            .body(containsString("<Value>data/</Value>"))
            .body(containsString("<Name>suffix</Name>"))
            .body(containsString("<Value>.json</Value>"));
    }

    @Test
    @Order(9)
    void putNotificationWithFilterBetweenOtherElements() {
        String xml = """
                <NotificationConfiguration>
                  <QueueConfiguration>
                    <Id>interleaved</Id>
                    <Queue>arn:aws:sqs:us-east-1:000000000000:interleaved-queue</Queue>
                    <Filter>
                      <S3Key>
                        <FilterRule>
                          <Name>suffix</Name>
                          <Value>.txt</Value>
                        </FilterRule>
                      </S3Key>
                    </Filter>
                    <Event>s3:ObjectCreated:Put</Event>
                  </QueueConfiguration>
                </NotificationConfiguration>
                """;
        given()
            .queryParam("notification", "")
            .contentType("application/xml")
            .body(xml)
        .when()
            .put("/" + BUCKET)
        .then()
            .statusCode(200);

        given()
            .queryParam("notification", "")
        .when()
            .get("/" + BUCKET)
        .then()
            .statusCode(200)
            .body(containsString("<Id>interleaved</Id>"))
            .body(containsString("interleaved-queue"))
            .body(containsString("s3:ObjectCreated:Put"))
            .body(containsString("<Name>suffix</Name>"))
            .body(containsString("<Value>.txt</Value>"));
    }

    @Test
    @Order(10)
    void putEmptyNotificationClearsConfig() {
        String xml = """
                <NotificationConfiguration>
                </NotificationConfiguration>
                """;
        given()
            .queryParam("notification", "")
            .contentType("application/xml")
            .body(xml)
        .when()
            .put("/" + BUCKET)
        .then()
            .statusCode(200);

        given()
            .queryParam("notification", "")
        .when()
            .get("/" + BUCKET)
        .then()
            .statusCode(200)
            .body(not(containsString("QueueConfiguration")))
            .body(not(containsString("TopicConfiguration")));
    }

    @Test
    @Order(99)
    void cleanup() {
        given().delete("/" + BUCKET);
    }
}
