---
sidebar_position: 40
---

# Call Activity: Invoking Subprocesses

## What is a Call Activity?

A **Call Activity** is a process node that invokes another process (subprocess) as part of the main workflow. When a process instance reaches a Call Activity node, it:

1. **Suspends** the parent process
2. **Creates** a new child process instance
3. **Passes variables** to the child (optional)
4. **Waits** for the child to complete
5. **Receives results** from the child (optional)
6. **Resumes** execution in the parent

Call Activities enable process decomposition, reusability, and cleaner process designs.

## When Should You Use Call Activities?

### ✅ Good Use Cases

**Process Reusability**
- Multi-step approval workflow used in 5+ parent processes
- Standardized payment processing logic
- Common validation procedures

**Process Modularity**
- Order fulfillment calls: inventory check, payment, shipping
- Loan application calls: credit check, income verification, approval

**Team Separation**
- Different teams own different subprocesses
- Clear process boundaries and ownership
- Independent development and deployment

**Error Handling**
- Subprocess errors contained within boundaries
- Parent can handle or escalate errors
- Error compensation in parent workflow

### ❌ Bad Use Cases

**Simple Sequence**
- Just a few sequential tasks → use regular sequence
- No reusability → don't wrap in subprocess

**High-Frequency Invocation**
- Called 100+ times per instance → performance impact
- Consider batch processing instead

**Tight Variable Coupling**
- Dozens of variable mappings needed
- Subprocess tightly depends on parent context
- Consider consolidating into single process

## Creating a Call Activity in the Modeler

### Step 1: Add Call Activity Node

1. Open **Easy BPMN Modeler** (http://localhost:3000)
2. Click **"Activities"** section in palette
3. Drag **"Call Activity"** onto canvas
4. Position the node in your process flow

### Step 2: Configure the Node

Click the Call Activity node. In the **Properties Panel**, you'll see:

#### Required Configuration

**Target Process Key** ← REQUIRED
- Enter the process key of the subprocess to invoke
- Example: `payment-processing`, `credit-check`, `approval-workflow`
- Must match a deployed process definition key
- ℹ️ Process key is set when you deploy the process

#### Optional Configuration

**Input Variable Mapping**
- Maps parent process variables to child process variables
- Format: `parent_var → child_var`
- Only specified variables passed to child
- Click **"Add Row"** to add more mappings
- Example:
  ```
  orderId → order_id
  amount → order_amount
  customerEmail → email
  ```

**Output Variable Mapping**
- Maps child process variables back to parent
- Format: `child_var → parent_var`
- Captures subprocess results
- Click **"Add Row"** to add more mappings
- Example:
  ```
  approval_status → approvalResult
  rejection_reason → declineReason
  ```

**Propagate All Variables**
- ☑️ Check to copy ALL parent variables to child
- ✓ Simplifies if child needs many parent variables
- ✗ Less explicit/documented than individual mappings
- ⚠️ If checked, explicit mappings are ignored
- Default: unchecked (explicit mappings only)

### Step 3: Deploy and Test

1. Click **"Deploy Process"** button
2. System validates:
   - ✓ Target process key exists
   - ✓ Process key doesn't contain spaces
   - ✓ Mappings reference valid variable names
3. Wait for success message
4. Start an instance to test!

## Call Activity Variable Mapping

### Input Mappings (Parent → Child)

**Purpose**: Pass parent process variables to child before execution

**Example Scenario**: Order fulfillment process calls payment processing

```
Parent Process Variables:
  - orderId: "ORD-12345"
  - totalAmount: 599.99
  - customerEmail: "john@example.com"

Call Activity Input Mappings:
  orderId → order_id
  totalAmount → payment_amount
  customerEmail → email_address

Child Process Receives:
  - order_id: "ORD-12345"
  - payment_amount: 599.99
  - email_address: "john@example.com"
```

### Output Mappings (Child → Parent)

**Purpose**: Capture subprocess results back to parent

**Example Scenario**: Credit check subprocess returns approval status

```
Child Process Completes With:
  - status: "APPROVED"
  - score: 750
  - message: "Excellent credit rating"

Call Activity Output Mappings:
  status → creditStatus
  score → creditScore
  message → creditMessage

Parent Receives:
  - creditStatus: "APPROVED"
  - creditScore: 750
  - creditMessage: "Excellent credit rating"
```

### Propagate All Variables

**Purpose**: Auto-copy all parent variables to child

```
Parent Variables: { a: 1, b: 2, c: 3, d: 4 }

Call Activity Configuration:
  ☑ Propagate All Variables = true

Child Automatically Receives:
  { a: 1, b: 2, c: 3, d: 4 }

Note: Explicit mappings are ignored when propagate-all is enabled
```

## Process Flow Examples

### Example 1: Simple Subprocess Call

```
Parent Process Flow:
  Start → Request Received → [Call Activity: validate-order] 
  → Send Confirmation Email → End

Call Activity: validate-order
  Input: orderId → order_id
  Output: isValid → orderValid

Validation Subprocess:
  Start → Check Inventory → Check Pricing → [End]
```

**Execution**:
1. Parent receives order
2. Parent suspends at Call Activity
3. Validation subprocess starts (new instance)
4. Checks inventory and pricing
5. Validation completes
6. Parent resumes with `orderValid` set
7. Parent sends confirmation email

### Example 2: Multi-Level Nesting

```
Main Process (Level 0)
  → Receive Order
  → [Call: fulfillment] (Level 1)
  → Complete Order

Fulfillment Process (Level 1)
  → Prepare Payment
  → [Call: process-payment] (Level 2)
  → Update Inventory
  → Complete

Payment Process (Level 2)
  → Validate Card
  → Charge Account
  → Send Receipt
  → Complete
```

**Variable Flow**:
```
Main: orderId=123, amount=99.99
  ↓ (map orderId, amount)
Fulfillment: order_id=123, payment_amount=99.99
  ↓ (map payment_amount)
Payment: amount=99.99
  ↓ (map receipt_number)
Fulfillment: receipt_id=REC-456
  ↓ (map receipt_id)
Main: paymentReceipt=REC-456
```

### Example 3: Error Handling with Call Activity

```
Parent Process:
  Start → Request Data → [Call Activity with Error Boundary]
                            ↓ Success
                         Process Result
                         Complete
                            ↓ Error
                         Log Error
                         Send Alert
                         Complete

Call Activity Configuration:
  Target Process: risky-operation
  Input: input_data → param
  Output: result → output_data
  
  Error Boundary:
    Capture Exception: exceptionMessage
```

**Execution on Error**:
1. Parent invokes risky-operation
2. Subprocess fails with exception
3. Error boundary catches: `exceptionMessage = "Database timeout"`
4. Parent resumes at error handler
5. Parent logs error and sends alert
6. Process completes gracefully

## Best Practices

### 1. **Use Explicit Input Mappings**
```
❌ Bad: ☑ Propagate All (implicit)
✅ Good: List exactly what variables needed
```
**Why**: More transparent, easier to troubleshoot, clearer variable dependencies

### 2. **Keep Subprocess Focused**
```
❌ 100-node subprocess
✅ 5-15 node subprocess with clear responsibility
```
**Why**: Easier to understand, test, and reuse

### 3. **Document Variable Names**
```
❌ Input: x → y, a → b
✅ Input: 
    customerId → customer_id      // Customer record ID
    orderAmount → amount          // Total order value
```
**Why**: Reduces confusion, easier maintenance

### 4. **Use Error Boundaries**
```
❌ Let errors propagate silently
✅ Attach error boundary to Call Activity
   Capture exception message
   Execute compensation logic
```
**Why**: Better error visibility, graceful degradation

### 5. **Test Variable Isolation**
```
✅ Verify: parent.var1 ≠ child.var1
   Child modifications don't affect parent
```
**Why**: Prevent unexpected side effects

### 6. **Limit Nesting Depth**
```
❌ A → B → C → D → E → F (6 levels)
✅ A → B → C (2-3 levels max)
```
**Why**: Performance, complexity management, easier debugging

## Monitoring in Admin UI

### Finding Parent-Child Relationships

1. Open **Easy BPM Admin** (http://localhost:5173)
2. Search for instance by ID
3. Scroll to **"Instance Hierarchy"** section
4. View:
   - Parent instance link (if subprocess)
   - Current instance with nesting level
   - Child instances (if parent)

### Inspecting Variable Mappings

1. Find parent instance in Admin UI
2. Scroll to **"Child Process Instances"**
3. Click **"Inspect Instance"** on a child
4. Scroll to **"Variable Mappings for Instance #XYZ"**
5. See:
   - Input mappings: parent → child
   - Output mappings: child → parent
   - Propagate all flag status

### Checking Instance Status

| Status | Meaning |
|--------|---------|
| SUSPENDED | Waiting for child subprocess |
| ACTIVE | Child completed, parent executing |
| COMPLETED | Process finished |
| FAILED | Child or parent failed |

## Common Issues & Solutions

### Issue: Child Instance Not Created

**Symptom**: Call Activity executed but no child instance appears

**Causes**:
1. ❌ Target process key doesn't exist
2. ❌ Target process not deployed
3. ❌ Target process is disabled/archived

**Solution**:
1. Check target process key in modeler properties
2. Deploy target process (ensure no errors)
3. Verify process status is "ACTIVE" in Admin → Deployed Workflows

### Issue: Variable Mapping Not Working

**Symptom**: Child receives empty/null variables

**Causes**:
1. ❌ Parent variable not set before Call Activity
2. ❌ Mapping names don't match variable names
3. ❌ Typos in mapping configuration

**Solution**:
1. Verify parent has variables assigned
2. Check variable names exactly match mappings
3. Use Admin UI to inspect actual variable values

### Issue: Parent Doesn't Resume

**Symptom**: Parent stuck in SUSPENDED state after child completes

**Causes**:
1. ❌ Child process failure not handled
2. ❌ Child error boundary issue
3. ❌ Database transaction rollback

**Solution**:
1. Check child instance status in Admin UI
2. Verify error boundary configuration
3. Check logs for error messages
4. Manually move parent node in Admin UI (recovery)

## Next Steps

- 📚 Read: **[Variable Mapping Tutorial](./call-activity-variable-mapping.md)**
- 📚 Read: **[Error Handling Guide](./call-activity-error-handling.md)**
- 🎬 Watch: Example videos (coming soon)
- 🧪 Try: Deploy sample order fulfillment process
- 🚀 Deploy: Your first subprocess-based workflow!
