#!/bin/bash
set -e

# 1. Start Floci
echo "=== Starting Floci with docker-compose ==="
docker compose up -d --build

# Wait for healthy
echo "Waiting for Floci to be healthy..."
# Portable wait without 'timeout' command
MAX_RETRIES=60
COUNT=0
until curl -sf http://localhost:4566/_floci/health >/dev/null 2>&1; do
  if [ $COUNT -ge $MAX_RETRIES ]; then
    echo "Floci failed to become healthy in time"
    exit 1
  fi
  sleep 1
  COUNT=$((COUNT + 1))
  echo -n "."
done
echo " Floci is up!"

# 2. Network setup (Floci uses floci_default from compose)
NETWORK="floci_default"
DOCKER_GID=$(stat -c '%g' /var/run/docker.sock 2>/dev/null || stat -f '%g' /var/run/docker.sock)

# 3. Test suites
SUITES=(
  "sdk-test-python"
  "sdk-test-node"
  "sdk-test-java"
  "sdk-test-go"
  "sdk-test-awscli"
  "sdk-test-rust"
  "compat-cdk"
  "compat-terraform"
  "compat-opentofu"
)

# results dir
mkdir -p test-results

for suite in "${SUITES[@]}"; do
  echo "=== Running $suite in Docker ==="
  
  IMAGE_NAME="compat-$suite"
  
  # Build
  docker build -q -t "$IMAGE_NAME" "compatibility-tests/$suite"
  
  EXTRA_ARGS=""
  if [ "$suite" = "compat-cdk" ]; then
    EXTRA_ARGS="-v /var/run/docker.sock:/var/run/docker.sock --group-add $DOCKER_GID"
  fi

  # Run
  docker run --rm --network "$NETWORK" \
    -e FLOCI_ENDPOINT=http://floci:4566 \
    -v "$(pwd)/test-results:/results" \
    $EXTRA_ARGS \
    "$IMAGE_NAME" || echo "Test suite $suite failed"
done

echo "=== All Docker tests completed ==="
