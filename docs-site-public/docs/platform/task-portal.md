---
title: Task Portal
---

# Task Portal

The Task Portal is where business users and developer testers start processes, view assigned work, claim group tasks, complete forms, upload documents, and preview PDFs.

![Easy BPM Task Portal](/img/screenshots/task-portal-home.png)

## Main actions

| Action | Description |
| --- | --- |
| Start process | Starts the latest deployed version of a process definition. |
| View tasks | Lists tasks visible to the current user based on assignee and group membership. |
| Claim task | Assigns a shared candidate-group task to the current user. |
| Complete task | Submits variables and continues process execution. |
| Upload documents | Stores files against the task, process instance, and form field. |
| Preview documents | Opens supported documents, especially PDFs, without forcing a download. |

## Permissions

Users need `ACCESS_PROCESS_PORTAL` to use process and task endpoints through the portal.

## Developer notes

The portal calls the same public APIs documented in this site:

| Portal feature | API |
| --- | --- |
| Login | `POST /auth/login` |
| Process list | `GET /processes` |
| Start process | `POST /processes/{processId}/start` |
| Task list | `GET /tasks/search` |
| Task details | `GET /tasks/{id}` |
| Claim task | `POST /tasks/{id}/claim` |
| Complete task | `POST /tasks/{id}/complete` |
| Document upload | `POST /api/documents` |
