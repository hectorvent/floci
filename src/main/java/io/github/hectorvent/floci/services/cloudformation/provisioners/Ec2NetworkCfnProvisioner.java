package io.github.hectorvent.floci.services.cloudformation.provisioners;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hectorvent.floci.services.cloudformation.model.StackResource;
import io.github.hectorvent.floci.services.ec2.Ec2Service;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CloudFormation provisioning for the EC2 networking types: {@code AWS::EC2::Subnet},
 * {@code InternetGateway}, {@code RouteTable}, {@code Route}, {@code NatGateway}, {@code EIP} and
 * {@code SubnetRouteTableAssociation}. Extracted from {@code CloudFormationResourceProvisioner} as
 * part of the per-service decomposition, behaviour unchanged: the physical id is the EC2 id the
 * service assigns (a Route gets a generated one, an EIP its public IP, which is what Ref returns on
 * AWS), and the attributes are the ones the switch exposed.
 *
 * <p>No delete override yet: the switch this replaces had no delete arm for any of these types, so
 * stack teardown leaves them alone. Adding one is a behaviour change and follows separately.
 */
@ApplicationScoped
public class Ec2NetworkCfnProvisioner implements CfnResourceProvisioner {

    private static final String SUBNET = "AWS::EC2::Subnet";
    private static final String INTERNET_GATEWAY = "AWS::EC2::InternetGateway";
    private static final String ROUTE_TABLE = "AWS::EC2::RouteTable";
    private static final String ROUTE = "AWS::EC2::Route";
    private static final String NAT_GATEWAY = "AWS::EC2::NatGateway";
    private static final String EIP = "AWS::EC2::EIP";
    private static final String SUBNET_ROUTE_TABLE_ASSOCIATION = "AWS::EC2::SubnetRouteTableAssociation";

    private final Ec2Service ec2Service;

    @Inject
    public Ec2NetworkCfnProvisioner(Ec2Service ec2Service) {
        this.ec2Service = ec2Service;
    }

    @Override
    public Set<String> resourceTypes() {
        return Set.of(SUBNET, INTERNET_GATEWAY, ROUTE_TABLE, ROUTE, NAT_GATEWAY, EIP, SUBNET_ROUTE_TABLE_ASSOCIATION);
    }

    @Override
    public void provision(StackResource r, JsonNode props, ProvisionContext ctx) {
        switch (r.getResourceType()) {
            case SUBNET -> provisionSubnet(r, props, ctx);
            case INTERNET_GATEWAY -> provisionInternetGateway(r, ctx);
            case ROUTE_TABLE -> provisionRouteTable(r, props, ctx);
            case ROUTE -> provisionRoute(r, props, ctx);
            case NAT_GATEWAY -> provisionNatGateway(r, props, ctx);
            case EIP -> provisionEip(r, ctx);
            case SUBNET_ROUTE_TABLE_ASSOCIATION -> provisionSubnetRouteTableAssociation(r, props, ctx);
            default -> throw new IllegalStateException("Ec2NetworkCfnProvisioner cannot handle " + r.getResourceType());
        }
    }

    private void provisionSubnet(StackResource r, JsonNode props, ProvisionContext ctx) {
        String vpcId = ctx.resolveOptional(props, "VpcId");
        String cidr = ctx.resolveOptional(props, "CidrBlock");
        String az = ctx.resolveOptional(props, "AvailabilityZone");
        String mapPublicIpOnLaunch = ctx.resolveOptional(props, "MapPublicIpOnLaunch");
        var subnet = ec2Service.createSubnet(ctx.region(), vpcId, cidr, az);
        if (mapPublicIpOnLaunch != null) {
            ec2Service.modifySubnetAttribute(ctx.region(), subnet.getSubnetId(), "mapPublicIpOnLaunch", mapPublicIpOnLaunch);
        }
        r.setPhysicalId(subnet.getSubnetId());
        r.getAttributes().put("SubnetId", subnet.getSubnetId());
        r.getAttributes().put("VpcId", subnet.getVpcId());
        r.getAttributes().put("AvailabilityZone", subnet.getAvailabilityZone());
    }

    private void provisionInternetGateway(StackResource r, ProvisionContext ctx) {
        var igw = ec2Service.createInternetGateway(ctx.region());
        r.setPhysicalId(igw.getInternetGatewayId());
        r.getAttributes().put("InternetGatewayId", igw.getInternetGatewayId());
    }

    private void provisionRouteTable(StackResource r, JsonNode props, ProvisionContext ctx) {
        String vpcId = ctx.resolveOptional(props, "VpcId");
        var rt = ec2Service.createRouteTable(ctx.region(), vpcId);
        r.setPhysicalId(rt.getRouteTableId());
        r.getAttributes().put("RouteTableId", rt.getRouteTableId());
    }

    private void provisionSubnetRouteTableAssociation(StackResource r, JsonNode props, ProvisionContext ctx) {
        String routeTableId = ctx.resolveOptional(props, "RouteTableId");
        String subnetId = ctx.resolveOptional(props, "SubnetId");
        var assoc = ec2Service.associateRouteTable(ctx.region(), routeTableId, subnetId);
        r.setPhysicalId(assoc.getRouteTableAssociationId());
        r.getAttributes().put("Id", assoc.getRouteTableAssociationId());
    }

    private void provisionRoute(StackResource r, JsonNode props, ProvisionContext ctx) {
        String routeTableId = ctx.resolveOptional(props, "RouteTableId");
        String destinationCidr = ctx.resolveOptional(props, "DestinationCidrBlock");
        String destinationIpv6Cidr = ctx.resolveOptional(props, "DestinationIpv6CidrBlock");
        String destinationPrefixListId = ctx.resolveOptional(props, "DestinationPrefixListId");
        String gatewayId = ctx.resolveOptional(props, "GatewayId");
        String natGatewayId = ctx.resolveOptional(props, "NatGatewayId");
        String egressOnlyInternetGatewayId = ctx.resolveOptional(props, "EgressOnlyInternetGatewayId");
        String vpcPeeringConnectionId = ctx.resolveOptional(props, "VpcPeeringConnectionId");
        ec2Service.createRoute(ctx.region(), routeTableId, destinationCidr, destinationIpv6Cidr,
                destinationPrefixListId, gatewayId, natGatewayId, egressOnlyInternetGatewayId,
                vpcPeeringConnectionId);
        r.setPhysicalId(r.getLogicalId() + "-" + UUID.randomUUID().toString().substring(0, 8));
        // CidrBlock is the attribute the registry schema declares read-only: the destination the
        // route was created with, whichever of the three destination properties carried it.
        String destination = destinationCidr != null ? destinationCidr
                : destinationIpv6Cidr != null ? destinationIpv6Cidr : destinationPrefixListId;
        if (destination != null) {
            r.getAttributes().put("CidrBlock", destination);
        }
    }

    private void provisionNatGateway(StackResource r, JsonNode props, ProvisionContext ctx) {
        String subnetId = ctx.resolveOptional(props, "SubnetId");
        String allocationId = ctx.resolveOptional(props, "AllocationId");
        var nat = ec2Service.createNatGateway(ctx.region(), subnetId, allocationId, "public", List.of());
        r.setPhysicalId(nat.getNatGatewayId());
        r.getAttributes().put("NatGatewayId", nat.getNatGatewayId());
    }

    private void provisionEip(StackResource r, ProvisionContext ctx) {
        var addr = ec2Service.allocateAddress(ctx.region());
        // Ref on AWS::EC2::EIP returns the public IP; AllocationId is exposed via Fn::GetAtt.
        r.setPhysicalId(addr.getPublicIp());
        r.getAttributes().put("AllocationId", addr.getAllocationId());
        r.getAttributes().put("PublicIp", addr.getPublicIp());
    }
}
