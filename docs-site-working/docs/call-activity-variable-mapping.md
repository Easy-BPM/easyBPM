---
sidebar_position: 41
---

# Variable Mapping Tutorial

## Understanding Variable Mapping

Variable mapping controls **what data flows between parent and child** processes when a Call Activity executes.

### The Problem: Isolation

Each process instance has its own variable scope. When you create a child process, it starts with **no parent variables** - they don't automatically inherit context.

```
Parent Instance Variables:
  orderId: "ORD-123"
  customerName: "Alice"
  amount: 599.99

Call Activity Without Mapping:
  ↓
Child Instance Variables:
  (empty - no parent variables!)
```

### The Solution: Explicit Mapping

Mapping lets you specify exactly which variables to share and how to transform them.

```
Parent Instance Variables:
  orderId: "ORD-123"
  customerName: "Alice"
  amount: 599.99

Input Mapping Configuration:
  orderId → order_id
  amount → total_amount

Call Activity Creates Child With:
  ↓
Child Instance Variables:
  order_id: "ORD-123"
  total_amount: 599.99
  (customerName NOT included - not mapped)
```

---

## Input Mapping (Parent → Child)

### What It Does

Input mapping copies variables from parent to child **before the child starts executing**.

### Syntax

```
parent_variable_name → child_variable_name
```

- **Left side**: Variable name in parent process
- **Right side**: Variable name in child process
- **Arrow**: Direction of flow (parent to child)

### Example: E-Commerce Order

**Parent Process: "Order Fulfillment"**

Variables before Call Activity:
```
{
  "order_id": "ORD-2024-001",
  "customer_id": "CUST-456",
  "total_price": 149.99,
  "shipping_address": "123 Main St, NYC",
  "coupon_discount": 10.00
}
```

**Call Activity: Invoke "payment-processing"**

Input Mappings:
```
order_id → order_number
total_price → amount_to_charge
customer_id → customer
coupon_discount → discount_amount
```

**Child Process Receives:**
```
{
  "order_number": "ORD-2024-001",
  "amount_to_charge": 149.99,
  "customer": "CUST-456",
  "discount_amount": 10.00
}
```

**Note**: `shipping_address` NOT passed (not in mappings)

### Common Mapping Patterns

#### 1. **Rename Variables**
```
orderId → order_id           // Snake case conversion
userName → name              // Simplification
customerFirstName → first    // Abbreviation
```

#### 2. **Select Subset**
```
Parent has: a, b, c, d, e, f
Child only needs: a, c, e
Mappings: a → a, c → c, e → e
```

#### 3. **Aggregate Values**
```
Parent has separate variables:
  firstName, lastName, email

Child receives combined:
  (But you must do this in parent via Service Task first)
  
Then map: fullName → name, emailAddress → email
```

---

## Output Mapping (Child → Parent)

### What It Does

Output mapping copies variables from child back to parent **after the child completes**.

### Syntax

```
child_variable_name → parent_variable_name
```

- **Left side**: Variable name in child process
- **Right side**: Variable name in parent process

### Example: Payment Processing Result

**Child Process: "payment-processing"**

After execution, child has:
```
{
  "transaction_id": "TXN-9999",
  "status": "APPROVED",
  "authorization_code": "AUTH-ABC123",
  "timestamp": "2024-04-22T10:30:00Z"
}
```

**Call Activity: Output Mappings**
```
transaction_id → payment_transaction_id
status → payment_status
authorization_code → payment_auth_code
```

**Parent Process Receives:**
```
{
  "payment_transaction_id": "TXN-9999",
  "payment_status": "APPROVED",
  "payment_auth_code": "AUTH-ABC123"
}
```

The parent can now:
- Log the transaction ID
- Make decisions based on payment_status
- Send authorization code in email confirmation

### Real-World Example: Multi-Step Approval

**Main Process: "Expense Request"**

1. Employee submits expense
2. Calls "manager-approval" subprocess
3. Calls "finance-audit" subprocess
4. Sends final notification

```
Main Process Variables:
  expenseId: "EXP-001"
  amount: 1000
  category: "Travel"

Step 2: Call "manager-approval"
  Input: expenseId → expense_id, amount → amount
  Output: approval_status → manager_decision
  
Step 3: Call "finance-audit"
  Input: expenseId → expense_id, manager_decision → previous_approval
  Output: audit_status → finance_decision, comments → audit_notes
  
Step 4: Send Notification
  Uses: expenseId, manager_decision, finance_decision, audit_notes
```

---

## Propagate All Variables

### What It Does

Instead of mapping individual variables, copy **ALL parent variables to child** automatically.

### When to Use

✅ **Use propagate-all when:**
- Child needs most/all parent variables
- Few or no variable name changes needed
- Quick prototyping or testing

❌ **Avoid when:**
- Only few variables needed (confusing what's used)
- Variable names need transformation
- Clear documentation important

### Example: Propagate All

**Parent Variables:**
```
{
  "orderId": "123",
  "customerId": "456",
  "amount": 99.99,
  "shippingAddress": "...",
  "paymentMethod": "credit_card",
  "notes": "..."
}
```

**Call Activity Configuration:**
```
Input Mappings: (none - all propagated)
Propagate All Variables: ☑ CHECKED
```

**Child Receives:**
```
{
  "orderId": "123",
  "customerId": "456",
  "amount": 99.99,
  "shippingAddress": "...",
  "paymentMethod": "credit_card",
  "notes": "..."
}
```

Same names, same values - no transformation.

### Comparison: Explicit vs Propagate All

| Aspect | Explicit Mapping | Propagate All |
|--------|------------------|---------------|
| **Clarity** | Clear what's passed | Implicit, less clear |
| **Performance** | Slightly faster | Copy all variables |
| **Maintenance** | Easier to track deps | Hard to know what's used |
| **Flexibility** | Transform names | No transformation |
| **Documentation** | Self-documenting | Need comments |

**Best Practice**: Use explicit mapping and add comments explaining why each variable is needed.

---

## Advanced Scenarios

### Scenario 1: Name Transformation

**Parent uses camelCase, child uses snake_case:**

```
Parent: firstName, lastName, emailAddress
Child: first_name, last_name, email_address

Mappings:
  firstName → first_name
  lastName → last_name
  emailAddress → email_address
```

### Scenario 2: Value Transformation

You **cannot** transform values in the mapping itself. Instead:
1. Use a Service Task before Call Activity to transform
2. Then map the transformed variable

```
Parent Task 1: Assign Variables
  fullPrice = 100.00
  discountPercent = 10
  finalPrice = fullPrice - (fullPrice * discountPercent / 100) = 90.00

Call Activity Mapping:
  finalPrice → amount_due

Child Receives: amount_due = 90.00
```

### Scenario 3: Conditional Mapping

**Different mappings based on process state:**

❌ **Can't do this:**
```
IF approvalLevel == "manager" THEN
  map: managerNotes → notes
ELSE
  map: directorNotes → notes
```

✅ **Instead, do this:**
```
Service Task: Prepare Notes
  IF approvalLevel == "manager"
    THEN preparedNotes = managerNotes
    ELSE preparedNotes = directorNotes

Call Activity Mapping:
  preparedNotes → notes
```

### Scenario 4: Multiple Children with Different Mappings

**Parent calls same subprocess 3 times with different data:**

```
Main Process:
  Set variable 1: supplier1Data
  Call "supplier-request" (map supplier1Data → supplierData)
  Set variable 2: supplier2Data
  Call "supplier-request" (map supplier2Data → supplierData)
  Set variable 3: supplier3Data
  Call "supplier-request" (map supplier3Data → supplierData)

Each call passes different data to the same subprocess
Each subprocess returns results mapped back:
  quote → supplier1Quote
  quote → supplier2Quote
  quote → supplier3Quote
```

---

## Troubleshooting Mapping Issues

### Issue: Child Variable Is Null/Undefined

**Symptom**: `child.expectedVariable === undefined`

**Causes**:
1. Parent variable not set before Call Activity
2. Wrong variable name in mapping
3. Variable name typo

**Solution**:
```
1. Add debug task: Log all parent variables before Call Activity
2. Check exact variable name (case-sensitive!)
3. Verify mapping in properties panel
4. In Admin UI, inspect parent variables
```

### Issue: Parent Variable Not Updated After Child

**Symptom**: `parent.resultVariable` doesn't change after Call Activity

**Causes**:
1. Child variable not set
2. Wrong child variable name in mapping
3. Output mapping pointing to wrong variable

**Solution**:
```
1. In Admin UI, inspect child variables after execution
2. Verify child actually set the variable
3. Check exact child variable name
4. Verify output mapping configuration
```

### Issue: Unexpected Overwrite

**Symptom**: Parent variable changed unexpectedly

**Causes**:
1. Output mapping overwrote parent variable
2. Propagate-all copied old child value

**Solution**:
```
1. Review output mappings
2. Use different variable names if you need to preserve parent value
3. Example: Child returns result → finalResult (not result)
```

---

## Best Practices for Variable Mapping

### 1. **Use Descriptive Variable Names**
```
❌ Bad:
  x → y
  a → b
  var1 → var2

✅ Good:
  orderId → order_id
  customerEmail → email_address
  approvalStatus → approval_result
```

### 2. **Document the Purpose**
```
// In modeler, add comment:
/*
Input Mappings:
  orderId: Customer order identifier
  totalAmount: Order total for charging
  
Output Mappings:
  transactionId: Payment gateway transaction reference
  status: Payment approval status (APPROVED/DECLINED)
*/
```

### 3. **Maintain Consistency**
```
If you map in parent:  parent_var → child_var
Then consistently use: child_var throughout the child process

Don't mix: some places use child_var, others use parent_var
```

### 4. **Use Explicit Mapping**
```
❌ Don't: ☑ Propagate All Variables
✅ Do: Map only variables child actually needs
```

### 5. **Test Variable Isolation**
```
Before deploying:
1. Start parent instance
2. Invoke subprocess
3. Verify parent and child have independent variables
4. Verify child can't accidentally modify parent
```

### 6. **Handle Null Values Gracefully**
```
If parent might not have a variable:
1. Don't map it (child won't have it)
2. Or provide default in child process

Subprocess should handle missing variables:
  IF variable == null OR variable == undefined
    THEN use default value
```

---

## Performance Considerations

### Variable Mapping Overhead

- **Per variable**: ~1-5ms depending on variable size
- **10 variables**: ~10-50ms total
- **100 variables**: ~100-500ms total

**Recommendation**: Map ≤20 variables per Call Activity for optimal performance.

### Optimization Tips

1. **Map only needed variables**
   - Don't use propagate-all for large variable sets
   - Select subset of variables child actually needs

2. **Keep variables small**
   - Large objects in variables = slow mapping
   - Consider storing large data in external system

3. **Avoid cycles**
   - Don't map results back to same variables
   - Could cause unexpected behavior

---

## Summary

| Concept | Purpose | Example |
|---------|---------|---------|
| **Input Mapping** | Pass data to child | `orderId → order_id` |
| **Output Mapping** | Get results from child | `status → payment_status` |
| **Propagate All** | Copy all variables | ☑ Checked |
| **Explicit** | Document dependencies | List each mapping |

**Remember**: Variables are **isolated by default**. Use mapping to explicitly control data flow!

---

## Next Steps

- 📚 Read: **[Error Handling Guide](./call-activity-error-handling.md)**
- 📚 Read: **[Call Activity Guide](./easy-modeler-call-activity.md)**
- 🧪 Try: Create a two-process example with input/output mapping
- 🚀 Deploy: Order fulfillment process with variable mapping
