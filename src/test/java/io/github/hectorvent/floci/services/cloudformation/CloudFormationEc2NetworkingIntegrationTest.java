package io.github.hectorvent.floci.services.cloudformation;

import io.github.hectorvent.floci.core.common.XmlParser;
import io.github.hectorvent.floci.testing.RestAssuredJsonUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A public/private networking stack the way CDK lays one out: VPC, internet gateway and attachment,
 * a public subnet with a route table routing {@code 0.0.0.0/0} to the gateway and an association, an
 * EIP and a NAT gateway in that subnet, and a private route table routing to the NAT. Asserts the
 * {@code Ref} shapes ({@code Route} is {@code <RouteTableId>|<destination>}, {@code EIP} the public IP)
 * and the {@code Fn::GetAtt} attributes, then that {@code DeleteStack} removes every one of the seven
 * networking resources, which the legacy switch left behind.
 */
@QuarkusTest
class CloudFormationEc2NetworkingIntegrationTest {

    private static final String CFN_AUTH =
            "AWS4-HMAC-SHA256 Credential=test/20260907/us-east-1/cloudformation/aws4_request";
    private static final String EC2_AUTH =
            "AWS4-HMAC-SHA256 Credential=test/20260907/us-east-1/ec2/aws4_request";
    private static final String STACK = "ec2-networking-it";

    private static final String TEMPLATE = """
        {
          "Resources": {
            "Vpc": {"Type": "AWS::EC2::VPC", "Properties": {"CidrBlock": "10.40.0.0/16"}},
            "Igw": {"Type": "AWS::EC2::InternetGateway"},
            "Attach": {"Type": "AWS::EC2::VPCGatewayAttachment",
                       "Properties": {"VpcId": {"Ref": "Vpc"}, "InternetGatewayId": {"Ref": "Igw"}}},
            "PublicSubnet": {"Type": "AWS::EC2::Subnet",
                             "Properties": {"VpcId": {"Ref": "Vpc"}, "CidrBlock": "10.40.0.0/24",
                                            "AvailabilityZone": "us-east-1a", "MapPublicIpOnLaunch": true}},
            "PublicRt": {"Type": "AWS::EC2::RouteTable", "Properties": {"VpcId": {"Ref": "Vpc"}}},
            "PublicRoute": {"Type": "AWS::EC2::Route", "DependsOn": "Attach",
                            "Properties": {"RouteTableId": {"Ref": "PublicRt"}, "DestinationCidrBlock": "0.0.0.0/0",
                                           "GatewayId": {"Ref": "Igw"}}},
            "PublicAssoc": {"Type": "AWS::EC2::SubnetRouteTableAssociation",
                            "Properties": {"RouteTableId": {"Ref": "PublicRt"}, "SubnetId": {"Ref": "PublicSubnet"}}},
            "NatEip": {"Type": "AWS::EC2::EIP", "Properties": {"Domain": "vpc"}},
            "Nat": {"Type": "AWS::EC2::NatGateway",
                    "Properties": {"SubnetId": {"Ref": "PublicSubnet"}, "AllocationId": {"Fn::GetAtt": ["NatEip", "AllocationId"]}}},
            "PrivateRt": {"Type": "AWS::EC2::RouteTable", "Properties": {"VpcId": {"Ref": "Vpc"}}},
            "PrivateRoute": {"Type": "AWS::EC2::Route",
                             "Properties": {"RouteTableId": {"Ref": "PrivateRt"}, "DestinationCidrBlock": "0.0.0.0/0",
                                            "NatGatewayId": {"Ref": "Nat"}}}
          },
          "Outputs": {
            "SubnetId": {"Value": {"Ref": "PublicSubnet"}},
            "SubnetAz": {"Value": {"Fn::GetAtt": ["PublicSubnet", "AvailabilityZone"]}},
            "IgwId": {"Value": {"Ref": "Igw"}},
            "PublicRtId": {"Value": {"Ref": "PublicRt"}},
            "PrivateRtId": {"Value": {"Ref": "PrivateRt"}},
            "PublicRouteRef": {"Value": {"Ref": "PublicRoute"}},
            "PublicRouteCidr": {"Value": {"Fn::GetAtt": ["PublicRoute", "CidrBlock"]}},
            "AssocId": {"Value": {"Ref": "PublicAssoc"}},
            "EipRef": {"Value": {"Ref": "NatEip"}},
            "EipAllocationId": {"Value": {"Fn::GetAtt": ["NatEip", "AllocationId"]}},
            "NatId": {"Value": {"Ref": "Nat"}}
          }
        }
        """;

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void networkingStackExposesTheAwsRefShapesAndDeletesEverythingItCreated() throws Exception {
        cloudFormation("CreateStack", TEMPLATE);

        String stacks = describeStacks("CREATE_COMPLETE");
        Map<String, String> out = XmlParser.extractPairs(stacks, "Outputs", "OutputKey", "OutputValue");
        assertTrue(out.get("SubnetId").startsWith("subnet-"), out.get("SubnetId"));
        assertEquals("us-east-1a", out.get("SubnetAz"));
        assertTrue(out.get("IgwId").startsWith("igw-"), out.get("IgwId"));
        assertTrue(out.get("PublicRtId").startsWith("rtb-"), out.get("PublicRtId"));
        assertEquals(out.get("PublicRtId") + "|0.0.0.0/0", out.get("PublicRouteRef"),
                "Ref of a Route is the registry primary identifier, table id and destination");
        assertEquals("0.0.0.0/0", out.get("PublicRouteCidr"));
        assertTrue(out.get("AssocId").startsWith("rtbassoc-"), out.get("AssocId"));
        assertTrue(out.get("EipAllocationId").startsWith("eipalloc-"), out.get("EipAllocationId"));
        assertFalse(out.get("EipRef").startsWith("eipalloc-"), "Ref of an EIP is its public IP: " + out.get("EipRef"));
        assertTrue(out.get("NatId").startsWith("nat-"), out.get("NatId"));

        assertTrue(ec2("DescribeSubnets").contains(out.get("SubnetId")));
        String routeTables = ec2("DescribeRouteTables");
        assertTrue(routeTables.contains(out.get("PublicRtId")) && routeTables.contains(out.get("AssocId")));
        assertTrue(routeTables.contains(out.get("NatId")), "the private route must point at the NAT gateway");
        assertTrue(ec2("DescribeNatGateways").contains(out.get("NatId")));
        assertTrue(ec2("DescribeAddresses").contains(out.get("EipAllocationId")));
        assertTrue(ec2("DescribeInternetGateways").contains(out.get("IgwId")));

        cloudFormation("DeleteStack", null);
        awaitStackDeleted();

        for (String id : List.of(out.get("SubnetId"), out.get("PublicRtId"), out.get("PrivateRtId"),
                out.get("AssocId"), out.get("NatId"), out.get("EipAllocationId"), out.get("IgwId"))) {
            String everything = ec2("DescribeSubnets") + ec2("DescribeRouteTables") + ec2("DescribeNatGateways")
                    + ec2("DescribeAddresses") + ec2("DescribeInternetGateways");
            assertFalse(everything.contains(id), "stack delete must remove " + id);
        }
    }

    private static void cloudFormation(String action, String templateBody) {
        var request = given()
            .contentType("application/x-www-form-urlencoded")
            .header("Authorization", CFN_AUTH)
            .formParam("Action", action)
            .formParam("StackName", STACK);
        if (templateBody != null) {
            request.formParam("TemplateBody", templateBody);
        }
        request.when().post("/").then().statusCode(200);
    }

    private static String describeStacks(String expectedStatus) {
        return given()
            .contentType("application/x-www-form-urlencoded")
            .header("Authorization", CFN_AUTH)
            .formParam("Action", "DescribeStacks")
            .formParam("StackName", STACK)
        .when().post("/").then().statusCode(200)
            .body(containsString("<StackStatus>" + expectedStatus + "</StackStatus>"))
            .extract().asString();
    }

    private static void awaitStackDeleted() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            String body = given()
                .contentType("application/x-www-form-urlencoded")
                .header("Authorization", CFN_AUTH)
                .formParam("Action", "DescribeStacks")
                .formParam("StackName", STACK)
            .when().post("/").then().extract().asString();
            if (body.contains("does not exist")) {
                return;
            }
            if (body.contains("<StackStatus>DELETE_FAILED</StackStatus>")) {
                fail("stack delete failed: " + body);
            }
            Thread.sleep(50);
        }
        fail("stack " + STACK + " was not deleted within the timeout");
    }

    private static String ec2(String action) {
        return given()
            .contentType("application/x-www-form-urlencoded")
            .header("Authorization", EC2_AUTH)
            .formParam("Action", action)
            .formParam("Version", "2016-11-15")
        .when().post("/").then().statusCode(200).extract().asString();
    }
}
