---
title: API Tasks
---

# API Tasks

API Tasks call external HTTP services through the Easy BPM worker. They are useful for CRM calls, ERP updates, payment checks, webhook-style integrations, and private service orchestration.

## Define an API task

```json
{
  "id": "create-ticket",
  "name": "Create Support Ticket",
  "type": "APITask",
  "properties": {
    "method": "POST",
    "url": "https://api.example.com/tickets",
    "headers": {
      "Content-Type": "application/json"
    },
    "body": {
      "customerId": "${customerId}",
      "summary": "${requestSummary}"
    },
    "auth": {
      "type": "bearer",
      "ref": "SUPPORT_API_TOKEN"
    }
  }
}
```

## Authentication options

| Type | Required fields | Behavior |
| --- | --- | --- |
| `bearer` | `ref` | Sends bearer credentials from the referenced secret or environment variable. |
| `basic` | `ref` | Sends basic credentials from the referenced secret or environment variable. |
| `apikey` | `ref`, optional `in`, optional `key` | Sends an API key in a header or query string. |

For API key auth:

```json
"auth": {
  "type": "apikey",
  "ref": "PARTNER_API_KEY",
  "in": "header",
  "key": "X-API-Key"
}
```

## Variable substitution

String values can reference process variables with `${variableName}`. Resolve sensitive values through your secret management approach rather than storing secrets in process variables.

## Worker execution

When an API task is reached, the backend publishes a worker request to RabbitMQ. The worker executes the call and returns the result so the process can continue.

Keep API tasks idempotent when possible. If a worker retries or an operator restarts a process path, the external system should be able to handle duplicate requests safely.
