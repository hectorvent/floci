#!/usr/bin/env bash
# Common setup for CDK bats tests

# Get repository root (3 levels up from test_helper)
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
CDK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Load bats helpers - support both local and Docker environments
if [[ -d "${REPO_ROOT}/lib/bats-support" ]]; then
    load "${REPO_ROOT}/lib/bats-support/load"
    load "${REPO_ROOT}/lib/bats-assert/load"
elif [[ -n "${BATS_LIB_PATH}" ]]; then
    load "${BATS_LIB_PATH}/bats-support/load"
    load "${BATS_LIB_PATH}/bats-assert/load"
else
    echo "Error: Cannot find bats-support/bats-assert libraries" >&2
    exit 1
fi

# Load shared compat helpers if available, otherwise define them inline
if [[ -f "${REPO_ROOT}/lib/compat-common.bash" ]]; then
    source "${REPO_ROOT}/lib/compat-common.bash"
else
    # Fallback definitions for Docker environment
    export FLOCI_ENDPOINT="${FLOCI_ENDPOINT:-http://localhost:4566}"
    export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
    export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
    export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
    export AWS_ENDPOINT_URL="$FLOCI_ENDPOINT"

    aws_cmd() {
        aws --endpoint-url "$FLOCI_ENDPOINT" --region "$AWS_DEFAULT_REGION" --output json "$@"
    }
fi

# CDK-specific environment
export LOCALSTACK_HOSTNAME="${LOCALSTACK_HOSTNAME:-localhost}"
export EDGE_PORT="${EDGE_PORT:-4566}"

# Override endpoint for Docker networking if needed
if [ "$LOCALSTACK_HOSTNAME" = "floci" ]; then
    export FLOCI_ENDPOINT="http://floci:4566"
    export AWS_ENDPOINT_URL="http://floci:4566"
fi
