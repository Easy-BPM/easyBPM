---
sidebar_position: 6
---

# Easy BPM Admin: Overview

## What is Easy BPM Admin?

Easy BPM Admin is a React-based web application designed to manage and monitor active process instances in the Easy BPM orchestrator. It provides a user-friendly interface for administrators and process operators to:

- **Monitor** running process instances in real-time
- **Manage** active workflows (stop, delete, or redirect execution)
- **Inspect** process variables and task assignments
- **Control** process state transitions and node movement

## Key Characteristics

### Focus: Management, Not Execution

Easy BPM Admin is explicitly **management-only**. It does **not** provide workflow execution or task completion capabilities. Its role is to observe, control, and troubleshoot running processes.

### Technology Stack

- **Frontend Framework**: React 19 with TypeScript
- **Build Tool**: Vite 6.2
- **Styling**: Tailwind CSS + Inter font
- **Icons**: Lucide React (comprehensive icon library)
- **HTTP Client**: Fetch API with mock fallback support

### Architecture Principles

1. **Service-Oriented**: All API integration isolated in `adminService.ts`
2. **Type-Safe**: Comprehensive TypeScript interfaces for all backend contracts
3. **State-Managed**: React hooks (`useState`, `useEffect`) for component state
4. **Mock-Capable**: Built-in mock mode for UI development and testing (`USE_MOCK` flag)
5. **Modal-Free**: Uses native browser dialogs for confirmations, reducing dependency complexity

## Core Features at a Glance

| Feature | Purpose | Status |
|---------|---------|--------|
| **Instance Explorer** | Browse and filter active process instances | ✅ Active |
| **Instance Lifecycle** | Stop or delete running instances | ✅ Active |
| **Variable Inspector** | View and modify process variables | ✅ Active |
| **Node Control** | Manually move process execution between nodes | ✅ Active |
| **Task Management** | View assigned tasks with embedded variables | ✅ Active |
| **Workflow Catalog** | Browse deployed process definitions | ✅ Active |
| **Dashboard** | Summary of system health and activity | ✅ Active |

## Running Instances

### Development Mode

```bash
cd easy-bpm-admin
npm install
npm run dev
```

Access at `http://localhost:5173`

### Production Build

```bash
npm run build
npm run preview
```

Output: `dist/` directory (217 KB uncompressed, 66 KB gzipped)

## Integration Points

Easy BPM Admin communicates exclusively with the Easy BPM backend API (default: `http://localhost:8080`). Key endpoints:

- `GET /processes/instances` - Fetch paginated instances
- `GET /processes/instances/{id}` - Single instance details
- `POST /processes/instances/{id}/stop` - Cancel execution
- `DELETE /processes/instances/{id}` - Hard delete with cleanup
- `GET|PUT /processes/instances/{id}/variables` - Variable inspection and assignment
- `POST /processes/instances/{id}/move-node` - Manual execution control

See [Easy Admin: API Integration](./easy-admin-api-integration.md) for detailed endpoint documentation.

## User Roles

### System Administrator
- Full access to all instances
- Can stop, delete, or redirect any process
- Manages system-level variables

### Process Operator
- Can monitor assigned instances
- Limited delete permissions (recent instances only, configurable)
- Can reassign tasks and update variables

### Viewer (Read-Only)
- Can browse instances and tasks
- No modification permissions
- Useful for auditing and monitoring

## Security Considerations

1. **Authentication**: Easy Admin displays a login screen (currently mock-bypassed in development)
2. **Authorization**: Backend enforces role-based access control (RBAC)
3. **No Direct State Modification**: Variables and node movement require POST requests, not form submissions
4. **Confirmation Dialogs**: Destructive operations (delete) require user confirmation
5. **Audit Trail**: All management operations are logged by the backend

## Next Steps

- [Getting Started with Easy Admin](./easy-admin-getting-started.md) - Setup and first run
- [Easy Admin: Architecture & Components](./easy-admin-architecture.md) - Component structure and design
- [Easy Admin: Features Guide](./easy-admin-features.md) - Detailed feature walkthroughs
- [Easy Admin: API Integration](./easy-admin-api-integration.md) - Backend endpoint reference

## Common Use Cases

### Pause a Stuck Process

A process instance is waiting indefinitely on a human task. Use Easy Admin to:

1. Find the instance by workflow name or ID
2. Click **Stop** to cancel execution and cleanup subscriptions
3. Optionally delete if the instance is unrecoverable

### Recover from Logic Error

A process took the wrong execution path due to a bug. Use Easy Admin to:

1. Navigate to the **Node Control** section
2. Move execution from the current (incorrect) node to the intended node
3. Adjust workflow variables if needed
4. Resume (manual restart - feature planned for v2)

### Audit Active Work

Review what processes are currently running and which tasks are pending. Use Easy Admin to:

1. Open the **Instance Explorer** dashboard
2. Filter by status (ACTIVE, PAUSED, CANCELLED)
3. Click an instance to view assigned tasks and variables
4. Export audit report for compliance

## Troubleshooting

### Easy Admin Won't Connect to Backend

1. Verify backend is running on `http://localhost:8080`
2. Check CORS configuration allows `http://localhost:5173`
3. Open browser developer console (F12) to see network errors

### Changes Not Persisting

1. Ensure authentication is successful (not bypassed in production)
2. Verify user role has write permissions for that operation
3. Check backend logs for transaction rollback errors

### Mock Mode vs. Real API

Set the `USE_MOCK` flag in `services/adminService.ts`:

```typescript
const USE_MOCK = false; // true = mock mode, false = real API
```

**Mock mode** is useful for:
- UI/UX development without a running backend
- Testing frontend error handling
- Demoing the application

---

**Last Updated**: April 2025 | **Version**: 1.0.0 | **Target Audience**: Administrators, Operators, DevOps Engineers
