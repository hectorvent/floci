package io.github.hectorvent.floci.services.iam;

import java.util.List;

/**
 * Catalog of commonly-used AWS managed policies seeded at startup.
 * Policy documents use a permissive wildcard because floci does not
 * enforce IAM policy evaluation.
 */
final class AwsManagedPolicies {

    static final String ARN_PREFIX = "arn:aws:iam::aws:policy";

    static final String PERMISSIVE_DOCUMENT =
            "{\"Version\":\"2012-10-17\",\"Statement\":"
            + "[{\"Effect\":\"Allow\",\"Action\":\"*\",\"Resource\":\"*\"}]}";

    record ManagedPolicyDef(String name, String path, String description) {
        String arn() {
            return ARN_PREFIX + path + name;
        }
    }

    static final List<ManagedPolicyDef> POLICIES = List.of(
        new ManagedPolicyDef("AdministratorAccess", "/",
                "Provides full access to AWS services and resources."),
        new ManagedPolicyDef("PowerUserAccess", "/",
                "Provides full access to AWS services and resources, but does not allow management of Users and groups."),
        new ManagedPolicyDef("ReadOnlyAccess", "/",
                "Provides read-only access to AWS services and resources."),
        new ManagedPolicyDef("IAMFullAccess", "/",
                "Provides full access to IAM."),
        new ManagedPolicyDef("AmazonS3FullAccess", "/",
                "Provides full access to all buckets via the AWS Management Console."),
        new ManagedPolicyDef("AmazonS3ReadOnlyAccess", "/",
                "Provides read-only access to all buckets via the AWS Management Console."),
        new ManagedPolicyDef("AmazonDynamoDBFullAccess", "/",
                "Provides full access to Amazon DynamoDB via the AWS Management Console."),
        new ManagedPolicyDef("AmazonEC2FullAccess", "/",
                "Provides full access to Amazon EC2 via the AWS Management Console."),
        new ManagedPolicyDef("AmazonSQSFullAccess", "/",
                "Provides full access to Amazon SQS via the AWS Management Console."),
        new ManagedPolicyDef("AmazonSNSFullAccess", "/",
                "Provides full access to Amazon SNS via the AWS Management Console."),
        new ManagedPolicyDef("AmazonVPCFullAccess", "/",
                "Provides full access to Amazon VPC via the AWS Management Console."),
        new ManagedPolicyDef("CloudWatchFullAccess", "/",
                "Provides full access to CloudWatch."),
        new ManagedPolicyDef("AWSLambdaBasicExecutionRole", "/service-role/",
                "Provides write permissions to CloudWatch Logs."),
        new ManagedPolicyDef("AWSLambdaFullAccess", "/",
                "Provides full access to Lambda, S3, DynamoDB, CloudWatch Metrics and Logs.")
    );

    private AwsManagedPolicies() {}
}
