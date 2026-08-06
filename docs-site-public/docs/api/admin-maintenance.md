---
title: Admin Maintenance API
---

# Admin Maintenance API

Use the Admin Maintenance API for customer retention operations. These endpoints perform hard deletes and require `ACCESS_BPM_ADMIN`.

Always run a dry run first.

## Operations

| Method | Path | Summary |
| --- | --- | --- |
| `POST` | `/admin/maintenance/purge-completed-instances` | Preview or execute purge of completed instances. |
| `DELETE` | `/admin/maintenance/process-definitions/{id}` | Preview or delete a process definition and related runtime data. |

## POST /admin/maintenance/purge-completed-instances

Deletes completed process instances older than a cutoff date. You can optionally filter by process definition id or process key.

Request:

```json
{
  "completedBefore": "2026-06-01T00:00:00",
  "processDefinitionId": 10,
  "processKey": null,
  "dryRun": true
}
```

Fields:

| Field | Description |
| --- | --- |
| `completedBefore` | Required. Completed instances with `updatedAt` before this value are candidates. |
| `processDefinitionId` | Optional deployed process definition version id. |
| `processKey` | Optional process key filter. |
| `dryRun` | `true` previews the cleanup. `false` executes deletion. |

Example preview:

```bash
curl -X POST "http://localhost:8080/admin/maintenance/purge-completed-instances" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "completedBefore": "2026-06-01T00:00:00",
    "processDefinitionId": 10,
    "dryRun": true
  }'
```

## DELETE /admin/maintenance/process-definitions/\{id\}

Deletes a process definition and all related runtime data. Use `dryRun=true` to preview.

Preview:

```bash
curl -X DELETE "http://localhost:8080/admin/maintenance/process-definitions/10?dryRun=true" \
  -H "Authorization: Bearer $TOKEN"
```

Execute:

```bash
curl -X DELETE "http://localhost:8080/admin/maintenance/process-definitions/10?dryRun=false" \
  -H "Authorization: Bearer $TOKEN"
```

## Cleanup summary

Both maintenance operations return a cleanup summary.

```json
{
  "dryRun": true,
  "processDefinitionsDeleted": 0,
  "processInstancesDeleted": 12,
  "tasksDeleted": 31,
  "processVariablesDeleted": 48,
  "taskVariablesDeleted": 74,
  "documentsDeleted": 6,
  "messageSubscriptionsDeleted": 0,
  "workerRequestsDeleted": 5,
  "codeTaskExecutionsDeleted": 3,
  "incidentsDeleted": 2,
  "incidentEventsDeleted": 4,
  "timelineEventsDeleted": 102,
  "callActivityMappingsDeleted": 1,
  "candidateInstanceIds": [101, 102, 103]
}
```

## Data removed

Maintenance cleanup removes:

- process instances
- tasks
- task variables
- process variables
- documents
- message subscriptions
- worker requests
- code-task execution audits
- incidents
- incident events
- process timeline events
- call-activity mappings

When deleting a process definition, the selected definition record is also deleted.
