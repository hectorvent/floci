#!/usr/bin/env bats
# Secrets Manager tests

setup() {
    load 'test_helper/common-setup'
    SECRET_NAME="bats/test/secret-$(unique_name)"
}

teardown() {
    aws_cmd secretsmanager delete-secret \
        --secret-id "$SECRET_NAME" \
        --force-delete-without-recovery >/dev/null 2>&1 || true
}

@test "Secrets Manager: create secret" {
    run aws_cmd secretsmanager create-secret \
        --name "$SECRET_NAME" \
        --secret-string '{"key":"value"}'
    assert_success
    arn=$(json_get "$output" '.ARN')
    [ -n "$arn" ]
}

@test "Secrets Manager: get secret value" {
    aws_cmd secretsmanager create-secret \
        --name "$SECRET_NAME" \
        --secret-string '{"key":"value"}' >/dev/null

    run aws_cmd secretsmanager get-secret-value --secret-id "$SECRET_NAME"
    assert_success
    value=$(json_get "$output" '.SecretString')
    [ "$value" = '{"key":"value"}' ]
}

@test "Secrets Manager: update secret" {
    aws_cmd secretsmanager create-secret \
        --name "$SECRET_NAME" \
        --secret-string '{"key":"value"}' >/dev/null

    run aws_cmd secretsmanager update-secret \
        --secret-id "$SECRET_NAME" \
        --secret-string '{"key":"updated"}'
    assert_success

    run aws_cmd secretsmanager get-secret-value --secret-id "$SECRET_NAME"
    assert_success
    value=$(json_get "$output" '.SecretString')
    [ "$value" = '{"key":"updated"}' ]
}

@test "Secrets Manager: list secrets" {
    aws_cmd secretsmanager create-secret \
        --name "$SECRET_NAME" \
        --secret-string '{"key":"value"}' >/dev/null

    run aws_cmd secretsmanager list-secrets
    assert_success
    found=$(echo "$output" | jq --arg name "$SECRET_NAME" '.SecretList | any(.Name == $name)')
    [ "$found" = "true" ]
}

@test "Secrets Manager: delete secret" {
    aws_cmd secretsmanager create-secret \
        --name "$SECRET_NAME" \
        --secret-string '{"key":"value"}' >/dev/null

    run aws_cmd secretsmanager delete-secret \
        --secret-id "$SECRET_NAME" \
        --force-delete-without-recovery
    assert_success
}
