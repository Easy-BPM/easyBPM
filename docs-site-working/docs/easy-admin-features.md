---
sidebar_position: 10
---

# Easy BPM Admin: Features Guide

## Feature Overview

Easy BPM Admin provides a comprehensive suite of features for managing and monitoring process instances. This guide details each feature with use cases and step-by-step instructions.

---

## 1. Dashboard & System Overview

### Purpose
Provide at-a-glance view of system health, activity trends, and key metrics.

### What You See

```
┌─────────────────────────────────────────┐
│  Easy BPM Admin Dashboard               │
├─────────────────────────────────────────┤
│                                         │
│  ┌──────────────┬──────────────┐        │
│  │ Active       │ Pending      │        │
│  │ Instances    │ Tasks        │        │
│  │     42       │     128      │        │
│  └──────────────┴──────────────┘        │
│                                         │
│  ┌──────────────┬──────────────┐        │
│  │ Recent       │ System       │        │
│  │ Errors       │ Uptime       │        │
│  │      3       │   99.8%      │        │
│  └──────────────┴──────────────┘        │
│                                         │
│  Recent Activity:                       │
│  • Instance 1002 stopped (2 min ago)    │
│  • Instance 994 created (15 min ago)    │
│  • Task completed by jane.doe (1h ago)  │
│                                         │
└─────────────────────────────────────────┘
```

### Metrics Explained

| Metric | Updates | Meaning |
|--------|---------|---------|
| **Active Instances** | Real-time | Count of ACTIVE status instances |
| **Pending Tasks** | Real-time | Count of PENDING status tasks |
| **Recent Errors** | Last 24h | Failed instances or execution exceptions |
| **System Uptime** | Periodic | Backend API availability percentage |

### Quick Actions

From dashboard, you can:
- Navigate to Instance Explorer to browse running processes
- View recent activity for audit trail
- Access Settings for configuration

### Use Cases

**Scene: Monday Morning Check-In**

1. Open Easy Admin Dashboard
2. Check "Active Instances" count (should be <10 for normal operation)
3. Check "Pending Tasks" count (should be assignable)
4. Check "Recent Errors" (any overnight failures?)
5. Verify "System Uptime" (expect >99%)

If all metrics are green, system is healthy. If issues detected:
- Click on error summary to see failed instances
- Navigate to Instance Explorer for details
- Use Stop/Delete to cleanup problematic instances

---

## 2. Instance Explorer: Browse & Manage

### Purpose
Central hub for observing and controlling process instances.

### Main View: Instance List

Shows paginated table of all instances:

```
┌──────┬──────────────────────┬────────┬──────────────┐
│ ID   │ Process              │ Status │ Current Node │
├──────┼──────────────────────┼────────┼──────────────┤
│ 1001 │ Order Fulfillment    │ ACTIVE │ user-review  │
│ 1002 │ Expense Approval     │ ACTIVE │ approval     │
│ 1000 │ PO Request           │ PAUSED │ (paused)     │
│ 999  │ Invoice Processing   │ CANCEL │ (terminated) │
└──────┴──────────────────────┴────────┴──────────────┘
```

### Status Color Coding

- 🟢 **ACTIVE** (green) - Running normally
- 🟡 **PAUSED** (amber) - Suspended, awaiting manual action
- 🔴 **CANCELLED** (red) - Stopped by user
- ⚪ **COMPLETED** (gray) - Finished execution

### Filtering & Search

**Filter by Status**:
```
[Filter] ┌─────────────────┐
         │ All             │
         │ ✓ ACTIVE        │
         │  PAUSED         │
         │  CANCELLED      │
         │  COMPLETED      │
         └─────────────────┘
```

**Search by ID or Name**:
```
[Search: "Order"] → Shows only "Order Fulfillment" instances
```

### Pagination

```
Showing 20 of 95 instances
[Previous] [1] [2] [3] [4] [5] [Next]
```

### Click to Expand: Instance Details

Clicking a row expands to show:

```
┌─ Instance #1001 ──────────────────────────────────┐
│                                                    │
│ Process: Order Fulfillment (key: order-fulfillment)│
│ Status: ACTIVE                                     │
│ Created: 2025-04-15 10:30 UTC                      │
│ Updated: 2025-04-15 14:25 UTC                      │
│                                                    │
│ Node History:                                      │
│  1. start (2025-04-15 10:30)                       │
│  2. validate-order (2025-04-15 10:32)              │
│  3. user-review (2025-04-15 10:35) ← CURRENT      │
│                                                    │
│ Variables:                                         │
│  orderId: "ORD-9912"                               │
│  priority: "HIGH"                                  │
│  amount: 1500.50                                   │
│  items: ["SKU-001", "SKU-002"]                     │
│                                                    │
│ Actions: [Stop] [Delete] [Move Node]               │
│                                                    │
└────────────────────────────────────────────────────┘
```

---

## 3. Stop Instance: Safe Cancellation

### Purpose
Gracefully cancel a process instance without data loss.

### When to Use

✅ **Good Use Cases**:
- Long-running instance stuck on manual task
- User requested process cancellation
- Wrong process started by accident (but early in execution)
- Workflow logic error causing infinite loop

❌ **Bad Use Cases**:
- Want to clean up all completed instances (use Delete instead)
- Need to change workflow definition (Stop instance, then restart with new definition)

### How to Stop an Instance

**Step 1**: Find instance in Instance Explorer
```
Scroll/search to locate your instance
```

**Step 2**: Click instance row to expand

**Step 3**: Click **STOP** button (orange icon)
```
┌──────────────┐
│ Stop ⏹       │  ← Click here
└──────────────┘
```

**Step 4**: Confirm dialog
```
┌────────────────────────────────────┐
│  Stop Process Instance?            │
│                                    │
│  This will cancel the instance.    │
│  Tasks will remain queryable.      │
│                                    │
│  [Cancel] [Stop]                   │
└────────────────────────────────────┘
```

**Step 5**: Status changes to CANCELLED
```
Status: ACTIVE → CANCELLED
Current Node: (cleared)
Message Subscriptions: (deleted)
```

### What Happens Behind the Scenes

1. ✅ Instance status changes to `CANCELLED`
2. ✅ All message subscriptions deleted (won't wake up on messages)
3. ✅ Current node cleared (no active execution)
4. ✅ Task records preserved (can be viewed and audited)
5. ✅ Variable values intact (can be reviewed)
6. ✅ Node execution history intact (audit trail preserved)

### What's NOT Deleted

- Task records
- Process variables
- Node history
- Instance record itself

### Recovery

To "resume" a stopped instance (not yet available in v1):
- Plan for manual restart feature in backend
- Frontend UI will have "Restart from Node" option
- Or delete and create new instance with same variables

### Example: Stopping a Stuck Order Review

**Scenario**: Customer order waiting 3 days for approval, approver on vacation.

**Steps**:
1. Open Instance Explorer
2. Search for order ID: "ORD-9912"
3. Find instance #1001 in ACTIVE status
4. Click to expand details
5. Verify current node is "user-review" (expected)
6. Click **Stop** button
7. Confirm: "This will cancel the instance. Continue?"
8. Status changes to CANCELLED
9. Escalate to manager manually outside system

---

## 4. Delete Instance: Permanent Cleanup

### Purpose
Permanently remove a process instance and all related data when no longer needed.

### When to Use

✅ **Good Use Cases**:
- Test/demo instance created accidentally
- Failed instance that cannot be recovered
- Instance from old workflow definition (deprecated)
- Compliance: remove sensitive data after period expires

❌ **Bad Use Cases**:
- Want to stop execution (use Stop instead)
- Want to reset and restart (Stop + create new instance)
- Regular cleanup of completed instances (dangerous, use archive instead)

### How to Delete an Instance

**Step 1**: Find instance in Instance Explorer

**Step 2**: Click instance row to expand

**Step 3**: Click **DELETE** button (red trash icon)
```
┌──────────────┐
│ Delete 🗑    │  ← Click here
└──────────────┘
```

**Step 4**: First confirmation
```
┌────────────────────────────────────┐
│  Delete Process Instance?          │
│                                    │
│  Instance #1001 will be removed.   │
│                                    │
│  [Cancel] [Delete]                 │
└────────────────────────────────────┘
```

**Step 5**: Second confirmation (double-check)
```
┌────────────────────────────────────┐
│  This cannot be undone!            │
│                                    │
│  All tasks, variables, and data    │
│  associated with this instance     │
│  will be permanently deleted.      │
│                                    │
│  [Cancel] [Really Delete]          │
└────────────────────────────────────┘
```

**Step 6**: Instance removed
```
Instance #1001 deleted successfully
(Removed from list, no longer queryable)
```

### What Gets Deleted

Cascading delete removes:
1. ✅ All tasks for this instance
2. ✅ All task variables (embedded variables in tasks)
3. ✅ All process variables
4. ✅ All message subscriptions
5. ✅ All worker requests (background jobs)
6. ✅ The instance record itself

**Deleted in a single transaction**: Either ALL deleted or NONE deleted (no partial deletes).

### What Cannot Be Recovered

- Instance record (unrecoverable)
- All variable values (unrecoverable)
- Task history (unrecoverable)
- Execution log (unrecoverable)

### Backup & Archival (Production Recommendation)

Before deleting high-value instances:

1. Export instance data to JSON/CSV
2. Store in archive database or S3 bucket
3. OR use database backup tool for point-in-time recovery
4. Then delete from active system

### Example: Deleting a Failed Test Instance

**Scenario**: Instance #999 created during testing, contains incorrect data, should not exist.

**Steps**:
1. Open Instance Explorer
2. Filter: Status = CANCELLED (to find test instances)
3. Find instance #999
4. Click to expand
5. Click **Delete** button
6. Confirm first dialog: "Delete this instance?" → Yes
7. Confirm second dialog: "This cannot be undone. Really delete?" → Yes
8. Instance is permanently removed

---

## 5. Process Variables: Inspect & Modify

### Purpose
View and modify the data context of a running process.

### Variables Explained

Each process instance has a set of named variables:

```
Process Instance #1001 Variables:
├── orderId: "ORD-9912" (String)
├── priority: "HIGH" (String)
├── approved: false (Boolean)
├── amount: 1500.50 (Number)
├── items: ["SKU-001", "SKU-002"] (Array)
└── metadata: { source: "web", version: 2 } (Object)
```

Variables can be:
- **Strings**: `"hello"`, `"ORD-12345"`
- **Numbers**: `42`, `3.14`, `0`
- **Booleans**: `true`, `false`
- **Arrays**: `[1, 2, 3]`, `["a", "b"]`
- **Objects**: `{ key: "value" }`
- **Null**: `null` (unset variable)

### How to View Variables

**Method 1: In Instance Details**

1. Open Instance Explorer
2. Click instance row to expand
3. Scroll to **Process Variables** section
4. See all variables with values displayed in JSON format

**Method 2: Get Instance Variables API**

```bash
GET /processes/instances/1001/variables
```

Returns:
```json
[
  { "name": "orderId", "value": "ORD-9912" },
  { "name": "priority", "value": "HIGH" },
  { "name": "approved", "value": false },
  { "name": "amount", "value": 1500.50 },
  { "name": "items", "value": ["SKU-001", "SKU-002"] },
  { "name": "metadata", "value": { "source": "web", "version": 2 } }
]
```

### How to Modify Variables

**Step 1**: Open instance in Instance Explorer and expand

**Step 2**: Click **Assign Variables** button
```
┌─────────────────────┐
│ Assign Variables    │  ← Click
└─────────────────────┘
```

**Step 3**: Edit modal opens
```
┌─ Edit Variables ──────────────────────┐
│                                       │
│ orderId: [ORD-9912            ]       │
│ priority: [HIGH               ]       │
│ approved: [true          ] ⌄          │
│ amount: [1500.50         ]            │
│ items: [["SKU-001","SKU-002"]]        │
│                                       │
│ [Cancel] [Save]                       │
└───────────────────────────────────────┘
```

**Step 4**: Modify values
- Edit any variable value
- Add new variable (if UI supports)
- Delete variable (set to null)

**Step 5**: Click **Save**

**Step 6**: Success confirmation
```
Variables updated successfully
5 variables assigned
```

### JSON Format Rules

When entering variables as JSON:

```javascript
// Strings use quotes
"orderId": "ORD-12345"

// Numbers no quotes
"amount": 1500.50

// Booleans lowercase
"approved": true

// Arrays with brackets
"items": ["SKU-001", "SKU-002"]

// Objects with braces
"metadata": { "source": "web", "version": 2 }

// Null for empty
"notes": null
```

### Variable Lifecycle

```
Instance Created
    ↓
Variables Set (by process or API)
    ↓
Variables Read (by user tasks/system tasks)
    ↓
Variables Modified (by process logic or Admin UI)
    ↓
Variables Persist (across node transitions)
    ↓
Instance Deleted (variables cascade deleted)
```

### Common Variable Operations

**Update Single Variable**:
```json
{
  "variables": {
    "approved": true
  }
}
```
→ Only `approved` changes. Other variables unchanged.

**Add New Variable**:
```json
{
  "variables": {
    "newField": "value"
  }
}
```
→ Creates `newField` if it doesn't exist.

**Clear Variable (set to null)**:
```json
{
  "variables": {
    "notes": null
  }
}
```
→ Sets `notes` to null (unset state).

### Example: Updating Order Approval Status

**Scenario**: Manager approved order in external system. Update process instance to reflect approval.

**Steps**:
1. Open Instance Explorer
2. Find order instance (e.g., #1001)
3. Expand details
4. Click **Assign Variables**
5. Edit modal shows:
   ```
   approved: false
   ```
6. Change to:
   ```
   approved: true
   ```
7. Add new variable:
   ```
   approvedBy: "jane.doe"
   approvedAt: "2025-04-15T14:30:00Z"
   ```
8. Click **Save**
9. Variables persisted to backend
10. Process logic can now see `approved=true` and continue

---

## 6. Node Control: Manual Execution Redirection

### Purpose
Manually move process execution from one node to another for recovery, escalation, or testing.

### When to Use

:::caution Advanced Feature
Requires understanding of process model and state machine. Use only when necessary.
:::

✅ **Good Use Cases**:
- Process took wrong path due to a bug
- Wrong decision made, need to backtrack
- Manual escalation or approval bypass
- Skip long-running task that's no longer needed

❌ **Bad Use Cases**:
- Regular process operation (process should handle routing)
- Skip multiple nodes (use one transition at a time)
- Jump out of sequence (violates process semantics)

### Process Model Requirements

Your process must have nodes defined:

```
Order Fulfillment Process:
├── start (start node)
├── validate-order
├── user-review (if validation passes)
│   ├── approved
│   └── rejected
├── ship-order
└── end (end node)
```

### How to Move Execution

**Step 1**: Open instance in Instance Explorer and expand

**Step 2**: Scroll to **Node Control** section
```
┌─ Node Control ────────────────────┐
│ Move execution between nodes      │
└──────────────────────────────────┘
```

**Step 3**: Select source node
```
Current Node:
[Dropdown showing: user-review] ← Current location
```

**Step 4**: Select target node
```
Target Node:
[Dropdown showing available nodes]:
  ✓ approved
  ✓ rejected
  ✓ ship-order
  (cannot show start node, already passed)
```

**Step 5**: Add reason (optional)
```
Reason:
[Manager requested escalation to shipping]
```

**Step 6**: Click **Move**
```
┌────────────────────────────────┐
│ Move Execution?                │
│                                │
│ From: user-review              │
│ To: ship-order                 │
│ Reason: Manager escalation     │
│                                │
│ [Cancel] [Move]                │
└────────────────────────────────┘
```

**Step 7**: Confirmation
```
Execution moved successfully
Current Node: Ship Order
Updated at: 2025-04-15 14:35:00 UTC
Reason recorded in history
```

### What Happens During Move

1. ✅ Exit current node (trigger exit handlers if configured)
2. ✅ Jump to target node
3. ✅ Process continues normally from target
4. ✅ Node history updated with both nodes
5. ✅ Movement reason recorded (for audit trail)
6. ✅ Timestamp recorded

### Node History After Move

```
Before:
  1. start
  2. validate-order
  3. user-review ← CURRENT

After Move to ship-order:
  1. start
  2. validate-order
  3. user-review
  4. [MANUAL MOVE] → ship-order ← CURRENT
     (reason: Manager requested escalation)
```

### Backward Movement (Backtracking)

You can move to **earlier nodes** (backward in process):

```
Current: user-review → Move to: validate-order
```

Useful for:
- Re-validate data after changes
- Start over in validation phase
- Test code without full restart

### Example: Escalate Stuck Approval

**Scenario**: User approval task stuck for 2 days. Manager approves externally, needs to move execution past approval node.

**Steps**:
1. Open instance in Instance Explorer
2. Expand details
3. Verify current node is "user-review"
4. Scroll to Node Control
5. From Node: user-review (auto-selected)
6. To Node: Select "approved" (next node after content approval)
7. Reason: "Manager approved externally via email"
8. Click Move
9. Confirm dialog
10. Node moved to "approved"
11. Process continues with next step (shipping)

---

## 7. Task Management: View Assigned Work

### Purpose
Monitor and track tasks assigned to users.

### Task Statuses

| Status | Meaning | Action Needed |
|--------|---------|---------------|
| **PENDING** | Not yet started | User should work on it |
| **ASSIGNED** | Assigned to user | User acknowledged receipt |
| **IN_PROGRESS** | User currently working | Expected to complete soon |
| **COMPLETED** | User finished task | Task closed |

### How to View Tasks

**Method 1: Dashboard**

1. Open Dashboard
2. See "Pending Tasks" count: **128**
3. Click on count to navigate to Tasks view

**Method 2: Task Menu** (if separate Tasks section)

1. Click **Tasks** in sidebar
2. See full task list with filters

**Method 3: Instance Detail**

1. Open Instance Explorer
2. Expand instance
3. Scroll to **Assigned Tasks** section
4. See tasks for this instance

### Task Information Displayed

```
┌──────────────────────────────────────┐
│ Task #501: Review Order              │
├──────────────────────────────────────┤
│ Process: Order Fulfillment           │
│ Instance: #1001                      │
│ Status: PENDING                      │
│ Assigned to: john.doe                │
│ Node: user-review                    │
│ Created: 2025-04-15 10:35 UTC        │
│                                      │
│ Task Variables (Embedded Context):   │
│  orderId: "ORD-9912"                 │
│  amount: 1500.50                     │
│  priority: "HIGH"                    │
│  items: ["SKU-001", "SKU-002"]       │
│                                      │
│ Form: order-review-form              │
│                                      │
└──────────────────────────────────────┘
```

### Task Filters

**Filter by Status**:
```
[ ] PENDING [✓]
[ ] ASSIGNED [✓]
[ ] IN_PROGRESS [ ]
[ ] COMPLETED [ ]
```

**Filter by Assignee**:
```
Assigned to: [john.doe      ▼]
  Available:
  - john.doe
  - jane.doe
  - team-leads
  - unassigned
```

**Filter by Process**:
```
Process: [Order Fulfillment ▼]
  - All Processes
  - Order Fulfillment
  - Expense Approval
```

### Task Variables (Context)

Each task includes embedded variables for task-specific context:

```json
{
  "id": 501,
  "title": "Review Order",
  "variables": {
    "orderId": "ORD-9912",
    "amount": 1500.50,
    "items": ["SKU-001", "SKU-002"],
    "priority": "HIGH"
  }
}
```

This allows users to see relevant information without leaving Easy Admin.

### Task Lifecycle

```
Instance Created → Task Created (PENDING)
                        ↓
User Views Task (still PENDING)
                        ↓
User Starts Task (IN_PROGRESS)
                        ↓
User Completes Form
                        ↓
Task Completed → Process Continues
```

### Example: Monitor Pending Order Reviews

**Scenario**: Daily check of pending order review tasks.

**Steps**:
1. Open Dashboard
2. See "Pending Tasks: 23"
3. Click to view tasks
4. Filter by Status: PENDING, Process: Order Fulfillment
5. See 8 pending reviews
6. Identify which ones are high-priority (priority="HIGH")
7. Export list for team meeting
8. Assign unassigned tasks to available reviewers

---

## 8. Workflow Catalog: Browse Definitions

### Purpose
Discover and review deployed process definitions.

### What's Displayed

```
┌──────────────────────────────────────────┐
│ Process Definition #1                    │
│ Name: Order Fulfillment                  │
│ Key: order-fulfillment                   │
│ Version: 3 (latest)                      │
│ Description: Handle customer orders...   │
│ Deployed: 2025-04-10 08:00 UTC          │
│ Active Instances: 42                     │
│                                          │
│ [View Model] [View History] [Export]    │
└──────────────────────────────────────────┘
```

### Searching Definitions

**Search by Name**:
```
[Search: "Order"] → Shows "Order Fulfillment"
```

**Filter by Status**:
```
[Active Definitions] [Deprecated] [Draft]
```

### Version History

Click a definition to see version history:

```
Version 3 (current)
  Deployed: 2025-04-10 08:00 UTC
  Details: Added validation step

Version 2
  Deployed: 2025-03-15 09:30 UTC
  Details: Initial approval workflow

Version 1
  Deployed: 2025-03-01 14:00 UTC
  Details: Alpha release
```

### Model Visualization

Click **View Model** to see BPMN diagram (if configured):

```
     [Start]
       ↓
  [Validate Order]
     ↙   ↖
[Invalid] [Valid]
    ↓       ↓
  [Error] [User Review]
           ↙       ↖
       [Approved]  [Rejected]
           ↓           ↓
       [Ship]      [Cancel]
           ↓           ↓
        [End] ←────────┘
```

### Instance Count

Each definition shows count of:
- **Active Instances**: Currently running
- **Completed Instances**: Successfully finished
- **Failed Instances**: Error state

### Example: Check Definition Before Starting

**Scenario**: New user needs to understand Order Fulfillment process.

**Steps**:
1. Open Easy Admin
2. Click **Workflows** in sidebar
3. Search: "Order"
4. Click on "Order Fulfillment"
5. View definition details
6. Click **View Model** to see diagram
7. See it has 4 nodes: validate → review → ship → complete
8. Click **View History** to see recent deployments
9. Now understand process before managing instances

---

## 9. Settings & Configuration

### Purpose
Customize Easy Admin behavior and preferences.

### Settings Available

**API Configuration**:
```
API Endpoint: [http://localhost:8080    ]
Timeout (seconds): [30]
Retry on Failure: [✓]
```

**Display Preferences**:
```
Items per page: [20]
Auto-refresh: [✓] Every [30] seconds
Theme: [Light ▼]
Language: [English ▼]
```

**Notifications**:
```
[✓] High Priority Process Errors
[✓] Instance Stopped
[ ] Task Reassigned
[ ] Variable Changed
```

**Export**:
```
[Export as CSV] [Export as JSON]
```

### Changing Settings

1. Click **Settings** in sidebar
2. Modify values
3. Click **Save**
4. Confirmation: "Settings saved successfully"

---

## Feature Comparison Matrix

| Feature | View | Modify | Admin Only |
|---------|------|--------|-----------|
| Dashboard | ✓ | - | No |
| Instance List | ✓ | - | No |
| Instance Details | ✓ | - | No |
| Variables | ✓ | ✓ | No |
| Stop Instance | ✓ | ✓ | Yes |
| Delete Instance | ✓ | ✓ | Yes |
| Move Node | ✓ | ✓ | Yes |
| Tasks | ✓ | - | No |
| Workflows | ✓ | - | No |
| Settings | ✓ | ✓ | User |

---

**Last Updated**: April 2025 | **Version**: 1.0.0 | **Audience**: All Users
