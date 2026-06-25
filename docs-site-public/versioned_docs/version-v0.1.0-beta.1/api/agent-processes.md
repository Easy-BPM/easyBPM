---
title: Agent Processes API
---

# Agent Processes API

Use the Agent Processes API to deploy reusable Agent Process definitions and retrieve the latest or versioned definitions by key.

## Operations

| Method | Path | Summary |
| --- | --- | --- |
| `GET` | `/agent-processes` | Get latest agent process definitions |
| `POST` | `/agent-processes` | Deploy an agent process definition |
| `GET` | `/agent-processes/{key}` | Get latest agent process definition by key |
| `GET` | `/agent-processes/{key}/versions` | Get all versions for an agent process key |

## POST /agent-processes

Deploys a new Agent Process definition version.

Validation rules:

| Field | Requirement |
| --- | --- |
| Root body | Must be a JSON object. |
| `resourceType` | Optional, but when present it must be `AgentProcess`. |
| `goal` | Required and must be a non-empty string. |
| `steps` | Optional, but when present it must be an array. |
| `availableTools` | Optional, but when present it must be an array. |
| `provider` | Optional, but when present it must be an object with non-empty `providerId` and `modelName`. |

Key resolution rules:

1. The backend uses `processKey` when provided.
2. Otherwise it falls back to `key`.
3. Otherwise it slugifies `processName`.

### Example request

```bash
curl -X POST "http://localhost:8080/agent-processes" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "resourceType": "AgentProcess",
    "processKey": "customer-support-resolution",
    "processName": "Customer Support Resolution",
    "goal": "Resolve customer complaints with an auditable AI-assisted decision.",
    "description": "Reusable agent definition for complaint intake and resolution planning.",
    "availableTools": ["CRM", "Knowledge Base", "Refund Approval BPMN"],
    "participants": ["Planner Agent", "Supervisor Agent"],
    "provider": {
      "providerId": "gemini",
      "modelName": "gemini-3.5-flash",
      "credentialRef": "$GEMINI_API_KEY"
    },
    "steps": [
      {
        "id": "collect_context",
        "title": "Collect case context",
        "status": "ready"
      }
    ]
  }'
```

### Example response

Status: `200 OK`

```json
{
  "id": 7,
  "key": "customer-support-resolution",
  "processName": "Customer Support Resolution",
  "description": "Reusable agent definition for complaint intake and resolution planning.",
  "definitionJson": "{\"resourceType\":\"AgentProcess\",\"processKey\":\"customer-support-resolution\",\"goal\":\"Resolve customer complaints with an auditable AI-assisted decision.\"}",
  "version": 3,
  "createdAt": "2026-06-25T09:14:22.481"
}
```

Response fields:

| Field | Meaning |
| --- | --- |
| `id` | Numeric database ID for this stored definition version. |
| `key` | Stable agent process key used by BPM `AgentProcessCall` nodes. |
| `processName` | Display name captured at deployment time. |
| `description` | Stored summary/description captured at deployment time. |
| `definitionJson` | Original deployed definition serialized as JSON text. |
| `version` | Incrementing version for the same key. |
| `createdAt` | Backend timestamp when this definition version was stored. |

## GET /agent-processes

Returns the latest stored version for each agent process key.

### Example request

```bash
curl -X GET "http://localhost:8080/agent-processes" \
  -H "Authorization: Bearer $TOKEN"
```

## `GET /agent-processes/{key}`

Returns the latest stored version for one agent process key, or `404` when the key does not exist.

### Example request

```bash
curl -X GET "http://localhost:8080/agent-processes/customer-support-resolution" \
  -H "Authorization: Bearer $TOKEN"
```

## `GET /agent-processes/{key}/versions`

Returns all stored versions for one agent process key, ordered by backend repository rules.

### Example request

```bash
curl -X GET "http://localhost:8080/agent-processes/customer-support-resolution/versions" \
  -H "Authorization: Bearer $TOKEN"
```
