# Easy BPM Admin

React/Vite workspace for operating and administering Easy BPM runtime data.

## What It Does

- Signs administrators in through the Easy BPM backend.
- Shows runtime dashboards for deployed processes and process instances.
- Searches process instances and inspects instance details, hierarchy, timeline, variables, and workflow diagrams.
- Moves, stops, and deletes process instances when intervention is needed.
- Reviews task ownership and reassigns or clears assignees.
- Tracks incidents, including acknowledge, resolve, reopen, retry, summary, and event views.
- Reviews code task execution history with metrics, filters, pagination, and detail modals.
- Runs maintenance actions for completed instances and process definitions.
- Manages users, groups, permissions, enabled state, passwords, and group membership.

## Requirements

- Node.js
- npm
- Easy BPM backend, usually running at `http://localhost:8080`

## Configuration

The app uses `http://localhost:8080` by default.

To point at a different backend, set:

```powershell
$env:EASY_BPM_ADMIN_API_BASE_URL = "http://localhost:8080"
```

The authentication session is stored in browser `localStorage` under `easybpm_admin_auth`.

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
- `GET /processes/definitions/{definitionId}`
- `/processes/instances` endpoints for search, detail, variables, timeline, hierarchy, move-node, stop, and delete
- `/tasks` and `/tasks/search` endpoints for task review and reassignment
- `/incidents` endpoints for list, summary, events, acknowledge, resolve, reopen, and retry
- `/code-tasks/executions`
- `/admin/maintenance` endpoints
- `/admin/users` and `/admin/groups` endpoints
