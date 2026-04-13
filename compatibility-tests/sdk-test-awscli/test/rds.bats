#!/usr/bin/env bats
# RDS integration tests

setup() {
    load 'test_helper/common-setup'
    DB_ID="bats-rds-$(unique_name)"
    DB_ID_2="bats-rds-2-$(unique_name)"
}

teardown() {
    aws_cmd rds delete-db-instance --db-instance-identifier "$DB_ID" --skip-final-snapshot >/dev/null 2>&1 || true
    aws_cmd rds delete-db-instance --db-instance-identifier "$DB_ID_2" --skip-final-snapshot >/dev/null 2>&1 || true
}

@test "RDS: create db instance returns resource identifiers" {
    run aws_cmd rds create-db-instance \
        --db-instance-identifier "$DB_ID" \
        --engine postgres \
        --db-instance-class db.t3.micro \
        --allocated-storage 10

    assert_success

    dbi_resource_id=$(json_get "$output" '.DBInstance.DbiResourceId')
    db_instance_arn=$(json_get "$output" '.DBInstance.DBInstanceArn')

    [ -n "$dbi_resource_id" ]
    [[ "$dbi_resource_id" =~ ^db- ]]

    [ -n "$db_instance_arn" ]
    [[ "$db_instance_arn" == *":db:$DB_ID" ]]
}

@test "RDS: describe db instances filters by identifier" {
    aws_cmd rds create-db-instance \
        --db-instance-identifier "$DB_ID" \
        --engine postgres \
        --db-instance-class db.t3.micro \
        --allocated-storage 10

    run aws_cmd rds describe-db-instances --db-instance-identifier "$DB_ID"
    assert_success

    count=$(echo "$output" | jq '.DBInstances | length')
    [ "$count" -eq 1 ]

    id=$(json_get "$output" '.DBInstances[0].DBInstanceIdentifier')
    [ "$id" = "$DB_ID" ]
}

@test "RDS: describe db instances is case-insensitive" {
    aws_cmd rds create-db-instance \
        --db-instance-identifier "$DB_ID" \
        --engine postgres \
        --db-instance-class db.t3.micro \
        --allocated-storage 10

    # shellcheck disable=SC2155
    local upper_id=$(echo "$DB_ID" | tr '[:lower:]' '[:upper:]')
    run aws_cmd rds describe-db-instances --db-instance-identifier "$upper_id"
    assert_success

    count=$(echo "$output" | jq '.DBInstances | length')
    [ "$count" -eq 1 ]

    id=$(json_get "$output" '.DBInstances[0].DBInstanceIdentifier')
    [ "$id" = "$DB_ID" ]
}

@test "RDS: describe db instances returns all when no filter" {
    aws_cmd rds create-db-instance \
        --db-instance-identifier "$DB_ID" \
        --engine postgres \
        --db-instance-class db.t3.micro \
        --allocated-storage 10

    aws_cmd rds create-db-instance \
        --db-instance-identifier "$DB_ID_2" \
        --engine postgres \
        --db-instance-class db.t3.micro \
        --allocated-storage 10

    run aws_cmd rds describe-db-instances
    assert_success

    # Might have more from other tests, but at least 2
    count=$(echo "$output" | jq '.DBInstances | length')
    [ "$count" -ge 2 ]
}
