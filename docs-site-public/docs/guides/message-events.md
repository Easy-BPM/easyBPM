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

For integrations with external systems, send an idempotency key with each message. Easy BPM uses it to avoid processing the same external message twice if the partner retries after a timeout or network error.

```bash
curl -X POST http://localhost:8080/processes/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: invoice-INV-7788" \
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

You can also send the same value as `messageId` in the request body when setting custom headers is not practical:

```json
{
  "messageId": "invoice-INV-7788",
  "messageName": "invoice-received",
  "correlationKey": "ORDER-12345",
  "variables": {
    "invoiceId": "INV-7788",
    "amount": 540.0
  }
}
```

Example response when the message resumes a waiting process:

```json
{
  "status": "success",
  "message": "Message received and process resumed",
  "messageId": "invoice-INV-7788",
  "messageName": "invoice-received",
  "correlationKey": "ORDER-12345",
  "correlated": true,
  "duplicate": false
}
```

If the same `Idempotency-Key` or `messageId` is sent again, Easy BPM returns the stored result and does not resume the process a second time. The response includes `"duplicate": true`.

If no process is currently waiting for the given `messageName` and `correlationKey`, the response uses `"status": "unmatched"` and `"correlated": false`. In that case, check whether the process has reached the message catch event, whether the correlation key matches exactly, and whether the partner sent the message too early.

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

For internal process-to-process messages, Easy BPM controls both sides of the exchange. For messages sent by external systems, prefer a stable `Idempotency-Key` or `messageId` and design the partner integration to retry with the same value.
