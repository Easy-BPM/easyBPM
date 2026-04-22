---
sidebar_position: 10
---

# Easy BPM Admin: Canvas Rendering & Boundary Events

## Overview

The Easy BPM Admin workflow canvas provides visual representation of active process instances with support for:
- **Process flow visualization** with orthogonal path routing
- **Node status tracking** (visited nodes, current execution state)
- **Error boundary events** for exception handling visualization
- **Message and timer boundary events** for event-driven flows

## Canvas Rendering Architecture

### Components

#### WorkflowCanvas Component (`easy-bpm-admin/components/WorkflowCanvas.tsx`)

The primary canvas rendering component handles:

```typescript
interface Props {
  definition: WorkflowDefinition;      // BPMN process definition
  nodeHistory: string[];               // Previously executed nodes
  currentNodes: string[];              // Currently executing nodes
}
```

#### Process Definition Structure

```typescript
interface WorkflowDefinition {
  processId?: string;
  key?: string;
  name?: string;
  nodes: WorkflowNode[];
  flows?: WorkflowFlow[];
}

interface WorkflowNode {
  id: string;
  name?: string;
  type: string;                  // Task type: UserTask, ServiceTask, APITask, etc.
  position?: { x: number; y: number };
  next?: string[];               // Next node IDs (legacy flow format)
  attachedTo?: string;           // Parent node ID for boundary events
  config?: Record<string, unknown>;  // Error boundary configuration
}
```

### Supported Node Types

| Type | Icon | Color | Size |
|------|------|-------|------|
| **StartEvent** | Circle | Green | 44×44 |
| **EndEvent** | Circle | Red | 44×44 |
| **UserTask** | Rounded Rect | Blue | 150×72 |
| **ServiceTask** | Rounded Rect | Amber | 150×72 |
| **APITask** | Rounded Rect | Purple | 150×72 |
| **Gateway** | Diamond | Orange | 54×54 |
| **ParallelGateway** | Diamond | Orange | 54×54 |
| **ErrorBoundaryEvent** | Circle | Red | 36×36 |
| **MessageBoundaryEvent** | Circle | Blue | 36×36 |
| **TimerBoundaryEvent** | Circle | Amber | 36×36 |

### Rendering Pipeline

1. **Edge Drawing**: Orthogonal flow paths with arrow markers
2. **Boundary Connections**: Red dashed lines linking boundary events to parents
3. **Node Drawing**: Regular nodes (tasks, gateways, events)
4. **Boundary Node Drawing**: Smaller circular boundary event markers
5. **Execution State**: Current token pins on active nodes

### Orthogonal Path Routing

The canvas uses L-shaped (orthogonal) routing for all flow connectors:

```
Start → Right/Down → Mid-point → Down/Right → End
└─────────────────────────────────────────────┘
         Single horizontal or vertical segment
```

**Advantage**: Cleaner, BPMN-standard flow visualization with reduced visual clutter.

**Algorithm**:
- Calculates horizontal vs. vertical dominance
- Routes through midpoint between nodes
- Maintains consistent spacing from node boundaries

## SVG Marker Definitions

### Arrow Markers

Three arrow marker types with BPMN-compliant styling:

```xml
<!-- Standard flow (inactive) -->
<marker id="wf-arrow" markerUnits="strokeWidth">
  <path d="M 0 0 L 12 6 L 0 12 Z" fill="#64748b" />
</marker>

<!-- Active flow (visited) -->
<marker id="wf-arrow-active" markerUnits="strokeWidth">
  <path d="M 0 0 L 12 6 L 0 12 Z" fill="#2563eb" />
</marker>

<!-- Boundary exception flow -->
<marker id="wf-arrow-boundary" markerUnits="strokeWidth">
  <path d="M 0 0 L 12 6 L 0 12 Z" fill="#dc2626" />
</marker>
```

**Key Features**:
- `markerUnits="strokeWidth"` ensures arrows scale with stroke width
- Path-based triangles for smooth rendering
- Color-coded for visual distinction

### Edge Styling

| Type | Stroke | Width | Dash Pattern | Color |
|------|--------|-------|--------------|-------|
| **Inactive Flow** | Solid | 2px | None | #94a3b8 (Slate) |
| **Active Flow** | Solid | 3px | None | #2563eb (Blue) |
| **Boundary Exception** | Dashed | 1.5px | 4,3 | #dc2626 (Red) |

All edges use:
- `strokeLinecap="round"` for smooth line endings
- `strokeLinejoin="round"` for smooth corners
- `vectorEffect="non-scaling-stroke"` for consistent rendering at zoom levels

## Boundary Event Visualization

### How Boundary Events Render

Boundary events are special nodes that attach to parent nodes (tasks):

1. **Detection**: Node with `attachedTo` field points to parent node ID
2. **Parent Identification**: Look up parent node by `attachedTo` value
3. **Connection Drawing**: Red dashed line from parent to boundary
4. **Node Rendering**: Separate circular marker at boundary position

### Example: Error Boundary Event

```typescript
// Process Definition JSON
{
  "nodes": [
    {
      "id": "task1",
      "type": "UserTask",
      "name": "Process Payment",
      "position": { "x": 100, "y": 100 }
    },
    {
      "id": "error_catch",
      "type": "ErrorBoundaryEvent",
      "attachedTo": "task1",           // ← Attaches to task1
      "position": { "x": 250, "y": 100 },
      "config": {
        "errorCode": "PaymentFailed",
        "exceptionVariable": "paymentError"
      }
    },
    {
      "id": "handle_error",
      "type": "UserTask",
      "name": "Handle Payment Failure",
      "position": { "x": 400, "y": 100 }
    }
  ],
  "flows": [
    { "from": "task1", "to": "handle_error" }  // Exception path
  ]
}
```

### Canvas Output

```
┌─────────────────────┐
│  Process Payment    │        ErrorBoundary
│    (UserTask)       │  - - -  (Red Circle)
└─────────────────────┘        /
                             /
                    Exception path
                           ↓
                  ┌─────────────────────┐
                  │ Handle Payment      │
                  │   Failure (Task)    │
                  └─────────────────────┘
```

## Visual State Indicators

### Node Status

- **Current (Emerald)**: Node with green background and darker border
  - Indicates active execution
  - Shows with "current token pin" (green arrow)

- **Visited (Blue)**: Node with light blue background
  - Indicates previously executed node
  - Part of audit trail

- **Unvisited (White)**: Default node appearance
  - Not yet executed
  - May be future execution path

### Flow Status

- **Visited Edge (Blue)**: 3px blue stroke
  - Part of execution history
  - Shows traversal path

- **Unvisited Edge (Slate)**: 2px gray stroke
  - Potential future flows
  - Not yet traversed

## Zoom and Responsiveness

The canvas provides:

- **Responsive Sizing**: SVG width/height calculated from node bounds with padding
- **Auto-Scaling**: Minimum 700×320px, grows with process complexity
- **Pan Support**: Overflow container allows horizontal/vertical scrolling
- **Vector Scaling**: `vectorEffect="non-scaling-stroke"` ensures lines stay crisp at any zoom

## Testing Checklist

### Basic Canvas Rendering
- [ ] Simple sequential process (3 tasks) renders without errors
- [ ] Node positions display correctly
- [ ] Flow paths connect proper nodes
- [ ] Text labels are readable

### Boundary Event Rendering
- [ ] Error boundary event displays as red circle
- [ ] Dashed red line connects to parent task
- [ ] Message boundary event displays as blue circle
- [ ] Timer boundary event displays as amber circle
- [ ] Multiple boundaries on same task render without overlap

### Execution State Visualization
- [ ] Current token pin shows green arrow on active nodes
- [ ] Visited nodes show blue background
- [ ] Visited edges show blue color
- [ ] Node history tracking matches expected path

### Complex Process Definition
- [ ] Process with 10+ nodes renders efficiently
- [ ] Multiple parallel paths display correctly
- [ ] Gateways display as diamond shapes
- [ ] All node types have correct colors

### Browser Compatibility
- [ ] Chrome/Edge: SVG rendering smooth, markers scale correctly
- [ ] Firefox: Dashed line patterns render consistently
- [ ] Safari: Path-based arrows render without distortion

## Known Limitations

1. **Icon-Based Boundary Events** (Future Enhancement)
   - Currently: Color-coded circles
   - Planned: Lightning bolt for Error, Envelope for Message, Clock for Timer
   - Modeler (port 3000) already uses icon-based approach
   - Admin UI consistency enhancement pending

2. **Touch Interactions**
   - Canvas is read-only (no editing in Admin)
   - Pan via scrollbars only (no touch drag)

3. **Large Processes**
   - Performance tested up to 100 nodes
   - No lazy rendering (all nodes in memory)

## Implementation Status

| Feature | Status | Version |
|---------|--------|---------|
| Basic Canvas Rendering | ✅ Complete | v1.0.0 |
| Boundary Event Support | ✅ Complete | v1.0.0 |
| Arrow Styling (BPMN) | ✅ Complete | v1.1.0 |
| Icon-Based Boundaries | ⏳ Planned | v1.2.0 |
| Touch Interactions | ⏳ Planned | v2.0.0 |
| Performance Optimization | ⏳ Future | v2.0.0 |

## Related Documentation

- [Easy BPM Admin: Overview](./easy-admin-overview.md)
- [Easy BPM Admin: Getting Started](./easy-admin-getting-started.md)
- [Easy BPMN Modeler: Overview](./easy-modeler-overview.md)
- [Developer Quick Reference](./developer-quick-reference.md)
