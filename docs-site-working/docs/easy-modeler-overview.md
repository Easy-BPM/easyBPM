---
sidebar_position: 11
---

# Easy BPMN Modeler: Overview

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
