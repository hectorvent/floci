# IAM

**Protocol:** Query (XML) — `POST http://localhost:4566/` with `Action=` parameter

## Supported Actions

### Users
`CreateUser` · `GetUser` · `DeleteUser` · `ListUsers` · `UpdateUser` · `TagUser` · `UntagUser` · `ListUserTags`

### Groups
`CreateGroup` · `GetGroup` · `DeleteGroup` · `ListGroups` · `AddUserToGroup` · `RemoveUserFromGroup` · `ListGroupsForUser`

### Roles
`CreateRole` · `GetRole` · `DeleteRole` · `ListRoles` · `UpdateRole` · `UpdateAssumeRolePolicy` · `TagRole` · `UntagRole` · `ListRoleTags`

### Policies
`CreatePolicy` · `GetPolicy` · `DeletePolicy` · `ListPolicies` · `CreatePolicyVersion` · `GetPolicyVersion` · `DeletePolicyVersion` · `ListPolicyVersions` · `SetDefaultPolicyVersion` · `TagPolicy` · `UntagPolicy` · `ListPolicyTags`

### Policy Attachments
`AttachUserPolicy` · `DetachUserPolicy` · `ListAttachedUserPolicies`
`AttachGroupPolicy` · `DetachGroupPolicy` · `ListAttachedGroupPolicies`
`AttachRolePolicy` · `DetachRolePolicy` · `ListAttachedRolePolicies`

### Inline Policies
`PutUserPolicy` · `GetUserPolicy` · `DeleteUserPolicy` · `ListUserPolicies`
`PutGroupPolicy` · `GetGroupPolicy` · `DeleteGroupPolicy` · `ListGroupPolicies`
`PutRolePolicy` · `GetRolePolicy` · `DeleteRolePolicy` · `ListRolePolicies`

### Instance Profiles
`CreateInstanceProfile` · `GetInstanceProfile` · `DeleteInstanceProfile` · `ListInstanceProfiles` · `AddRoleToInstanceProfile` · `RemoveRoleFromInstanceProfile` · `ListInstanceProfilesForRole`

### Access Keys
`CreateAccessKey` · `GetAccessKeyLastUsed` · `ListAccessKeys` · `UpdateAccessKey` · `DeleteAccessKey`

### Login Profiles
`CreateLoginProfile` · `DeleteLoginProfile` · `UpdateLoginProfile`

## Examples

```bash
export AWS_ENDPOINT_URL=http://localhost:4566

# Create a role
aws iam create-role \
  --role-name lambda-execution-role \
  --assume-role-policy-document '{
    "Version": "2012-10-17",
    "Statement": [{
      "Effect": "Allow",
      "Principal": {"Service": "lambda.amazonaws.com"},
      "Action": "sts:AssumeRole"
    }]
  }' \
  --endpoint-url $AWS_ENDPOINT_URL

# Attach a managed policy
aws iam attach-role-policy \
  --role-name lambda-execution-role \
  --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole \
  --endpoint-url $AWS_ENDPOINT_URL

# Create a user
aws iam create-user --user-name alice --endpoint-url $AWS_ENDPOINT_URL

# Create an access key
aws iam create-access-key --user-name alice --endpoint-url $AWS_ENDPOINT_URL

# List roles
aws iam list-roles --endpoint-url $AWS_ENDPOINT_URL
```
