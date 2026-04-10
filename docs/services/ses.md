# SES

**Protocol:** Query (XML) with `Action=` parameter
**Endpoint:** `POST http://localhost:4566/`

Floci exposes the classic Amazon SES Query API used by `aws ses ...` commands and SDKs targeting SES v1.

## Supported Actions

| Action                              | Description                                               |
|-------------------------------------|-----------------------------------------------------------|
| `VerifyEmailIdentity`               | Mark an email address as verified                         |
| `VerifyEmailAddress`                | Legacy alias for email verification                       |
| `VerifyDomainIdentity`              | Mark a domain as verified and return a verification token |
| `DeleteIdentity`                    | Delete an email or domain identity                        |
| `ListIdentities`                    | List verified identities                                  |
| `GetIdentityVerificationAttributes` | Get verification status for one or more identities        |
| `SendEmail`                         | Send a structured email with text or HTML body            |
| `SendRawEmail`                      | Send a raw MIME payload                                   |
| `GetSendQuota`                      | Return local send quota counters                          |
| `GetSendStatistics`                 | Return aggregate delivery stats for sent messages         |
| `GetAccountSendingEnabled`          | Report whether sending is enabled                         |
| `ListVerifiedEmailAddresses`        | List verified email identities                            |
| `DeleteVerifiedEmailAddress`        | Delete a verified email identity                          |
| `SetIdentityNotificationTopic`      | Store SNS notification topic ARNs for an identity         |
| `GetIdentityNotificationAttributes` | Read stored notification topic settings                   |
| `GetIdentityDkimAttributes`         | Return DKIM status for identities                         |

## Configuration

```yaml
floci:
  services:
    ses:
      enabled: true
```

## Examples

```bash
export AWS_ENDPOINT_URL=http://localhost:4566

# Verify sender and recipient identities
aws ses verify-email-identity \
  --email-address sender@example.com \
  --endpoint-url $AWS_ENDPOINT_URL

aws ses verify-email-identity \
  --email-address recipient@example.com \
  --endpoint-url $AWS_ENDPOINT_URL

# Verify a domain
aws ses verify-domain-identity \
  --domain example.com \
  --endpoint-url $AWS_ENDPOINT_URL

# List all identities
aws ses list-identities \
  --endpoint-url $AWS_ENDPOINT_URL

# Send a plain-text email
aws ses send-email \
  --from sender@example.com \
  --destination ToAddresses=recipient@example.com \
  --message "Subject={Data=Hello},Body={Text={Data=Sent from Floci SES}}" \
  --endpoint-url $AWS_ENDPOINT_URL

# Send a raw MIME email
aws ses send-raw-email \
  --raw-message Data="$(printf 'Subject: Raw test\r\n\r\nHello from raw SES')" \
  --source sender@example.com \
  --destinations recipient@example.com \
  --endpoint-url $AWS_ENDPOINT_URL

# Inspect locally captured messages
curl $AWS_ENDPOINT_URL/_aws/ses
```

## Local Inspection Endpoint

For test assertions and debugging, Floci exposes a LocalStack-compatible mailbox endpoint:

- `GET /_aws/ses` lists captured messages
- `GET /_aws/ses?id=<message-id>` returns a specific captured message
- `DELETE /_aws/ses` clears the captured mailbox

Messages are stored locally by Floci and can be persisted when SES storage is backed by persistent or hybrid storage.

## Current Behavior

- Identity verification succeeds immediately; no real DNS or inbox verification flow is required.
- `SendEmail` stores the text body or the HTML body as the captured message body.
- `SetIdentityNotificationTopic` stores SNS topic ARNs and returns them via `GetIdentityNotificationAttributes`.
- Notification topics are configuration metadata only; SES delivery, bounce, or complaint events are not emitted automatically.
- SMTP submission is not implemented. For the REST JSON API see [SES v2](#v2) below.

## SES v2 (REST JSON) {#v2}

**Protocol:** REST JSON
**Endpoint:** `http://localhost:4566/v2/email/...`

Alongside the classic Query API, Floci implements a subset of the SES v2 REST JSON API used by `aws sesv2 ...` commands and SDK v2 clients that target the modern SES surface.

### Supported Operations

| Method | Path | Action |
|---|---|---|
| `POST` | `/v2/email/identities` | `CreateEmailIdentity` |
| `GET` | `/v2/email/identities` | `ListEmailIdentities` |
| `GET` | `/v2/email/identities/{emailIdentity}` | `GetEmailIdentity` |
| `DELETE` | `/v2/email/identities/{emailIdentity}` | `DeleteEmailIdentity` |
| `PUT` | `/v2/email/identities/{emailIdentity}/dkim` | `PutEmailIdentityDkimAttributes` |
| `PUT` | `/v2/email/identities/{emailIdentity}/feedback` | `PutEmailIdentityFeedbackAttributes` |
| `POST` | `/v2/email/outbound-emails` | `SendEmail` (simple / raw / templated) |
| `GET` | `/v2/email/account` | `GetAccount` |
| `PUT` | `/v2/email/account/sending` | `PutAccountSendingAttributes` |

Identity and sent-message state is shared with the v1 Query API, so messages sent through v2 appear in the same `GET /_aws/ses` inspection mailbox.
