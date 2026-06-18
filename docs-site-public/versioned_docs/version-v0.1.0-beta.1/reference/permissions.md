---
title: Permissions
---

# Permissions

Easy BPM uses permission codes assigned directly to users or through groups.

| Permission | Purpose |
| --- | --- |
| `ACCESS_BPM_ADMIN` | Access administrative process operations and admin-facing APIs. |
| `ACCESS_PROCESS_PORTAL` | Access task portal workflows, tasks, forms, processes, and documents. |
| `ACCESS_BPM_MODELER` | Access process and form modeling/deployment APIs. |
| `MANAGE_USERS` | Create, update, delete, and reset users. |
| `MANAGE_GROUPS` | Create, update, delete, and manage groups. |
| `MANAGE_PERMISSIONS` | Permission administration where enabled. |

## Suggested customer roles

| Role | Permissions |
| --- | --- |
| Modeler | `ACCESS_BPM_MODELER` |
| Portal user | `ACCESS_PROCESS_PORTAL` |
| Operator | `ACCESS_BPM_ADMIN`, `ACCESS_PROCESS_PORTAL` |
| Security admin | `MANAGE_USERS`, `MANAGE_GROUPS`, `MANAGE_PERMISSIONS` |
| Platform admin | All permissions |
