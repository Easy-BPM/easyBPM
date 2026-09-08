# Easy BPM Modeler

React/Vite workspace for designing and deploying Easy BPM processes and forms. It also ships as an independent desktop modeler, similar in spirit to Camunda Desktop Modeler.

## What It Does

- Signs modelers in through the Easy BPM backend.
- Creates process models with start/end events, human tasks, service tasks, API tasks, code tasks, AI tasks, call activities, exclusive gateways, parallel gateways, pools, and boundary events.
- Edits node properties, process variables, sequence flows, conditions, and task configuration.
- Validates process structure before export and deployment.
- Imports and exports BPMN 2.0 XML (`.bpmn`/`.xml`) for process definitions.
- Shows the generated BPMN XML beside the visual modeler.
- Deploys process definitions to the backend.
- Builds form definitions with tabs, fields, validation, preview mode, import/export, and deployment.
- Maintains an in-session form library for attaching forms to human tasks.
- Includes code-task modeling helpers for JAR uploads, class selection, method selection, and variable mappings.
- Runs as a desktop app with local file open/save. The desktop app does not access the database directly.
- Can deploy from desktop when a backend URL, username, and password are configured in the app.

## Requirements

- Node.js
- npm
- Easy BPM backend, usually running at `http://localhost:8080`
- For desktop packaging: Electron dependencies installed with npm.

## Configuration

The app uses `http://localhost:8080` by default.

To point at a different backend, set:

```powershell
$env:EASY_BPM_MODELER_API_BASE_URL = "http://localhost:8080"
```

The authentication session is stored in browser `localStorage` under `easybpm_modeler_auth`.

In the desktop app, the backend URL is optional and can be supplied in the connection dialog. The saved URL is stored locally under `easybpm_modeler_backend_url`. The desktop app talks only to the Easy BPM backend HTTP API; it never connects to the database.

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

## Desktop App

Run a desktop production build:

```powershell
npm run build:desktop
npm run desktop:start
```

Package installers:

```powershell
npm run desktop:build
```

To preconfigure the backend address when launching the desktop app:

```powershell
$env:EASY_BPM_MODELER_API_BASE_URL = "http://localhost:8080"
npm run desktop:start
```

Users can also set the backend URL, username, and password from the desktop connection dialog before deploying.

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

The desktop app uses these APIs only after the user configures a backend connection. It does not use JDBC or direct database credentials.
