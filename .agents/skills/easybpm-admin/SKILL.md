---
name: easybpm-admin
description: Build, change, or review the EasyBPM administration console in easy-bpm-admin, including dashboards, process operations, incidents, task reassignment, security, secrets, maintenance, and code-task audits. Do not use for end-user Task Portal flows.
---

# EasyBPM Admin

Work in `easy-bpm-admin/`, a React 19, TypeScript, Vite operations console.

## Preserve these contracts

- Centralize backend calls and authentication handling in `services/adminService.ts`; keep request/response shapes aligned with `types.ts` and backend DTOs.
- Treat stop, delete, move-node, retry, maintenance, security, secret, and reassignment actions as privileged and potentially destructive. Require clear confirmation where the existing UX does, show backend failures, and refresh only the affected state.
- Enforce visibility and permissions in the backend; UI gating is helpful but is never the security boundary.
- Preserve pagination, filtering, loading, empty, partial-data, and error states. Avoid assuming every instance has variables, hierarchy, incidents, or complete BPMN diagram metadata.
- Never expose secret values, credentials, raw tokens, or unnecessarily sensitive payloads in the UI, logs, or test fixtures.

## Workflow

Trace each screen through component state, `adminService`, controller/DTO, and authorization rules. For operational actions, define the success transition and the safe recovery behavior for 400, 401, 403, 404, 409, and 5xx responses as relevant.

Reuse existing components and design language. Keep tables usable with large datasets and ensure critical status is conveyed by text as well as color.

## Validation

From `easy-bpm-admin/`, run `npm run build`. Run targeted tests when a configured runner is available. For a changed operation, manually or automatically cover success, forbidden, stale/not-found, conflict, and backend-unavailable behavior as applicable.
