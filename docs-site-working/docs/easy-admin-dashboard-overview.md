# Easy BPM Admin Dashboard Overview

## Introduction

The **Easy BPM Admin Dashboard** is a comprehensive operations console designed to provide real-time visibility into process execution health, metrics, incidents, and operational insights. Inspired by Camunda Operate, the dashboard enables operations teams to monitor, investigate, and resolve process execution issues efficiently.

### Purpose

The Admin Dashboard transforms Easy BPM Admin from a simple instance search tool into a sophisticated operational control center. It empowers operations teams to:

- 📊 **Monitor execution health** - Real-time metrics on running, completed, failed, and suspended instances
- 🔍 **Investigate incidents** - Drill down into failed processes with detailed error information
- 📈 **Track performance** - Monitor execution times, identify bottlenecks, and track SLA compliance
- 🎯 **Filter intelligently** - Advanced filtering by status, process, date range, nesting level, and assignee
- 📋 **View activity** - Comprehensive activity feed showing recent process events and state changes

---

## Dashboard Layout

### Main Dashboard Components

```
┌─────────────────────────────────────────────────────────┐
│ Easy BPM Admin > Dashboard                 [Refresh]    │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  Period: [24h] [7d] [30d] [Custom]                     │
│                                                           │
│  ┌──────────────┬──────────────┬──────────────┬────────┐ │
│  │ Total: 1,250 │ Running: 245 │ Completed:  │ Failed:│ │
│  │              │              │ 847         │ 98     │ │
│  └──────────────┴──────────────┴──────────────┴────────┘ │
│                                                           │
│  ┌──────────────┬──────────────┐                        │
│  │ Suspended:   │ Incidents:   │                        │
│  │ 60           │ 15           │                        │
│  └──────────────┴──────────────┘                        │
│                                                           │
├─────────────────────────────────────────────────────────┤
│ [Processes] [Incidents] [Activities] [Performance]      │
├─────────────────────────────────────────────────────────┤
│ Tab Content (processes list, incidents, etc.)           │
└─────────────────────────────────────────────────────────┘
```

### Section Details

#### 1. **Header & Navigation**
- Breadcrumb: "Admin > Dashboard"
- Refresh button to manually trigger metric updates
- Period selector to filter data by time range

#### 2. **Metric Cards**
Six metric cards display key execution statistics:

| Metric | Definition | Color |
|--------|-----------|-------|
| **Total** | Total number of started process instances | Blue |
| **Running** | Active instances currently executing | Green |
| **Completed** | Instances finished successfully | Gray |
| **Failed** | Instances with execution errors | Red |
| **Suspended** | Instances waiting for human input | Orange |
| **Incidents** | Failed instances requiring intervention | Dark Red |

**Card Layout**: 
- Desktop: 4 columns (2×3 grid)
- Tablet: 2 columns (3 rows)
- Mobile: 1 column (stacked)

#### 3. **Tabbed Views**

**Tab 1: Processes**
- List of all processes with execution statistics
- Shows: Total, Running, Completed, Failed, Suspended instances per process
- Search and filter by process name/ID
- Click process to view instance list

**Tab 2: Incidents**
- Dedicated incident management view
- Failed and suspended instances requiring action
- Shows: Instance ID, Error Message, Last Updated, Retry Count
- Actions: View Details, Retry, Escalate, Delete
- Bulk actions supported

**Tab 3: Activities**
- Recent process events and state changes
- Shows: Timestamp, Event Type, Process ID, Instance ID, Details
- Pagination: 50 items per page
- Time-based sorting

**Tab 4: Performance**
- Execution time analytics
- Charts: Execution time trends, SLA compliance
- Shows: Avg, Median, P95, P99 execution times
- Activity feed with per-process performance

#### 4. **Responsive Design**
- **Mobile (≤480px)**: Single column, stacked cards, compact tables
- **Tablet (768-1024px)**: Two column cards, responsive filters
- **Desktop (≥1280px)**: Full 4-column layout with side-by-side views

---

## Metric Definitions

### Total Instances
**Definition**: The cumulative count of all process instances started within the selected time period.

**Calculation**: 
```sql
SELECT COUNT(*) FROM process_instance 
WHERE created_at >= start_date AND created_at <= end_date
```

**Use Case**: Understand process execution volume and throughput.

### Running Instances
**Definition**: Active instances currently executing (status = RUNNING).

**Calculation**: 
```sql
SELECT COUNT(*) FROM process_instance 
WHERE status = 'RUNNING'
```

**Use Case**: Monitor current workload and active process execution.

### Completed Instances
**Definition**: Instances that finished successfully (status = COMPLETED).

**Calculation**: 
```sql
SELECT COUNT(*) FROM process_instance 
WHERE status = 'COMPLETED'
```

**Use Case**: Track successful process execution rate.

### Failed Instances
**Definition**: Instances with execution errors (status = FAILED).

**Calculation**: 
```sql
SELECT COUNT(*) FROM process_instance 
WHERE status = 'FAILED'
```

**Use Case**: Identify process errors requiring attention.

### Suspended Instances
**Definition**: Instances waiting for human input (status = WAITING).

**Calculation**: 
```sql
SELECT COUNT(*) FROM process_instance 
WHERE status = 'WAITING'
```

**Use Case**: Monitor pending human tasks and work backlogs.

### Incidents
**Definition**: Failed instances or instances with errors requiring operational intervention.

**Calculation**: 
```sql
SELECT COUNT(*) FROM process_instance 
WHERE status = 'FAILED' OR status = 'SUSPENDED' AND error_message IS NOT NULL
```

**Use Case**: Focus operations team on critical issues.

---

## Period Selector

The period selector allows filtering metrics by time range:

### Available Periods
- **24h** (Last 24 hours) - Default view
- **7d** (Last 7 days)
- **30d** (Last 30 days)
- **Custom** (Select date range)

### How It Works
1. Click a period button to apply instant filter
2. All metric cards update automatically
3. Process list and incidents filter by date
4. Performance analytics use the selected period

### Example Use Cases

**24h Period**: 
- Daily standup - "How many processes executed yesterday?"
- Incident response - "What failed in the last 24 hours?"

**7d Period**:
- Weekly review - "What's the execution trend this week?"
- SLA tracking - "Are we meeting SLA targets?"

**30d Period**:
- Monthly metrics - "Process volume and success rate?"
- Trend analysis - "Is performance improving?"

**Custom Period**:
- Historical analysis - "Compare Q1 vs Q2?"
- Specific incident window - "What happened between 2pm-4pm?"

---

## Real-time Updates & Polling

### How Updates Work

The dashboard automatically fetches fresh metrics every **30 seconds**:

1. Timer starts when dashboard loads
2. Every 30 seconds, metrics endpoint is called
3. Metric cards update if values changed
4. Process list refreshes if new instances detected
5. No page reload required

### Manual Refresh

Click the **Refresh** button (⟳) in the header to immediately fetch latest data.

### Polling Interval

- **30 seconds** (default) - Balances freshness and server load
- Can be adjusted in dashboard settings (future feature)
- Stops when dashboard tab is inactive (browser feature)

---

## Filtering & Search

### Quick Filters

Located above the table view:

- **Status**: Filter by Running, Completed, Failed, Waiting
- **Process**: Select specific process or all processes
- **Date Range**: Use period selector above (24h, 7d, 30d)
- **Nesting Level**: Parent instances only, children only, all

### Search Box

Type in the search box to find:
- Instance ID (exact match)
- Process name (substring match)
- Process ID (exact match)

### Advanced Filtering

Combine multiple filters:
1. Select status: "Failed"
2. Select process: "InvoiceProcessing"
3. Select date range: "Last 7 days"
4. Result: Failed invoice processing instances from last 7 days

---

## Incident Management

### What Is an Incident?

An incident is a process instance that requires operational intervention:

- ❌ **Failed**: Execution errors, unhandled exceptions
- ⏸️ **Suspended with Error**: Human task timeout, external service failure
- ⚠️ **Long Running**: Instance exceeding SLA time
- 🔄 **Retry Needed**: Failed instance ready for retry

### Incident View

The **Incidents** tab shows all active incidents:

| Column | Description |
|--------|-------------|
| Instance ID | Unique instance identifier |
| Process Name | Name of the process |
| Error Message | Brief error description |
| Last Updated | Timestamp of last status change |
| Retry Count | Number of automatic/manual retries |
| Actions | View, Retry, Escalate, Delete |

### Incident Actions

**View Details**
- Opens detailed incident view
- Shows process variables at failure point
- Displays full error stack trace
- Shows execution history leading to failure

**Retry**
- Restarts failed instance from beginning
- Increments retry counter
- Useful for transient failures (network, timeout)

**Escalate**
- Marks incident as high priority
- Sends notification to manager
- Useful for critical process failures

**Delete**
- Removes incident from view
- Marks as resolved manually
- Use when issue investigated and root cause documented

---

## Best Practices

### Daily Operations

1. **Morning Standup** (9am)
   - Check dashboard for overnight incidents
   - Review last 24 hours metrics
   - Address critical failures

2. **Mid-Day Check** (12pm)
   - Monitor current running instances
   - Ensure no performance degradation
   - Check incident count

3. **End of Day** (5pm)
   - Review daily completion rate
   - Archive resolved incidents
   - Document any recurring failures

### Incident Investigation

1. **View incident** in incidents tab
2. **Read error message** for quick diagnosis
3. **Click "View Details"** to see process state
4. **Review process variables** at failure point
5. **Check execution history** for pattern
6. **Retry or escalate** based on root cause

### Performance Monitoring

1. **Check execution trends** every 7 days
2. **Identify slow processes** using performance tab
3. **Set SLA targets** for critical processes
4. **Review activity feed** for bottlenecks
5. **Document improvements** for process optimization

### Filtering Best Practices

- **Use status filters** to focus on specific issues
- **Combine process + date filters** for targeted analysis
- **Use nesting level filter** for subprocess troubleshooting
- **Save custom filter views** (future feature)

---

## Integration Points

The Admin Dashboard integrates with other Easy BPM features:

- **Task Portal**: View human task details from dashboard
- **Process Modeler**: Jump to process definition from incident view
- **API Integration**: All dashboard data available via REST API
- **Audit Log**: All dashboard actions logged for compliance

---

## Performance Characteristics

### Load Times

- **Dashboard load**: < 2 seconds with 10k instances
- **Metric aggregation**: < 500ms
- **Filter response**: < 500ms
- **Table pagination**: < 1 second for 1000 rows

### Resource Usage

- **Memory**: ~50MB for dashboard with 10k instances
- **Network**: ~100KB per dashboard load
- **Database**: Indexed queries, optimized for performance
- **Polling**: ~10KB per 30-second update cycle

---

## What's Next?

- 📱 **Mobile Dashboard** - Dedicated mobile interface (Phase 10)
- 🔔 **Real-time Alerts** - WebSocket-based incident notifications (Phase 10)
- 📊 **Custom Reports** - Export metrics and analytics (Phase 11)
- 🔐 **Advanced Security** - RBAC and audit trails (Phase 11)
- 🤖 **AI-Powered Insights** - Anomaly detection and recommendations (Phase 12)

---

## Getting Help

- See [Dashboard Quick Start Guide](./easy-admin-dashboard-getting-started.md) for first-time setup
- See [Filtering Guide](./easy-admin-dashboard-filtering.md) for advanced filtering
- See [Incident Management Guide](./easy-admin-dashboard-incidents.md) for troubleshooting
- See [Performance Guide](./easy-admin-dashboard-performance.md) for analytics
- See [Troubleshooting Guide](./easy-admin-dashboard-troubleshooting.md) for common issues
- See [Accessibility Guide](./easy-admin-dashboard-accessibility.md) for keyboard shortcuts
