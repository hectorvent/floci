# Installation

Floci can be run three ways: as a Docker image, as a pre-built native binary, or built from source.

## Docker (Recommended)

No installation required beyond Docker itself.

```bash
docker pull hectorvent/floci:latest
```

| Tag | Description |
|---|---|
| `latest` | Native image — sub-second startup, low memory (**default**) |
| `x.y.z` | Native image — specific release version |
| `latest-jvm` | JVM image — most compatible |
| `x.y.z-jvm` | JVM image — specific release version |

### Requirements

- Docker 20.10+
- `docker compose` v2+ (plugin syntax, not standalone `docker-compose`)

## Native vs JVM

The `latest` tag is the native image — a self-contained executable with no JVM dependency.

```yaml title="docker-compose.yml"
services:
  floci:
    image: hectorvent/floci:latest   # native — recommended
    ports:
      - "4566:4566"
```

Use the JVM image if you need broader platform compatibility or encounter native image issues:

```yaml title="docker-compose.yml"
services:
  floci:
    image: hectorvent/floci:latest-jvm
    ports:
      - "4566:4566"
```

### Startup comparison

| Image | Tag | Typical startup | Idle memory |
|---|---|---|---|
| Native | `latest` / `x.y.z` | < 100 ms | ~50 MB |
| JVM | `latest-jvm` / `x.y.z-jvm` | ~2 s | ~250 MB |

## Build from Source

### Prerequisites

- Java 25+
- Maven 3.9+
- (Optional) GraalVM Mandrel for native compilation

### Clone and run

```bash
git clone https://github.com/floci-io/floci.git
cd floci
mvn quarkus:dev          # dev mode with hot reload on port 4566
```

### Build a production JAR

```bash
mvn clean package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

### Build a native executable

```bash
mvn clean package -Pnative -DskipTests
./target/floci-runner
```

!!! note
    Native compilation requires GraalVM or Mandrel with the `native-image` tool on your PATH. Build time is typically 2–5 minutes.