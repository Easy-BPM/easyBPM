# Easy BPM Task Portal — Overview

![Easy BPM Task Portal home](/img/screenshots/task-portal-home.png)

The **Easy BPM Task Portal** is the end-user task inbox for process participants. While the [Easy BPM Admin](easy-admin-overview.md) targets operators who manage running instances, the Task Portal targets **human performers** — the people who receive, fill in, and complete tasks as part of a running process.

---

## Purpose

In a BPMN process, a **User Task** waits for a human to take action before the process can advance. The Task Portal surfaces those pending tasks, presents the correct UI (form or variable editor), and sends results back to the engine — which then advances the process and synchronises submitted values as global process variables.

---

## Key Features

| Feature | Description |
|---|---|
| **Task Inbox** | Lists all `PENDING` user tasks across all active process instances |
| **Form Rendering** | When a task has an attached form (`formKey`), the form fields are rendered dynamically from the JSON Schema stored in the Forms API |
| **Variable Editor Fallback** | When a task has no attached form, existing task variables are shown as an editable key/type/value list |
| **Add New Variables** | Performers can create new variables directly in the variable-editor screen; they become global on completion |
| **Typed Values** | Supports `string`, `number`, `boolean`, and `json` types — values are coerced on submission |
| **Complete Task** | Submits the form data or variable map to the backend; the engine advances the flow and persists all values as process-global variables |

---

## Architecture

![Task portal architecture](/img/architecture/platform-overview.svg)

![Task completion flow](/img/architecture/task-completion-flow.svg)

```
┌─────────────────────────────────┐       REST
│   Easy BPM Task Portal          │ ──────────────────────────►  GET  /tasks
│   React 19 + Vite               │                             GET  /tasks/{id}
│   localhost:5174 (default)      │                             GET  /forms/{key}
│                                 │ ──────────────────────────►  POST /tasks/{id}/complete
└─────────────────────────────────┘      { assignee, variables }
```

The portal is a pure SPA — it has no server of its own. It communicates with the Easy BPM backend (`http://localhost:8085` by default, overridable via `VITE_API_BASE_URL`).

---

## Variable Synchronisation

This is the most important semantic guarantee: **every variable submitted during task completion becomes a process-global variable**.

Flow:

1. Performer fills in the form (or variable editor) and clicks **Complete Task**.
2. Portal POSTs `{ assignee, variables: { key: value, ... } }` to `/tasks/{id}/complete`.
3. `TaskService` persists the task variables.
4. `TaskService` calls `syncTaskVariablesToProcess()` — all submitted variables are written (upserted) into the `process_variable` table for the current process instance.
5. Explicit output mappings defined in the process definition are applied afterwards (allowing remapping or override).
6. The process advances to the next node.

This means submitted form values are **always available** as `${variableName}` in subsequent tasks, gateway conditions, and API task body templates — no explicit output mapping required.

---

## UI Screens

### Task Inbox (Sidebar)

![Task Portal home screen](/img/screenshots/task-portal-home.png)

- Shows task name, associated process instance ID, and creation timestamp.
- Clicking a task loads the task detail panel.

### Task Detail — With Form

![Task Portal detail view](/img/screenshots/task-portal-home.png)

- Renders the form schema returned by `GET /forms/{formKey}`.
- All tabs and field types (text, number, boolean, radio, select, date, textarea) are supported.
- Required field validation is enforced in the browser before submission.

### Task Detail — Without Form

![Task Portal variable editor context](/img/screenshots/task-portal-home.png)

- Lists current task variables (name, type, value) as editable rows.
- Each row has an inline type selector and value input.
- **"+ Add Variable"** button inserts a new row.
- Rows can be deleted.
- All entries are sent as the variables payload on completion.

---

## Technology Stack

| Concern | Choice |
|---|---|
| Framework | React 19 |
| Build | Vite 6 |
| Language | TypeScript 5.8 |
| Styling | Tailwind CSS (CDN) |
| HTTP | Native `fetch` |
| Icons | Lucide React |

---

## Related

- [Easy BPM Admin](easy-admin-overview.md) — instance lifecycle operations
- [Easy BPMN Modeler](easy-modeler-overview.md) — design processes and forms
- [API Reference: Tasks](api-controllers.md)
