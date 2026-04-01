# CloudFormation

**Protocol:** Query (XML) — `POST http://localhost:4566/` with `Action=` parameter
**Endpoint:** `POST http://localhost:4566/`

## Supported Actions

| Action | Description |
|---|---|
| `CreateStack` | Deploy a CloudFormation template |
| `UpdateStack` | Update an existing stack |
| `DeleteStack` | Delete a stack and its resources |
| `DescribeStacks` | Get stack status and outputs |
| `ListStacks` | List stacks by status |
| `DescribeStackEvents` | Get stack creation/update event history |
| `DescribeStackResources` | Get all resources in a stack |
| `DescribeStackResource` | Get a specific stack resource |
| `ListStackResources` | List resource summaries |
| `GetTemplate` | Retrieve the template body |
| `ValidateTemplate` | Validate a template without deploying |
| `CreateChangeSet` | Create a change set |
| `DescribeChangeSet` | Get change set details |
| `ExecuteChangeSet` | Apply a change set |
| `ListChangeSets` | List change sets for a stack |
| `DeleteChangeSet` | Delete a change set |
| `SetStackPolicy` | Set a stack policy |
| `GetStackPolicy` | Retrieve the current stack policy |
| `ListStackSets` | List StackSets |
| `DescribeStackSet` | Get StackSet details |
| `CreateStackSet` | Create a new StackSet |

## Examples

```bash
export AWS_ENDPOINT=http://localhost:4566

# Validate a template
aws cloudformation validate-template \
  --template-body file://template.yml \
  --endpoint-url $AWS_ENDPOINT

# Deploy a stack
aws cloudformation create-stack \
  --stack-name my-stack \
  --template-body file://template.yml \
  --parameters ParameterKey=Env,ParameterValue=dev \
  --endpoint-url $AWS_ENDPOINT

# Check status
aws cloudformation describe-stacks \
  --stack-name my-stack \
  --endpoint-url $AWS_ENDPOINT

# Watch events
aws cloudformation describe-stack-events \
  --stack-name my-stack \
  --endpoint-url $AWS_ENDPOINT

# Update
aws cloudformation update-stack \
  --stack-name my-stack \
  --template-body file://template.yml \
  --endpoint-url $AWS_ENDPOINT

# Delete
aws cloudformation delete-stack \
  --stack-name my-stack \
  --endpoint-url $AWS_ENDPOINT

# Create a change set
aws cloudformation create-change-set \
  --stack-name my-stack \
  --change-set-name my-change-set \
  --template-body file://template.yml \
  --endpoint-url $AWS_ENDPOINT

# List change sets
aws cloudformation list-change-sets \
  --stack-name my-stack \
  --endpoint-url $AWS_ENDPOINT

# Describe a change set
aws cloudformation describe-change-set \
  --stack-name my-stack \
  --change-set-name my-change-set \
  --endpoint-url $AWS_ENDPOINT

# Delete a change set
aws cloudformation delete-change-set \
  --stack-name my-stack \
  --change-set-name my-change-set \
  --endpoint-url $AWS_ENDPOINT
```
