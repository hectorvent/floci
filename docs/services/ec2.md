# EC2

**Protocol:** EC2 Query (XML) — `POST http://localhost:4566/` with `Action=` parameter

## Default Resources

Floci seeds the following resources on first use in each region so Terraform, the AWS CLI, and SDK clients work out of the box without any setup:

| Resource | ID | Details |
|---|---|---|
| Default VPC | `vpc-default` | CIDR `172.31.0.0/16` |
| Default Subnet (AZ a) | `subnet-default-a` | CIDR `172.31.0.0/20` |
| Default Subnet (AZ b) | `subnet-default-b` | CIDR `172.31.16.0/20` |
| Default Subnet (AZ c) | `subnet-default-c` | CIDR `172.31.32.0/20` |
| Default Security Group | `sg-default` | `groupName=default`, all-traffic egress |
| Default Internet Gateway | `igw-default` | Attached to default VPC |
| Main Route Table | `rtb-default` | Associated with default VPC |

## Supported Actions

### Instances
`RunInstances` · `DescribeInstances` · `TerminateInstances` · `StartInstances` · `StopInstances` · `RebootInstances` · `DescribeInstanceStatus` · `DescribeInstanceAttribute` · `ModifyInstanceAttribute`

### VPCs
`CreateVpc` · `DescribeVpcs` · `DeleteVpc` · `ModifyVpcAttribute` · `DescribeVpcAttribute` · `CreateDefaultVpc` · `AssociateVpcCidrBlock` · `DisassociateVpcCidrBlock`

### Subnets
`CreateSubnet` · `DescribeSubnets` · `DeleteSubnet` · `ModifySubnetAttribute`

### Security Groups
`CreateSecurityGroup` · `DescribeSecurityGroups` · `DeleteSecurityGroup` · `AuthorizeSecurityGroupIngress` · `AuthorizeSecurityGroupEgress` · `RevokeSecurityGroupIngress` · `RevokeSecurityGroupEgress` · `DescribeSecurityGroupRules` · `ModifySecurityGroupRules` · `UpdateSecurityGroupRuleDescriptionsIngress` · `UpdateSecurityGroupRuleDescriptionsEgress`

### Key Pairs
`CreateKeyPair` · `DescribeKeyPairs` · `DeleteKeyPair` · `ImportKeyPair`

### AMIs
`DescribeImages`

### Tags
`CreateTags` · `DeleteTags` · `DescribeTags`

### Internet Gateways
`CreateInternetGateway` · `DescribeInternetGateways` · `DeleteInternetGateway` · `AttachInternetGateway` · `DetachInternetGateway`

### Route Tables
`CreateRouteTable` · `DescribeRouteTables` · `DeleteRouteTable` · `AssociateRouteTable` · `DisassociateRouteTable` · `CreateRoute` · `DeleteRoute`

### Elastic IPs
`AllocateAddress` · `DescribeAddresses` · `AssociateAddress` · `DisassociateAddress` · `ReleaseAddress`

### Availability Zones & Regions
`DescribeAvailabilityZones` · `DescribeRegions` · `DescribeAccountAttributes`

### Instance Types
`DescribeInstanceTypes`

## Examples

```bash
export AWS_ENDPOINT=http://localhost:4566

# List default subnets
aws ec2 describe-subnets --endpoint-url $AWS_ENDPOINT

# List default VPC
aws ec2 describe-vpcs --endpoint-url $AWS_ENDPOINT

# Launch an instance
aws ec2 run-instances \
  --image-id ami-0abcdef1234567890 \
  --instance-type t2.micro \
  --min-count 1 \
  --max-count 1 \
  --endpoint-url $AWS_ENDPOINT

# Describe running instances
aws ec2 describe-instances \
  --filters "Name=instance-state-name,Values=running" \
  --endpoint-url $AWS_ENDPOINT

# Create a VPC and subnet
aws ec2 create-vpc --cidr-block 10.0.0.0/16 --endpoint-url $AWS_ENDPOINT
aws ec2 create-subnet --vpc-id vpc-XXXXX --cidr-block 10.0.1.0/24 --endpoint-url $AWS_ENDPOINT

# Create and configure a security group
aws ec2 create-security-group \
  --group-name my-sg \
  --description "My security group" \
  --vpc-id vpc-XXXXX \
  --endpoint-url $AWS_ENDPOINT

aws ec2 authorize-security-group-ingress \
  --group-id sg-XXXXX \
  --protocol tcp \
  --port 22 \
  --cidr 0.0.0.0/0 \
  --endpoint-url $AWS_ENDPOINT

# Allocate and associate an Elastic IP
aws ec2 allocate-address --domain vpc --endpoint-url $AWS_ENDPOINT
aws ec2 associate-address \
  --allocation-id eipalloc-XXXXX \
  --instance-id i-XXXXX \
  --endpoint-url $AWS_ENDPOINT
```

## Notes

- Instances transition to `running` immediately (no Docker container is launched).
- `DescribeImages` returns a static list of common AMIs (Amazon Linux 2, Amazon Linux 2023, Ubuntu 20.04, Windows Server 2022).
- Key material returned by `CreateKeyPair` is a dummy RSA PEM — not usable for real SSH.
- Instance metadata service (IMDS / `169.254.169.254`) is not emulated.
