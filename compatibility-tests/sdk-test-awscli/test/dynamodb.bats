#!/usr/bin/env bats
# DynamoDB tests

setup() {
    load 'test_helper/common-setup'
    TABLE_NAME="bats-test-table-$(unique_name)"
}

teardown() {
    aws_cmd dynamodb delete-table --table-name "$TABLE_NAME" >/dev/null 2>&1 || true
}

@test "DynamoDB: create table" {
    run aws_cmd dynamodb create-table \
        --table-name "$TABLE_NAME" \
        --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=sk,AttributeType=S \
        --key-schema AttributeName=pk,KeyType=HASH AttributeName=sk,KeyType=RANGE \
        --billing-mode PAY_PER_REQUEST
    assert_success
    status=$(json_get "$output" '.TableDescription.TableStatus')
    [ "$status" = "ACTIVE" ] || [ "$status" = "CREATING" ]
}

@test "DynamoDB: describe table" {
    aws_cmd dynamodb create-table \
        --table-name "$TABLE_NAME" \
        --attribute-definitions AttributeName=pk,AttributeType=S \
        --key-schema AttributeName=pk,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST >/dev/null

    ddb_wait_table "$TABLE_NAME"

    run aws_cmd dynamodb describe-table --table-name "$TABLE_NAME"
    assert_success
    name=$(json_get "$output" '.Table.TableName')
    [ "$name" = "$TABLE_NAME" ]
}

@test "DynamoDB: list tables" {
    aws_cmd dynamodb create-table \
        --table-name "$TABLE_NAME" \
        --attribute-definitions AttributeName=pk,AttributeType=S \
        --key-schema AttributeName=pk,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST >/dev/null

    ddb_wait_table "$TABLE_NAME"

    run aws_cmd dynamodb list-tables
    assert_success
    found=$(echo "$output" | jq --arg name "$TABLE_NAME" '.TableNames | any(. == $name)')
    [ "$found" = "true" ]
}

@test "DynamoDB: put and get item" {
    aws_cmd dynamodb create-table \
        --table-name "$TABLE_NAME" \
        --attribute-definitions AttributeName=pk,AttributeType=S \
        --key-schema AttributeName=pk,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST >/dev/null

    ddb_wait_table "$TABLE_NAME"

    run aws_cmd dynamodb put-item \
        --table-name "$TABLE_NAME" \
        --item '{"pk":{"S":"user#1"},"name":{"S":"Alice"}}'
    assert_success

    run aws_cmd dynamodb get-item \
        --table-name "$TABLE_NAME" \
        --key '{"pk":{"S":"user#1"}}'
    assert_success
    name=$(json_get "$output" '.Item.name.S')
    [ "$name" = "Alice" ]
}

@test "DynamoDB: update item" {
    aws_cmd dynamodb create-table \
        --table-name "$TABLE_NAME" \
        --attribute-definitions AttributeName=pk,AttributeType=S \
        --key-schema AttributeName=pk,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST >/dev/null

    ddb_wait_table "$TABLE_NAME"

    aws_cmd dynamodb put-item \
        --table-name "$TABLE_NAME" \
        --item '{"pk":{"S":"user#1"},"name":{"S":"Alice"}}' >/dev/null

    run aws_cmd dynamodb update-item \
        --table-name "$TABLE_NAME" \
        --key '{"pk":{"S":"user#1"}}' \
        --update-expression 'SET #n = :v' \
        --expression-attribute-names '{"#n":"name"}' \
        --expression-attribute-values '{":v":{"S":"Bob"}}'
    assert_success

    run aws_cmd dynamodb get-item \
        --table-name "$TABLE_NAME" \
        --key '{"pk":{"S":"user#1"}}'
    assert_success
    name=$(json_get "$output" '.Item.name.S')
    [ "$name" = "Bob" ]
}

@test "DynamoDB: scan table" {
    aws_cmd dynamodb create-table \
        --table-name "$TABLE_NAME" \
        --attribute-definitions AttributeName=pk,AttributeType=S \
        --key-schema AttributeName=pk,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST >/dev/null

    ddb_wait_table "$TABLE_NAME"

    aws_cmd dynamodb put-item --table-name "$TABLE_NAME" --item '{"pk":{"S":"user#1"},"name":{"S":"Alice"}}' >/dev/null
    aws_cmd dynamodb put-item --table-name "$TABLE_NAME" --item '{"pk":{"S":"user#2"},"name":{"S":"Bob"}}' >/dev/null

    run aws_cmd dynamodb scan --table-name "$TABLE_NAME"
    assert_success
    count=$(json_get "$output" '.Count')
    [ "$count" -ge 2 ]
}

@test "DynamoDB: query table" {
    aws_cmd dynamodb create-table \
        --table-name "$TABLE_NAME" \
        --attribute-definitions AttributeName=pk,AttributeType=S AttributeName=sk,AttributeType=S \
        --key-schema AttributeName=pk,KeyType=HASH AttributeName=sk,KeyType=RANGE \
        --billing-mode PAY_PER_REQUEST >/dev/null

    ddb_wait_table "$TABLE_NAME"

    aws_cmd dynamodb put-item --table-name "$TABLE_NAME" --item '{"pk":{"S":"user#1"},"sk":{"S":"profile"},"name":{"S":"Alice"}}' >/dev/null

    run aws_cmd dynamodb query \
        --table-name "$TABLE_NAME" \
        --key-condition-expression 'pk = :pk' \
        --expression-attribute-values '{":pk":{"S":"user#1"}}'
    assert_success
    count=$(json_get "$output" '.Count')
    [ "$count" -ge 1 ]
}

@test "DynamoDB: delete item" {
    aws_cmd dynamodb create-table \
        --table-name "$TABLE_NAME" \
        --attribute-definitions AttributeName=pk,AttributeType=S \
        --key-schema AttributeName=pk,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST >/dev/null

    ddb_wait_table "$TABLE_NAME"

    aws_cmd dynamodb put-item --table-name "$TABLE_NAME" --item '{"pk":{"S":"user#1"}}' >/dev/null

    run aws_cmd dynamodb delete-item \
        --table-name "$TABLE_NAME" \
        --key '{"pk":{"S":"user#1"}}'
    assert_success
}

@test "DynamoDB: delete table" {
    aws_cmd dynamodb create-table \
        --table-name "$TABLE_NAME" \
        --attribute-definitions AttributeName=pk,AttributeType=S \
        --key-schema AttributeName=pk,KeyType=HASH \
        --billing-mode PAY_PER_REQUEST >/dev/null

    ddb_wait_table "$TABLE_NAME"

    run aws_cmd dynamodb delete-table --table-name "$TABLE_NAME"
    assert_success
}
