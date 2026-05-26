# Easy BPM Admin Dashboard: Incident Management Guide

## What Is an Incident?

An **incident** is a process instance that requires operational intervention:

- ❌ **Failed Instance**: Stopped with an unhandled error or exception
- ⏸️ **Suspended with Error**: Waiting for human action but error detected
- ⚠️ **Long-Running Instance**: Exceeds SLA time limit
- 🔄 **Retry Needed**: Failed instance ready for retry attempt
- ⚡ **Critical Path Failure**: High-priority process stopped

**Key Point**: Not all failed instances become incidents. Only those requiring manual action are marked as incidents.

---

## Failed Status vs. Incident: Key Difference

This is an important distinction that often causes confusion:

### Failed Status

**Definition**: An instance status indicating execution error or completion with failure.

**Technical**: Instance has `status = FAILED` in database.

**When It Occurs**:
- Unhandled exception in process node
- External service returns error (HTTP 5xx, timeout)
- Database error or connection failure
- Logic error in process definition
- Data validation error

**Automatic Handling**: System marks as FAILED, no manual action required if:
- Process completes with failure (terminal state)
- Auto-retry is disabled
- Error is logged and monitored

**Example**:
```
Process: OrderProcessing
Instance: ORD-042-2026
Error: "HTTP 503 - Service Temporarily Unavailable"
Status: FAILED
Incident: YES (needs manual action)
```

### Incident

**Definition**: A process instance requiring operational intervention and manual action.

**Technical**: Subset of failed/suspended instances flagged for attention.

**When It Occurs**:
- Instance has FAILED status + needs investigation
- Instance has WAITING status + error detected
- Instance exceeded SLA time limit
- Auto-retry exhausted (tried 3 times, still failing)
- Critical business process stopped

**Requires Manual Action**:
- Investigate root cause
- Determine fix (retry, escalate, resolve manually)
- Monitor retry attempt
- Document resolution

**Example**:
```
Process: OrderProcessing
Instance: ORD-042-2026
Status: FAILED
Incident: YES ← Flagged because:
  - Retry needed (transient service error)
  - Affects customer order
  - Requires manual decision
```

### Key Differences in Table

| Aspect | Failed Status | Incident |
|--------|---------------|----------|
| **Definition** | Instance execution error | Instance requiring action |
| **Scope** | All failed instances | Subset of failed/suspended |
| **Automatic** | Yes, system marks automatically | No, manually flagged or auto-escalated |
| **Examples** | 1,000 failed instances | 50 incidents requiring investigation |
| **Action** | Can be ignored (logged) | Must be addressed |
| **Dashboard Card** | "Failed: 98" (metric) | "Incidents: 15" (actionable items) |

### Real-World Scenario

```
📊 Dashboard Metrics:
┌──────────────────────────────────────────┐
│ Failed: 100    Incidents: 5              │
└──────────────────────────────────────────┘

Analysis:
- 100 instances failed (status = FAILED)
- 95 failed due to transient network timeout (auto-recovered after retry)
- 5 failed due to data errors (need manual fix)
- 5 incidents appear in Incidents tab (those needing action)

Operations Team Action:
- 95 failed but resolved automatically → No action needed
- 5 incidents remain → Investigate and fix data issues
```

### When to Use Each Term

**Use "Failed"** when talking about:
- Technical metrics and statistics
- System performance
- Failure rate calculations
- Database queries (status = 'FAILED')

**Use "Incident"** when talking about:
- Operational action items
- Investigation and resolution
- Manual interventions needed
- Dashboard incident view

### Dashboard Indicators

**Failed Card**:
```
Failed: 98

Meaning: 98 instances have status = FAILED
Action: No action required (informational)
Context: Historical metric, some may be auto-resolved
```

**Incidents Card**:
```
Incidents: 15

Meaning: 15 instances flagged as incidents
Action: Review immediately (actionable)
Context: These require manual investigation/intervention
```

---

## Incident Lifecycle

```
┌─────────────────────────────────────────┐
│ Instance Starts Executing               │
└────────────┬────────────────────────────┘
             │
             ├─ ✓ Completes Successfully
             │   └─ No Incident
             │
             └─ ❌ Error Occurs
                 └─ Incident Created
                    ├─ Auto-retry (if configured)
                    │   ├─ Success → Resolved
                    │   └─ Failed → Manual action needed
                    │
                    └─ Manual Action
                        ├─ Investigate
                        ├─ Fix root cause
                        └─ Retry → Success
```

---

## Incident View

### Access Incidents

Click the **Incidents** tab on the dashboard:

```
[Processes] [Incidents] [Activities] [Performance]
                  ↑
              Click here
```

### Incident List Columns

| Column | Description |
|--------|-------------|
| **ID** | Unique process instance identifier |
| **Process** | Process name |
| **Status** | Current status (Failed, Suspended, etc.) |
| **Error** | Brief error message (first 100 chars) |
| **Started** | When instance started |
| **Updated** | Last status change time |
| **Retries** | How many retry attempts made |
| **Actions** | View, Retry, Escalate, Delete |

### Incident List Example

```
ID              Process           Status   Error                Updated    Retries  Actions
INV-001-2026    InvoiceProc       FAILED   DB connection timed  2m ago     1        [View] [Retry] [Escalate] [Delete]
ORD-042-2026    OrderProcessing   FAILED   Tax service 404      15m ago    0        [View] [Retry] [Escalate] [Delete]
APR-089-2026    ApprovalWF        WAITING  Manager absent       1h ago     0        [View] [Reassign] [Escalate] [Delete]
```

---

## Investigating an Incident

### Step 1: Read the Error Message

The **Error** column shows brief error summary:

**Common Error Messages:**

| Message | Meaning | Fix |
|---------|---------|-----|
| `DB connection timed out` | Database unreachable | Check DB server, restart connection pool |
| `HTTP 503 External Service` | External API down | Wait for service recovery, retry |
| `Cannot parse JSON response` | API returned invalid data | Check API documentation, file support ticket |
| `Timeout after 30s` | Operation took too long | Check if service slow, increase timeout |
| `NullPointerException at line 42` | Code error | Check process definition, contact developer |

### Step 2: Click "View Details"

Opens detailed incident investigation view:

```
┌─────────────────────────────────────┐
│ Incident Details: INV-001-2026      │
├─────────────────────────────────────┤
│ Process:    InvoiceProcessing       │
│ Instance:   INV-001-2026            │
│ Status:     FAILED                  │
│ Started:    May 26, 2:15 PM         │
│ Failed:     May 26, 2:47 PM         │
│ Duration:   32 minutes              │
│                                     │
│ Error Message:                      │
│ Connection timeout to database      │
│ (full stack trace below)            │
├─────────────────────────────────────┤
│ Process Variables at Failure:       │
│ invoiceId: INV-12345                │
│ amount: $5,000                      │
│ vendor: ACME Corp                   │
├─────────────────────────────────────┤
│ Execution History:                  │
│ 1. Start                            │
│ 2. ValidateInvoice        ✓ OK     │
│ 3. SubmitForApproval      ✓ OK     │
│ 4. FetchExchangeRate      ❌ FAILED│
│ (not executed): CheckForDuplicates │
│ (not executed): CreatePaymentOrder │
└─────────────────────────────────────┘
```

### Step 3: Analyze the Context

**What to Look For:**

1. **Process Variables**: What data was the instance processing?
   - Helps identify if issue is data-specific
   - Example: "Only high-value invoices fail?"

2. **Execution History**: Which node failed?
   - Shows how far process got
   - Shows skipped nodes
   - Example: "Failed at FetchExchangeRate node"

3. **Error Message**: What was the root cause?
   - Full stack trace provided
   - System vs. process logic errors
   - Example: "Database connection timeout"

4. **Timing**: When did it happen?
   - Helps identify correlations
   - Example: "Failed during database backup?"

### Step 4: Determine Root Cause

Ask yourself:

**Is it a transient error?**
- Network timeout
- Temporary service outage
- Resource unavailable
- **Action**: Retry - usually works after retry

**Is it a data error?**
- Invalid input data
- Missing required field
- Malformed data format
- **Action**: Fix data in master system, then retry

**Is it a system error?**
- Database crash
- Service down
- Configuration wrong
- **Action**: Fix system issue, then retry

**Is it a process design error?**
- Logic error in process
- Wrong configuration
- Incorrect node transition
- **Action**: Update process design, restart

**Is it an external system error?**
- Third-party API down
- Third-party data error
- Integration issue
- **Action**: Contact third party, escalate

---

## Incident Actions

### Action 1: Retry

Restarts the failed instance from the beginning.

**When to Use:**
- Transient errors (network timeout, temporary service outage)
- After fixing root cause (re-submitted data, system recovered)
- Quick test if issue resolved

**How to Use:**
1. Click **[Retry]** button
2. Confirm: "Restart instance?"
3. Instance restarts from beginning
4. Watch new execution in activity feed
5. Monitor outcome

**Example:**
```
Incident: DB connection timeout
Root Cause: Database backup in progress
Fix: Wait 5 minutes for backup to complete
Action: Click [Retry] button
Result: Instance succeeds, invoice processed
```

**Retry Count:**
- Tracks total retries per incident
- Example: "Retries: 3" means tried 3 times
- Helps identify stuck/broken instances

---

### Action 2: Escalate

Mark incident as high-priority for immediate action.

**When to Use:**
- Critical process failure
- Customer-impacting issue
- Root cause requires engineering
- Needs management attention

**How to Use:**
1. Click **[Escalate]** button
2. Add comment: Explain urgency and context
3. Notification sent to management
4. Incident marked as "ESCALATED" status
5. Management team takes over

**Example:**
```
Incident: PaymentProcessing FAILED
Escalate Comment: "High-value transaction ($100k) failed. 
Customer contacted. Requires immediate fix. May need 
engineering to debug external payment gateway integration."
```

---

### Action 3: Delete

Removes incident from active list after resolving.

**When to Use:**
- Incident investigated and resolved
- Instance manually completed offline
- No further action needed
- Want to keep incidents list clean

**How to Use:**
1. Review incident thoroughly before deleting
2. Verify root cause fixed or workaround in place
3. Click **[Delete]** button
4. Confirm: "Mark as resolved?"
5. Incident removed from active list

**Important:**
- Delete is for resolved incidents only
- Deleted incidents still in audit log (cannot truly delete)
- Use "Resolved" comment before deleting

**Example:**
```
Incident: AddressValidation FAILED (INV-001-2026)
Root Cause: Invalid ZIP code in master data
Fix: Corrected ZIP code to 12345
Result: Retry succeeded, invoice processed
Action: Click [Delete] - mark as resolved
```

---

## Incident Patterns

### Pattern 1: Recurring Failures

**Symptom**: Same error happening repeatedly

**Investigation**:
1. Filter to Failed status
2. Group by error message
3. Identify if same error repeating
4. Check timestamp pattern

**Example Pattern**:
```
Time       Instance    Error
2:15 PM    INV-001     DB connection timeout
2:47 PM    INV-002     DB connection timeout
3:22 PM    INV-003     DB connection timeout
Pattern: Every 30-45 minutes, same error
Root Cause: Database connection pool exhausted
Fix: Increase pool size, restart app
```

---

### Pattern 2: Data-Specific Failures

**Symptom**: Failures only for certain data

**Investigation**:
1. Click "View Details" on multiple incidents
2. Compare process variables
3. Identify if pattern exists in data
4. Example: All high-value invoices fail?

**Example Pattern**:
```
Incident 1: Amount $10,000 - FAILED
Incident 2: Amount $50,000 - FAILED
Incident 3: Amount $100,000 - FAILED
Pattern: High-value invoices fail, low-value succeed
Root Cause: External service has $50k transaction limit
Fix: Configure routing for high-value to different service
```

---

### Pattern 3: Time-Based Failures

**Symptom**: Failures only at certain times

**Investigation**:
1. Check timestamps of incidents
2. Identify time pattern
3. Correlate with external events
4. Example: Fails every day at midnight?

**Example Pattern**:
```
Time-based pattern:
2026-05-24 11:45 PM - INV-001 FAILED
2026-05-25 11:47 PM - INV-002 FAILED
2026-05-26 11:49 PM - INV-003 FAILED
Pattern: Fails every night around midnight
Root Cause: Nightly database backup locks tables
Fix: Schedule backup to off-peak time (2am)
```

---

## Bulk Incident Actions

### Retry Multiple Incidents

When many incidents need retry (e.g., service recovered):

1. Click checkbox next to each incident
2. Click **[Bulk Retry]** button (appears when selected)
3. Confirm: "Retry 15 selected incidents?"
4. All retry simultaneously
5. Watch success in activity feed

**Example:**
```
Scenario: External payment service recovered after 30min outage
Action: Select all 15 failed payment incidents
Click: [Bulk Retry]
Result: All 15 retry immediately, 14 succeed, 1 requires manual fix
```

---

### Delete Multiple Incidents

When incidents are all resolved:

1. Click checkbox next to each incident
2. Click **[Bulk Delete]** button
3. Confirm: "Mark 10 incidents as resolved?"
4. All removed from active incidents list
5. Incidents archived for audit

---

## Common Incident Scenarios

### Scenario 1: Network Timeout

**Error Message**: `Connection timeout after 30s`

**Investigation**:
- Check external service health (is it up?)
- Check network connectivity
- Check process configuration (timeout value)

**Fix Options**:
- Service issue: Wait for recovery or contact service provider
- Network issue: Check firewall, VPN, DNS
- Timeout too short: Increase timeout in process definition
- Performance issue: Check process performance, optimize

**Retry Decision**: Yes, retry after service/network recovers

---

### Scenario 2: Invalid Data

**Error Message**: `Cannot parse JSON - unexpected character`

**Investigation**:
- What's the source system sending?
- Is format correct?
- Are required fields present?
- Is data encoding correct?

**Fix Options**:
- Data quality: Fix in source system, then retry
- Format change: Update process to handle new format
- Missing field: Ask source to add required field

**Retry Decision**: Fix data first, then retry

---

### Scenario 3: External Service Error

**Error Message**: `HTTP 404 - Service not found`

**Investigation**:
- Is external service running?
- Is endpoint URL correct?
- Are credentials valid?
- Has API changed?

**Fix Options**:
- Service down: Wait for recovery, then retry
- Wrong endpoint: Update configuration, then retry
- API changed: Update integration code
- Credentials expired: Refresh credentials, then retry

**Retry Decision**: Yes, after fixing root cause

---

### Scenario 4: Logic Error

**Error Message**: `NullPointerException at MyService.java:42`

**Investigation**:
- Review code at that line
- Check what variable is null
- Verify process data flow
- Test with sample data

**Fix Options**:
- Code bug: Developer fixes code, deploy new version
- Data issue: Fix data quality
- Process design: Update process logic
- Configuration: Correct configuration values

**Retry Decision**: No, fix code/process first, then restart

---

## Best Practices for Incident Management

### 1. Investigate Quickly
- Check incident within 15 minutes of creation
- Read error message, understand context
- Share findings with team

### 2. Categorize Root Cause
- Is it transient (retry-able)?
- Is it data (fixable)?
- Is it system (requires fix)?
- Is it process (redesign needed)?

### 3. Document Resolution
- Add comment before deleting
- Note root cause
- Note fix applied
- Reference any tickets filed

### 4. Prevent Recurrence
- Log recurring patterns
- Create tickets for improvements
- Update process definitions
- Improve data validation

### 5. Monitor Retry Success
- After retry, monitor execution
- Verify process completed
- If fails again, escalate
- Don't retry same error endlessly

---

## Incident Statistics & Reporting

### Questions to Answer

**Daily:**
- How many incidents created overnight?
- What are top error types?
- Any critical issues?

**Weekly:**
- What's the incident trend?
- Are we improving?
- Top 3 root causes?

**Monthly:**
- Incident rate vs. process volume
- Reliability metrics
- Impact assessment

### Metrics to Track

| Metric | Formula | Target |
|--------|---------|--------|
| **Incident Rate** | Incidents / Total Instances | < 1% |
| **Retry Success** | Successful Retries / Total Retries | > 70% |
| **MTTR** | Average Time to Resolve | < 2 hours |
| **First-Time Success** | Completed on first try / Total | > 99% |

---

## Troubleshooting Incidents

### Issue: Incident not appearing in list

**Causes**:
- Different time period selected
- Incident has different status
- Filter applied excludes it
- Incident is older than retention

**Solution**:
1. Check period selector (24h, 7d, 30d?)
2. Click "Clear All" filters
3. Expand date range with Custom filter
4. Verify incident exists in Details tab

---

### Issue: Cannot retry - button disabled

**Causes**:
- Already being retried
- Process definition removed
- Incident already resolved

**Solution**:
1. Refresh page (F5)
2. Check incident status
3. Verify process definition exists
4. Try again in 30 seconds

---

### Issue: Retry succeeded but process still shows as failed?

**Causes**:
- Dashboard not refreshed yet
- Polling interval (30 seconds)
- Browser cache issue

**Solution**:
1. Click [Refresh] button manually
2. Wait 1-2 seconds
3. Clear browser cache (Ctrl+Shift+Delete)
4. Refresh page (F5)

---

## Next Steps

- Learn [performance analytics](./easy-admin-dashboard-performance.md)
- Master [advanced filtering](./easy-admin-dashboard-filtering.md)
- Review [troubleshooting guide](./easy-admin-dashboard-troubleshooting.md)
- Check [accessibility features](./easy-admin-dashboard-accessibility.md)

---

## Summary

**Incident management workflow**:
1. Identify incident in Incidents tab
2. Click [View Details] to investigate
3. Analyze error message and context
4. Determine root cause
5. Take action: Retry, Escalate, or Delete
6. Document resolution
7. Monitor for patterns

**Key Principle**: Every incident is an opportunity to improve process reliability and data quality!
