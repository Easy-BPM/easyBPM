---
title: Documents API
---

# Documents API

Use the Documents API to upload files, read metadata, preview, download, list, and delete task documents.

## Operations

| Method | Path | Summary |
| --- | --- | --- |
| `GET` | `/api/documents` | List documents |
| `POST` | `/api/documents` | Upload a document |
| `GET` | `/api/documents/{id}` | Get document metadata |
| `DELETE` | `/api/documents/{id}` | Delete a document |
| `GET` | `/api/documents/{id}/download` | Download a document |
| `GET` | `/api/documents/{id}/preview` | Preview a document inline |

<a id="get-api-documents"></a>
## GET /api/documents

**List documents**

Returns document metadata list, optionally filtered by taskId

| Property | Value |
| --- | --- |
| Operation ID | `list` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [DocumentResponseDto](./schemas)[] |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `taskId` | query | No | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/api/documents?taskId=123" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [DocumentResponseDto](./schemas)[] |

### Example response

Status: `200 OK`

```json
[
  {
    "id": "3f3c7af7-34ae-4dd4-96e4-cbcba52c6b8f",
    "fileName": "contract.pdf",
    "contentType": "application/pdf",
    "fileSize": 245760,
    "taskId": 123,
    "processInstanceId": 456,
    "formFieldKey": "signedContract",
    "uploadedBy": "admin",
    "createdAt": "2026-06-17T10:00:00"
  }
]
```

<a id="post-api-documents"></a>
## POST /api/documents

**Upload a document**

Upload a file and associate it with a task, process instance and/or form field

| Property | Value |
| --- | --- |
| Operation ID | `upload` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | `DocumentUploadRequest` |
| Request content type | `multipart/form-data` |
| Response DTO | [DocumentResponseDto](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `taskId` | query | No | integer(int64) |  |
| `processInstanceId` | query | No | integer(int64) |  |
| `formFieldKey` | query | No | string |  |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `multipart/form-data` | `DocumentUploadRequest` |

Example multipart fields:

| Field | Example |
| --- | --- |
| `file` | `@contract.pdf` |
| `taskId` | `123` |
| `processInstanceId` | `456` |
| `formFieldKey` | `signedContract` |

### Example request

```bash
curl -X POST "http://localhost:8080/api/documents?taskId=123&processInstanceId=456&formFieldKey=signedContract" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@contract.pdf" \
  -F "taskId=123" \
  -F "processInstanceId=456" \
  -F "formFieldKey=signedContract"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [DocumentResponseDto](./schemas) |

### Example response

Status: `201 Created`

```json
{
  "id": "3f3c7af7-34ae-4dd4-96e4-cbcba52c6b8f",
  "fileName": "contract.pdf",
  "contentType": "application/pdf",
  "fileSize": 245760,
  "taskId": 123,
  "processInstanceId": 456,
  "formFieldKey": "signedContract",
  "uploadedBy": "admin",
  "createdAt": "2026-06-17T10:00:00"
}
```

<a id="get-api-documents-id"></a>
## GET /api/documents/\{id\}

**Get document metadata**

Returns document metadata without binary content

| Property | Value |
| --- | --- |
| Operation ID | `getMetadata` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [DocumentResponseDto](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | string(uuid) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/api/documents/3f3c7af7-34ae-4dd4-96e4-cbcba52c6b8f" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [DocumentResponseDto](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": "3f3c7af7-34ae-4dd4-96e4-cbcba52c6b8f",
  "fileName": "contract.pdf",
  "contentType": "application/pdf",
  "fileSize": 245760,
  "taskId": 123,
  "processInstanceId": 456,
  "formFieldKey": "signedContract",
  "uploadedBy": "admin",
  "createdAt": "2026-06-17T10:00:00"
}
```

<a id="delete-api-documents-id"></a>
## DELETE /api/documents/\{id\}

**Delete a document**

Permanently deletes a stored document

| Property | Value |
| --- | --- |
| Operation ID | `delete` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | `No body` |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | string(uuid) |  |

### Example request

```bash
curl -X DELETE "http://localhost:8080/api/documents/3f3c7af7-34ae-4dd4-96e4-cbcba52c6b8f" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | - |

### Example response

Status: `204 No Content`

_No response body._

<a id="get-api-documents-id-download"></a>
## GET /api/documents/\{id\}/download

**Download a document**

Returns the document content as an attachment

| Property | Value |
| --- | --- |
| Operation ID | `download` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | `binary file` |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | string(uuid) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/api/documents/3f3c7af7-34ae-4dd4-96e4-cbcba52c6b8f/download" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | `string(binary)` |

### Example response

Status: `200 OK`

Binary file response with Content-Disposition: attachment; filename="contract.pdf"

<a id="get-api-documents-id-preview"></a>
## GET /api/documents/\{id\}/preview

**Preview a document inline**

Returns the document content inline (suitable for PDF embed). Falls back to attachment for non-PDF types.

| Property | Value |
| --- | --- |
| Operation ID | `preview` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | `binary file` |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | string(uuid) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/api/documents/3f3c7af7-34ae-4dd4-96e4-cbcba52c6b8f/preview" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | `string(binary)` |

### Example response

Status: `200 OK`

Binary file response. PDFs use Content-Disposition: inline; filename="contract.pdf"
