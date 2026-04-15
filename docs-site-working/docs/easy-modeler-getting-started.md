---
sidebar_position: 12
---

# Easy BPMN Modeler: Getting Started

## Prerequisites

- Node.js 18+
- npm 9+
- Easy BPM backend running on `http://localhost:8085`

## Install and Run

### 1. Open the modeler directory

```bash
cd easybpmn-modeler
```

### 2. Install dependencies

```bash
npm install
```

### 3. Start development server

```bash
npm run dev
```

Default local URL:

- `http://localhost:3000`

### 4. Build for production

```bash
npm run build
npm run preview
```

## Configure Backend URL

By default, the modeler targets `http://localhost:8085`.

To override, define:

```bash
VITE_API_BASE_URL=http://your-api-host:port
```

You can provide this via environment or `.env` file consumed by Vite.

## First Modeling Flow

1. Create/start a process in Process Modeler view
2. Drag nodes from palette to canvas
3. Connect nodes and configure properties
4. Add global variables and task mappings
5. Click **Deploy Process**
6. Confirm process appears in Easy BPM Admin workflow list

## First Form Flow

1. Switch to Form Modeler view
2. Create tabs and add fields
3. Configure names, types, and required/read-only settings
4. Click **Deploy to API**
5. Confirm form is available through backend forms endpoints

## Troubleshooting

### Deploy fails with connection error

- Confirm backend is up on `http://localhost:8085`
- Confirm `VITE_API_BASE_URL` is correct if overridden
- Check browser network tab for request/response details

### Deploy button disabled

Process deploy is disabled if validation fails, such as:

- duplicate node IDs
- duplicate variable names
- empty process ID

Fix validation warnings in the right-side properties panel.

### Build works but app cannot call backend

- Verify CORS for modeler origin (`http://localhost:3000`)
- Verify backend exposes `POST /processes` and `POST /forms`

## Related Docs

- [Easy BPMN Modeler: Overview](./easy-modeler-overview.md)
- [Easy BPMN Modeler: Deploy & API Integration](./easy-modeler-deploy-integration.md)
