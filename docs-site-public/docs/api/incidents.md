---
title: Incidents API
---

# Incidents API

Use the Incidents API to review, acknowledge, retry, resolve, and reopen operational incidents.

All endpoints require `ACCESS_BPM_ADMIN`.

## Operations

| Method | Path | Summary |
| --- | --- | --- |
| `GET` | `/incidents` | List incidents with optional filters. |
| `GET` | `/incidents/summary` | Get dashboard incident counts. |
| `GET` | `/incidents/{id}` | Get one incident. |
| `GET` | `/incidents/{id}/events` | Get incident timeline events. |
| `GET` | `/incidents/process-instances/{processInstanceId}` | Get incidents for one process instance. |
| `POST` | `/incidents/{id}/acknowledge` | Acknowledge an incident. |
| `POST` | `/incidents/{id}/resolve` | Resolve an incident. |
| `POST` | `/incidents/{id}/reopen` | Reopen an incident. |
| `POST` | `/incidents/{id}/retry` | Retry a recoverable worker/API incident. |

## GET /incidents

List incidents.

Query parameters:

| Name | Type | Description |
| --- | --- | --- |
| `status` | string | Optional. `OPEN`, `ACKNOWLEDGED`, or `RESOLVED`. |
| `source` | string | Optional. `PROCESS_ENGINE`, `WORKER`, `CODE_TASK`, `AI_TASK`, or `MESSAGE`. |
| `processInstanceId` | integer | Optional process instance filter. |
| `page`, `size`, `sort` | pageable | Standard Spring pageable parameters. |

Example:

```bash
curl -X GET "http://localhost:8080/incidents?status=OPEN&source=WORKER&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

## GET /incidents/summary

Returns dashboard counts.

Example response:

```json
{
  "openIncidents": 3,
  "criticalIncidents": 1,
  "acknowledgedIncidents": 2,
  "incidentsCreatedToday": 4
}
```

## GET /incidents/\{id\}

Returns a single incident.

Example response:

```json
{
  "id": 25,
  "processInstanceId": 456,
  "nodeId": "sync-crm",
  "status": "OPEN",
  "severity": "HIGH",
  "source": "WORKER",
  "message": "API task 'sync-crm' timed out after 2 minutes without completion",
  "technicalDetails": "Process instance 456 failed at node 'sync-crm'",
  "externalReferenceId": "worker_request:91",
  "occurrenceCount": 1,
  "lastOccurredAt": "2026-06-23T10:15:00",
  "createdAt": "2026-06-23T10:15:00",
  "updatedAt": "2026-06-23T10:15:00",
  "acknowledgedAt": null,
  "acknowledgedBy": null,
  "resolvedAt": null,
  "resolvedBy": null,
  "resolutionNote": null,
  "resolutionAction": null
}
```

## GET /incidents/\{id\}/events

Returns the incident lifecycle timeline.

Example response:

```json
[
  {
    "id": 100,
    "incidentId": 25,
    "eventType": "CREATED",
    "message": "Incident created from WORKER.",
    "actor": null,
    "createdAt": "2026-06-23T10:15:00"
  }
]
```

## POST /incidents/\{id\}/acknowledge

Mark an open incident as acknowledged.

Request:

```json
{
  "acknowledgedBy": "admin"
}
```

## POST /incidents/\{id\}/resolve

Resolve an incident.

Request:

```json
{
  "resolvedBy": "admin",
  "resolutionNote": "CRM endpoint recovered and retry completed.",
  "resolutionAction": "RETRIED_SUCCESSFULLY"
}
```

## POST /incidents/\{id\}/reopen

Move a resolved incident back to `OPEN`.

```bash
curl -X POST "http://localhost:8080/incidents/25/reopen" \
  -H "Authorization: Bearer $TOKEN"
```

## POST /incidents/\{id\}/retry

Retry is supported for `WORKER` incidents. The backend restores the instance to the failed node, resets the related worker request, and republishes the service task request.

Request:

```json
{
  "requestedBy": "admin"
}
```

If the incident is not retryable, the API returns `400`.
