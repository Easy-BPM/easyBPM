---
title: Examples
---

# Examples

The public docs include copy-paste examples for the most common customer workflows.

## Login

```bash
TOKEN=$(curl -s http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' \
  | jq -r '.token')
```

## Deploy a form

```bash
curl -X POST http://localhost:8080/forms \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"formId":"simpleApproval","name":"Simple Approval","schema":{"type":"object","properties":{"approved":{"type":"boolean","title":"Approved"}}}}'
```

## Start a process

```bash
curl -X POST http://localhost:8080/processes/expense-approval/start \
  -H "Authorization: Bearer $TOKEN"
```

## Complete a task

```bash
curl -X POST http://localhost:8080/tasks/123/complete \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"variables":{"approved":true}}'
```

## Upload a document

```bash
curl -X POST http://localhost:8080/api/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@contract.pdf" \
  -F "taskId=123"
```
