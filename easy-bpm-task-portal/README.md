# Easy BPM Task Portal

React/Vite workspace for operators who start Easy BPM processes and complete human tasks.

## What It Does

- Signs users in through the Easy BPM backend.
- Shows assigned, shared, and completed tasks.
- Starts deployed process definitions.
- Opens task details and automatically claims unassigned tasks for the current user.
- Renders deployed form schemas for human tasks.
- Falls back to editable task variables when no form is attached.
- Saves task drafts and completes tasks with output variables.
- Supports document upload, download, and inline PDF preview fields.
- Lets users unclaim tasks back to the shared pool.

## Requirements

- Node.js
- npm
- Easy BPM backend, usually running at `http://localhost:8080`

## Configuration

The app uses `http://localhost:8080` by default.

To point at a different backend, set:

```powershell
$env:EASY_BPM_TASK_PORTAL_API_BASE_URL = "http://localhost:8080"
```

The authentication session is stored in browser `localStorage` under `easybpm_portal_auth`.

## Run Locally

```powershell
npm install
npm run dev
```

Open the Vite URL printed in the terminal.

## Build

```powershell
npm run build
```

The production output is written to `dist`.

## Preview A Production Build

```powershell
npm run preview
```

## Backend APIs Used

- `POST /auth/login`
- `GET /auth/me`
- `GET /processes`
- `POST /processes/{processKey}/start`
- `GET /tasks/search`
- `GET /tasks/{id}`
- `POST /tasks/{id}/claim`
- `POST /tasks/{id}/unclaim`
- `POST /tasks/{id}/draft`
- `POST /tasks/{id}/complete`
- `GET /forms/{id}`
- `/api/documents` endpoints for upload, metadata, download, preview, delete, and task document lookup
