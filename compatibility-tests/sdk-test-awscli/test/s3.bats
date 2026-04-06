#!/usr/bin/env bats
# S3 tests

setup() {
    load 'test_helper/common-setup'
    BUCKET="bats-test-bucket-$(unique_name)"
}

teardown() {
    # Clean up all objects in bucket
    aws_cmd s3 rm "s3://$BUCKET" --recursive >/dev/null 2>&1 || true
    aws_cmd s3api delete-bucket --bucket "$BUCKET" >/dev/null 2>&1 || true
}

@test "S3: create bucket" {
    run aws_cmd s3api create-bucket --bucket "$BUCKET"
    assert_success
}

@test "S3: list buckets" {
    aws_cmd s3api create-bucket --bucket "$BUCKET" >/dev/null

    run aws_cmd s3api list-buckets
    assert_success
    found=$(echo "$output" | jq --arg name "$BUCKET" '.Buckets | any(.Name == $name)')
    [ "$found" = "true" ]
}

@test "S3: put object" {
    aws_cmd s3api create-bucket --bucket "$BUCKET" >/dev/null

    local body_file
    body_file=$(mktemp)
    echo -n "hello-s3-bats" > "$body_file"

    run aws_cmd s3api put-object --bucket "$BUCKET" --key "test.txt" --body "$body_file"
    assert_success
    rm -f "$body_file"
}

@test "S3: get object" {
    aws_cmd s3api create-bucket --bucket "$BUCKET" >/dev/null

    local body_file get_file
    body_file=$(mktemp)
    get_file=$(mktemp)
    echo -n "hello-s3-bats" > "$body_file"

    aws_cmd s3api put-object --bucket "$BUCKET" --key "test.txt" --body "$body_file" >/dev/null

    run aws_cmd s3api get-object --bucket "$BUCKET" --key "test.txt" "$get_file"
    assert_success

    content=$(cat "$get_file")
    [ "$content" = "hello-s3-bats" ]

    rm -f "$body_file" "$get_file"
}

@test "S3: head object" {
    aws_cmd s3api create-bucket --bucket "$BUCKET" >/dev/null

    local body_file
    body_file=$(mktemp)
    echo -n "hello-s3-bats" > "$body_file"
    aws_cmd s3api put-object --bucket "$BUCKET" --key "test.txt" --body "$body_file" >/dev/null

    run aws_cmd s3api head-object --bucket "$BUCKET" --key "test.txt"
    assert_success
    length=$(json_get "$output" '.ContentLength')
    [ "$length" = "13" ]

    rm -f "$body_file"
}

@test "S3: list objects" {
    aws_cmd s3api create-bucket --bucket "$BUCKET" >/dev/null

    local body_file
    body_file=$(mktemp)
    echo -n "hello" > "$body_file"
    aws_cmd s3api put-object --bucket "$BUCKET" --key "test.txt" --body "$body_file" >/dev/null

    run aws_cmd s3api list-objects-v2 --bucket "$BUCKET"
    assert_success
    found=$(echo "$output" | jq '.Contents | any(.Key == "test.txt")')
    [ "$found" = "true" ]

    rm -f "$body_file"
}

@test "S3: copy object" {
    aws_cmd s3api create-bucket --bucket "$BUCKET" >/dev/null

    local body_file
    body_file=$(mktemp)
    echo -n "hello" > "$body_file"
    aws_cmd s3api put-object --bucket "$BUCKET" --key "src.txt" --body "$body_file" >/dev/null

    run aws_cmd s3api copy-object --bucket "$BUCKET" --copy-source "$BUCKET/src.txt" --key "dst.txt"
    assert_success

    rm -f "$body_file"
}

@test "S3: put and get object tagging" {
    aws_cmd s3api create-bucket --bucket "$BUCKET" >/dev/null

    local body_file
    body_file=$(mktemp)
    echo -n "hello" > "$body_file"
    aws_cmd s3api put-object --bucket "$BUCKET" --key "test.txt" --body "$body_file" >/dev/null

    run aws_cmd s3api put-object-tagging \
        --bucket "$BUCKET" \
        --key "test.txt" \
        --tagging 'TagSet=[{Key=env,Value=test}]'
    assert_success

    run aws_cmd s3api get-object-tagging --bucket "$BUCKET" --key "test.txt"
    assert_success
    found=$(echo "$output" | jq '.TagSet | any(.Key == "env" and .Value == "test")')
    [ "$found" = "true" ]

    rm -f "$body_file"
}

@test "S3: delete object" {
    aws_cmd s3api create-bucket --bucket "$BUCKET" >/dev/null

    local body_file
    body_file=$(mktemp)
    echo -n "hello" > "$body_file"
    aws_cmd s3api put-object --bucket "$BUCKET" --key "test.txt" --body "$body_file" >/dev/null

    run aws_cmd s3api delete-object --bucket "$BUCKET" --key "test.txt"
    assert_success

    rm -f "$body_file"
}

@test "S3: delete bucket" {
    aws_cmd s3api create-bucket --bucket "$BUCKET" >/dev/null

    run aws_cmd s3api delete-bucket --bucket "$BUCKET"
    assert_success
}
