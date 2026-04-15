---
sidebar_position: 7
---

# Easy BPM Admin: Getting Started

## Prerequisites

- Node.js 18+ and npm 9+
- Easy BPM backend running on `http://localhost:8080`
- Modern web browser (Chrome, Firefox, Safari, or Edge)

## Installation & Setup

### 1. Navigate to the Project Directory

```bash
cd easy-bpm-admin
```

### 2. Install Dependencies

```bash
npm install
```

This installs:
- `react` (19.2.1) - UI framework
- `react-dom` (19.2.1) - DOM rendering
- `lucide-react` (0.556.0) - Icon library
- Build tools: TypeScript, Vite, React Vite plugin

### 3. Start Development Server

```bash
npm run dev
```

Expected output:
```
  VITE v6.2.0  ready in 245 ms

  ➜  Local:   http://localhost:5173/
  ➜  press h to show help
```

### 4. Access the Application

Open your browser to `http://localhost:5173/`

You should see:
1. **Login Screen** (if authentication is enabled)
2. **Dashboard** (mock login with empty credentials)

## First Login (Development Mode)

In development, the login panel accepts any username:

1. Enter a username (e.g., `admin`, `operator`, `demo-user`)
2. Password field is optional (mock authentication)
3. Click **Sign In**
4. You're now in the Dashboard

:::note Mock Authentication
Production deployments require proper authentication. See [Security Configuration](#security-configuration) below.
:::

## Dashboard Walkthrough

Once logged in, you'll see the **Dashboard** with several cards:

### Summary Cards

| Card | Shows |
|------|-------|
| **Active Instances** | Count of running processes |
| **Pending Tasks** | Number of tasks awaiting assignment |
| **Recent Errors** | Failed instances or execution errors |
| **System Uptime** | Backend availability status |

### Quick Navigation

Use the sidebar to navigate to:

- 📊 **Dashboard** - Overview and metrics
- 🔎 **Instance Explorer** - Browse and manage running instances
- 📋 **Workflows** - View deployed process definitions
- ⚙️ **Settings** - Configuration and preferences

## Common First Tasks

### View Running Process Instances

1. Click **Instance Explorer** in the sidebar
2. You'll see a paginated list of active instances with:
   - Process name and ID
   - Current execution node
   - Status (ACTIVE, PAUSED, CANCELLED)
   - Created/Updated timestamps

### Inspect a Specific Instance

1. Click an instance row to expand details
2. View:
   - Full process definition information
   - Current node in the workflow
   - Node execution history
   - Assigned tasks
   - Process variables (key-value pairs)

### Monitor Variables

1. Expand an instance
2. Scroll to the **Process Variables** section
3. View current variable values and types:
   - String: "value"
   - Number: 42
   - Boolean: true/false
   - Null: (unset)

### Stop a Running Instance

1. Find the instance in Instance Explorer
2. Click the **Stop** button (orange icon)
3. Confirm: "This will cancel the instance. Continue?"
4. Instance status changes to CANCELLED, subscriptions cleared

:::caution Non-Destructive
Stopping preserves all instance history. Tasks remain queryable. This is reversible by manually restarting the instance (manual restart is a future feature).
:::

### Delete a Complete/Failed Instance

1. Find the instance to remove
2. Click the **Delete** button (red trash icon)
3. First confirmation: "Delete this instance?"
4. Second confirmation: "This cannot be undone. Really delete?"
5. Instance and all related data (tasks, variables, subscriptions) are permanently removed

:::danger Destructive Operation
Deletion is **irreversible**. All associated data is cascaded deleted. Use for cleanup only.
:::

## Managing Tasks

### View All Tasks

1. Look for the **Tasks** section (may be in a separate tab or within Instance Explorer)
2. Tasks are displayed with:
   - Task ID and node name
   - Status (PENDING, COMPLETED, ASSIGNED)
   - Assignee (user or team)
   - Embedded variables for context
   - Created/Completed timestamps

### Filter Tasks

Use filters to narrow down tasks:

- **By Status**: PENDING, COMPLETED, ASSIGNED
- **By Assignee**: Current user or specific team member
- **By Process**: Filter by process name or ID
- **Date Range**: View tasks created/completed within a timeframe

### View Task Details

Click a task row to see:
- Full task metadata
- Associated process instance
- Variables relevant to task completion (embedded in response)
- Form metadata (if configured)

## Managing Variables

### View Process Variables

1. Open an instance in Instance Explorer
2. Scroll to **Process Variables** section
3. See all variables with current values and types

### Assign/Update Variables

1. Click **Assign Variables** (or edit button)
2. Modify existing variables or add new ones:
   ```json
   {
     "orderId": "ORD-12345",
     "status": "APPROVED",
     "amount": 1500.00
   }
   ```
3. Click **Save**
4. Variables are persisted in the backend

### Variable Types Supported

- **String**: `"hello"`
- **Number**: `42`, `3.14`
- **Boolean**: `true`, `false`
- **Null**: `null` (unset variable)
- **JSON Objects**: `{ "key": "value" }`
- **Arrays**: `[1, 2, 3]`

## Manual Node Control

### Move Execution Between Nodes

:::caution Advanced Feature
This feature allows manual redirection of process execution. Use only when you understand the process model.
:::

1. Open an instance detail view
2. Scroll to **Node Control** section
3. Select:
   - **From Node**: Current execution node
   - **To Node**: Target node
   - **Reason**: Optional description (for audit logging)
4. Click **Move**

The process will:
- Exit the current node (if it has exit handling)
- Jump to the target node
- Resume execution normally
- Record the manual movement in node history

## Settings & Preferences

1. Click **Settings** in the sidebar
2. Configure:
   - **API Endpoint**: Backend URL (default: `http://localhost:8080`)
   - **Page Size**: Instances per page (default: 20)
   - **Auto-Refresh**: Enable/disable polling (default: 30s interval)
   - **Theme**: Light/Dark mode
   - **Language**: Localization (if configured)

## Troubleshooting First Run

### "Cannot Connect to Backend" Error

**Problem**: Easy Admin shows connection error on startup

**Solutions**:
1. Verify Easy BPM backend is running: `http://localhost:8080/swagger-ui.html`
2. Check CORS configuration in backend `application.yml`:
   ```yaml
   cors:
     allowed-origins: http://localhost:5173
   ```
3. Verify network: `curl http://localhost:8080/health`

### "Login Always Fails" Error

**Problem**: Login button doesn't respond or always shows error

**Solutions**:
1. Open browser Developer Tools (F12)
2. Check **Network** tab for failed requests
3. Check **Console** tab for error messages
4. Verify backend authentication endpoint:
   ```bash
   curl -X POST http://localhost:8080/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"password"}'
   ```

### "Instances Page is Blank" Error

**Problem**: Instance Explorer shows no data

**Solutions**:
1. Verify instances exist in backend: Check database directly
2. Check pagination: Increase page size or go to page 0
3. Check filters: Ensure no filters are active
4. Toggle mock mode by editing `services/adminService.ts`:
   ```typescript
   const USE_MOCK = true; // See sample data instead
   ```

### "Stop/Delete Buttons Do Nothing" Error

**Problem**: Clicking Stop/Delete shows no response

**Solutions**:
1. Open Developer Tools (F12) → **Network** tab
2. Click Stop/Delete and watch for API requests
3. Expected requests:
   - `POST /processes/instances/{id}/stop` (Stop)
   - `DELETE /processes/instances/{id}` (Delete)
4. Check response status:
   - `200` or `204`: Success
   - `401`: Unauthorized (auth needed)
   - `403`: Forbidden (permission denied)
   - `404`: Instance not found
   - `500`: Server error (check backend logs)

## Next Steps

- [Easy Admin: Architecture & Components](./easy-admin-architecture.md) - Understand the codebase structure
- [Easy Admin: Features Guide](./easy-admin-features.md) - Deep dive into each feature
- [Easy Admin: API Integration](./easy-admin-api-integration.md) - Backend endpoint details
- [Integration Testing](./integration-testing.md) - Test Easy Admin with the backend

## Tips & Tricks

### Using Mock Mode for Development

Edit `services/adminService.ts`:

```typescript
const USE_MOCK = true; // Enable mock data
```

Benefits:
- Develop without a running backend
- Faster iteration
- Predictable test data

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Cmd/Ctrl + K` | Search instances |
| `Cmd/Ctrl + Shift + S` | Settings |
| `Escape` | Close dialogs |

### Exporting Instance Data

Currently not GUI-supported, but you can:

1. Open Developer Tools → **Network** tab
2. Trigger any download action
3. View the response JSON
4. Save to file manually

(Bulk export feature planned for v2)

---

**Last Updated**: April 2025 | **Version**: 1.0.0 | **Questions?** Check [Troubleshooting](./easy-admin-overview.md#troubleshooting)
