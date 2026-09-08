package io.github.hectorvent.floci.services.cloudformation.provisioners;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.hectorvent.floci.services.cloudformation.CloudFormationTemplateEngine;
import io.github.hectorvent.floci.services.cloudformation.model.StackResource;
import io.github.hectorvent.floci.services.ec2.Ec2Service;
import io.github.hectorvent.floci.services.ec2.model.Address;
import io.github.hectorvent.floci.services.ec2.model.InternetGateway;
import io.github.hectorvent.floci.services.ec2.model.NatGateway;
import io.github.hectorvent.floci.services.ec2.model.RouteTable;
import io.github.hectorvent.floci.services.ec2.model.RouteTableAssociation;
import io.github.hectorvent.floci.services.ec2.model.Subnet;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The EC2 networking provisioner in isolation: one mocked service. Every case asserts the exact
 * physical id and the exact {@code Fn::GetAtt} attribute keys, since an unmapped type still reports
 * CREATE_COMPLETE through the dispatcher's stub arm.
 */
class Ec2NetworkCfnProvisionerTest {

    private static final String REGION = "us-east-1";
    private static final String VPC_ID = "vpc-0123456789abcdef0";
    private static final String SUBNET_ID = "subnet-0123456789abcdef0";
    private static final String RTB_ID = "rtb-0123456789abcdef0";
    private static final String IGW_ID = "igw-0123456789abcdef0";
    private static final String NAT_ID = "nat-0123456789abcdef0";
    private static final String ASSOC_ID = "rtbassoc-0123456789abcdef0";
    private static final String ALLOC_ID = "eipalloc-0123456789abcdef0";
    private static final String PUBLIC_IP = "54.0.0.1";

    private final Ec2Service ec2 = mock(Ec2Service.class);
    private final Ec2NetworkCfnProvisioner provisioner = new Ec2NetworkCfnProvisioner(ec2);
    private final ObjectMapper mapper = new ObjectMapper();

    private ProvisionContext ctx() {
        CloudFormationTemplateEngine engine = mock(CloudFormationTemplateEngine.class);
        when(engine.resolve(any())).thenAnswer(inv -> {
            JsonNode node = inv.getArgument(0);
            return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
        });
        when(engine.resolveNode(any())).thenAnswer(inv -> inv.getArgument(0));
        when(engine.resolveStringList(any())).thenCallRealMethod();
        return new ProvisionContext(engine, REGION, "000000000000", "my-stack");
    }

    private static StackResource resource(String type, String logicalId) {
        StackResource r = new StackResource();
        r.setLogicalId(logicalId);
        r.setResourceType(type);
        r.setAttributes(new HashMap<>());
        return r;
    }

    private static Subnet subnet() {
        Subnet s = new Subnet();
        s.setSubnetId(SUBNET_ID);
        s.setVpcId(VPC_ID);
        s.setAvailabilityZone("us-east-1a");
        return s;
    }

    @Test
    void subnetSetsItsIdAndTheThreeAttributesAndAppliesMapPublicIpOnLaunch() {
        when(ec2.createSubnet(REGION, VPC_ID, "10.0.1.0/24", "us-east-1a")).thenReturn(subnet());
        ObjectNode props = mapper.createObjectNode().put("VpcId", VPC_ID).put("CidrBlock", "10.0.1.0/24")
                .put("AvailabilityZone", "us-east-1a").put("MapPublicIpOnLaunch", true);
        StackResource r = resource("AWS::EC2::Subnet", "Subnet");

        provisioner.provision(r, props, ctx());

        assertEquals(SUBNET_ID, r.getPhysicalId());
        assertEquals(Set.of("SubnetId", "VpcId", "AvailabilityZone"), r.getAttributes().keySet());
        assertEquals("us-east-1a", r.getAttributes().get("AvailabilityZone"));
        verify(ec2).modifySubnetAttribute(REGION, SUBNET_ID, "mapPublicIpOnLaunch", "true");
    }

    @Test
    void subnetWithoutMapPublicIpOnLaunchLeavesTheAttributeAlone() {
        when(ec2.createSubnet(eq(REGION), eq(VPC_ID), eq("10.0.1.0/24"), isNull())).thenReturn(subnet());

        provisioner.provision(resource("AWS::EC2::Subnet", "Subnet"),
                mapper.createObjectNode().put("VpcId", VPC_ID).put("CidrBlock", "10.0.1.0/24"), ctx());

        verify(ec2, never()).modifySubnetAttribute(any(), any(), any(), any());
    }

    @Test
    void internetGatewaySetsItsIdAttribute() {
        InternetGateway igw = new InternetGateway();
        igw.setInternetGatewayId(IGW_ID);
        when(ec2.createInternetGateway(REGION)).thenReturn(igw);
        StackResource r = resource("AWS::EC2::InternetGateway", "Igw");

        provisioner.provision(r, mapper.createObjectNode(), ctx());

        assertEquals(IGW_ID, r.getPhysicalId());
        assertEquals(Set.of("InternetGatewayId"), r.getAttributes().keySet());
    }

    @Test
    void routeTableSetsItsIdAttribute() {
        RouteTable rt = new RouteTable();
        rt.setRouteTableId(RTB_ID);
        rt.setVpcId(VPC_ID);
        when(ec2.createRouteTable(REGION, VPC_ID)).thenReturn(rt);
        StackResource r = resource("AWS::EC2::RouteTable", "Rtb");

        provisioner.provision(r, mapper.createObjectNode().put("VpcId", VPC_ID), ctx());

        assertEquals(RTB_ID, r.getPhysicalId());
        assertEquals(Set.of("RouteTableId"), r.getAttributes().keySet());
    }

    @Test
    void associationSetsTheSchemasIdAttribute() {
        RouteTableAssociation assoc = new RouteTableAssociation();
        assoc.setRouteTableAssociationId(ASSOC_ID);
        when(ec2.associateRouteTable(REGION, RTB_ID, SUBNET_ID)).thenReturn(assoc);
        StackResource r = resource("AWS::EC2::SubnetRouteTableAssociation", "Assoc");

        provisioner.provision(r, mapper.createObjectNode().put("RouteTableId", RTB_ID).put("SubnetId", SUBNET_ID), ctx());

        assertEquals(ASSOC_ID, r.getPhysicalId());
        assertEquals(Set.of("Id"), r.getAttributes().keySet());
    }

    @Test
    void routePassesEveryTargetAndExposesTheDestinationAsCidrBlock() {
        StackResource r = resource("AWS::EC2::Route", "Default");
        ObjectNode props = mapper.createObjectNode().put("RouteTableId", RTB_ID)
                .put("DestinationCidrBlock", "0.0.0.0/0").put("GatewayId", IGW_ID);

        provisioner.provision(r, props, ctx());

        verify(ec2).createRoute(REGION, RTB_ID, "0.0.0.0/0", null, null, IGW_ID, null, null, null);
        assertTrue(r.getPhysicalId().startsWith("Default-"), r.getPhysicalId());
        assertEquals(Set.of("CidrBlock"), r.getAttributes().keySet());
        assertEquals("0.0.0.0/0", r.getAttributes().get("CidrBlock"));
    }

    @Test
    void routeToAPrefixListExposesThatAsCidrBlock() {
        StackResource r = resource("AWS::EC2::Route", "S3Route");
        ObjectNode props = mapper.createObjectNode().put("RouteTableId", RTB_ID)
                .put("DestinationPrefixListId", "pl-63a5400a").put("NatGatewayId", NAT_ID);

        provisioner.provision(r, props, ctx());

        verify(ec2).createRoute(REGION, RTB_ID, null, null, "pl-63a5400a", null, NAT_ID, null, null);
        assertEquals("pl-63a5400a", r.getAttributes().get("CidrBlock"));
    }

    @Test
    void natGatewayIsCreatedPublicWithNoTags() {
        NatGateway nat = new NatGateway();
        nat.setNatGatewayId(NAT_ID);
        when(ec2.createNatGateway(REGION, SUBNET_ID, ALLOC_ID, "public", List.of())).thenReturn(nat);
        StackResource r = resource("AWS::EC2::NatGateway", "Nat");

        provisioner.provision(r, mapper.createObjectNode().put("SubnetId", SUBNET_ID).put("AllocationId", ALLOC_ID), ctx());

        assertEquals(NAT_ID, r.getPhysicalId());
        assertEquals(Set.of("NatGatewayId"), r.getAttributes().keySet());
    }

    @Test
    void eipRefIsThePublicIpAndAllocationIdIsAnAttribute() {
        Address addr = new Address();
        addr.setAllocationId(ALLOC_ID);
        addr.setPublicIp(PUBLIC_IP);
        when(ec2.allocateAddress(REGION)).thenReturn(addr);
        StackResource r = resource("AWS::EC2::EIP", "Eip");

        provisioner.provision(r, mapper.createObjectNode().put("Domain", "vpc"), ctx());

        assertEquals(PUBLIC_IP, r.getPhysicalId());
        assertEquals(Set.of("AllocationId", "PublicIp"), r.getAttributes().keySet());
        assertEquals(ALLOC_ID, r.getAttributes().get("AllocationId"));
    }

    @Test
    void unknownTypeIsRejected() {
        StackResource r = resource("AWS::EC2::SecurityGroup", "Sg");

        assertThrows(IllegalStateException.class, () -> provisioner.provision(r, mapper.createObjectNode(), ctx()));
    }
}
