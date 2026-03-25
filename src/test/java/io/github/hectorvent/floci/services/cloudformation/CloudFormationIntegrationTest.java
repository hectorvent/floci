package io.github.hectorvent.floci.services.cloudformation;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class CloudFormationIntegrationTest {

    @Test
    void createStack_withS3AndSqs() {
        String template = """
            {
              "Resources": {
                "MyBucket": {
                  "Type": "AWS::S3::Bucket",
                  "Properties": {
                    "BucketName": "cf-test-bucket"
                  }
                },
                "MyQueue": {
                  "Type": "AWS::SQS::Queue",
                  "Properties": {
                    "QueueName": "cf-test-queue"
                  }
                }
              }
            }
            """;

        // 1. Create Stack
        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "CreateStack")
            .formParam("StackName", "test-stack")
            .formParam("TemplateBody", template)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("<StackId>"));

        // 2. Verify S3 Bucket exists
        given()
            .header("Host", "cf-test-bucket.localhost")
        .when()
            .get("/")
        .then()
            .statusCode(200);

        // 3. Verify SQS Queue exists
        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "GetQueueUrl")
            .formParam("QueueName", "cf-test-queue")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("cf-test-queue"));
        
        // 4. Describe Stacks
        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "DescribeStacks")
            .formParam("StackName", "test-stack")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("<StackName>test-stack</StackName>"))
            .body(containsString("<StackStatus>CREATE_COMPLETE</StackStatus>"));
    }

    @Test
    void createStack_lambdaWithS3Code() {
        byte[] zipBytes = buildHandlerZip();

        // Create S3 bucket
        given()
            .when()
            .put("/cfn-lambda-code-bucket")
        .then()
            .statusCode(200);

        // Upload ZIP to S3
        given()
            .contentType("application/zip")
            .body(zipBytes)
        .when()
            .put("/cfn-lambda-code-bucket/handler.zip")
        .then()
            .statusCode(200);

        String template = """
            {
              "Resources": {
                "MyFunction": {
                  "Type": "AWS::Lambda::Function",
                  "Properties": {
                    "FunctionName": "cfn-s3code-func",
                    "Runtime": "nodejs20.x",
                    "Handler": "index.handler",
                    "Code": {
                      "S3Bucket": "cfn-lambda-code-bucket",
                      "S3Key": "handler.zip"
                    },
                    "Role": "arn:aws:iam::000000000000:role/cfn-test-lambda-role"
                  }
                }
              }
            }
            """;

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "CreateStack")
            .formParam("StackName", "cfn-s3code-stack")
            .formParam("TemplateBody", template)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("<StackId>"));

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "DescribeStacks")
            .formParam("StackName", "cfn-s3code-stack")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("<StackStatus>CREATE_COMPLETE</StackStatus>"));

        // Verify Lambda function was created
        given()
        .when()
            .get("/2015-03-31/functions/cfn-s3code-func")
        .then()
            .statusCode(200)
            .body("Configuration.FunctionName", equalTo("cfn-s3code-func"));
    }

    @Test
    void createStack_lambdaWithNoCode() {
        String template = """
            {
              "Resources": {
                "MyFunction": {
                  "Type": "AWS::Lambda::Function",
                  "Properties": {
                    "FunctionName": "cfn-nocode-func",
                    "Runtime": "nodejs20.x",
                    "Handler": "index.handler",
                    "Role": "arn:aws:iam::000000000000:role/cfn-test-lambda-role"
                  }
                }
              }
            }
            """;

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "CreateStack")
            .formParam("StackName", "cfn-nocode-stack")
            .formParam("TemplateBody", template)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("<StackId>"));

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "DescribeStacks")
            .formParam("StackName", "cfn-nocode-stack")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("<StackStatus>CREATE_COMPLETE</StackStatus>"));

        given()
        .when()
            .get("/2015-03-31/functions/cfn-nocode-func")
        .then()
            .statusCode(200)
            .body("Configuration.FunctionName", equalTo("cfn-nocode-func"));
    }

    @Test
    void createStack_lambdaWithImageUri() {
        String template = """
            {
              "Resources": {
                "MyFunction": {
                  "Type": "AWS::Lambda::Function",
                  "Properties": {
                    "FunctionName": "cfn-image-func",
                    "Handler": "index.handler",
                    "Code": {
                      "ImageUri": "123456789012.dkr.ecr.us-east-1.amazonaws.com/my-repo:latest"
                    },
                    "Role": "arn:aws:iam::000000000000:role/cfn-test-lambda-role"
                  }
                }
              }
            }
            """;

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "CreateStack")
            .formParam("StackName", "cfn-image-stack")
            .formParam("TemplateBody", template)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("<StackId>"));

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "DescribeStacks")
            .formParam("StackName", "cfn-image-stack")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("<StackStatus>CREATE_COMPLETE</StackStatus>"));
    }

    @Test
    void createStack_lambdaWithZipFile() {
        String base64Zip = Base64.getEncoder().encodeToString(buildHandlerZip());

        String template = """
            {
              "Resources": {
                "MyFunction": {
                  "Type": "AWS::Lambda::Function",
                  "Properties": {
                    "FunctionName": "cfn-zipfile-func",
                    "Runtime": "nodejs20.x",
                    "Handler": "index.handler",
                    "Code": {
                      "ZipFile": "%s"
                    },
                    "Role": "arn:aws:iam::000000000000:role/cfn-test-lambda-role"
                  }
                }
              }
            }
            """.formatted(base64Zip);

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "CreateStack")
            .formParam("StackName", "cfn-zipfile-stack")
            .formParam("TemplateBody", template)
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("<StackId>"));

        given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("Action", "DescribeStacks")
            .formParam("StackName", "cfn-zipfile-stack")
        .when()
            .post("/")
        .then()
            .statusCode(200)
            .body(containsString("<StackStatus>CREATE_COMPLETE</StackStatus>"));

        given()
        .when()
            .get("/2015-03-31/functions/cfn-zipfile-func")
        .then()
            .statusCode(200)
            .body("Configuration.FunctionName", equalTo("cfn-zipfile-func"));
    }

    private static byte[] buildHandlerZip() {
        try {
            var baos = new ByteArrayOutputStream();
            try (var zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("index.js"));
                zos.write("exports.handler=async(e)=>({statusCode:200})".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
