# Easy BPM Modeler

React/Vite workspace for designing and deploying Easy BPM processes and forms.

## What It Does

- Signs modelers in through the Easy BPM backend.
- Creates process models with start/end events, human tasks, service tasks, API tasks, code tasks, AI tasks, call activities, exclusive gateways, parallel gateways, pools, and boundary events.
- Edits node properties, process variables, sequence flows, conditions, and task configuration.
- Validates process structure before export and deployment.
- Imports and exports process JSON.
- Deploys process definitions to the backend.
- Builds form definitions with tabs, fields, validation, preview mode, import/export, and deployment.
- Maintains an in-session form library for attaching forms to human tasks.
- Includes code-task modeling helpers for JAR uploads, class selection, method selection, and variable mappings.

## Requirements

- Node.js
- npm
- Easy BPM backend, usually running at `http://localhost:8080`

## Configuration

The app uses `http://localhost:8080` by default.

To point at a different backend, set:

```powershell
$env:EASY_BPM_MODELER_API_BASE_URL = "http://localhost:8080"
```

The authentication session is stored in browser `localStorage` under `easybpm_modeler_auth`.

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

## Type Check

```powershell
npm run lint
```

This runs `tsc --noEmit`.

## Preview A Production Build

```powershell
npm run preview
```

## Backend APIs Used

- `POST /auth/login`
- `GET /auth/me`
- `POST /processes`
- `POST /forms`
- Code task endpoints such as `/code-tasks/upload`, `/code-tasks/jar/{jarId}/classes`, and `/code-tasks/jar/{jarId}/classes/{className}/methods`
