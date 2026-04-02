# OpenSearch Service

**Protocol:** REST JSON
**Endpoint:** `http://localhost:4566/2021-01-01/...`
**Credential scope:** `es`

## Implementation Mode

OpenSearch supports two implementation modes controlled by `FLOCI_SERVICES_OPENSEARCH_MODE`:

| Mode | Behaviour |
|---|---|
| `mock` *(default)* | Management API only. Domains are stored in the configured StorageBackend and appear `ACTIVE` immediately. No real search capability. |
| `real` | *(v2, coming soon)* Spins up a real OpenSearch Docker container per domain and proxies data-plane requests to it. |

## Supported Operations

### Domain Lifecycle

| Operation | Method + Path | Description |
|---|---|---|
| `CreateDomain` | `POST /2021-01-01/opensearch/domain` | Create a new domain |
| `DescribeDomain` | `GET /2021-01-01/opensearch/domain/{name}` | Get domain details |
| `DescribeDomains` | `POST /2021-01-01/opensearch/domain-info` | Batch describe domains |
| `DescribeDomainConfig` | `GET /2021-01-01/opensearch/domain/{name}/config` | Get domain configuration |
| `UpdateDomainConfig` | `POST /2021-01-01/opensearch/domain/{name}/config` | Update cluster config, EBS options, engine version |
| `DeleteDomain` | `DELETE /2021-01-01/opensearch/domain/{name}` | Delete a domain |
| `ListDomainNames` | `GET /2021-01-01/domain` | List all domains (supports `?engineType=` filter) |

### Tags

| Operation | Method + Path | Description |
|---|---|---|
| `AddTags` | `POST /2021-01-01/tags` | Add tags to a domain by ARN |
| `ListTags` | `GET /2021-01-01/tags/?arn=` | List tags for a domain |
| `RemoveTags` | `POST /2021-01-01/tags-removal` | Remove tag keys from a domain |

### Versions & Instance Types

| Operation | Method + Path | Description |
|---|---|---|
| `ListVersions` | `GET /2021-01-01/opensearch/versions` | List supported engine versions |
| `GetCompatibleVersions` | `GET /2021-01-01/opensearch/compatibleVersions` | List valid upgrade paths |
| `ListInstanceTypeDetails` | `GET /2021-01-01/opensearch/instanceTypeDetails/{version}` | List available instance types |
| `DescribeInstanceTypeLimits` | `GET /2021-01-01/opensearch/instanceTypeLimits/{version}/{type}` | Get limits for an instance type |

### Stubs (SDK-compatible, no-op responses)

| Operation | Notes |
|---|---|
| `DescribeDomainChangeProgress` | Returns empty `ChangeProgressStatus` |
| `DescribeDomainAutoTunes` | Returns empty `AutoTunes` list |
| `DescribeDryRunProgress` | Returns empty `DryRunProgressStatus` |
| `DescribeDomainHealth` | Returns `ClusterHealth: Green` |
| `GetUpgradeHistory` | Returns empty list |
| `GetUpgradeStatus` | Returns `StepStatus: SUCCEEDED` |
| `UpgradeDomain` | Stores new engine version, returns immediately |
| `CancelDomainConfigChange` | Returns empty `CancelledChangeIds` |
| `StartServiceSoftwareUpdate` | Returns no-op `ServiceSoftwareOptions` |
| `CancelServiceSoftwareUpdate` | Returns no-op `ServiceSoftwareOptions` |

## Configuration

```yaml title="application.yml"
floci:
  services:
    opensearch:
      enabled: true
      mode: mock                                    # mock | real (real is v2, not yet available)
      default-image: "opensearchproject/opensearch:2"  # used only when mode=real
      proxy-base-port: 9400                         # port range for real-mode containers
      proxy-max-port: 9499

  storage:
    services:
      opensearch:
        flush-interval-ms: 5000                     # flush interval when using hybrid/wal storage
```

### Environment Variables

| Variable | Default | Description |
|---|---|---|
| `FLOCI_SERVICES_OPENSEARCH_ENABLED` | `true` | Enable/disable the service |
| `FLOCI_SERVICES_OPENSEARCH_MODE` | `mock` | `mock` or `real` |
| `FLOCI_SERVICES_OPENSEARCH_DEFAULT_IMAGE` | `opensearchproject/opensearch:2` | Docker image for `real` mode |
| `FLOCI_SERVICES_OPENSEARCH_PROXY_BASE_PORT` | `9400` | Port range start for `real` mode |
| `FLOCI_SERVICES_OPENSEARCH_PROXY_MAX_PORT` | `9499` | Port range end for `real` mode |
| `FLOCI_STORAGE_SERVICES_OPENSEARCH_MODE` | *(global default)* | Storage mode override |
| `FLOCI_STORAGE_SERVICES_OPENSEARCH_FLUSH_INTERVAL_MS` | `5000` | Flush interval (ms) |

## Emulation Behaviour

- **Domain name validation:** 3–28 characters, must start with a lowercase letter, only lowercase letters, digits, and hyphens.
- **ARN format:** `arn:aws:es:{region}:{accountId}:domain/{domainName}`
- **Domain ID format:** `{accountId}/{domainName}`
- **Processing:** Always `false` in `mock` mode — domains are `ACTIVE` immediately.
- **Engine version default:** `OpenSearch_2.11`
- **Cluster defaults:** `m5.large.search`, 1 instance, EBS enabled with 10 GiB `gp2` volume.

## Examples

```bash
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_DEFAULT_REGION=us-east-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

# Create a domain
aws opensearch create-domain \
  --domain-name my-search \
  --engine-version "OpenSearch_2.11" \
  --cluster-config InstanceType=m5.large.search,InstanceCount=1 \
  --ebs-options EBSEnabled=true,VolumeType=gp2,VolumeSize=10

# Describe the domain
aws opensearch describe-domain --domain-name my-search

# List all domains
aws opensearch list-domain-names

# Update cluster config
aws opensearch update-domain-config \
  --domain-name my-search \
  --cluster-config InstanceCount=3

# Add tags
aws opensearch add-tags \
  --arn arn:aws:es:us-east-1:000000000000:domain/my-search \
  --tag-list Key=env,Value=dev

# List tags
aws opensearch list-tags \
  --arn arn:aws:es:us-east-1:000000000000:domain/my-search

# Delete domain
aws opensearch delete-domain --domain-name my-search
```

## SDK Example (Java)

```java
OpenSearchClient os = OpenSearchClient.builder()
    .endpointOverride(URI.create("http://localhost:4566"))
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test", "test")))
    .build();

// Create a domain
CreateDomainResponse created = os.createDomain(req -> req
    .domainName("my-search")
    .engineVersion("OpenSearch_2.11")
    .clusterConfig(c -> c
        .instanceType(OpenSearchPartitionInstanceType.M5_LARGE_SEARCH)
        .instanceCount(1))
    .ebsOptions(e -> e
        .ebsEnabled(true)
        .volumeType(VolumeType.GP2)
        .volumeSize(10)));

System.out.println("ARN: " + created.domainStatus().arn());

// Describe the domain
DescribeDomainResponse desc = os.describeDomain(req -> req
    .domainName("my-search"));

System.out.println("Version: " + desc.domainStatus().engineVersion());

// List domains
os.listDomainNames(req -> req.build())
    .domainNames()
    .forEach(d -> System.out.println(d.domainName()));

// Delete
os.deleteDomain(req -> req.domainName("my-search"));
```

## SDK Example (Python)

```python
import boto3

os_client = boto3.client(
    "opensearch",
    endpoint_url="http://localhost:4566",
    region_name="us-east-1",
    aws_access_key_id="test",
    aws_secret_access_key="test"
)

# Create a domain
response = os_client.create_domain(
    DomainName="my-search",
    EngineVersion="OpenSearch_2.11",
    ClusterConfig={"InstanceType": "m5.large.search", "InstanceCount": 1},
    EBSOptions={"EBSEnabled": True, "VolumeType": "gp2", "VolumeSize": 10}
)
print(response["DomainStatus"]["ARN"])

# List domains
domains = os_client.list_domain_names()
for d in domains["DomainNames"]:
    print(d["DomainName"])

# Delete
os_client.delete_domain(DomainName="my-search")
```

## Limitations (v1)

- No real search capability in `mock` mode — data-plane endpoints (`/_search`, `/_index`, etc.) are not proxied.
- No Elasticsearch-compatible endpoints (`/2015-01-01/es/domain/...`).
- VPC options, fine-grained access control, encryption-at-rest, and cross-cluster connections are accepted in the request but silently ignored.
- All 41 "not applicable" operations (VPC endpoints, reserved instances, packages, applications, data sources) return `UnsupportedOperationException`.
