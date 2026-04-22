---
sidebar_position: 42
---

# Call Activity Error Handling

## Overview

When a subprocess fails, your process needs a strategy to respond. This guide covers error boundaries, exception variables, and compensation strategies for Call Activities.

---

## Error Boundary Basics

### What is an Error Boundary?

An **error boundary** is an event attached to the Call Activity node that catches subprocess failures and routes execution to an error handler instead of failing the entire parent process.

### When Errors Occur

Without error boundary:
```
Parent → Call Activity → Child fails
                         ↓
                      Parent FAILED
                      ✗ Process halts
```

With error boundary:
```
Parent → Call Activity → Child fails
                         ↓
                      Error Boundary Triggered
                      ↓
                      Error Handler Task
                      ↓
                      Parent ACTIVE
                      ✓ Process continues
```

---

## Setting Up Error Boundaries

### Step 1: Attach Error Boundary in Modeler

1. Open Call Activity node properties
2. Look for **"Error Boundary"** section
3. Enable error boundary: ☑ **Attach Error Boundary**

### Step 2: Configure Exception Variable

**Exception Variable**: Captures the error message

1. In error boundary settings, enter variable name: `exceptionMessage`
2. When child fails, this variable will contain error details
3. Example: `"Database connection timeout"`, `"Process definition not found"`

### Step 3: Route to Error Handler

1. Add a task node after Call Activity (error handler)
2. Connect error boundary to error handler
3. Error handler executes when child fails:
   - Log error
   - Send alert
   - Retry logic
   - Compensation steps

### Example Configuration

**Modeler Properties Panel:**

```
Call Activity: "process-payment"
├─ Target Process Key: "payment-processor"
├─ Input Mappings:
│   └─ amount → payment_amount
├─ Output Mappings:
│   └─ transactionId → transaction_id
└─ Error Boundary:
   ├─ ✓ Enabled
   ├─ Exception Variable: paymentError
   └─ Handler Task: Send-Alert-Email
```

---

## Exception Variable Mapping

### Capturing Exception Details

The exception variable automatically captures error information:

```
Child Process Fails With: "Payment gateway returned error 503"

Parent Process:
  exceptionMessage = "Payment gateway returned error 503"

Parent Task (error handler):
  IF exceptionMessage contains "503"
    THEN retry payment
  ELSE
    THEN contact support
```

### Using Exception Variable in Error Handler

**Example: Payment Processing**

```
Parent Process:

Service Task: Process Payment
  Call Activity: "charge-card"
  └─ Error Boundary: error_message
    └─ Task: Handle Payment Error
       
Task: Handle Payment Error
  - Condition 1: IF error_message contains "card expired"
    THEN send "Update your card" notification
    
  - Condition 2: IF error_message contains "insufficient funds"
    THEN send "Payment declined - check balance" notification
    
  - Condition 3: ELSE
    THEN send "Payment failed - contact support" alert
    
  - Finally: Set refund_reason = error_message
```

---

## Error Scenarios & Solutions

### Scenario 1: Child Process Fails

**Setup**:
- Parent calls "validate-order" subprocess
- Validation subprocess fails (out of stock)
- Error boundary captures: `validationError = "Item out of stock"`

**Process Flow**:
```
Parent Process:
  Start
  ↓
  Request Order
  ↓
  [Call Activity: Validate Order] ← Fails
  ↓ (Error Boundary Caught)
  [Task: Notify Customer]
    Subject: "Order cannot be fulfilled"
    Body: "Reason: " + validationError
  ↓
  [Task: Log Failure]
  ↓
  [End: CANCELLED]
```

**Configuration**:
```
Call Activity: "validate-order"
  Error Boundary: stockError
  
Task: Notify Customer
  message = "Item " + orderId + " is " + stockError
  
Task: Log Failure
  failureReason = stockError
```

### Scenario 2: Timeout Without Retry

**Setup**:
- Parent calls subprocess
- Subprocess takes > 30 minutes (timeout)
- Parent marks as failed

**Process Flow**:
```
Parent Process:
  Start
  ↓
  [Call Activity: External Service] ← Timeout after 30min
  ↓ (Error Boundary Caught)
  [Task: Log Timeout]
    timeout_error = "Subprocess exceeded 30-minute timeout"
  ↓
  [Task: Alert Operations]
    Send email: "Subprocess timeout - manual intervention needed"
    Include: subprocess_id, timeout_error
  ↓
  [End: FAILED]
```

**Admin Recovery**:
- Operations team reviews in Admin UI
- Can manually move parent forward or restart

### Scenario 3: Retry Logic

**Setup**:
- Parent calls subprocess
- First attempt fails
- Retry up to 3 times
- If all fail, escalate

**Process Flow**:
```
Parent Process:
  Set: retryCount = 0
  ↓
  [Loop Decision] ← While retryCount < 3
  │  ├─ retryCount == 0: Try 1
  │  ├─ retryCount == 1: Try 2
  │  ├─ retryCount == 2: Try 3
  │  └─ retryCount == 3: Fail & Escalate
  ↓
  [Call Activity: Process Payment]
  ├─ Success → End
  └─ Error (Boundary Caught)
    ├─ retryCount < 2:
    │   Set: retryCount++
    │   Wait: 5 seconds
    │   Loop back
    └─ retryCount >= 2:
        Send alert & End FAILED
```

**Implementation**:
```
Service Task: Initialize
  Set retryCount = 0

Loop Service Task: Increment Retry
  Set retryCount = retryCount + 1

Wait Task: Backoff Delay
  Duration: PT5S (5 seconds)

Call Activity: Process Payment
  Error Boundary: payment_error
  
Decision: Retry or Fail
  IF payment_error AND retryCount < 3
    THEN wait 5 seconds → call again
  ELSE
    THEN send alert → end
```

### Scenario 4: Compensation (Undo on Error)

**Setup**:
- Multi-step process: create order → reduce inventory → charge payment
- If payment fails, need to undo inventory reduction

**Process Flow**:
```
Parent Process:
  ↓
  [Task: Create Order]
    order_id = new_order()
  ↓
  [Task: Reduce Inventory]
    inventory[product] -= quantity
  ↓
  [Call Activity: Charge Payment] ← Might fail
  ├─ Success
  │   ↓
  │   [Task: Send Confirmation]
  │   ↓
  │   End SUCCESS
  └─ Error (Boundary Caught)
      ↓
      [Task: Restore Inventory]
        inventory[product] += quantity  ← UNDO
      ↓
      [Task: Cancel Order]
        order_status = "CANCELLED"
      ↓
      [Task: Notify Customer]
        "Payment failed, order cancelled"
      ↓
      End FAILED
```

**Configuration**:
```
Call Activity: "payment-processor"
  Input:
    order_id → order_number
    amount → payment_amount
  
  Error Boundary: payment_error
  Handler Task: Compensation

Task: Compensation
  // 1. Restore inventory
  [Update Inventory]
    product_id = original_product
    quantity_to_add = original_quantity
  
  // 2. Cancel order
  [Cancel Order]
    order_status = "CANCELLED"
    cancellation_reason = payment_error
  
  // 3. Notify customer
  [Send Email]
    to = customer_email
    subject = "Order Cancelled"
    body = "Payment failed: " + payment_error
```

---

## Error Types & Messages

### Common Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| **Process definition not found** | Target process key doesn't exist | Deploy target process first |
| **Process definition is disabled** | Target process archived/disabled | Reactivate process |
| **Variable mapping error** | Missing source variable | Ensure parent variable set |
| **Database connection timeout** | Database unavailable | Check database connection |
| **External service unavailable** | Called service down | Implement retry logic |
| **Insufficient permissions** | Access control issue | Check service account permissions |
| **Circular reference detected** | A→B→A nesting | Redesign process flow |
| **Max nesting depth exceeded** | Too many levels | Reduce nesting depth |

### Capturing Error Details

Use exception variable to get error information:

```
// Generic error handling
IF error exists
  THEN {
    exceptionMessage = <captured error message>
    exceptionType = <error category>
    timestamp = <when error occurred>
    subprocess_id = <which subprocess failed>
  }
```

---

## Best Practices

### 1. **Always Attach Error Boundaries**

```
❌ Bad: No error handling
Parent [Call Activity] ← Child fails → Parent FAILED

✅ Good: Error boundary with handler
Parent [Call Activity] ← Child fails → Error Handler → Continue
```

### 2. **Capture Exception Variable**

```
❌ Bad: Error boundary with no variable
  Errors silently handled, no way to know why

✅ Good: Capture exception variable
  exceptionMessage = <error details>
  // Now can react to specific errors
```

### 3. **React to Specific Errors**

```
❌ Bad: Same handling for all errors
  IF error THEN send generic alert

✅ Good: Different handling by error type
  IF error contains "timeout"
    THEN retry
  ELSE IF error contains "permission"
    THEN escalate
  ELSE
    THEN log and alert
```

### 4. **Implement Compensation When Needed**

```
❌ Bad: Multi-step process, no rollback
  Step1 ✓ → Step2 ✓ → Step3 (child) ✗
  Step1 & 2 remain done (inconsistent state)

✅ Good: Implement compensation
  Step1 ✓ → Step2 ✓ → Step3 ✗
  Step2 Undo ✓ → Step1 Undo ✓
  (consistent state maintained)
```

### 5. **Log Errors Clearly**

```
Each error handler should log:
  - Timestamp
  - Exception message
  - Parent instance ID
  - Child instance ID
  - What action was taken (retry/skip/escalate)
  - Result of action
```

### 6. **Use Exponential Backoff for Retries**

```
❌ Bad: Immediate retry
  Retry immediately 3 times
  (if service down, all 3 fail immediately)

✅ Good: Exponential backoff
  Attempt 1: Immediate
  Attempt 2: Wait 5 seconds
  Attempt 3: Wait 30 seconds
  Attempt 4: Wait 2 minutes
  (gives service time to recover)
```

### 7. **Alert Operations on Critical Errors**

```
For each error type, decide:
  - Should operations be alerted?
  - Should it be escalated?
  - Should it stop the process?

Examples:
  Payment Error: ALERT + STOP
  Inventory Check: ALERT + SKIP
  Email Service: LOG + RETRY + CONTINUE
```

---

## Monitoring Errors

### In Admin UI

1. Search for process instance
2. Check Status: Should show path taken (success or error handler)
3. Check Node History: Shows which error handler executed
4. Check Variables: `exceptionMessage` contains error details

### Setting Up Alerting

Configure alerts for:
- Error boundary triggered
- Retries exceeded
- Critical subprocess failures
- Timeouts

### Metrics to Track

```
• Error rate: % of calls that fail
• Retry success rate: How many retries succeed?
• Time to recovery: How long to resolve?
• Most common errors: Which errors most frequent?
```

---

## Testing Error Scenarios

### Unit Tests

```kotlin
// Test error boundary catches exception
fun testErrorBoundaryCaptures() {
  // Setup child to fail
  // Invoke parent with error boundary
  // Assert: exceptionMessage populated
  // Assert: Error handler executed
}
```

### Integration Tests

```
Test Case 1: Child fails → Error boundary triggers
Test Case 2: Exception variable captured
Test Case 3: Error handler executes
Test Case 4: Parent resumes correctly
Test Case 5: Compensation undoes changes
```

### E2E Tests

```
Test Case: Order → Validate → (Fails) → Notify Customer
  1. Start order process
  2. Reach validation step
  3. Validation fails (simulate)
  4. Error handler sends email
  5. Check email sent successfully
  6. Check order status = CANCELLED
```

---

## Summary

| Concept | Purpose | Example |
|---------|---------|---------|
| **Error Boundary** | Catch subprocess failure | Attached to Call Activity |
| **Exception Variable** | Capture error message | `paymentError = "timeout"` |
| **Error Handler** | React to error | Send alert, retry, compensate |
| **Retry Logic** | Handle transient failures | Retry 3x with backoff |
| **Compensation** | Undo previous steps | Restore inventory on failure |

---

## Next Steps

- 📚 Read: **[Call Activity Guide](./easy-modeler-call-activity.md)**
- 📚 Read: **[Variable Mapping Tutorial](./call-activity-variable-mapping.md)**
- 🧪 Try: Create process with error boundary and compensation
- 🚀 Deploy: Order fulfillment with error handling
