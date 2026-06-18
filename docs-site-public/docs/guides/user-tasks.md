---
title: User Tasks
---

# User Tasks

User tasks create human work items that appear in the Task Portal.

## Assign a task

Assign directly to a user:

```json
{
  "id": "manager-review",
  "name": "Manager Review",
  "type": "HumanTask",
  "config": {
    "assignee": "manager",
    "formId": "expenseReview"
  }
}
```

Assign to candidate groups:

```json
{
  "id": "finance-review",
  "name": "Finance Review",
  "type": "HumanTask",
  "config": {
    "candidateGroups": "FINANCE,ADMIN",
    "formId": "financeReview"
  }
}
```

Candidate users can claim shared work before completing it.

## Map variables into a task

Task inputs copy process variables or static values into the task form context.

```json
"inputs": [
  { "targetName": "amount", "source": "variable", "value": "amount" },
  { "targetName": "currency", "source": "static", "value": "USD" }
]
```

## Map task output back to the process

Task outputs copy submitted values back to process variables.

```json
"outputs": [
  { "target": "process", "sourceName": "approved", "value": "approved" },
  { "target": "process", "sourceName": "comment", "value": "managerComment" }
]
```

## Search tasks

```bash
curl "http://localhost:8080/tasks/search?status=PENDING&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

## Claim a task

```bash
curl -X POST http://localhost:8080/tasks/123/claim \
  -H "Authorization: Bearer $TOKEN"
```

## Complete a task

```bash
curl -X POST http://localhost:8080/tasks/123/complete \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "variables": {
      "approved": true,
      "comment": "Approved for payment"
    }
  }'
```

When the task is completed, Easy BPM stores task variables, updates mapped process variables, and continues process execution.
