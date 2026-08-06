---
title: Documents
---

# Documents

Easy BPM stores documents uploaded through task forms or API calls. Metadata is returned through JSON endpoints, while file content is retrieved through download or preview endpoints.

## Upload a document

```bash
curl -X POST http://localhost:8080/api/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@contract.pdf" \
  -F "taskId=123" \
  -F "processInstanceId=456" \
  -F "formFieldKey=signedContract"
```

The response includes:

| Field | Description |
| --- | --- |
| `id` | Document UUID. |
| `fileName` | Original file name. |
| `contentType` | MIME type. |
| `fileSize` | Size in bytes. |
| `taskId` | Associated task, if provided. |
| `processInstanceId` | Associated process instance, if provided. |
| `formFieldKey` | Form field that produced the document, if provided. |
| `uploadedBy` | Authenticated username. |
| `createdAt` | Upload timestamp. |

## Get metadata

```bash
curl http://localhost:8080/api/documents/{documentId} \
  -H "Authorization: Bearer $TOKEN"
```

## Download content

```bash
curl -L http://localhost:8080/api/documents/{documentId}/download \
  -H "Authorization: Bearer $TOKEN" \
  -o contract.pdf
```

## Preview content

```bash
curl http://localhost:8080/api/documents/{documentId}/preview \
  -H "Authorization: Bearer $TOKEN"
```

PDF files are returned inline for browser preview. Other file types fall back to attachment behavior.

## List documents for a task

```bash
curl "http://localhost:8080/api/documents?taskId=123" \
  -H "Authorization: Bearer $TOKEN"
```

## Delete a document

```bash
curl -X DELETE http://localhost:8080/api/documents/{documentId} \
  -H "Authorization: Bearer $TOKEN"
```
