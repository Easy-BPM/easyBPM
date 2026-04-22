# Phase 8.2: Code Task Modeler UI

**Status**: 🔄 IN PROGRESS (2026-04-22)

## Overview

Phase 8.2 implements the BPMN Modeler UI for Code Task design, enabling users to:
- Upload JAR files and discover available classes/methods
- Create Code Task nodes on the BPMN canvas
- Configure method invocation with variable mappings
- Validate and save Code Task configurations
- Deploy processes with Code Task nodes

## Components Created

### 1. CodeTaskJarUploadPanel
**File**: `easybpmn-modeler/components/CodeTaskJarUploadPanel.tsx`

**Purpose**: Manage JAR file uploads and browse discovered classes

**Features**:
- Upload JAR files via multipart form
- Display uploaded JARs with metadata (file name, class count, method count)
- Expandable class list with method information
- Delete JAR files
- Show upload progress and error handling

**Props**:
```typescript
interface CodeTaskJarUploadPanelProps {
  onJarSelected?: (jarId: number, className: string, methodName: string) => void;
  onJarUpload?: (response: CodeTaskJarUploadResponse) => void;
}
```

**State Management**:
- `uploadedJars`: List of uploaded JAR files
- `uploading`: Upload in-progress flag
- `expandedJars`: Set of expanded JAR IDs
- `expandedClasses`: Set of expanded class keys

**API Integration**:
- `POST /code-tasks/upload` - Upload JAR file
- `GET /code-tasks/jar/{jarId}/classes` - List classes (on expand)

### 2. CodeTaskPropertyPanel
**File**: `easybpmn-modeler/components/CodeTaskPropertyPanel.tsx`

**Purpose**: Configure Code Task node properties including method and variable mappings

**Features**:
- JAR file selection dropdown
- Dynamic class list loading based on selected JAR
- Dynamic method list loading based on selected class
- Display method signature and return type
- Input variable mapping interface (process var → method param)
- Output variable mapping interface (return value → process var)
- Add/remove mapping rows
- Process variable autocomplete

**Props**:
```typescript
interface CodeTaskPropertyPanelProps {
  nodeId: string;
  properties: {
    jarId?: number;
    className?: string;
    methodName?: string;
    inputMappings?: Record<string, string>;
    outputMappings?: Record<string, string>;
  };
  onPropertiesChange: (properties: any) => void;
  processVariables: Array<{ name: string; type?: string }>;
  availableJars: Array<{ jarId: number; fileName: string; classCount: number }>;
}
```

**State Management**:
- `selectedJar`: Currently selected JAR ID
- `selectedClass`: Currently selected class name
- `selectedMethod`: Currently selected method name
- `classes`: List of classes in selected JAR
- `methods`: List of methods in selected class
- `inputMappings`: Array of input variable mappings
- `outputMappings`: Array of output variable mappings
- `loadingClasses`, `loadingMethods`: Loading flags

**API Integration**:
- `GET /code-tasks/jar/{jarId}/classes` - Load classes
- `GET /code-tasks/jar/{jarId}/classes/{className}/methods` - Load methods

### 3. CodeTaskNode
**File**: `easybpmn-modeler/components/CodeTaskNode.tsx`

**Purpose**: Visual SVG representation of Code Task node on BPMN canvas

**Components**:
- **CodeTaskNode**: SVG group representing task node
- **CodeTaskPaletteItem**: Draggable palette component

**CodeTaskNode Features**:
- SVG background rectangle with rounded corners
- Blue icon background with code symbol (`<>`)
- Method name label (truncated if long)
- Class name display (simplified)
- Selection highlight (thicker border, blue color)
- Click and double-click handlers

**Props**:
```typescript
interface CodeTaskNodeProps {
  id: string;
  x: number;
  y: number;
  width?: number;        // Default: 120px
  height?: number;       // Default: 80px
  label?: string;        // Default: "Code Task"
  methodName?: string;   // Displayed in node
  className?: string;    // Displayed below method
  isSelected?: boolean;  // Blue highlight
  onClick?: () => void;
  onDoubleClick?: () => void;
}
```

**CodeTaskPaletteItem Features**:
- Draggable component for palette
- Blue styling with dashed border
- Hover effects
- Icon and description

### 4. CodeTaskModeler (Main Component)
**File**: `easybpmn-modeler/components/CodeTaskModeler.tsx`

**Purpose**: Orchestrates Code Task design with canvas, properties, and JAR management

**Features**:
- Three-tab interface: Canvas, Properties, Upload
- Canvas with drag-and-drop for creating nodes
- Property panel for configuring selected node
- JAR upload and management panel
- Validation with error display
- Node deletion

**Props**:
```typescript
interface CodeTaskModelerProps {
  processDefinitionKey?: string;
  processVariables?: Array<{ name: string; type?: string }>;
  onNodeCreated?: (nodeConfig: CodeTaskNodeConfig) => void;
  onNodeUpdated?: (nodeConfig: CodeTaskNodeConfig) => void;
  existingNodes?: CodeTaskNodeConfig[];
}
```

**State Management**:
- `nodes`: Array of Code Task nodes on canvas
- `selectedNodeId`: Currently selected node ID
- `uploadedJars`: Array of uploaded JAR metadata
- `isDraggingFromPalette`: Drag-and-drop flag
- `validationErrors`: Array of validation error messages

**Validation Rules**:
1. JAR file is required
2. Class is required
3. Method is required
4. At least one input mapping is recommended (warning)
5. Output mappings should match method return type

**Canvas Interaction**:
- Drag CodeTaskPaletteItem from left panel → Canvas
- Drop creates new Code Task node at cursor position
- Click node to select
- Double-click node to open Properties tab
- Delete button removes selected node

## Data Flow

### Upload JAR Workflow
```
User selects JAR file
    ↓
POST /code-tasks/upload (multipart)
    ↓
Server validates + discovers classes
    ↓
Response: jarId, classes, methodCount
    ↓
Update uploadedJars state
    ↓
Display in CodeTaskJarUploadPanel
```

### Create Code Task Node Workflow
```
User drags CodeTaskPaletteItem to canvas
    ↓
handleCanvasDrop() creates new node
    ↓
Node added to nodes[] state
    ↓
Node rendered on SVG canvas
    ↓
User clicks node → selectedNodeId set
    ↓
Properties panel shows node properties
```

### Configure Node Workflow
```
User selects JAR in Properties panel
    ↓
GET /code-tasks/jar/{jarId}/classes
    ↓
Classes dropdown populated
    ↓
User selects Class
    ↓
GET /code-tasks/jar/{jarId}/classes/{className}/methods
    ↓
Methods dropdown populated
    ↓
User selects Method
    ↓
Method signature displayed
    ↓
User adds input/output mappings
    ↓
onPropertiesChange() callback fired
    ↓
Node properties updated in nodes[] state
    ↓
validateNode() checks requirements
```

## Integration with BPMN Modeler

### Canvas Integration
The CodeTaskModeler component should be integrated into the main modeler canvas as a new tool/palette section:

```typescript
// In main Modeler component
<div className="grid grid-cols-3 gap-4">
  <div>
    <BPMNPalette>
      <StartEvent />
      <EndEvent />
      <UserTask />
      <CallActivity />
      <CodeTaskPaletteItem />
    </BPMNPalette>
  </div>
  <div className="col-span-2">
    <BPMNCanvas>
      {/* Render all nodes including CodeTaskNode components */}
    </BPMNCanvas>
  </div>
</div>
```

### Process Definition Export
When saving the process, Code Task nodes should be exported as XML:

```xml
<bpmn:serviceTask id="codeTask1" name="Calculate Total">
  <bpmn:extensionElements>
    <code:codeTask>
      <code:jarId>1</code:jarId>
      <code:className>com.example.OrderProcessor</code:className>
      <code:methodName>calculateTotal</code:methodName>
      <code:inputMappings>
        <code:mapping processVar="order" methodParam="0" />
        <code:mapping processVar="taxRate" methodParam="1" />
      </code:inputMappings>
      <code:outputMappings>
        <code:mapping methodReturn="total" processVar="orderTotal" />
      </code:outputMappings>
    </code:codeTask>
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

## UI/UX Design

### Color Scheme
- Primary: Blue (#2563eb) - Code Task colors
- Background: Gray (#f3f4f6) - Canvas background
- Border: Gray (#d1d5db) - Component borders
- Text: Dark gray (#1f2937) - Primary text
- Icons: Lucide React icons

### Layout
- **Canvas Tab**: Palette (left) + SVG Canvas (right)
- **Properties Tab**: Form with dropdowns, signature display, mapping tables
- **Upload Tab**: JAR list with expandable classes

### Responsive Design
- Desktop-first layout
- Flex-based responsive grid
- Scrollable panels for overflow
- Minimum canvas height: 500px

## Files Created

1. **CodeTaskJarUploadPanel.tsx** (150 lines)
2. **CodeTaskPropertyPanel.tsx** (250 lines)
3. **CodeTaskNode.tsx** (120 lines)
4. **CodeTaskModeler.tsx** (300 lines)

## Dependencies

### Frontend Libraries
- React 19
- Lucide React (icons)
- Tailwind CSS (styling)
- @radix-ui/react-tabs (tab component)

## TODO / In Progress

### Phase 8.2.1: Canvas & Palette (STARTED)
- ✅ CodeTaskNode SVG rendering
- ✅ CodeTaskPaletteItem component
- ✅ Canvas drag-and-drop
- ⏳ Connect with main BPMN canvas

### Phase 8.2.2: JAR Upload (STARTED)
- ✅ CodeTaskJarUploadPanel component
- ✅ File upload UI
- ✅ JAR list display
- ⏳ Error handling & retry logic

### Phase 8.2.3: Variable Mapping (STARTED)
- ✅ Input mapping UI
- ✅ Output mapping UI
- ✅ Add/remove mapping rows
- ⏳ Type checking (process var type → method param type)

### Phase 8.2.4: Validation & Deploy (PENDING)
- ⏳ Validation error display
- ⏳ Deploy endpoint integration (`POST /processes`)
- ⏳ Show method signature after selection
- ⏳ Handle invalid JAR/class/method errors

## Testing Strategy

### Unit Tests
- Component rendering with props
- State changes on user interaction
- Props propagation to child components

### Integration Tests
- JAR upload flow (upload → select class → select method)
- Canvas node creation (drag → drop → select → configure)
- Variable mapping (add/remove/update)
- Validation (missing required fields)

### E2E Tests
- Complete workflow: Upload JAR → Create node → Configure → Deploy

## Future Enhancements

1. **Type Checking**: Validate that process variable types match method parameter types
2. **Method Search**: Filter methods by name in dropdown
3. **Code Preview**: Show method implementation code snippet on hover
4. **Execution Monitoring**: Show execution history for Code Task nodes in Admin UI
5. **Error Boundary Integration**: Add error boundary event support
6. **Performance Optimization**: Cache classes/methods to reduce API calls
7. **Offline Mode**: Store uploaded JARs locally for offline modeler usage
8. **Multi-JAR Support**: Create nodes from multiple JARs in same process

---
**Last Updated**: 2026-04-22 | **Author**: Frontend Development Team
