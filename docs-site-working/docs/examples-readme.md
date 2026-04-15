# BPM Process Examples

This directory contains example BPMN process definitions that demonstrate various BPM engine capabilities, with focus on message event usage.

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
curl -X POST http://localhost:8085/processes \
  -H "Content-Type: application/json" \
  -d @simple-message-example.json

# Start instance (returns processDefinitionId from deploy response)
curl -X POST http://localhost:8085/processes/{processDefinitionId}/start

# Send message to resume process
curl -X POST http://localhost:8085/processes/messages \
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
