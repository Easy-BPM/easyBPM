---
title: Process JSON Reference
---

# Process JSON Reference

Easy BPM process definitions are JSON objects with `processId`, `nodes`, and `flows`.

## Top-level fields

| Field | Required | Description |
| --- | --- | --- |
| `processId` | Yes | Stable process identifier. |
| `key` | No | Version lookup key. Defaults to `processId`. |
| `processName` | No | Display name. |
| `name` | No | Alternate display name. |
| `description` | No | Process description. |
| `variables` | No | Initial process variables. |
| `nodes` | Yes | Array of process nodes. |
| `flows` | Yes | Array of directed edges. |

## Flow fields

| Field | Description |
| --- | --- |
| `source` | Source node ID. |
| `target` | Target node ID. |
| `condition` | Optional condition for gateway routing. |

Legacy `from` and `to` names are supported in some runtime paths, but new definitions should use `source` and `target`.

## Variables

```json
{
  "variables": [
    { "name": "amount", "initialValue": 0 },
    { "name": "approved", "initialValue": false },
    { "name": "requester", "initialValue": { "id": 42 } }
  ]
}
```

## Node naming

Use node IDs that are stable, lowercase, and meaningful:

```json
{ "id": "manager-review", "name": "Manager Review", "type": "HumanTask" }
```

Avoid changing node IDs after a process is in use because operators, audits, variables, and message correlations may refer to them.
