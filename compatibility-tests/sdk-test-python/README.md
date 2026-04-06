# sdk-test-python

Compatibility tests for [Floci](https://github.com/hectorvent/floci) using **boto3 (1.37.1)**.

## Services Covered

| Group                   | Description                                                              |
| ----------------------- | ------------------------------------------------------------------------ |
| `ssm`                   | Parameter Store — put, get, label, history, path, tags                   |
| `sqs`                   | Queues, send/receive/delete, DLQ, visibility                             |
| `sns`                   | Topics, subscriptions, publish, SQS delivery                             |
| `s3`                    | Buckets, objects, tagging, copy, batch delete                            |
| `dynamodb`              | Tables, CRUD, batch, TTL, tags                                           |
| `lambda`                | Create/invoke/update/delete functions                                    |
| `iam`                   | Users, roles, policies, access keys                                      |
| `sts`                   | GetCallerIdentity, AssumeRole, GetSessionToken                           |
| `secretsmanager`        | Create/get/put/list/delete secrets, versioning, tags                     |
| `kms`                   | Keys, aliases, encrypt/decrypt, data keys, sign/verify                   |
| `kinesis`               | Streams, shards, PutRecord/GetRecords                                    |
| `cloudwatch-metrics`    | PutMetricData, ListMetrics, GetMetricStatistics, alarms                  |
| `cloudformation-naming` | Auto physical name generation, explicit name precedence, cross-reference |
| `cognito`               | User pools, clients, AdminCreateUser, InitiateAuth, GetUser              |

## Requirements

- Python 3.9+
- pip

## Running

```bash
pip install -r requirements.txt

# All groups
python test_all.py

# Specific groups
python test_all.py sqs s3

# Env var (comma-separated)
FLOCI_TESTS=kms python test_all.py
```

## Configuration

| Variable         | Default                 | Description             |
| ---------------- | ----------------------- | ----------------------- |
| `FLOCI_ENDPOINT` | `http://localhost:4566` | Floci emulator endpoint |

AWS credentials are always `test` / `test` / `us-east-1`.

## Docker

```bash
docker build -t floci-sdk-python .
docker run --rm --network host floci-sdk-python

# Custom endpoint (macOS/Windows)
docker run --rm -e FLOCI_ENDPOINT=http://host.docker.internal:4566 floci-sdk-python
```
