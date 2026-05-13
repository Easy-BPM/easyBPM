# BPM Process Examples

This directory contains example BPMN process definitions that demonstrate various BPM engine capabilities.

## New Examples (All Node Types)

The following files cover every node type available in the Easy BPMN Modeler. See the root `src/test/resources/` folder for additional examples.

| File | Node Types Demonstrated |
|------|------------------------|
| `error-boundary.json` | ErrorBoundaryEvent attached to a ServiceTask |
| `../process-api-task.json` | HumanTask → APITask (bearer auth) → EndEvent |
| `../process-timer-event.json` | TimerEvent (30 s timeout) → ServiceTask → HumanTask |
| `../process-call-activity.json` | CallActivity (parent) with input/output variable mappings |
| `../process-call-activity-child.json` | Child subprocess (HumanTask) used by parent above |
| `../process-inclusive-gateway.json` | InclusiveGateway (OR-fork) → two ServiceTasks |
| `../process-comprehensive.json` | Multi-feature: HumanTask, ServiceTask, APITask, ExclusiveGateway, ParallelGateway (fork + join), ErrorBoundaryEvent |
| `../process-complete-all-components.json` | HumanTask + ServiceTask (public API) + ErrorBoundary + Timer + Message Throw/Catch + Code Task config example |

### Form Examples (`forms/` sub-folder)

Forms are deployed to `POST /forms` and referenced from HumanTask nodes via `config.formId`:

| File | `formId` | Use Case |
|------|----------|----------|
| `forms/form-loan-application.json` | `loan-application-form` | Applicant fills name, amount, credit score |
| `forms/form-approval.json` | `approval-form` | Reviewer selects APPROVED / REJECTED |
| `forms/form-submit-order.json` | `submit-order-form` | Operator enters order details |

---

## Message Event Examples (original content)

## Files

### 1. `simple-message-example.json`
**Purpose**: Minimal example showing basic message event usage

**Use Case**: Learn the fundamental structure of a message event

**Key Features**:
- Single message event node
- Variable substitution in correlation key: `${orderId}-${instanceNumber}`
- 30-minute timeout

**Example Execution**:
```bash
# Deploy process
curl -X POST http://localhost:8080/processes \
  -H "Content-Type: application/json" \
  -d @simple-message-example.json

# Start instance (returns processDefinitionId from deploy response)
curl -X POST http://localhost:8080/processes/{processDefinitionId}/start

# Send message to resume process
curl -X POST http://localhost:8080/processes/messages \
  -H "Content-Type: application/json" \
  -d '{
    "messageName": "MyMessage",
    "correlationKey": "ORD-123-1",
    "variables": {
      "status": "received"
    }
  }'
```

---

### 2. `payment-process.json`
**Purpose**: Real-world payment confirmation workflow

**Use Case**: Demonstrate message event in a typical e-commerce order fulfillment

**Process Flow**:
```
Order Received
    ↓
[Wait for Payment Confirmation] ← External Payment Processor sends message
    ↓
Send Order Confirmation Email
    ↓
Process Complete
```

**Key Features**:
- Message from external payment processor
- Correlation key uses orderId + customerId: `${orderId}-${customerId}`
- Service task for email notification
- Payment variables captured in message payload

**Variables**:
- `orderId`: Order identifier
- `customerId`: Customer identifier  
- `amount`: Order amount
- `paymentStatus`: Added by incoming message
- `transactionId`: Added by incoming message

**Example Execution**:
```bash
# Deploy
curl -X POST http://localhost:8080/processes \
  -H "Content-Type: application/json" \
  -d @payment-process.json

# Start (assume processDefinitionId = 1)
curl -X POST http://localhost:8080/processes/1/start

# Process waits for PaymentConfirmed message with correlationKey = "ORD-001-CUST-001"
# External payment processor sends confirmation:
curl -X POST http://localhost:8080/processes/messages \
  -H "Content-Type: application/json" \
  -d '{
    "messageName": "PaymentConfirmed",
    "correlationKey": "ORD-001-CUST-001",
    "variables": {
      "paymentStatus": "confirmed",
      "transactionId": "TXN-123456",
      "amount": 99.99
    }
  }'
```

---

### 3. `loan-application-process.json`
**Purpose**: Complex multi-step approval workflow with multiple message events

**Use Case**: Loan application requiring sequential async operations

**Process Flow**:
```
Application Submitted
    ↓
Validate Application Data
    ↓
[Wait for Credit Check Results] ← Credit Bureau sends message
    ↓
Evaluate Credit Score
    ├─ [Approve Loan] → Send Approval → [Wait for Documents] ← Customer submits documents → Verify
    └─ [Deny Loan] → Send Denial → End
```

**Key Features**:
- Multiple sequential message events
- Different timeouts (7200s for credit, 604800s for documents)
- Conditional branching (exclusive gateway) based on credit score
- Dynamic correlation keys varying by step

**Correlation Keys**:
1. Credit check: `${customerId}-${applicationId}`
2. Documents: `${applicationId}`

**Messages**:
1. **CreditCheckComplete**: Sent by credit bureau
   - Must include `creditScore` variable
   - Timeout: 2 hours
   
2. **DocumentsSubmitted**: Sent by customer/applicant
   - May include document metadata
   - Timeout: 7 days

**Example Execution**:
```bash
# Deploy
curl -X POST http://localhost:8080/processes \
  -H "Content-Type: application/json" \
  -d @loan-application-process.json

# Start (assume processDefinitionId = 1)
INSTANCE=$(curl -X POST http://localhost:8080/processes/1/start)
# Response contains processInstanceId

# Step 1: Credit bureau sends results
curl -X POST http://localhost:8080/processes/messages \
  -H "Content-Type: application/json" \
  -d '{
    "messageName": "CreditCheckComplete",
    "correlationKey": "CUST-001-LOAN-APP-001",
    "variables": {
      "creditScore": 750,
      "creditReportId": "REPORT-789"
    }
  }'

# Process branches to approval path (creditScore >= 700)
# Then waits for documents

# Step 2: Customer submits documents
curl -X POST http://localhost:8080/processes/messages \
  -H "Content-Type: application/json" \
  -d '{
    "messageName": "DocumentsSubmitted",
    "correlationKey": "LOAN-APP-001",
    "variables": {
      "documentCount": 5,
      "verificationCode": "DOC-123"
    }
  }'

# Process completes after document verification
```

---

## Message Event Configuration

All examples use message events with this structure:

```json
{
  "id": "waitForMessage",
  "type": "MessageEvent",
  "name": "Human-Readable Name",
  "properties": {
    "messageName": "UniqueCategoryName",
    "correlationKey": "${var1}-${var2}",
    "timeoutSeconds": 3600
  },
  "next": ["nextNodeId"]
}
```

### Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `messageName` | string | ✓ | Category identifier for the message (e.g., "PaymentConfirmed") |
| `correlationKey` | string | ✓ | Template for matching messages to instances. Supports `${variableName}` syntax |
| `timeoutSeconds` | integer | ✗ | Auto-fail process if no message received within this time |

### Correlation Key Best Practices

1. **Include instance identifier**: Ensures 1-to-1 messaging
   ```
   "${orderId}-${customerId}"
   "${applicationId}"
   "${requestId}-${timestamp}"
   ```

2. **Make keys unique**: Avoid collisions across concurrent instances
   ```
   GOOD:   "ORDER-123-CUST-456"
   BAD:    "order"
   ```

3. **Use process variables**: Keys are evaluated at message event execution
   ```
   "${externalSystemId}-${internalProcessId}"
   ```

---

## Testing Message Events

### Via REST API

```bash
# 1. Deploy process
DEFINITION=$(curl -s -X POST http://localhost:8080/processes \
  -H "Content-Type: application/json" \
  -d @simple-message-example.json)
PROCESS_DEF_ID=$(echo $DEFINITION | jq '.id')

# 2. Start instance
INSTANCE=$(curl -s -X POST http://localhost:8080/processes/$PROCESS_DEF_ID/start)
INSTANCE_ID=$(echo $INSTANCE | jq '.id')

# 3. Verify subscription created
curl -s "http://localhost:8080/processes/instances" | jq ".content[] | select(.id == $INSTANCE_ID)"

# 4. Send message
curl -X POST http://localhost:8080/processes/messages \
  -H "Content-Type: application/json" \
  -d '{
    "messageName": "MyMessage",
    "correlationKey": "ORD-123-1",
    "variables": {
      "customField": "customValue"
    }
  }'

# 5. Verify process completed
curl -s "http://localhost:8080/processes/instances" | jq ".content[] | select(.id == $INSTANCE_ID) | .status"
# Should return: "COMPLETED"
```

### Via Database

```sql
-- Check message subscriptions
SELECT * FROM message_subscription 
WHERE status = 'AWAITING';

-- Check received messages
SELECT * FROM message_subscription 
WHERE status = 'RECEIVED';

-- Check process instance status
SELECT id, status, current_nodes FROM process_instance;

-- Check process variables
SELECT name, value FROM process_variable 
WHERE process_instance_id = ?;
```

---

## Common Patterns

### Pattern 1: Request-Reply
```
Process A sends request → Waits for reply message → Continues
```

### Pattern 2: Event Notification
```
Process A emits event → Process B receives notification → Resumes
```

### Pattern 3: Coordination
```
Multiple processes wait for same message → One sends → All resume
(Note: Future feature for broadcasts)
```

### Pattern 4: Fallback with Timeout
```
Process waits for message with timeout → No reply = Auto-fail → Handle in error flow
```

---

## Integration with External Systems

### When to Use HTTP POST `/processes/messages`

1. **External service notifications**
   ```
   Payment Gateway → POST /processes/messages (PaymentConfirmed)
   ```

2. **Event sourcing systems**
   ```
   Event Bus Topic → POST /processes/messages
   ```

3. **Manual interventions**
   ```
   Admin Portal → POST /processes/messages
   ```

4. **System integrations**
   ```
   Third-party API responses → POST /processes/messages
   ```

### When to Use RabbitMQ

- Default expectation for high-volume messaging
- Reliable message delivery
- Complex message routing
- See [AmqpConfig.kt](../src/main/kotlin/com/easy/bpm/messaging/AmqpConfig.kt)

---

## Debugging

### Check Pending Messages
```sql
SELECT 
  ms.id,
  ms.message_name,
  ms.correlation_key,
  ms.status,
  ms.timeout_at,
  pi.status as process_status
FROM message_subscription ms
JOIN process_instance pi ON ms.process_instance_id = pi.id
WHERE ms.status = 'AWAITING'
ORDER BY ms.created_at DESC;
```

### Check Message History
```sql
SELECT 
  id,
  message_name,
  correlation_key,
  status,
  received_at - created_at as wait_duration
FROM message_subscription
WHERE status != 'AWAITING'
ORDER BY received_at DESC
LIMIT 20;
```

### Monitor Timeouts
```sql
SELECT 
  id,
  message_name,
  correlation_key,
  timeout_at,
  CURRENT_TIMESTAMP as now
FROM message_subscription
WHERE status = 'AWAITING'
  AND timeout_at < CURRENT_TIMESTAMP;
```

---

## References

- [Message Event Implementation Documentation](../../MESSAGE_EVENT_IMPLEMENTATION.md)
- [ProcessService.kt](../src/main/kotlin/com/easy/bpm/service/ProcessService.kt) - Core message handling
- [MessageSubscriptionService.kt](../src/main/kotlin/com/easy/bpm/service/MessageSubscriptionService.kt) - Subscription management
- [MessageEventIntegrationTest.kt](../../test/kotlin/com/easy/bpm/integration/MessageEventIntegrationTest.kt) - Test examples
