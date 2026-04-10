# API Gateway

Floci supports both API Gateway v1 (REST APIs) and API Gateway v2 (HTTP APIs).

## API Gateway v1 (REST APIs) {#v1}

**Protocol:** REST JSON
**Endpoint:** `http://localhost:4566/restapis/...`

### Supported Operations

| Category | Operations |
|---|---|
| **APIs** | CreateRestApi, ImportRestApi, PutRestApi, GetRestApi, GetRestApis, UpdateRestApi, DeleteRestApi |
| **Resources** | CreateResource, GetResource, GetResources, UpdateResource, DeleteResource |
| **Methods** | PutMethod, GetMethod, UpdateMethod, DeleteMethod |
| **Method Responses** | PutMethodResponse, GetMethodResponse |
| **Integrations** | PutIntegration, GetIntegration, UpdateIntegration, DeleteIntegration |
| **Integration Responses** | PutIntegrationResponse, GetIntegrationResponse |
| **Deployments** | CreateDeployment, GetDeployments |
| **Stages** | CreateStage, GetStage, GetStages, UpdateStage, DeleteStage |
| **Authorizers** | CreateAuthorizer, GetAuthorizer, GetAuthorizers |
| **API Keys** | CreateApiKey, GetApiKeys |
| **Usage Plans** | CreateUsagePlan, GetUsagePlans, DeleteUsagePlan |
| **Usage Plan Keys** | CreateUsagePlanKey, GetUsagePlanKey, GetUsagePlanKeys, DeleteUsagePlanKey |
| **Request Validators** | CreateRequestValidator, GetRequestValidator, GetRequestValidators, DeleteRequestValidator |
| **Models** | CreateModel, GetModel, GetModels, DeleteModel |
| **Domain Names** | CreateDomainName, GetDomainName, GetDomainNames, DeleteDomainName |
| **Base Path Mappings** | CreateBasePathMapping, GetBasePathMapping, GetBasePathMappings, DeleteBasePathMapping |
| **Tags** | TagResource, UntagResource, GetTags (ListTagsForResource) |

### Not Implemented

These management-plane operations have no handler in v1. Calls will return `404` or an error:

- Deployment detail and lifecycle: `GetDeployment`, `UpdateDeployment`, `DeleteDeployment`
- Authorizer lifecycle: `UpdateAuthorizer`, `DeleteAuthorizer`, `TestInvokeAuthorizer`
- API key detail: `GetApiKey`, `UpdateApiKey`, `DeleteApiKey`, `ImportApiKeys`
- Usage plan detail: `GetUsagePlan`, `UpdateUsagePlan`
- Model updates and templates: `UpdateModel`, `GetModelTemplate`
- Gateway Responses (the entire family: `PutGatewayResponse`, `GetGatewayResponse`, etc.)
- Documentation parts and versions (the entire family, 10 operations)
- VPC Links (5 operations)
- Client Certificates (5 operations)
- Account: `GetAccount`, `UpdateAccount`
- `GetExport` / `ImportDocumentationParts`

The execute plane (actual proxied HTTP traffic via `/restapis/{id}/{stage}/_user_request_/…`) is implemented separately and is not counted as management-plane operations.

### Examples

```bash
export AWS_ENDPOINT_URL=http://localhost:4566

# Create a REST API
API_ID=$(aws apigateway create-rest-api \
  --name "My API" \
  --query id --output text \
  --endpoint-url $AWS_ENDPOINT_URL)

# Get the root resource
ROOT_ID=$(aws apigateway get-resources \
  --rest-api-id $API_ID \
  --query 'items[?path==`/`].id' --output text \
  --endpoint-url $AWS_ENDPOINT_URL)

# Create a resource
RESOURCE_ID=$(aws apigateway create-resource \
  --rest-api-id $API_ID \
  --parent-id $ROOT_ID \
  --path-part users \
  --query id --output text \
  --endpoint-url $AWS_ENDPOINT_URL)

# Add a GET method
aws apigateway put-method \
  --rest-api-id $API_ID \
  --resource-id $RESOURCE_ID \
  --http-method GET \
  --authorization-type NONE \
  --endpoint-url $AWS_ENDPOINT_URL

# Add a Lambda integration
aws apigateway put-integration \
  --rest-api-id $API_ID \
  --resource-id $RESOURCE_ID \
  --http-method GET \
  --type AWS_PROXY \
  --integration-http-method POST \
  --uri "arn:aws:apigateway:us-east-1:lambda:path/2015-03-31/functions/arn:aws:lambda:us-east-1:000000000000:function:my-function/invocations" \
  --endpoint-url $AWS_ENDPOINT_URL

# Deploy to a stage
aws apigateway create-deployment \
  --rest-api-id $API_ID \
  --stage-name dev \
  --endpoint-url $AWS_ENDPOINT_URL

# Call the deployed API
curl http://localhost:4566/restapis/$API_ID/dev/_user_request_/users
```

---

## API Gateway v2 (HTTP APIs) {#v2}

**Protocol:** REST JSON
**Endpoint:** `http://localhost:4566/v2/apis/...`

### Supported Operations

| Category | Operations |
|---|---|
| **APIs** | CreateApi, GetApi, GetApis, DeleteApi |
| **Routes** | CreateRoute, GetRoute, GetRoutes, DeleteRoute |
| **Integrations** | CreateIntegration, GetIntegration, GetIntegrations |
| **Authorizers** | CreateAuthorizer, GetAuthorizer, GetAuthorizers, DeleteAuthorizer |
| **Stages** | CreateStage, GetStage, GetStages, DeleteStage |
| **Deployments** | CreateDeployment, GetDeployments |

### Examples

```bash
export AWS_ENDPOINT_URL=http://localhost:4566

# Create an HTTP API
API_ID=$(aws apigatewayv2 create-api \
  --name "My HTTP API" \
  --protocol-type HTTP \
  --query ApiId --output text \
  --endpoint-url $AWS_ENDPOINT_URL)

# Create a Lambda integration
INTEGRATION_ID=$(aws apigatewayv2 create-integration \
  --api-id $API_ID \
  --integration-type AWS_PROXY \
  --integration-uri "arn:aws:lambda:us-east-1:000000000000:function:my-function" \
  --payload-format-version 2.0 \
  --query IntegrationId --output text \
  --endpoint-url $AWS_ENDPOINT_URL)

# Create a route
aws apigatewayv2 create-route \
  --api-id $API_ID \
  --route-key "GET /users" \
  --target "integrations/$INTEGRATION_ID" \
  --endpoint-url $AWS_ENDPOINT_URL

# Deploy
aws apigatewayv2 create-stage \
  --api-id $API_ID \
  --stage-name dev \
  --auto-deploy \
  --endpoint-url $AWS_ENDPOINT_URL
```
