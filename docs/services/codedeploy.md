# CodeDeploy

Floci implements the CodeDeploy management API (Phase 1) — stored-state CRUD for applications, deployment groups, and deployment configurations. Built-in deployment configurations are pre-seeded and always available. Deployment execution (Phase 2) is not yet implemented.

**Protocol:** JSON 1.1 — `POST /` with `X-Amz-Target: CodeDeploy_20141006.<Action>`

**ARN formats:**

- `arn:aws:codedeploy:<region>:<account>:application:<name>`
- `arn:aws:codedeploy:<region>:<account>:deploymentgroup:<app>/<group>`

## Supported Operations (Phase 1 — 21 total)

| Operation | Notes |
|---|---|
| `CreateApplication` | Supports `computePlatform`: `Server`, `Lambda`, `ECS` |
| `GetApplication` | Returns application metadata |
| `UpdateApplication` | Renames an application |
| `DeleteApplication` | Removes application and all its deployment groups |
| `ListApplications` | Returns all application names |
| `BatchGetApplications` | Returns info for multiple applications |
| `CreateDeploymentGroup` | Stores group config; deployment config defaults to `CodeDeployDefault.OneAtATime` |
| `GetDeploymentGroup` | Returns group metadata |
| `UpdateDeploymentGroup` | Partial update; supports rename via `newDeploymentGroupName` |
| `DeleteDeploymentGroup` | Returns `hooksNotCleanedUp: []` |
| `ListDeploymentGroups` | Returns all group names for an application |
| `BatchGetDeploymentGroups` | Returns info for multiple groups |
| `CreateDeploymentConfig` | Creates a custom config; names starting with `CodeDeployDefault.` are rejected |
| `GetDeploymentConfig` | Returns config including built-ins |
| `DeleteDeploymentConfig` | Custom configs only; built-ins cannot be deleted |
| `ListDeploymentConfigs` | Returns all configs including all 17 pre-seeded built-ins |
| `TagResource` | Tags any resource by ARN |
| `UntagResource` | Removes specific tag keys |
| `ListTagsForResource` | Returns tags for a resource ARN |
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

# List built-in deployment configs
aws --endpoint-url http://localhost:4566 deploy list-deployment-configs

# Create a deployment group
aws --endpoint-url http://localhost:4566 deploy create-deployment-group \
  --application-name my-app \
  --deployment-group-name my-group \
  --deployment-config-name CodeDeployDefault.LambdaAllAtOnce \
  --service-role-arn arn:aws:iam::000000000000:role/codedeploy-role
```

## Phase 2 (not yet implemented)

`CreateDeployment`, `GetDeployment`, `StopDeployment`, `ContinueDeployment` with real Lambda traffic-shifting (updating alias `RoutingConfig` weights). Phase 2 focuses on the `computePlatform: Lambda` path since Lambda is already fully real in Floci.
