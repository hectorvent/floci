package io.github.hectorvent.floci.services.cloudformation.provisioners;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.hectorvent.floci.services.cloudformation.model.StackResource;
import io.github.hectorvent.floci.services.ec2.Ec2Service;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CloudFormation provisioning for the EC2 networking types: {@code AWS::EC2::Subnet},
 * {@code InternetGateway}, {@code RouteTable}, {@code Route}, {@code NatGateway}, {@code EIP} and
 * {@code SubnetRouteTableAssociation}. Extracted from {@code CloudFormationResourceProvisioner} as
 * part of the per-service decomposition. The physical id is the EC2 id the service assigns, except
 * for a Route, whose id is the registry primary identifier {@code <RouteTableId>|<destination>} (what
 * Ref returns on AWS and what a delete needs), and an EIP, whose Ref is its public IP.
 *
 * <p>Deletes tolerate only the service's own not-found code for each type; a
 * {@code DependencyViolation} still fails the stack delete, as on AWS.
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
    private static final String ROUTE_ID_SEPARATOR = "|";

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

    /** An EIP's Ref is its public IP, so its release needs the AllocationId recorded at create time. */
    @Override
    public void delete(StackResource resource, String region) {
        if (EIP.equals(resource.getResourceType()) && resource.getAttributes() != null
                && resource.getAttributes().get("AllocationId") != null) {
            String allocationId = resource.getAttributes().get("AllocationId");
            CfnDeletes.safeDelete("elastic IP", allocationId,
                    () -> ec2Service.releaseAddress(region, allocationId), "InvalidAllocationID.NotFound");
            return;
        }
        delete(resource.getResourceType(), resource.getPhysicalId(), region);
    }

    @Override
    public void delete(String resourceType, String physicalId, String region) {
        if (physicalId == null || physicalId.isBlank()) {
            return;
        }
        switch (resourceType) {
            case SUBNET -> CfnDeletes.safeDelete("subnet", physicalId,
                    () -> ec2Service.deleteSubnet(region, physicalId), "InvalidSubnetID.NotFound");
            case INTERNET_GATEWAY -> CfnDeletes.safeDelete("internet gateway", physicalId,
                    () -> ec2Service.deleteInternetGateway(region, physicalId), "InvalidInternetGatewayID.NotFound");
            case ROUTE_TABLE -> CfnDeletes.safeDelete("route table", physicalId,
                    () -> ec2Service.deleteRouteTable(region, physicalId), "InvalidRouteTableID.NotFound");
            case ROUTE -> deleteRoute(physicalId, region);
            case NAT_GATEWAY -> CfnDeletes.safeDelete("NAT gateway", physicalId,
                    () -> ec2Service.deleteNatGateway(region, physicalId), "NatGatewayNotFound");
            case EIP -> releaseByPublicIp(physicalId, region);
            case SUBNET_ROUTE_TABLE_ASSOCIATION -> CfnDeletes.safeDelete("route table association", physicalId,
                    () -> ec2Service.disassociateRouteTable(region, physicalId), "InvalidAssociationID.NotFound");
            default -> { }
        }
    }

    /**
     * The route's id names its table and destination; a route created by the switch before this
     * provisioner existed carries a generated id with no separator and cannot be located, so it is
     * left alone rather than guessed at. A table that is already gone took its routes with it.
     */
    private void deleteRoute(String physicalId, String region) {
        int separator = physicalId.indexOf(ROUTE_ID_SEPARATOR);
        if (separator <= 0 || separator == physicalId.length() - 1) {
            return;
        }
        String routeTableId = physicalId.substring(0, separator);
        String destination = physicalId.substring(separator + 1);
        CfnDeletes.safeDelete("route", physicalId, () -> ec2Service.deleteRoute(region, routeTableId,
                        isIpv4Cidr(destination) ? destination : null,
                        isIpv6Cidr(destination) ? destination : null,
                        isPrefixList(destination) ? destination : null),
                "InvalidRoute.NotFound", "InvalidRouteTableID.NotFound");
    }

    /** Without the recorded AllocationId (a rollback of a failed create, for instance), the address is looked up by IP. */
    private void releaseByPublicIp(String publicIp, String region) {
        ec2Service.describeAddresses(region, List.of(), Map.of("public-ip", List.of(publicIp))).stream()
                .findFirst()
                .ifPresent(address -> CfnDeletes.safeDelete("elastic IP", address.getAllocationId(),
                        () -> ec2Service.releaseAddress(region, address.getAllocationId()), "InvalidAllocationID.NotFound"));
    }

    private static boolean isPrefixList(String destination) {
        return destination.startsWith("pl-");
    }

    private static boolean isIpv6Cidr(String destination) {
        return destination.contains(":");
    }

    private static boolean isIpv4Cidr(String destination) {
        return !isPrefixList(destination) && !isIpv6Cidr(destination);
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
        // The registry primary identifier is RouteTableId|CidrBlock, which is also what Ref returns
        // on AWS, and the only id a later delete can act on. CidrBlock is the schema's read-only
        // attribute: the destination the route was created with, whichever property carried it.
        String destination = destinationCidr != null ? destinationCidr
                : destinationIpv6Cidr != null ? destinationIpv6Cidr : destinationPrefixListId;
        r.setPhysicalId(routeTableId + ROUTE_ID_SEPARATOR + destination);
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
