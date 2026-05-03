# Docker Images

Floci publishes images to [Docker Hub (`floci/floci`)](https://hub.docker.com/r/floci/floci).

Every image tag combines two independent choices: **what's inside** (variant) and **how stable it is** (channel).

## Axis 1 — Variant (what's inside)

| Variant | Contents | When to use |
|---|---|---|
| **Standard** | Floci native binary only | General use — CI, local dev, Testcontainers **(recommended)** |
| **Compat** | Floci + Python 3 + AWS CLI + boto3 | Workflows that need AWS tooling available inside the container |

The compat image is built on top of the standard image — startup time and memory footprint are identical. Only the image size increases.

## Axis 2 — Channel (how stable)

| Channel | Source | Published |
|---|---|---|
| **Release** | Tagged version (e.g. `1.5.11`) | On every release |
| **Nightly** | Tip of `main` | Every night at 22:00 CT |

Release images are stable and recommended for most use cases. Nightly images track active development and may include unreleased changes.

## Full Tag Matrix

Combining both axes gives the complete set of published tags:

|  | Standard | Compat |
|---|---|---|
| **Release (latest)** | `latest` ✅ | `latest-compat` |
| **Release (pinned)** | `x.y.z` | `x.y.z-compat` |
| **Nightly (floating)** | `nightly` | `nightly-compat` |
| **Nightly (dated)** | `nightly-mmddyyyy` | `nightly-mmddyyyy-compat` |

Dated nightly tags (e.g. `nightly-05022026`) are fixed and never move — use them for reproducible builds from `main`.

!!! warning
    Nightly images may include unreleased or experimental changes. Use release tags in production-like environments.

## Quick Reference

```yaml title="docker-compose.yml"
# Standard release — recommended
image: floci/floci:latest

# Compat release — includes AWS CLI and boto3
image: floci/floci:latest-compat

# Pinned release — reproducible builds
image: floci/floci:1.5.11

# Nightly — track main
image: floci/floci:nightly
```

## Multi-Architecture

All images are published as multi-arch manifests supporting `linux/amd64` and `linux/arm64`. Docker selects the correct variant automatically.

## What's in the Compat Image

The compat image installs the following on top of the standard image:

- Python 3 + pip (via `microdnf` on `ubi9-minimal`)
- [AWS CLI](https://pypi.org/project/awscli/) (via pip)
- [boto3](https://pypi.org/project/boto3/) (via pip)

## Local Development

The project ships a `docker-compose.yml` at the repository root configured for local development. By default it uses `docker/Dockerfile` (a fast JVM build suited for iteration). Switch the `dockerfile` entry to test the native image locally:

```yaml title="docker-compose.yml"
build:
  context: .
  dockerfile: docker/Dockerfile.native   # or docker/Dockerfile for fast JVM dev build
```
