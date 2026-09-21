---
name: easybpm-modeler
description: Build, change, or review the EasyBPM process and form modeler in easy-bpm-modeler, including BPMN XML import/export, canvas behavior, node properties, validation, deployment, and Electron desktop behavior. Do not use for Admin or Task Portal-only UI work.
---

# EasyBPM Modeler

Work in `easy-bpm-modeler/`, a React 19, TypeScript, Vite application with an Electron desktop target.

## Preserve these contracts

- Treat BPMN 2.0 XML as the interchange and persistence contract. Verify import -> edit -> export behavior, stable node/flow identifiers, namespaces, EasyBPM extension data, conditions, variable mappings, form references, and unsupported-data preservation.
- Keep visual state, `types.ts`, `utils/bpmnXml.ts`, validation, property panels, and backend deployment payloads aligned.
- The web modeler may use its configured backend. The desktop modeler must remain server-optional, use the desktop bridge for local file operations, and never access the database directly.
- Keep authentication and backend URL behavior compatible with `config/runtimeConfig.ts`, `services/processService.ts`, and `services/desktopBridge.ts`.
- Reject structurally invalid processes before deployment with actionable user feedback. Check duplicate or blank IDs, broken sequence flows, invalid gateway conditions, missing task configuration, and invalid form or variable mappings as applicable.

## Workflow

Trace the model from its UI editor through types, XML conversion, validation, and deployment before editing. When introducing or changing a BPMN property, update both serialization directions and add or update a realistic fixture or test where the repository has coverage.

Use existing components and styling patterns. Keep canvas interactions keyboard-accessible where feasible and avoid coupling domain serialization to purely visual coordinates beyond BPMN diagram data.

## Validation

From `easy-bpm-modeler/`, run:

```text
npm run lint
npm run build
```

For desktop-specific changes, also run `npm run build:desktop`. Exercise an import/export round trip for BPMN changes and report any desktop packaging check that was not available.
