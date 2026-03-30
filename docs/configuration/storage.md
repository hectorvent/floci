# Storage Modes

Floci supports four storage backends. You can set a global default and override it per service.

## Modes

| Mode | Data survives restart | Write performance | Use case |
|---|---|---|---|
| `memory` | No | Fastest | Unit tests, CI pipelines |
| `persistent` | Yes | Synchronous disk write on every change | Development with durable state |
| `hybrid` | Yes | In-memory reads, async flush to disk | General local development |
| `wal` | Yes | Append-only write-ahead log with compaction | High-write workloads |

## Global Configuration

```yaml title="application.yml"
floci:
  storage:
    mode: hybrid              # default for all services
    persistent-path: ./data   # base directory for all persistent data
    wal:
      compaction-interval-ms: 30000
```

## Per-Service Override

```yaml title="application.yml"
floci:
  storage:
    mode: memory
    services:
      dynamodb:
        mode: persistent
        flush-interval-ms: 5000
      s3:
        mode: hybrid
      sqs:
        mode: memory
```

## Per-Service Storage Overrides

Override the global mode for individual services via environment variables:

| Variable                                                        | Default  | Description                            |
|-----------------------------------------------------------------|----------|----------------------------------------|
| `FLOCI_STORAGE_SERVICES_SSM_MODE`                               | `memory` | SSM storage mode                       |
| `FLOCI_STORAGE_SERVICES_SSM_FLUSH_INTERVAL_MS`                  | `5000`   | SSM flush interval (ms)                |
| `FLOCI_STORAGE_SERVICES_SQS_MODE`                               | `memory` | SQS storage mode                       |
| `FLOCI_STORAGE_SERVICES_SQS_PERSIST_ON_SHUTDOWN`                | `true`   | Flush SQS messages to disk on shutdown |
| `FLOCI_STORAGE_SERVICES_S3_MODE`                                | `hybrid` | S3 storage mode                        |
| `FLOCI_STORAGE_SERVICES_S3_CACHE_SIZE_MB`                       | `100`    | S3 in-memory cache size (MB)           |
| `FLOCI_STORAGE_SERVICES_DYNAMODB_MODE`                          | `memory` | DynamoDB storage mode                  |
| `FLOCI_STORAGE_SERVICES_DYNAMODB_FLUSH_INTERVAL_MS`             | `5000`   | DynamoDB flush interval (ms)           |
| `FLOCI_STORAGE_SERVICES_SNS_MODE`                               | `memory` | SNS storage mode                       |
| `FLOCI_STORAGE_SERVICES_SNS_FLUSH_INTERVAL_MS`                  | `5000`   | SNS flush interval (ms)                |
| `FLOCI_STORAGE_SERVICES_LAMBDA_MODE`                            | `memory` | Lambda storage mode                    |
| `FLOCI_STORAGE_SERVICES_LAMBDA_FLUSH_INTERVAL_MS`               | `5000`   | Lambda flush interval (ms)             |
| `FLOCI_STORAGE_SERVICES_APIGATEWAY_MODE`                        | `memory` | API Gateway (v1) storage mode          |
| `FLOCI_STORAGE_SERVICES_APIGATEWAY_FLUSH_INTERVAL_MS`           | `5000`   | API Gateway (v1) flush interval (ms)   |
| `FLOCI_STORAGE_SERVICES_APIGATEWAYV2_MODE`                      | `memory` | API Gateway (v2) storage mode          |
| `FLOCI_STORAGE_SERVICES_APIGATEWAYV2_FLUSH_INTERVAL_MS`         | `5000`   | API Gateway (v2) flush interval (ms)   |
| `FLOCI_STORAGE_SERVICES_IAM_MODE`                               | `memory` | IAM storage mode                       |
| `FLOCI_STORAGE_SERVICES_IAM_FLUSH_INTERVAL_MS`                  | `5000`   | IAM flush interval (ms)                |
| `FLOCI_STORAGE_SERVICES_RDS_MODE`                               | `memory` | RDS storage mode                       |
| `FLOCI_STORAGE_SERVICES_RDS_FLUSH_INTERVAL_MS`                  | `5000`   | RDS flush interval (ms)                |
| `FLOCI_STORAGE_SERVICES_EVENTBRIDGE_MODE`                       | `memory` | EventBridge storage mode               |
| `FLOCI_STORAGE_SERVICES_EVENTBRIDGE_FLUSH_INTERVAL_MS`          | `5000`   | EventBridge flush interval (ms)        |
| `FLOCI_STORAGE_SERVICES_CLOUDWATCHLOGS_MODE`                    | `memory` | CloudWatch Logs storage mode           |
| `FLOCI_STORAGE_SERVICES_CLOUDWATCHLOGS_FLUSH_INTERVAL_MS`       | `5000`   | CloudWatch Logs flush interval (ms)    |
| `FLOCI_STORAGE_SERVICES_CLOUDWATCHMETRICS_MODE`                 | `memory` | CloudWatch Metrics storage mode        |
| `FLOCI_STORAGE_SERVICES_CLOUDWATCHMETRICS_FLUSH_INTERVAL_MS`    | `5000`   | CloudWatch Metrics flush interval (ms) |
| `FLOCI_STORAGE_SERVICES_SECRETSMANAGER_MODE`                    | `memory` | Secrets Manager storage mode           |
| `FLOCI_STORAGE_SERVICES_SECRETSMANAGER_FLUSH_INTERVAL_MS`       | `5000`   | Secrets Manager flush interval (ms)    |
| `FLOCI_STORAGE_SERVICES_OPENSEARCH_MODE`                        | global default | OpenSearch storage mode           |
| `FLOCI_STORAGE_SERVICES_OPENSEARCH_FLUSH_INTERVAL_MS`           | `5000`         | OpenSearch flush interval (ms)    |

## Environment Variable Override

```bash
FLOCI_STORAGE_MODE=persistent
FLOCI_STORAGE_PERSISTENT_PATH=/app/data
```

## Recommended Profiles

=== "Fast CI"

    All in memory, fastest possible startup and test execution:

    ```yaml
    floci:
      storage:
        mode: memory
    ```

=== "Local development"

    Hybrid — survive restarts without slowing down writes:

    ```yaml
    floci:
      storage:
        mode: hybrid
        persistent-path: ./data
    ```

=== "Durable development"

    Persistent — every write is immediately on disk:

    ```yaml
    floci:
      storage:
        mode: persistent
        persistent-path: ./data
    ```