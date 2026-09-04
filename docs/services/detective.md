# Amazon Detective

Floci implements the REST JSON Detective organization and behavior-graph operations used by local security-governance workflows.

## Supported behavior

Enabling an organization administrator makes the organization behavior graph available in the delegated account. Member creation returns `ACCEPTED_BUT_DISABLED`, and `StartMonitoringMember` moves that member to `ENABLED`, which is the state transition consumed by Cloud Launchpad and the AWS SDK.

The supported surface includes administrator management, graph listing, organization configuration, member creation/listing, and member monitoring state. All eight operations use the POST JSON wire methods defined by the AWS service model.

Invalid graph/account/member data returns `ValidationException` or `ResourceNotFoundException`; duplicate or incompatible member transitions return `ConflictException`; locally enforced member limits return `ServiceQuotaExceededException`. AWS provider-side internal and rate-limit errors are not injected artificially.

See the [Amazon Detective API Reference](https://docs.aws.amazon.com/detective/latest/APIReference/Welcome.html).
