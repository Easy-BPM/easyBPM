---
title: Admin Console
---

# Admin Console

The Admin Console is the operational workspace for process monitoring, instance intervention, user and group management, and Code Task audits.

![Easy BPM Admin Console](/img/screenshots/admin-home.png)

## Capabilities

| Area | What administrators can do |
| --- | --- |
| Process instances | View status, current nodes, history, and related variables. |
| Variables | Inspect and update process variables for controlled support actions. |
| Token movement | Move an active instance from one node to another with an operator reason. |
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
