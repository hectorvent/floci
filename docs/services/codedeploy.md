# CodeDeploy

Floci implements the CodeDeploy API — stored-state management for applications, deployment groups, and configs, plus real Lambda deployment execution with traffic shifting and lifecycle hooks.

**Protocol:** JSON 1.1 — `POST /` with `X-Amz-Target: CodeDeploy_20141006.<Action>`

**ARN formats:**

- `arn:aws:codedeploy:<region>:<account>:application:<name>`
- `arn:aws:codedeploy:<region>:<account>:deploymentgroup:<app>/<group>`
- `arn:aws:codedeploy:<region>:<account>:deploymentconfig:<name>`
- `arn:aws:codedeploy:<region>:<account>:deployment:<id>`

## Supported Operations (30 total)

### Applications

| Operation | Notes |
|---|---|
| `CreateApplication` | Supports `computePlatform`: `Server`, `Lambda`, `ECS` |
| `GetApplication` | Returns application metadata |
| `UpdateApplication` | Renames an application |
| `DeleteApplication` | Removes application and all its deployment groups |
| `ListApplications` | Returns all application names |
| `BatchGetApplications` | Returns info for multiple applications |

### Deployment Groups

| Operation | Notes |
|---|---|
| `CreateDeploymentGroup` | Stores group config; deployment config defaults to `CodeDeployDefault.OneAtATime` |
| `GetDeploymentGroup` | Returns group metadata |
| `UpdateDeploymentGroup` | Partial update; supports rename via `newDeploymentGroupName` |
| `DeleteDeploymentGroup` | Returns `hooksNotCleanedUp: []` |
| `ListDeploymentGroups` | Returns all group names for an application |
| `BatchGetDeploymentGroups` | Returns info for multiple groups |

### Deployment Configs

| Operation | Notes |
|---|---|
| `CreateDeploymentConfig` | Creates a custom config; names starting with `CodeDeployDefault.` are rejected |
| `GetDeploymentConfig` | Returns config including built-ins |
| `DeleteDeploymentConfig` | Custom configs only; built-ins cannot be deleted |
| `ListDeploymentConfigs` | Returns all configs including all 17 pre-seeded built-ins |

### Deployment Execution

| Operation | Notes |
|---|---|
| `CreateDeployment` | Starts a real Lambda deployment: shifts alias `RoutingConfig` weights for canary/linear strategies; invokes lifecycle hooks |
| `GetDeployment` | Returns current deployment state; poll `status` until `Succeeded`, `Failed`, or `Stopped` |
| `StopDeployment` | Signals an in-progress deployment to stop; transitions to `Stopped` |
| `ContinueDeployment` | Accepted (no-op for fully automated deployments) |
| `ListDeployments` | Returns deployment IDs filtered by application, group, or status |
| `BatchGetDeployments` | Returns info for multiple deployments |
| `ListDeploymentTargets` | Returns target IDs for a deployment |
| `BatchGetDeploymentTargets` | Returns target details including lifecycle event status |
| `PutLifecycleEventHookExecutionStatus` | Called by lifecycle hook Lambda to report `Succeeded` or `Failed`; failure triggers auto-rollback |

### Tagging

| Operation | Notes |
|---|---|
| `TagResource` | Tags any resource by ARN |
| `UntagResource` | Removes specific tag keys |
| `ListTagsForResource` | Returns tags for a resource ARN |

### On-Premises (no-op)

| Operation | Notes |
|---|---|
| `AddTagsToOnPremisesInstances` | Accepted, no-op |
| `RemoveTagsFromOnPremisesInstances` | Accepted, no-op |

## Pre-seeded Built-in Deployment Configs

The following 17 configurations are always available (matching real AWS):

**Server:**
- `CodeDeployDefault.OneAtATime`
- `CodeDeployDefault.HalfAtATime`
- `CodeDeployDefault.AllAtOnce`

**Lambda:**
- `CodeDeployDefault.LambdaAllAtOnce`
- `CodeDeployDefault.LambdaCanary10Percent5Minutes`
- `CodeDeployDefault.LambdaCanary10Percent10Minutes`
- `CodeDeployDefault.LambdaCanary10Percent15Minutes`
- `CodeDeployDefault.LambdaCanary10Percent30Minutes`
- `CodeDeployDefault.LambdaLinear10PercentEvery1Minute`
- `CodeDeployDefault.LambdaLinear10PercentEvery2Minutes`
- `CodeDeployDefault.LambdaLinear10PercentEvery3Minutes`
- `CodeDeployDefault.LambdaLinear10PercentEvery10Minutes`

**ECS:**
- `CodeDeployDefault.ECSAllAtOnce`
- `CodeDeployDefault.ECSCanary10Percent5Minutes`
- `CodeDeployDefault.ECSCanary10Percent15Minutes`
- `CodeDeployDefault.ECSLinear10PercentEvery1Minutes`
- `CodeDeployDefault.ECSLinear10PercentEvery3Minutes`

## Lambda Deployment Model

For `computePlatform: Lambda`, `CreateDeployment` performs real traffic shifting:

1. Reads the deployment group's `deploymentStyle` and `deploymentConfigName` to determine the traffic shift strategy
2. For **canary** and **linear** strategies: updates the Lambda alias `RoutingConfig` to route a percentage to the new function version, waits the configured interval, then shifts to 100%
3. For **all-at-once**: shifts directly to 100% of the new version
4. Invokes `BeforeAllowTraffic` lifecycle hook Lambda (if configured) and waits for `PutLifecycleEventHookExecutionStatus` callback
5. Invokes `AfterAllowTraffic` lifecycle hook Lambda (if configured) and waits for the callback
6. If any lifecycle hook reports `Failed`, auto-rolls back the alias to the previous version and marks the deployment `Failed`

## Configuration

```yaml
floci:
  services:
    codedeploy:
      enabled: true   # default
```

## CLI Examples

```bash
# Create an application
aws --endpoint-url http://localhost:4566 deploy create-application \
  --application-name my-app \
  --compute-platform Lambda

# Create a deployment group for Lambda
aws --endpoint-url http://localhost:4566 deploy create-deployment-group \
  --application-name my-app \
  --deployment-group-name my-group \
  --deployment-config-name CodeDeployDefault.LambdaCanary10Percent5Minutes \
  --service-role-arn arn:aws:iam::000000000000:role/codedeploy-role \
  --deployment-style deploymentType=BLUE_GREEN,deploymentOption=WITH_TRAFFIC_CONTROL

# Start a deployment
aws --endpoint-url http://localhost:4566 deploy create-deployment \
  --application-name my-app \
  --deployment-group-name my-group \
  --revision revisionType=AppSpecContent,appSpecContent={content='{"version":0.0,"Resources":[{"myFunction":{"Type":"AWS::Lambda::Function","Properties":{"Name":"my-function","Alias":"live","CurrentVersion":"1","TargetVersion":"2"}}}]}'}

# Poll deployment status
aws --endpoint-url http://localhost:4566 deploy get-deployment --deployment-id <id>

# List built-in deployment configs
aws --endpoint-url http://localhost:4566 deploy list-deployment-configs
```
