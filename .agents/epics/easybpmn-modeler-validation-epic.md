# EPIC: Easy BPMN Modeler Validation and Export/Deploy UX Hardening

## EPIC Owner
- CTO (architecture and quality gate)

## Delivery Team
- Frontend Developer (implementation)

## Goal
Improve Easy BPMN Modeler reliability and user experience by:
- fixing Deploy/Export toolbar layout and action clarity,
- enforcing BPM structural validation before Deploy and Export JSON,
- adding deeper validation layers for gateway and flow semantics,
- reducing invalid definitions reaching backend.

## Scope
- App: `bpm/easybpmn-modeler`
- Views: Process Modeler (BPM canvas)
- Out of scope: backend API contract changes

## Architecture Constraints
- Keep deployment contract via `POST /processes` unchanged.
- Keep form deployment contract via `POST /forms` unchanged.
- Validation should run client-side and block actions on hard errors.
- Warnings should be non-blocking but visible.

## Priority Backlog

### Task FE-VAL-001 (Done in this increment)
Title: Fix toolbar action formatting and visibility
Owner: Frontend Developer
Priority: P0
Deliverables:
- Clean horizontal layout for Deploy and Export actions.
- Remove stacked-button formatting artifact.
- Preserve responsive behavior for narrow widths.
Acceptance Criteria:
- Deploy and Export appear in one aligned action group.
- No overlap, clipping, or accidental vertical compression.

### Task FE-VAL-002 (Done in this increment)
Title: Introduce multi-layer BPM validation engine
Owner: Frontend Developer
Priority: P0
Deliverables:
- Validation categories: graph integrity, node rules, variable rules, gateway rules.
- Blocking errors + non-blocking warnings model.
Acceptance Criteria:
- Deploy/Export blocked when validation errors exist.
- First actionable error shown via toast on click.
- Validation summary shown near action buttons.

### Task FE-VAL-003 (Done in this increment)
Title: Parallel Gateway semantic validation
Owner: Frontend Developer
Priority: P0
Rules:
- Fork pattern valid: `1 incoming`, `2+ outgoing`.
- Join pattern valid: `2+ incoming`, `1 outgoing`.
- Any other shape is invalid.
Acceptance Criteria:
- Invalid parallel gateway shape blocks deploy/export.
- Warning emitted for conditional outgoing flows on parallel gateways.

### Task FE-VAL-004
Title: Exclusive Gateway conditional flow policy
Owner: Frontend Developer
Priority: P1
Deliverables:
- Warn when exclusive split has multiple outgoing edges with no conditions.
- Optional later: enforce at least one default path semantics.
Acceptance Criteria:
- User sees warning with gateway ID and guidance.

### Task FE-VAL-005
Title: Reachability and dead-node analysis UX
Owner: Frontend Developer
Priority: P1
Deliverables:
- Detect unreachable nodes from any start event.
- Show warning list with node IDs.
- Optional UI highlight in canvas for unreachable nodes.
Acceptance Criteria:
- Reachability analysis appears before export/deploy.
- Warning remains non-blocking.

### Task FE-VAL-006
Title: Boundary event integrity validation
Owner: Frontend Developer
Priority: P1
Deliverables:
- Boundary event must be attached to task node.
- No incoming flow to boundary event.
- At least one outgoing flow from boundary event.
Acceptance Criteria:
- Violations block deploy/export with specific node ID.

### Task FE-VAL-007
Title: Validation panel in right-side properties
Owner: Frontend Developer
Priority: P2
Deliverables:
- Add dedicated "Validation" section with grouped Errors/Warnings.
- Include quick links to select offending node/edge.
Acceptance Criteria:
- User can navigate from validation message to problematic element.

### Task FE-VAL-008
Title: Preflight validation report in exported JSON metadata
Owner: Frontend Developer
Priority: P3
Deliverables:
- Include non-blocking validation report in exported metadata.
- Keep backend payload compatibility.
Acceptance Criteria:
- Export includes `metadata.validationSummary` without breaking backend deploy.

## Definition of Done
- `npm run build` succeeds in `easybpmn-modeler`.
- No regression on import/export/deploy flow.
- Blocking validation rules prevent invalid deploy/export.
- Warnings are visible and understandable.
- Documentation for modeler behavior updated if rules change.

## QA Checklist
- Duplicate node IDs block actions.
- Empty process ID blocks actions.
- Missing start/end blocks actions.
- Task nodes without incoming/outgoing block actions.
- Parallel gateway invalid shape blocks actions.
- Boundary event detached blocks actions.
- Unreachable node emits warning.

## Release Notes (Increment 1)
- Toolbar action formatting fixed.
- Deploy/Export now guarded by multi-layer validation.
- Added structural graph checks and gateway semantics, including parallel gateway fork/join validation.
- Added validation feedback tooltip near action buttons.
