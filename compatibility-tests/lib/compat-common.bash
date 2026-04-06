#!/usr/bin/env bash
# Common helpers for IaC compatibility tests

# Environment configuration
export FLOCI_ENDPOINT="${FLOCI_ENDPOINT:-http://localhost:4566}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}"
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_ENDPOINT_URL="$FLOCI_ENDPOINT"

# AWS CLI wrapper with endpoint override
aws_cmd() {
    aws --endpoint-url "$FLOCI_ENDPOINT" --region "$AWS_DEFAULT_REGION" --output json "$@"
}

# JSON path extraction helper
json_get() {
    local json="$1"
    local path="$2"
    echo "$json" | jq -r "$path" 2>/dev/null || echo ""
}

# Wait for endpoint to be ready
wait_for_endpoint() {
    local max_attempts="${1:-30}"
    local attempt=1
    while [ $attempt -le $max_attempts ]; do
        if curl -sf "$FLOCI_ENDPOINT" > /dev/null 2>&1; then
            return 0
        fi
        echo "Waiting for endpoint... (attempt $attempt/$max_attempts)"
        sleep 1
        ((attempt++))
    done
    return 1
}
