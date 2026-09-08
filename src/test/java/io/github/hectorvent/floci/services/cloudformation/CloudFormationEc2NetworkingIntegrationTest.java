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
 * A public/private networking stack the way CDK lays one out: internet gateway and attachment,
 * a public subnet with a route table routing {@code 0.0.0.0/0} to the gateway and an association, an
 * EIP and a NAT gateway in that subnet, and a private route table routing to the NAT. Asserts the
 * {@code Ref} shapes ({@code Route} is {@code <RouteTableId>|<destination>}, {@code EIP} the public IP)
 * and the {@code Fn::GetAtt} attributes, that an {@code UpdateStack} with the same template keeps
 * every id (the legacy switch re-created all of them), that a changed subnet CIDR replaces the subnet
 * and the NAT gateway inside it and removes the displaced ones, and that {@code DeleteStack} removes
 * every one of the seven networking resources, which the legacy switch left behind. The VPC is
 * created through the EC2 API and passed in as a literal: {@code Ec2VpcCfnProvisioner} still
 * re-creates a VPC on every update (the #3033 fix), which would rightly replace everything in it.
 */
@QuarkusTest
class CloudFormationEc2NetworkingIntegrationTest {

    private static final String CFN_AUTH =
            "AWS4-HMAC-SHA256 Credential=test/20260907/us-east-1/cloudformation/aws4_request";
    private static final String EC2_AUTH =
            "AWS4-HMAC-SHA256 Credential=test/20260907/us-east-1/ec2/aws4_request";
    private static final String STACK = "ec2-networking-it";

    private static String template(String vpcId, String publicSubnetCidr) {
        return """
        {
          "Resources": {
            "Igw": {"Type": "AWS::EC2::InternetGateway"},
            "Attach": {"Type": "AWS::EC2::VPCGatewayAttachment",
                       "Properties": {"VpcId": "%1$s", "InternetGatewayId": {"Ref": "Igw"}}},
            "PublicSubnet": {"Type": "AWS::EC2::Subnet",
                             "Properties": {"VpcId": "%1$s", "CidrBlock": "%2$s",
                                            "AvailabilityZone": "us-east-1a", "MapPublicIpOnLaunch": true}},
            "PublicRt": {"Type": "AWS::EC2::RouteTable", "Properties": {"VpcId": "%1$s"}},
            "PublicRoute": {"Type": "AWS::EC2::Route", "DependsOn": "Attach",
                            "Properties": {"RouteTableId": {"Ref": "PublicRt"}, "DestinationCidrBlock": "0.0.0.0/0",
                                           "GatewayId": {"Ref": "Igw"}}},
            "PublicAssoc": {"Type": "AWS::EC2::SubnetRouteTableAssociation",
                            "Properties": {"RouteTableId": {"Ref": "PublicRt"}, "SubnetId": {"Ref": "PublicSubnet"}}},
            "NatEip": {"Type": "AWS::EC2::EIP", "Properties": {"Domain": "vpc"}},
            "Nat": {"Type": "AWS::EC2::NatGateway",
                    "Properties": {"SubnetId": {"Ref": "PublicSubnet"}, "AllocationId": {"Fn::GetAtt": ["NatEip", "AllocationId"]}}},
            "PrivateRt": {"Type": "AWS::EC2::RouteTable", "Properties": {"VpcId": "%1$s"}},
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
        """.formatted(vpcId, publicSubnetCidr);
    }

    @BeforeAll
    static void configureRestAssured() {
        RestAssuredJsonUtils.configureAwsContentTypes();
    }

    @Test
    void networkingStackExposesTheAwsRefShapesAndDeletesEverythingItCreated() throws Exception {
        String vpcId = between(ec2("CreateVpc", Map.of("CidrBlock", "10.40.0.0/16")), "<vpcId>", "</vpcId>");
        cloudFormation("CreateStack", template(vpcId, "10.40.0.0/24"));

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

        // The same template again: every id survives (the switch re-created all seven).
        cloudFormation("UpdateStack", template(vpcId, "10.40.0.0/24"));
        Map<String, String> again = XmlParser.extractPairs(describeStacks("UPDATE_COMPLETE"), "Outputs", "OutputKey", "OutputValue");
        assertEquals(out, again, "an unchanged update must keep every networking resource");

        // CidrBlock is createOnly: the public subnet is replaced, and with it the NAT gateway and the
        // association that name it; the gateway, EIP and route tables stay.
        cloudFormation("UpdateStack", template(vpcId, "10.40.2.0/24"));
        Map<String, String> replaced = XmlParser.extractPairs(describeStacks("UPDATE_COMPLETE"), "Outputs", "OutputKey", "OutputValue");
        assertFalse(out.get("SubnetId").equals(replaced.get("SubnetId")), "a changed CIDR must replace the subnet");
        assertFalse(out.get("NatId").equals(replaced.get("NatId")), "the NAT gateway follows its subnet");
        assertFalse(out.get("AssocId").equals(replaced.get("AssocId")), "the association follows its subnet");
        assertEquals(out.get("IgwId"), replaced.get("IgwId"));
        assertEquals(out.get("EipAllocationId"), replaced.get("EipAllocationId"));
        assertEquals(out.get("PublicRtId"), replaced.get("PublicRtId"));
        assertEquals(out.get("PrivateRtId"), replaced.get("PrivateRtId"));
        assertFalse(ec2("DescribeSubnets").contains(out.get("SubnetId")), "the displaced subnet must be gone");
        assertFalse(ec2("DescribeNatGateways").contains(out.get("NatId")), "the displaced NAT gateway must be gone");
        assertTrue(ec2("DescribeSubnets").contains(replaced.get("SubnetId")));
        out = replaced;

        cloudFormation("DeleteStack", null);
        awaitStackDeleted();

        for (String id : List.of(out.get("SubnetId"), out.get("PublicRtId"), out.get("PrivateRtId"),
                out.get("AssocId"), out.get("NatId"), out.get("EipAllocationId"), out.get("IgwId"))) {
            String everything = ec2("DescribeSubnets") + ec2("DescribeRouteTables") + ec2("DescribeNatGateways")
                    + ec2("DescribeAddresses") + ec2("DescribeInternetGateways");
            assertFalse(everything.contains(id), "stack delete must remove " + id);
        }
        ec2("DeleteVpc", Map.of("VpcId", vpcId));
    }

    private static String between(String haystack, String open, String close) {
        int start = haystack.indexOf(open);
        int end = haystack.indexOf(close, start);
        assertTrue(start >= 0 && end > start, "missing " + open + " in " + haystack);
        return haystack.substring(start + open.length(), end);
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
        return ec2(action, Map.of());
    }

    private static String ec2(String action, Map<String, String> params) {
        var request = given()
            .contentType("application/x-www-form-urlencoded")
            .header("Authorization", EC2_AUTH)
            .formParam("Action", action)
            .formParam("Version", "2016-11-15");
        params.forEach(request::formParam);
        return request.when().post("/").then().statusCode(200).extract().asString();
    }
}
