---
title: Forms
---

# Forms

Forms define the fields shown to users in the Task Portal. They are versioned by a stable `formId`, so a process can reference the latest deployed version without changing the process definition.

## Deploy a form

```bash
curl -X POST http://localhost:8080/forms \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "formId": "expenseReview",
    "name": "Expense Review",
    "schema": {
      "type": "object",
      "title": "Expense Review",
      "required": ["approved"],
      "properties": {
        "approved": { "type": "boolean", "title": "Approve request" },
        "comment": { "type": "string", "title": "Manager comment" }
      }
    }
  }'
```

## Attach a form to a user task

Set `config.formId` on a `HumanTask` node:

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

## Field types

The Task Portal supports standard JSON-schema-style fields and Easy BPM file fields.

| Field type | Use for |
| --- | --- |
| `string` | Short text values. |
| `number` | Numeric values. |
| `boolean` | Checkbox or true/false input. |
| `text` | Longer comments. |
| `radio` | Single choice with visible options. |
| `select` | Single choice dropdown. |
| `date` | Date input. |
| `fileUpload` | Upload a document and store document metadata. |
| `fileDownload` | Provide a downloadable document reference. |
| `pdfViewer` | Preview PDF documents inline. |

## Retrieve forms

```bash
curl "http://localhost:8080/forms/latest?formId=expenseReview" \
  -H "Authorization: Bearer $TOKEN"
```

```bash
curl "http://localhost:8080/forms?formId=expenseReview" \
  -H "Authorization: Bearer $TOKEN"
```
