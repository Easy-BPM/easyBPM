---
title: Admin Console
---

# Admin Console

The Admin Console is the operational workspace for process monitoring, instance intervention, incident recovery, maintenance cleanup, user and group management, and Code Task audits.

![Easy BPM Admin Console](/img/screenshots/admin-home.png)

## Capabilities

| Area | What administrators can do |
| --- | --- |
| Process instances | View status, current nodes, history, and related variables. |
| Process timeline | Inspect timestamped runtime events for a process instance. |
| Variables | Inspect and update process variables for controlled support actions. |
| Token movement | Move an active instance from one node to another with an operator reason. |
| Incident Manager | Review, acknowledge, retry, resolve, and reopen operational incidents. |
| Purge & Archiving | Preview and execute cleanup of completed instances or delete process definitions with related data. |
| Stop/delete | Stop active instances or hard-delete runtime data when policy allows it. |
| Security | Manage users, groups, and permission assignments. |
| Code Task audits | Review execution history, status, duration, inputs, outputs, and failures. |

## Permissions

Administrators typically need:

| Permission | Enables |
| --- | --- |
| `ACCESS_BPM_ADMIN` | Admin console access and operational API calls. |
| `MANAGE_USERS` | User administration. |
| `MANAGE_GROUPS` | Group administration. |
| `MANAGE_PERMISSIONS` | Permission administration where enabled. |

## Operational guidance

Use token movement and variable edits as support tools, not as normal process design. If the same intervention is needed repeatedly, update the process model so the business path is explicit.

Use Incident Manager before manual token movement when an instance failed because of a worker/API timeout, Code Task failure, or AI Task failure. Incidents preserve the recovery history and can link directly to the affected process instance.

Use Purge & Archiving only after a customer retention window has passed. Maintenance operations are hard deletes. Always run a preview first and review the cleanup summary before executing.

For a complete operations workflow, see [Operations](./operations).
