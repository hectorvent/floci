# CodeBuild

Floci implements the CodeBuild management API (Phase 1) — stored-state CRUD for projects, report groups, and source credentials. Build execution (Phase 2) is not yet implemented.

**Protocol:** JSON 1.1 — `POST /` with `X-Amz-Target: CodeBuild_20161006.<Action>`

**ARN formats:**

- `arn:aws:codebuild:<region>:<account>:project/<name>`
- `arn:aws:codebuild:<region>:<account>:report-group/<name>`
- `arn:aws:codebuild:<region>:<account>:token/<type>-<uuid>`

## Supported Operations (Phase 1 — 14 total)

| Operation | Notes |
|---|---|
| `CreateProject` | Stores project config; requires `name`, `source.type`, `artifacts.type`, `environment`, `serviceRole` |
| `UpdateProject` | Partial update — only supplied fields are modified |
| `DeleteProject` | Removes project by name |
| `BatchGetProjects` | Returns found projects and a `projectsNotFound` list |
| `ListProjects` | Returns all project names in the region |
| `CreateReportGroup` | Stores report group config |
| `UpdateReportGroup` | Partial update by ARN |
| `DeleteReportGroup` | Removes report group by ARN |
| `BatchGetReportGroups` | Returns found report groups and a `reportGroupsNotFound` list |
| `ListReportGroups` | Returns all report group ARNs in the region |
| `ImportSourceCredentials` | Stores server type and auth type; token is accepted but not returned |
| `ListSourceCredentials` | Returns stored credential metadata (no tokens) |
| `DeleteSourceCredentials` | Removes source credentials by ARN |
| `ListCuratedEnvironmentImages` | Returns a static list of standard CodeBuild images for AL2 and Ubuntu |

## Configuration

```yaml
floci:
  services:
    codebuild:
      enabled: true   # default
```

## CLI Examples

```bash
# Create a project
aws --endpoint-url http://localhost:4566 codebuild create-project \
  --name my-project \
  --source type=S3,location=my-bucket/source.zip \
  --artifacts type=NO_ARTIFACTS \
  --environment type=LINUX_CONTAINER,image=aws/codebuild/standard:7.0,computeType=BUILD_GENERAL1_SMALL \
  --service-role arn:aws:iam::000000000000:role/codebuild-role

# List projects
aws --endpoint-url http://localhost:4566 codebuild list-projects

# List curated images
aws --endpoint-url http://localhost:4566 codebuild list-curated-environment-images
```

## Phase 2 (not yet implemented)

`StartBuild`, `BatchGetBuilds`, `StopBuild`, `ListBuilds`, `ListBuildsForProject`, `RetryBuild`. Builds will execute buildspecs in real Docker containers, stream logs to CloudWatch, and upload artifacts to S3.
