---
sidebar_position: 43
---

# Real-World Examples

## Example 1: E-Commerce Order Fulfillment

**Scenario**: Customer places order → Validate → Process Payment → Update Inventory → Ship

### Process Flow

```
Order Fulfillment Process (Level 0):
  
  Start
  ↓
  [Receive Order] (Service Task)
    orderId = generate_id()
    customerEmail = input.email
    totalAmount = input.amount
    items = input.items
  ↓
  [Call Activity: validate-order]
    Input:
      orderId → order_id
      items → order_items
    Output:
      validationStatus → isValid
      reason → validationReason
    Error Boundary: orderValidationError
    
    ├─ Success: isValid = true
    │   ↓
    │   [Call Activity: process-payment] ← Level 1
    │     Input:
    │       orderId → order_id
    │       totalAmount → amount
    │       customerEmail → email
    │     Output:
    │       transactionId → paymentTransactionId
    │       status → paymentStatus
    │     Error Boundary: paymentError
    │     
    │     ├─ Success: paymentStatus = "APPROVED"
    │     │   ↓
    │     │   [Update Inventory] (Service Task)
    │     │     For each item: inventory[item.id] -= item.qty
    │     │   ↓
    │     │   [Call Activity: create-shipment] ← Level 1
    │     │     Input:
    │     │       orderId → order_id
    │     │       items → products
    │     │     Output:
    │     │       trackingNumber → shipmentTracking
    │     │     Error Boundary: shipmentError
    │     │     
    │     │     ├─ Success
    │     │     │   ↓
    │     │     │   [Send Confirmation Email] (Service Task)
    │     │     │     to = customerEmail
    │     │     │     body = "Order confirmed! Tracking: " + shipmentTracking
    │     │     │   ↓
    │     │     │   End: SUCCESS
    │     │     │
    │     │     └─ Error: shipmentError captured
    │     │         ↓
    │     │         [Handle Shipment Error]
    │     │           Log error: shipmentError
    │     │           Refund payment
    │     │           Restore inventory
    │     │           Send: "Unable to ship your order"
    │     │         ↓
    │     │         End: CANCELLED
    │     │
    │     └─ Error: paymentError captured
    │         ↓
    │         [Handle Payment Error]
    │           IF paymentError contains "card declined"
    │             Send: "Payment declined. Try another card."
    │           ELSE
    │             Send: "Payment processing error. Try again later."
    │         ↓
    │         End: PENDING (customer can retry)
    │
    └─ Error: orderValidationError captured
        ↓
        [Handle Validation Error]
          IF orderValidationError contains "out of stock"
            Send: "Item out of stock: " + validationReason
          ELSE
            Send: "Order cannot be processed: " + validationReason
        ↓
        End: CANCELLED
```

### Variable Flow

**Initial Variables:**
```
orderId: "ORD-2024-001"
customerEmail: "customer@example.com"
totalAmount: 299.99
items: [
  { id: "SKU-123", qty: 2, name: "Laptop" },
  { id: "SKU-456", qty: 1, name: "Mouse" }
]
```

**After validate-order:**
```
isValid: true
validationReason: null
```

**After process-payment:**
```
paymentTransactionId: "TXN-9999"
paymentStatus: "APPROVED"
```

**After create-shipment:**
```
shipmentTracking: "TRACK-ABC123"
```

**Final Variables (Success):**
```
orderId: "ORD-2024-001"
customerEmail: "customer@example.com"
totalAmount: 299.99
paymentTransactionId: "TXN-9999"
paymentStatus: "APPROVED"
shipmentTracking: "TRACK-ABC123"
```

### Key Points

- ✅ Each subprocess independent (can reuse for other processes)
- ✅ Clear variable mappings (what each subprocess needs)
- ✅ Error boundaries handle failures gracefully
- ✅ Customer notified at each stage
- ✅ Inventory only updated after payment confirmed

---

## Example 2: Multi-Level Approval Workflow

**Scenario**: Expense request → Manager approval → Finance audit → Executive sign-off

### Process Hierarchy

```
Request Approval (Level 0)
  ├─ [Call: manager-approval] (Level 1)
  │   ├─ [Collect Decision]
  │   ├─ [Send Notification]
  │   └─ Return: decision, notes
  │
  ├─ [Decision Point]
  │   └─ IF approved by manager
  │
  ├─ [Call: finance-audit] (Level 1)
  │   ├─ [Verify Budget]
  │   ├─ [Check Compliance]
  │   └─ Return: audit_status, budget_code
  │
  ├─ [Decision Point]
  │   └─ IF approved by finance
  │
  ├─ [Call: executive-review] (Level 1)
  │   ├─ [Review Request]
  │   ├─ [Provide Comments]
  │   └─ Return: final_decision, executive_comments
  │
  └─ [Final Decision]
      ├─ IF all approved
      │   └─ Process expense
      └─ ELSE
          └─ Notify requestor of denial
```

### Variable Transformations

**Initial (Employee Submits):**
```
expenseId: "EXP-2024-001"
amount: 5000
category: "Travel"
description: "Conference trip to NYC"
requestorEmail: "employee@company.com"
```

**Call manager-approval:**
```
Input Mapping:
  expenseId → expense_id
  amount → expense_amount
  description → expense_details

Manager Subprocess Adds:
  decision: "APPROVED"
  notes: "Looks reasonable"
  reviewedBy: "manager@company.com"

Output Mapping:
  decision → managerDecision
  notes → managerNotes
  reviewedBy → managerEmail
```

**Call finance-audit:**
```
Input Mapping:
  expenseId → expense_id
  amount → amount_to_verify
  managerDecision → prior_approval
  category → expense_category

Finance Subprocess Adds:
  audit_status: "APPROVED"
  budget_code: "TRAVEL-2024-Q2"
  budgetAvailable: true

Output Mapping:
  audit_status → financeDecision
  budget_code → budgetCode
  budgetAvailable → budgetOK
```

**Call executive-review:**
```
Input Mapping:
  expenseId → id
  amount → total_amount
  financeDecision → finance_status
  managerNotes → manager_feedback

Executive Subprocess Adds:
  decision: "APPROVED"
  comments: "Approved. Ensure conference report submitted."

Output Mapping:
  decision → executiveDecision
  comments → executiveComments
```

**Final Variables:**
```
expenseId: "EXP-2024-001"
amount: 5000
managerDecision: "APPROVED"
financeDecision: "APPROVED"
executiveDecision: "APPROVED"
budgetCode: "TRAVEL-2024-Q2"
executiveComments: "Approved. Ensure conference report submitted."
status: "APPROVED_FOR_PROCESSING"
```

### Error Handling

**Each subprocess has error boundary:**

```
[Call: manager-approval]
  Error Boundary: managerError
  Handler:
    Log error
    Send to HR for manual review
    Set status: "PENDING_HR_REVIEW"

[Call: finance-audit]
  Error Boundary: financeError
  Handler:
    IF financeError contains "budget exceeded"
      Send request back to manager for adjustment
    ELSE
      Escalate to CFO

[Call: executive-review]
  Error Boundary: executiveError
  Handler:
    Retry after 24 hours
    If still fails, mark as "PENDING_EXECUTIVE_DECISION"
```

---

## Example 3: Banking Loan Application

**Scenario**: Complex multi-step loan approval with parallel checks

### Sequential Process

```
Loan Application (Level 0)
  ↓
  [Input Loan Details]
    loanAmount: 500000
    loanTerm: 360 (months)
    purpose: "Home purchase"
  ↓
  [Call: credit-check] (Level 1)
    Input: applicantId → applicant_id
    Output: creditScore → credit_score, status → credit_status
    Error: creditError → log and continue
  ↓
  [Decision: Credit score > 650?]
    ├─ YES → Continue
    └─ NO → Send rejection
  ↓
  [Call: income-verification] (Level 1)
    Input: 
      applicantId → applicant_id
      loanAmount → loan_amount
    Output: 
      verifiedIncome → annual_income
      employment_status → employment
    Error: incomeError → manual verification request
  ↓
  [Decision: Income sufficient?]
    ├─ YES → Continue
    └─ NO → Request co-applicant
  ↓
  [Call: property-appraisal] (Level 1)
    Input: 
      propertyAddress → address
      loanAmount → requested_amount
    Output: 
      appraisedValue → property_value
      appraisalStatus → appraisal_status
    Error: appraisalError → request new appraisal
  ↓
  [Decision: Property value covers loan?]
    ├─ YES → Continue
    └─ NO → Offer lower amount
  ↓
  [Call: underwriting] (Level 1)
    Input:
      loanAmount → amount
      creditScore → credit_score
      annualIncome → income
      propertyValue → property_value
    Output:
      underwritingDecision → final_decision
      approvedAmount → loan_amount_approved
      interestRate → approved_rate
    Error: underwritingError → escalate to senior underwriter
  ↓
  [Final Decision]
    IF final_decision = "APPROVED"
      → Send approval + documents
    ELSE
      → Send denial + reasons
  ↓
  End
```

### Variable Flow Diagram

```
Application Start:
  loanAmount=500k, applicantId=123, propertyAddress="123 Main St"
  
  ↓ [credit-check]
  credit_score=750, credit_status=GOOD
  
  ↓ [income-verification]
  annual_income=150k, employment=EMPLOYED
  
  ↓ [property-appraisal]
  property_value=550k, appraisal_status=OK
  
  ↓ [underwriting]
  final_decision=APPROVED
  loan_amount_approved=450k (lower due to DTI)
  approved_rate=5.5%
  
Application Result:
  loanAmount=500k (requested)
  approvedAmount=450k (approved)
  creditScore=750
  annualIncome=150k
  propertyValue=550k
  approvedRate=5.5%
  status=APPROVED
```

### Error Scenarios

**Scenario A: Credit check fails**
```
[Call: credit-check] → Error
  creditError = "Applicant not found in database"
  
[Error Handler]
  → Request SSN verification
  → Retry credit check
  → If still fails, require manual review
```

**Scenario B: Income verification fails**
```
[Call: income-verification] → Error
  incomeError = "Employer verification unavailable"
  
[Error Handler]
  → Request tax returns as alternative
  → Set employment_status = "UNVERIFIED"
  → Continue to underwriting (may decrease approval amount)
```

**Scenario C: Appraisal value too low**
```
[Call: property-appraisal] → Success
  property_value = 400k (but loan requested for 500k)
  
[Decision: Property value covers loan?]
  NO → Trigger workflow
  
[Renegotiation]
  → Offer loan amount = 350k (80% LTV)
  → Ask applicant if acceptable
  → If YES: Continue to underwriting
  → If NO: Mark as "PENDING_APPLICANT_DECISION"
```

---

## Example 4: Document Processing

**Scenario**: Batch document processing with quality checks

### Process

```
Document Processing (Level 0)
  ↓
  [For Each Document in Batch]
    ├─ documentId: "DOC-001"
    ├─ fileType: "invoice"
    └─ content: "..."
  ↓
  [Call: extract-data] (Level 1)
    Input: 
      documentId → doc_id
      fileType → type
      content → raw_content
    Output:
      extractedData → data_json
      confidence → extraction_confidence
    Error: extractError → manual review queue
  ↓
  [Call: validate-data] (Level 1)
    Input:
      extractedData → data
    Output:
      isValid → validation_result
      issues → validation_issues
    Error: validationError → escalate
  ↓
  [Decision: Valid?]
    ├─ YES
    │   ↓
    │   [Call: classify-document] (Level 1)
    │     Input: data → document_data
    │     Output: classification → doc_class
    │   ↓
    │   [Store in Database]
    │   ↓
    │   [Send Confirmation]
    │
    └─ NO
        ↓
        [Queue for Manual Review]
        ↓
        [Notify Operator]
        content: "Issues found: " + validation_issues
  ↓
  End
```

### Batch Processing Variables

```
Initial:
  batchId: "BATCH-2024-04-22"
  documentCount: 100
  documents: [
    { id: "DOC-001", type: "invoice", ...},
    { id: "DOC-002", type: "receipt", ...},
    ...
  ]

Processing Stats (accumulated):
  processedCount: 0
  successCount: 0
  failureCount: 0
  manualReviewCount: 0

Per Document:
  currentDocId: "DOC-001"
  extractedData: { invoice_num: "INV-123", ... }
  extractionConfidence: 0.95
  validationResult: true
  documentClass: "Invoice"
  
Final:
  processedCount: 100
  successCount: 95
  failureCount: 3
  manualReviewCount: 2
  batchStatus: "COMPLETED_WITH_ISSUES"
```

---

## Lessons Learned

### What Worked Well

✅ **Clear subprocess boundaries** → Easier to test and maintain  
✅ **Explicit variable mappings** → No surprises about data flow  
✅ **Error boundaries on all Call Activities** → Graceful error handling  
✅ **Decision points between calls** → Can adapt based on intermediate results  
✅ **Reusable subprocesses** → Payment processing used in 5+ workflows  

### What Required Iteration

⚠️ **Initial nested depth** → Had to simplify from 4 levels to 2-3  
⚠️ **Exception handling** → Added more granular error handlers later  
⚠️ **Variable naming** → Standardized naming convention mid-project  
⚠️ **Performance** → Optimized variable mapping for high-volume processes  

### Best Practices Adopted

1. **One subprocess per business capability**
   - Don't mix domain logic
   - Single responsibility principle

2. **Minimal variable passing**
   - Only what subprocess needs
   - Not propagate-all (except prototyping)

3. **Always have error handling**
   - Error boundary on every Call Activity
   - Meaningful exception messages

4. **Audit trails**
   - Log subprocess invocations
   - Track variable transformations
   - Monitor error handling

---

## Next Steps

- 📚 Read: **[Call Activity Guide](./easy-modeler-call-activity.md)**
- 📚 Read: **[Variable Mapping Tutorial](./call-activity-variable-mapping.md)**
- 📚 Read: **[Error Handling Guide](./call-activity-error-handling.md)**
- 🧪 Try: Implement one of these examples
- 🚀 Deploy: Your own multi-subprocess workflow
