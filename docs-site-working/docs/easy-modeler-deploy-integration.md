---
sidebar_position: 13
---

# Easy BPMN Modeler: Deploy & API Integration

## Overview

This document describes how the integrated modeler sends process and form definitions to Easy BPM backend APIs.

## Base URL Resolution

The modeler uses the following base URL strategy:

1. `VITE_API_BASE_URL` (if provided)
2. fallback: `http://localhost:8085`

This behavior is implemented in:

- `easybpmn-modeler/services/processService.ts`
- `easybpmn-modeler/components/FormModeler.tsx`

## Process Deployment

### Endpoint

- `POST /processes`

### Trigger

- Toolbar button: **Deploy Process**

### Source

- Process payload builder in `App.tsx` (`buildExportObject`)
- API call in `services/processService.ts` (`deployProcess`)

### Request Shape (example)

```json
{
  "processId": "expense_approval_v1",
  "metadata": {
    "exportedAt": "2026-04-15T16:00:00.000Z",
    "version": "1.0"
  },
  "variables": [
    {
      "name": "amount",
      "type": "number",
      "initialValue": "0"
    }
  ],
  "nodes": [
    {
      "id": "start_1",
      "name": "Start",
      "type": "StartEvent",
      "position": { "x": 120, "y": 180 },
      "next": ["manager_review"]
    },
    {
      "id": "manager_review",
      "name": "Manager Review",
      "type": "HumanTask",
      "position": { "x": 320, "y": 180 },
      "next": ["end_1"],
      "config": {
        "formId": "expense_form_v1",
        "assignee": "manager",
        "inputs": [],
        "outputs": []
      }
    },
    {
      "id": "end_1",
      "name": "End",
      "type": "EndEvent",
      "position": { "x": 560, "y": 180 },
      "next": []
    }
  ],
  "flows": [
    { "from": "start_1", "to": "manager_review", "condition": null },
    { "from": "manager_review", "to": "end_1", "condition": null }
  ]
}
```

### Response Handling

- `2xx`: success toast (`Process deployed successfully.`)
- non-`2xx`: throws with status and response body

## API Task Authentication (Reference-Based)

### Design Goal

API Task credentials are not stored in process JSON as raw secrets.

- Modeler remains design-time only
- Process definition carries only an auth reference (`auth.ref`)
- Worker resolves real secrets at runtime from environment variables

This keeps the modeler independent from database-managed secret stores.

### APITask Payload Contract

For `type: APITask`, modeler exports `properties` with optional `auth`:

```json
{
  "id": "call_partner_api",
  "type": "APITask",
  "name": "Call Partner API",
  "properties": {
    "url": "https://api.partner.com/orders",
    "method": "POST",
    "body": {
      "orderId": "${orderId}"
    },
    "auth": {
      "type": "bearer",
      "ref": "PARTNER_API_TOKEN"
    }
  }
}
```

Supported `auth.type` values:

- `bearer`
- `basic`
- `apikey`

APITask without auth should omit the `auth` object.

### API Key Options

When `auth.type = apikey`, two optional fields are supported:

- `in`: `header` (default) or `query`
- `key`: header/query parameter name (default: `X-API-Key`)

Example:

```json
{
  "auth": {
    "type": "apikey",
    "ref": "PARTNER_API_KEY",
    "in": "header",
    "key": "X-Partner-Key"
  }
}
```

### Runtime Resolution in Worker

Worker resolves secrets from environment variables using this convention:

- `bearer`: reads env var named exactly as `auth.ref`
- `basic`: reads `${auth.ref}_USERNAME` and `${auth.ref}_PASSWORD`
- `apikey`: reads env var named exactly as `auth.ref`

If a required env var is missing, the worker fails the call and applies retry/DLQ policies.

### Environment Examples

```bash
# Bearer
PARTNER_API_TOKEN=eyJhbGciOi...

# Basic
ERP_CREDENTIALS_USERNAME=integration-user
ERP_CREDENTIALS_PASSWORD=super-secret-password

# API Key
PARTNER_API_KEY=pk_live_xxxxx
```

### Compatibility Note

Backend currently accepts both:

- `node.properties` (current format)
- `node.service` (legacy format)

For new definitions, always prefer `properties`.

## Form Deployment

### Endpoint

- `POST /forms`

### Trigger

- Form Modeler button: **Deploy to API**

### Request Shape (example)

```json
{
  "key": "expenseRequestForm",
  "name": "expense-request-form",
  "schema": {
    "title": "Expense Request Form",
    "type": "object",
    "properties": {
      "employeeId": { "title": "Employee ID", "type": "string", "readOnly": false },
      "amount": { "title": "Amount", "type": "number", "readOnly": false },
      "comment": { "title": "Comment", "type": "string", "format": "textarea", "readOnly": false }
    },
    "required": ["employeeId", "amount"]
  }
}
```

The form key is the stable identifier used for version lineage and for attaching a form to a user task. It must be unique within the form lineage and should not contain spaces.

### Response Handling

- `2xx`: success toast for deployed form
- non-`2xx`: error toast with backend message when available

## Validation and Guardrails

### Process deploy button disabled when

- process ID is empty
- duplicate node IDs exist
- duplicate global variables exist
- duplicate task variable names exist

### Runtime error handling

- process deploy: fetch errors surfaced with HTTP status/body
- form deploy: connection/deploy errors shown via toast

## Operational Checklist

1. Backend running and reachable on configured base URL
2. CORS allows modeler origin (`http://localhost:3000`)
3. `POST /processes` and `POST /forms` available
4. Payload validation passes in modeler UI

## Related Docs

- [Easy BPMN Modeler: Overview](./easy-modeler-overview.md)
- [Easy BPMN Modeler: Getting Started](./easy-modeler-getting-started.md)
- [API Controllers](./api-controllers.md)
