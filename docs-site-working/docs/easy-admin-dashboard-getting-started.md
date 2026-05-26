# Easy BPM Admin Dashboard: Quick Start Guide

## Getting Started in 5 Minutes

Welcome to the Easy BPM Admin Dashboard! This guide walks you through accessing the dashboard and understanding the basic concepts.

### Prerequisites

- Access to Easy BPM Admin UI (usually http://localhost:5173 or your deployment URL)
- Admin or Operator role
- Basic understanding of BPMN processes

---

## Step 1: Access the Dashboard

### Navigate to Dashboard

1. Open Easy BPM Admin in your browser
2. Log in with your credentials (if authentication is enabled)
3. Click **"Dashboard"** in the sidebar menu
4. You're now on the Admin Execution Dashboard!

### Alternative URL Access

If sidebar link not visible:
- Navigate directly to: `http://your-bpm-url/dashboard`
- Or use browser navigation: Admin → Dashboard

---

## Step 2: Understanding the Metric Cards

### What You See

At the top of the dashboard, you'll see six metric cards:

```
┌─────────────────────────────────────────────┐
│ Total        Running      Completed  Failed │
│ 1,250        245          847        98     │
│                                              │
│ Suspended    Incidents                      │
│ 60           15                             │
└─────────────────────────────────────────────┘
```

### What Each Card Means

| Card | Meaning | Example |
|------|---------|---------|
| **Total** | How many processes started | 1,250 total processes this week |
| **Running** | Processes currently executing | 245 are running right now |
| **Completed** | Processes finished successfully | 847 finished successfully |
| **Failed** | Processes with errors | 98 failed with errors |
| **Suspended** | Waiting for human input | 60 are waiting for human tasks |
| **Incidents** | Issues needing attention | 15 incidents need investigation |

### Reading the Cards

- **Blue cards** = Healthy, informational
- **Green cards** = Good, processes running normally
- **Gray cards** = Completed, finished successfully
- **Red cards** = Problems, need attention
- **Orange cards** = Waiting, human action needed

---

## Step 3: Change the Time Period

### Period Selector

At the top-left, you'll see period buttons:

```
Period: [24h] [7d] [30d] [Custom]
```

### How to Use

1. Click **24h** to see last 24 hours (default)
2. Click **7d** to see last 7 days
3. Click **30d** to see last 30 days
4. Click **Custom** to pick specific dates

### What Happens

- All metrics update immediately
- Process list filters to selected period
- Charts/graphs update to new time range
- No page reload needed

### Common Use Cases

**Monday 9am?** → Click **7d** to see week overview
**After incident?** → Click **Custom** to zoom on incident time
**Weekly meeting?** → Click **7d** for discussion data
**Night shift?** → Click **24h** for overnight activity

---

## Step 4: Explore the Tabs

Below the metric cards, you'll see tabs:

```
[Processes] [Incidents] [Activities] [Performance]
```

### Tab 1: Processes

Shows all processes with their statistics:

**What you see**:
- Process name
- Total instances this period
- Running, Completed, Failed counts
- Success rate percentage

**How to use**:
1. Click process name to see instances
2. Click status badge to filter by status
3. Search for specific process name

**Example**: Click "InvoiceProcessing" to see all invoice processes

---

### Tab 2: Incidents

Shows problems that need fixing:

**What you see**:
- Failed or suspended instances
- Error messages
- When it happened
- Retry count

**How to use**:
1. Read error message for quick diagnosis
2. Click "View Details" to investigate
3. Click "Retry" to run again
4. Click "Delete" when resolved

**Example**: See "Database connection timeout" error, retry incident, watch it succeed

---

### Tab 3: Activities

Shows recent events and changes:

**What you see**:
- What happened (started, completed, failed)
- When it happened (timestamp)
- Which process and instance
- Details about the event

**How to use**:
1. Scroll through recent activity
2. Identify patterns ("All failed at 3pm?")
3. Click instance to drill down

**Example**: See pattern of failures, notice they all happen at midnight backup time

---

### Tab 4: Performance

Shows execution speed and efficiency:

**What you see**:
- Average execution time
- Slowest processes (P95, P99)
- SLA compliance status
- Performance trends

**How to use**:
1. Find which processes are slow
2. Check if performance improving/degrading
3. Set SLA targets for critical processes

**Example**: "Approval workflow is slow" → Find average is 4 hours, set SLA to 2 hours

---

## Step 5: Common Tasks

### Task 1: Find Failed Process

**Goal**: A customer reports their order failed to process. Find why.

**Steps**:
1. Click **Incidents** tab
2. Find the failed order process
3. Read the error message
4. Click **View Details** for full error trace
5. Click **Retry** to run again

**Result**: You see error was "Address validation failed", fix in master data, retry succeeds

---

### Task 2: Check Process Health

**Goal**: See how well a specific process is running.

**Steps**:
1. Click **Processes** tab
2. Find "InvoiceProcessing" or your process
3. Look at: Total runs, success rate, failures
4. Click to see instance list
5. If many failures, check recent incidents

**Result**: "InvoiceProcessing ran 100 times this week, 98 succeeded (98% success rate), 2 failed"

---

### Task 3: Investigate Slow Process

**Goal**: A process is taking too long. Find out why.

**Steps**:
1. Click **Performance** tab
2. Find slowest processes in chart
3. Look at average execution time
4. Click process to see instances
5. Find slow instances and check what nodes took longest

**Result**: "Approval workflow averages 4 hours, bottleneck is manager approval step"

---

### Task 4: Refresh Dashboard

**Goal**: Get latest data immediately.

**Steps**:
1. Look at top-right of dashboard
2. Click the **⟳ Refresh** button
3. Wait 1-2 seconds for update

**Result**: Metrics update with fresh data, shows any new incidents

---

## Step 6: Understanding Status Colors

Throughout the dashboard, you'll see status colors:

| Color | Status | Meaning |
|-------|--------|---------|
| 🟢 Green | RUNNING | Executing now |
| 🟢 Green | COMPLETED | Finished successfully |
| 🔴 Red | FAILED | Error occurred |
| 🟠 Orange | WAITING | Waiting for human action |
| ⚫ Gray | SUSPENDED | Paused or waiting |

---

## Step 7: Dashboard Settings

### Change Time Period Permanently

Click **Custom** → Set your preferred default date range in settings

### Set Metric Refresh Rate

Go to **Settings** → **Dashboard** → Set refresh interval (default 30 seconds)

### Choose Sidebar Visibility

Click **≡ Menu** to toggle sidebar on/off

---

## Tips & Tricks

### 💡 Quick Tips

1. **Use search box** to find specific instance ID quickly
2. **Click metric cards** to filter to that status automatically
3. **Use 7d view** for weekly reviews and meetings
4. **Save incident URLs** for documentation and follow-up
5. **Bookmark the dashboard** for easy access

### ⌨️ Keyboard Shortcuts

- **Tab** → Navigate between elements
- **Enter** → Click focused button/link
- **Escape** → Close modals/dropdowns
- **Ctrl+F** → Search page content

### 🖱️ Mouse Tips

- **Hover over metrics** to see exact counts
- **Right-click instance** to copy ID
- **Double-click to select** and copy text
- **Scroll horizontally** on mobile for full table

---

## Common Questions

### Q: How often does the dashboard update?

**A**: Automatically every 30 seconds via polling. Click Refresh button for immediate update.

---

### Q: Why are my metrics different than other systems?

**A**: Dashboard shows Easy BPM data only. Check that time period matches. Different systems may have different time zones.

---

### Q: Can I export the dashboard data?

**A**: Currently dashboard displays only. Use API endpoints to get raw data for export (see documentation).

---

### Q: What if a process is stuck?

**A**: Click **Incidents** tab, find the stuck instance, click **View Details** to see where it's stuck, then contact the process owner for remediation.

---

### Q: How do I know if SLAs are being met?

**A**: Click **Performance** tab to see SLA monitoring section. Red bars = SLA violated.

---

### Q: Can I see historical data from last month?

**A**: Click **Custom** period selector, choose date range from last month to current, then view data.

---

## Next Steps

- Learn [advanced filtering techniques](./easy-admin-dashboard-filtering.md)
- Master [incident investigation](./easy-admin-dashboard-incidents.md)
- Understand [performance analytics](./easy-admin-dashboard-performance.md)
- Troubleshoot [common issues](./easy-admin-dashboard-troubleshooting.md)
- Use [keyboard shortcuts and accessibility features](./easy-admin-dashboard-accessibility.md)

---

## Support

- **Questions?** Check the main [Dashboard Overview](./easy-admin-dashboard-overview.md)
- **Technical Issues?** See [Troubleshooting Guide](./easy-admin-dashboard-troubleshooting.md)
- **Need Help?** Contact your BPM administrator or support team

---

**Congratulations!** You now understand the Easy BPM Admin Dashboard basics. Happy process monitoring! 🚀
