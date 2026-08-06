---
title: Call Activities
---

# Call Activities

Call Activities start another Easy BPM process as a subprocess. Use them when you want reusable process fragments such as KYC review, document verification, onboarding, or exception handling.

## Configure a subprocess call

```json
{
  "id": "run-kyc",
  "name": "Run KYC",
  "type": "CallActivity",
  "config": {
    "processKey": "kyc-review",
    "propagateAllVariables": false,
    "inputMappings": {
      "customerId": "customerId",
      "requestId": "parentRequestId"
    },
    "outputMappings": {
      "kycStatus": "kycStatus",
      "reviewNotes": "kycNotes"
    }
  }
}
```

## Variable mapping

| Mapping | Direction |
| --- | --- |
| `inputMappings` | Parent process variable to child process variable. |
| `outputMappings` | Child process variable to parent process variable. |
| `propagateAllVariables` | Copies all parent variables into the child when set to `true`. |

Prefer explicit mappings for customer workflows. They make subprocess boundaries easier to understand and reduce accidental variable coupling.

## Inspect relationships

Get child instances:

```bash
curl http://localhost:8080/processes/instances/100/children \
  -H "Authorization: Bearer $TOKEN"
```

Get the parent of a child:

```bash
curl http://localhost:8080/processes/instances/101/parent \
  -H "Authorization: Bearer $TOKEN"
```

Get mapping details:

```bash
curl http://localhost:8080/processes/instances/100/children/101/mapping \
  -H "Authorization: Bearer $TOKEN"
```

## Error handling

Attach an `ErrorBoundaryEvent` to catch failures and route the parent process to a recovery path. Include an `errorCode` in the boundary configuration when you need different recovery paths for different classes of failure.
