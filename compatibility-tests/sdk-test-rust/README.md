# sdk-test-rust

Compatibility tests for [Floci](https://github.com/hectorvent/floci) using the **AWS SDK for Rust (1.8.15)**.

## Services Covered

| Group            | Description                             |
| ---------------- | --------------------------------------- |
| `ssm`            | Parameter Store — put, get, path        |
| `sqs`            | Queues, send/receive/delete, visibility |
| `s3`             | Buckets, objects, tagging, copy, delete |
| `sts`            | GetCallerIdentity                       |
| `kms`            | Keys, aliases, encrypt/decrypt          |
| `secretsmanager` | Create/get/put/list/delete secrets      |

## Requirements

- Rust (stable)
- Cargo

## Running

```bash
# All groups
cargo run

# Specific groups
cargo run -- ssm sqs s3

# Env var (comma-separated)
FLOCI_TESTS=kms cargo run
```

## Configuration

| Variable         | Default                 | Description             |
| ---------------- | ----------------------- | ----------------------- |
| `FLOCI_ENDPOINT` | `http://localhost:4566` | Floci emulator endpoint |

AWS credentials are always `test` / `test` / `us-east-1`.

## Docker

```bash
docker build -t floci-sdk-rust .
docker run --rm --network host floci-sdk-rust

# Custom endpoint (macOS/Windows)
docker run --rm -e FLOCI_ENDPOINT=http://host.docker.internal:4566 floci-sdk-rust
```
