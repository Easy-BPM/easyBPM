---
sidebar_position: 11
---

# Easy BPMN Modeler: Overview

![Easy BPMN Modeler home](/img/screenshots/modeler-home.png)

## What is Easy BPMN Modeler?

Easy BPMN Modeler is a React-based visual designer used to create and deploy process definitions directly to Easy BPM backend APIs. It is now integrated into the main `bpm` repository under `easybpmn-modeler/`.

The modeler supports end-to-end authoring flow:

- Design BPMN-like process graphs on canvas
- Configure task properties and variable mappings
- Export process definition JSON
- Deploy process definitions to backend using `POST /processes`
- Model and deploy forms using `POST /forms`

## Scope and Role

Easy BPMN Modeler is a **design-time** tool.

- It does: modeling, variable mapping, export, deploy
- It does not: instance execution monitoring (handled by Easy BPM Admin)

This keeps responsibilities clear:

- **Modeler**: build and publish definitions
- **Admin**: monitor and control running instances

## Technology Stack

- **Frontend**: React 19 + TypeScript
- **Build**: Vite
- **UI**: Tailwind CSS + Lucide icons
- **Notifications**: Sonner
- **DnD for Form Builder**: `@dnd-kit`

## Main Capabilities

| Capability | Description |
|---|---|
| Process Canvas | Drag/drop nodes, connect flows, edit node properties |
| Node Types | Start/End, User Task, Service Task, API Task, Gateways, Message and Boundary events |
| Variable Mapping | Input/output mapping across global variables and task variables |
| Form Modeling | Build JSON-schema-driven forms with tabs and fields |
| Deploy Process | Sends modeled process payload to backend `POST /processes` |
| Deploy Form | Sends generated form schema to backend `POST /forms` |

## BPM Modeler Components

The Process Modeler view is composed of five primary UI components and two supporting service/util layers.

### 1. Toolbar (`components/Toolbar.tsx`)

- Top action bar for **New**, **Import**, **Export JSON**, and **Deploy Process**.
- Provides view switching between **Process Modeler** and **Form Modeler**.
- Disables deploy/export when validation rules fail.

### 2. Palette (`components/Palette.tsx`)

- Left-hand BPMN component catalog.
- Drag source for node types: start/end events, user/service/API tasks, gateways, message events, boundary events.
- Groups node types by domain to speed up authoring.

### 3. Canvas (`components/Canvas.tsx`)

- Central drawing surface for process graph editing.
- Handles drag/drop placement, node movement, edge connection, and multi-select.
- Renders orthogonal connectors and visual state (selected/hovered nodes).

### 4. Properties Panel (`components/PropertiesPanel.tsx`)

- Right-side context panel for node/edge/process metadata editing.
- Configures task assignments, form keys, API endpoints, message names, conditions, and variable mappings.
- Hosts global variable definitions and mapping semantics (static vs global-variable sources).

### 5. Form Modeler (`components/FormModeler.tsx`)

- Dedicated form-design workspace with tabbed field layout.
- Supports field creation/reorder/edit and JSON schema generation.
- Deploys forms to backend via `POST /forms`.

### 6. Process Deploy Service (`services/processService.ts`)

- Encapsulates backend call to `POST /processes`.
- Uses `VITE_API_BASE_URL` with fallback to `http://localhost:8085`.
- Centralized error handling for deploy responses.

### 7. Geometry Utilities (`utils/geometry.ts`)

- Grid snap, ID generation, and edge path computation helpers.
- Keeps canvas rendering and connector routing deterministic.

### Component Interaction Flow

1. User drags nodes from **Palette** into **Canvas**.
2. User configures behavior in **Properties Panel**.
3. **Toolbar** triggers export/deploy actions.
4. **processService** sends the normalized process payload to backend.

## Integration Contract

### API Base URL

Both process and form deploy use:

- `VITE_API_BASE_URL` when configured
- fallback: `http://localhost:8085`

### Process Deploy Endpoint

- `POST /processes`

### Form Deploy Endpoint

- `POST /forms`

## Repository Location

- Application root: `easybpmn-modeler/`
- Process deploy service: `easybpmn-modeler/services/processService.ts`
- Process canvas app shell: `easybpmn-modeler/App.tsx`
- Form builder deploy logic: `easybpmn-modeler/components/FormModeler.tsx`

## Related Docs

- [Easy BPMN Modeler: Getting Started](./easy-modeler-getting-started.md)
- [Easy BPMN Modeler: Deploy & API Integration](./easy-modeler-deploy-integration.md)
- [Easy BPM Admin: Overview](./easy-admin-overview.md)
