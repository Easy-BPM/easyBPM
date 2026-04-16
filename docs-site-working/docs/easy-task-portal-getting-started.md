# Easy BPM Task Portal — Getting Started

This guide walks you through running the Task Portal locally, connecting it to a live Easy BPM backend, and completing your first task end-to-end.

---

## Prerequisites

| Requirement | Version |
|---|---|
| Node.js | ≥ 18 |
| npm | ≥ 9 |
| Easy BPM backend | running on port `8085` |
| PostgreSQL | running (backend dependency) |

---

## Installation

```bash
cd easy-bpm-task-portal
npm install
```

---

## Starting the Development Server

```bash
npm run dev
```

The portal starts at **http://localhost:5174** by default (Vite auto-selects the next available port if 5173 is taken by another app).

### Custom API URL

If your backend runs on a different host or port, set the environment variable before starting:

```bash
# Windows PowerShell
$env:VITE_API_BASE_URL = "http://my-server:8085"
npm run dev

# Linux / macOS
VITE_API_BASE_URL=http://my-server:8085 npm run dev
```

Or create a `.env.local` file in `easy-bpm-task-portal/`:

```env
VITE_API_BASE_URL=http://localhost:8085
```

---

## Production Build

```bash
npm run build
```

Output is placed in `easy-bpm-task-portal/dist/`. Serve it with any static file server:

```bash
npx serve dist
```

---

## Your First Task — End-to-End Walkthrough

### 1. Design a process with a User Task

Open the **Easy BPMN Modeler** (`npm run dev` in `easybpmn-modeler/`, port 3000) and place a **User Task** node. Optionally attach a form key in the Properties Panel.

### 2. Deploy the process

Click **Deploy Process** in the modeler toolbar. The process definition is posted to `POST /processes`.

### 3. Start a process instance

Use the Easy BPM Admin or call the API directly:

```bash
curl -X POST http://localhost:8085/processes/my-process-key/start \
  -H "Content-Type: application/json" \
  -d '{"variables": {"applicant": "Alice"}}'
```

### 4. Open the Task Portal

Navigate to **http://localhost:5174**. The sidebar shows the pending User Task.

### 5. Complete the task

Click the task. If the task has a `formKey`:
- The form fields are rendered — fill them in and click **Complete Task**.

If the task has no form:
- Existing task variables are shown as editable rows.
- Add new variables with **+ Add Variable** if needed.
- Click **Complete Task**.

### 6. Verify global variable sync

After completion the process advances. All submitted values are now available as process-global variables. Verify in the Easy BPM Admin or via the API:

```bash
curl http://localhost:8085/instances/{instanceId}/variables
```

---

## Connecting a Form to a Task

1. In the **Easy BPMN Modeler**, open the Form Modeler tab and design your form.
2. Set the **Form Key (ID)** to something memorable, e.g. `approval_form`.
3. Click **Deploy to API** — the form schema is posted to `POST /forms`.
4. In the Process Modeler, select the User Task node and set the **Attached Form** field to `approval_form`.
5. Deploy the process.

When the task is opened in the Task Portal, the portal calls `GET /forms/approval_form` and renders the fields automatically.

---

## CORS

The backend must allow requests from the Task Portal origin. Add `http://localhost:5174` (or whichever port you use) to the allowed origins in `application.properties` or the Spring CORS configuration.

> See [Architecture docs](architecture.md) for CORS configuration details.

---

## Folder Structure

```
easy-bpm-task-portal/
├── App.tsx              # Main app — task list, form/variable rendering, completion
├── types.ts             # Shared TypeScript interfaces (Task, Form, FormField, …)
├── services/
│   └── bpmService.ts    # All REST calls (getTasks, getForm, completeTask, …)
├── components/
│   ├── Sidebar.tsx      # Task inbox list
│   └── DynamicForm.tsx  # Form field renderer
├── index.html
├── vite.config.ts
└── tsconfig.json
```

---

## Available NPM Scripts

| Script | Purpose |
|---|---|
| `npm run dev` | Start Vite dev server with HMR |
| `npm run build` | Production build to `dist/` |
| `npm run preview` | Preview the production build locally |
| `npm run lint` | TypeScript type-check (`tsc --noEmit`) |
