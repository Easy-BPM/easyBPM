---
title: Message Events
---

# Message Events

Message events let external systems resume a waiting process by message name and correlation key.

## Catch a message

```json
{
  "id": "wait-for-invoice",
  "name": "Wait for Invoice",
  "type": "MessageIntermediateCatchEvent",
  "message": {
    "name": "invoice-received",
    "correlationKeys": ["${orderId}"],
    "payload": [
      { "sourceName": "invoiceId", "target": "process", "value": "invoiceId" },
      { "sourceName": "amount", "target": "process", "value": "invoiceAmount" }
    ]
  }
}
```

The process waits at this node until a matching message arrives.

## Send a message to Easy BPM

```bash
curl -X POST http://localhost:8080/processes/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "messageName": "invoice-received",
    "correlationKey": "ORDER-12345",
    "variables": {
      "invoiceId": "INV-7788",
      "amount": 540.0
    }
  }'
```

## Throw a message

```json
{
  "id": "notify-warehouse",
  "name": "Notify Warehouse",
  "type": "MessageIntermediateThrowEvent",
  "message": {
    "name": "order-approved",
    "correlationKeys": ["${orderId}"],
    "payload": [
      { "targetName": "orderId", "source": "variable", "value": "orderId" },
      { "targetName": "approved", "source": "variable", "value": "approved" }
    ]
  }
}
```

## Timeouts

Some message waits can be paired with timeout behavior in the Modeler. Use a timeout when the workflow needs an escalation or fallback path if a partner system never responds.

## Correlation guidance

Use correlation keys that are stable in both systems, such as order IDs, case IDs, claim IDs, or customer request IDs. Avoid using values that may be reformatted by another system.
