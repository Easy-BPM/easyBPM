# Easy BPM Admin Dashboard: Advanced Filtering Guide

## Overview

The filtering system allows you to focus on specific process instances based on multiple criteria. Combine filters to answer specific questions about your process execution.

---

## Filter Types

### 1. Status Filter

Filter instances by their current status.

#### Status Values

| Status | Description | Icon | Use Case |
|--------|-------------|------|----------|
| **RUNNING** | Currently executing | 🟢 | Monitor active work |
| **COMPLETED** | Finished successfully | ✓ | Verify successful executions |
| **FAILED** | Execution error | ❌ | Find and fix problems |
| **WAITING** | Awaiting human action | ⏸️ | Find pending human tasks |
| **SUSPENDED** | Paused, not executing | ⊙ | Find stuck instances |

#### How to Use

1. Click **Status** filter dropdown
2. Check boxes for statuses you want (multiple select)
3. Results update instantly
4. Click **X** on status button to remove filter

#### Examples

**Find all failed processes:**
- Status: Failed
- Result: 98 instances showing only failures

**Find processes needing attention:**
- Status: Failed, Waiting (select both)
- Result: 158 instances with failures or pending tasks

**Find successfully completed:**
- Status: Completed
- Result: 847 instances that finished successfully

---

### 2. Process Filter

Filter by specific process definition.

#### How to Use

1. Click **Process** dropdown
2. Type process name to search (auto-complete)
3. Select process(es) from list
4. Multiple processes can be selected
5. Click **X** to remove process from filter

#### Examples

**Find all InvoiceProcessing instances:**
- Process: InvoiceProcessing
- Status: (any)
- Result: 145 instances

**Find all failures across two processes:**
- Process: InvoiceProcessing, OrderProcessing
- Status: Failed
- Result: 23 instances from two processes

**Find specific process running now:**
- Process: ApprovalWorkflow
- Status: Running
- Result: 12 approval workflow instances currently executing

---

### 3. Date Range Filter

Filter by when instance started.

#### Quick Period Buttons

Located at top of dashboard:

```
Period: [24h] [7d] [30d] [Custom]
```

**Built-in Periods:**
- **24h** = Last 24 hours (default)
- **7d** = Last 7 days
- **30d** = Last 30 days
- **Custom** = Pick exact date range

#### Custom Date Range

1. Click **Custom** button
2. Select start date (calendar picker)
3. Select end date (calendar picker)
4. Click **Apply**
5. Results filter to date range

#### Examples

**Find today's activity:**
- Period: 24h
- Result: All instances started in last 24 hours

**Find last week's instances:**
- Period: 7d
- Result: All instances from last 7 days

**Find specific incident window:**
- Period: Custom (May 20, 2:00 PM - May 20, 4:00 PM)
- Result: Only instances in that 2-hour window

**Find month-to-date metrics:**
- Period: Custom (May 1 - May 31)
- Result: All May activity

---

### 4. Nesting Level Filter

Filter by process hierarchy (parent vs. child instances).

#### Nesting Levels

| Level | Description | Use Case |
|-------|-------------|----------|
| **All** | Parent and child instances | General view |
| **Parent Only** | Top-level instances only | Find main process invocations |
| **Children Only** | Subprocess instances only | Troubleshoot subprocess issues |

#### How to Use

1. Click **Nesting Level** filter
2. Select: All, Parent Only, or Children Only
3. Results update instantly
4. Click to change selection

#### Examples

**Find main process invocations:**
- Nesting Level: Parent Only
- Status: Failed
- Result: 5 parent processes failed (not counting their subprocesses)

**Find subprocess issues:**
- Nesting Level: Children Only
- Process: SharedSubprocess
- Status: Failed
- Result: 2 subprocess instances failed

**Understand parent-child relationships:**
- Start with Parent Only to see main process
- Then switch to Children Only to see subprocesses
- Then check Call Activity details for input/output mapping

---

### 5. Assignee Filter

Filter human tasks by assignee.

#### How to Use

1. Click **Assignee** dropdown
2. Type user name or email to search
3. Select assignee(s) from list
4. Multiple assignees can be selected
5. Shows instances with tasks assigned to that person

#### Examples

**Find John's pending tasks:**
- Assignee: John Smith
- Status: Waiting
- Result: 12 instances waiting for John's action

**Find team's backlog:**
- Assignee: Team A (select multiple team members)
- Status: Waiting
- Result: 45 instances assigned to Team A

**Find unassigned tasks:**
- Assignee: (empty/null)
- Status: Waiting
- Result: 3 instances with no assigned owner

---

## Combining Filters

### Filter Combinations

Create powerful queries by combining multiple filters:

#### Example 1: Overnight Failures
**Goal**: Find failures that happened overnight

```
Period:  Custom (11pm - 7am)
Status:  Failed
Process: All
Result:  8 instances failed overnight, all in invoice processing
```

**Action**: Check if batch job issue at midnight

---

#### Example 2: Team Workload
**Goal**: What's the pending work for approval team?

```
Assignee:     Approval Team (3 users)
Status:       Waiting
Nesting Level: Parent Only
Process:      All
Result:       28 parent approval workflows waiting
```

**Action**: Distribute workload among team

---

#### Example 3: Specific Process Failures This Week
**Goal**: How many OrderProcessing failed this week?

```
Process:  OrderProcessing
Status:   Failed
Period:   7d
Result:   12 order processing failures, 2.4% failure rate
```

**Action**: Review error messages, fix root cause

---

#### Example 4: SLA Violations
**Goal**: Which instances took longer than expected?

```
Period:       7d
Status:       Completed
Nesting Level: Parent Only
Result:       Check Performance tab for execution time analysis
```

**Action**: See which took > 4 hours (exceeded SLA)

---

## Advanced Filtering Techniques

### Technique 1: Progressive Filtering

Start broad, then narrow down:

1. **Start**: View all instances (no filters)
2. **Narrow**: Filter by process
3. **Focus**: Filter by status
4. **Deep dive**: Filter by date range
5. **Action**: Click instance to see details

### Technique 2: Status Drill-Down

Understand your full status distribution:

1. Start with **Status: All** (implicit) to see all
2. Click on metric card (e.g., "Failed") to auto-filter
3. Review results
4. Remove filter and click next status
5. Compare patterns across statuses

### Technique 3: Time-Based Investigation

Find when problems occur:

1. Start with **Period: 24h**
2. Look for spikes or patterns
3. If spike found, click **Custom** period
4. Narrow date range to spike window
5. Filter by status to focus on problems
6. Result: Isolated incident time window

### Technique 4: Process Performance Comparison

Compare processes side-by-side:

1. Filter to **Process: Process A**
2. Note: Success rate, failure count
3. Clear process filter
4. Filter to **Process: Process B**
5. Compare metrics
6. Identify which process needs improvement

---

## Filter Controls

### Filter Buttons

At the top of table/list, you'll see active filters as buttons:

```
[Process: InvoiceProcessing] [Status: Failed] [7d] [X]
```

**Click X** to remove individual filter

**Click "Clear All"** to remove all filters at once

### Filter Persistence

- Filters **do not persist** after page refresh (intentional)
- Use bookmarks to save URLs with filter combinations
- Example: `http://localhost:5173/dashboard?status=FAILED&process=InvoiceProcessing`

### Mobile Filtering

On mobile devices:
1. Tap **≡ Filters** button to show filter panel
2. Filters appear in side drawer
3. Tap again to collapse/expand
4. Results update instantly

---

## Filter Limitations & Performance

### Performance Considerations

| Scenario | Time | Notes |
|----------|------|-------|
| Filter 1000 instances | < 100ms | Instant |
| Filter 10,000 instances | < 500ms | Very fast |
| Filter 100,000 instances | < 2s | Still responsive |
| Complex filter (3+ criteria) | < 500ms | Server optimized |

### Known Limitations

1. **AND logic only** - Filters combine with AND (all must match)
2. **No OR logic** - Cannot do "Status A OR Status B" (use multi-select)
3. **Text search** - Limited to exact process name (uses auto-complete)
4. **Date ranges** - Must be within available data (past 90 days)

### Tips for Best Performance

- Use specific **Process** filter when possible
- Combine with **Status** filter to reduce result set
- Use **7d or Custom** date range instead of 30d when possible
- Avoid filtering on empty/rare statuses

---

## Common Filter Patterns

### Pattern 1: Daily Incident Review
```
Period:  24h
Status:  Failed
Process: All
Action:  Review each incident, retry or escalate
```

### Pattern 2: Weekly Performance Audit
```
Period:       7d
Status:       Completed
Nesting Level: Parent Only
Action:       Check Performance tab for trends
```

### Pattern 3: Team Standup
```
Status:   Waiting
Assignee: Team (multiple users)
Period:   24h
Action:   Review pending work, update status
```

### Pattern 4: Process Health Check
```
Process: [Specific Process]
Period:  7d
Status:  All
Action:  Review completion rate, errors, performance
```

### Pattern 5: Subprocess Troubleshooting
```
Process:       [Parent Process]
Nesting Level: Children Only
Status:        Failed
Action:        Check Call Activity inputs/outputs
```

---

## Filter Examples by Role

### For Operations Manager

**Daily Standup:**
- Period: 24h
- Status: Failed, Waiting
- Shows: Overnight incidents and pending work

**Weekly Metrics:**
- Period: 7d
- Process: All
- Shows: Weekly volume, completion rate, failures

---

### For Process Owner

**Process Health:**
- Period: 7d
- Process: [Your Process]
- Status: Failed
- Shows: Failures to fix

**Performance Tracking:**
- Period: 7d
- Process: [Your Process]
- Shows: Execution times, SLA compliance

---

### For Incident Response Team

**Active Incidents:**
- Status: Failed
- Period: 24h
- Nesting Level: Parent Only
- Shows: Parent-level failures to fix

**Follow-up on Retries:**
- Process: [Specific]
- Period: Custom (last 2 hours)
- Shows: Did retries succeed?

---

## Troubleshooting Filters

### No Results Showing?

1. Check date range - is it within data available?
2. Check process name - is name spelled correctly?
3. Try removing one filter at a time
4. Click **Clear All** and start fresh

### Getting Too Many Results?

1. Add more filters (process, status, assignee)
2. Narrow date range (24h instead of 30d)
3. Filter to parent level only
4. Check if sorting options available

### Filter Not Updating Results?

1. Click **Refresh** button (⟳) at top
2. Wait 1-2 seconds for update
3. Try removing filter and re-adding
4. Check browser console for errors (F12)

---

## Next Steps

- Learn [incident investigation](./easy-admin-dashboard-incidents.md)
- Understand [performance analytics](./easy-admin-dashboard-performance.md)
- See [common workflows](./easy-admin-dashboard-troubleshooting.md)
- Use [keyboard shortcuts](./easy-admin-dashboard-accessibility.md)

---

**Pro Tip**: Combine filters with URL bookmarks for quick access to your most-used views!
