---
id: document-handling
title: Document Handling in Forms
sidebar_label: Document Handling
---

# Document Handling in Forms

Easy BPM supports native document handling capabilities inside forms.  
You can upload, download, and preview files (including PDFs) as part of a business process task — providing an experience similar to document-centric workflows in IBM BPM / BAW.

---

## Overview

Three form component types are available:

| Component | JSON Schema format | Description |
|---|---|---|
| **File Upload** | `fileUpload` | Upload a single file from a task form |
| **File Download** | `fileDownload` | Download a previously uploaded document |
| **PDF Viewer** | `pdfViewer` | Inline PDF preview (embed/iframe) with zoom and new-tab |

All binary content is stored in the `documents` table in PostgreSQL.  
Field values in task variables hold the **document UUID** — a stable reference that travels with the process instance.

---

## Backend Architecture

### Database Schema (V24 migration)

```sql
CREATE TABLE documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name    VARCHAR(255)  NOT NULL,
    content_type VARCHAR(255)  NOT NULL,
    file_size    BIGINT        NOT NULL,
    content      BYTEA         NOT NULL,
    task_id      BIGINT        REFERENCES task(id) ON DELETE SET NULL,
    process_instance_id BIGINT REFERENCES process_instance(id) ON DELETE SET NULL,
    form_field_key VARCHAR(255),
    uploaded_by  VARCHAR(255),
    created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Documents are associated with:
- **task_id** — the task being worked on at upload time
- **process_instance_id** — the parent process instance
- **form_field_key** — the field variable name inside the form

### Replace semantics

When a user uploads a new file to a field that already has an uploaded document (same `task_id` + `form_field_key`), the previous document is **deleted automatically** before the new one is saved. This prevents orphaned blobs.

---

## REST API Reference

All endpoints live under `/api/documents`.  
Authentication is required (same permissions as `/tasks/**`).

### Upload a Document

```http
POST /api/documents
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

**Form parameters:**

| Parameter | Required | Description |
|---|---|---|
| `file` | ✅ | The file to upload (multipart) |
| `taskId` | ❌ | Associate with a task |
| `processInstanceId` | ❌ | Associate with a process instance |
| `formFieldKey` | ❌ | Field variable name (used for replace semantics) |

**Response `201 Created`:**
```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "fileName": "contract.pdf",
  "contentType": "application/pdf",
  "fileSize": 102400,
  "taskId": 42,
  "processInstanceId": 7,
  "formFieldKey": "supportingDocument",
  "uploadedBy": "alice",
  "createdAt": "2026-05-21T12:00:00"
}
```

**Validation:**
- Maximum file size: **20 MB**
- Allowed content types: `application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `application/vnd.ms-excel`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `image/png`, `image/jpeg`, `text/plain`
- Allowed extensions: `pdf`, `doc`, `docx`, `xls`, `xlsx`, `png`, `jpg`, `jpeg`, `txt`
- Returns `400 Bad Request` on validation failure

---

### Get Document Metadata

```http
GET /api/documents/{id}
Authorization: Bearer <token>
```

Returns metadata only — no binary content.

**Response `200 OK`:** (same shape as upload response)

---

### Download a Document

```http
GET /api/documents/{id}/download
Authorization: Bearer <token>
```

Returns binary content with:
```
Content-Disposition: attachment; filename="contract.pdf"
Cache-Control: no-store
X-Content-Type-Options: nosniff
```

---

### Preview a Document (Inline)

```http
GET /api/documents/{id}/preview
Authorization: Bearer <token>
```

For **PDF files** returns:
```
Content-Disposition: inline; filename="contract.pdf"
```

For **non-PDF files** falls back to attachment disposition.  
This endpoint is safe to use as an `<iframe>` or `<embed>` `src` for PDF preview.

---

### Delete a Document

```http
DELETE /api/documents/{id}
Authorization: Bearer <token>
```

Returns `204 No Content` on success.  
Returns `404 Not Found` if the document does not exist.

---

### List Documents for a Task

```http
GET /api/documents?taskId={taskId}
Authorization: Bearer <token>
```

Returns an array of `DocumentResponseDto` objects for all documents associated with the task.

---

## Form Definition

### File Upload Field

```json
{
  "type": "string",
  "format": "fileUpload",
  "title": "Supporting Document",
  "allowedExtensions": ["pdf", "docx"],
  "maxSizeMb": 20,
  "readOnly": false
}
```

**Form schema example:**
```json
{
  "formId": "expenseApproval",
  "name": "Expense Approval",
  "schema": {
    "type": "object",
    "title": "Expense Approval Form",
    "required": ["receipt"],
    "properties": {
      "amount": { "type": "number", "title": "Amount (USD)" },
      "receipt": {
        "type": "string",
        "format": "fileUpload",
        "title": "Receipt",
        "allowedExtensions": ["pdf", "png", "jpg"],
        "maxSizeMb": 5
      }
    }
  }
}
```

The **value** stored in task variables for this field is the **document UUID** (string) returned by the upload API.

---

### File Download Field

```json
{
  "type": "string",
  "format": "fileDownload",
  "title": "Generated Contract"
}
```

The field value is a **document UUID** that was previously stored in the process variables (e.g. injected by a service task or a prior user task). The portal renders a download button with the original filename.

---

### PDF Viewer Field

```json
{
  "type": "string",
  "format": "pdfViewer",
  "title": "Document Preview"
}
```

The field value is a **document UUID**.  
For PDF content types the portal renders an inline `<iframe>` with:
- Zoom and pagination controlled by the browser's native PDF viewer
- **Open in new tab** button
- **Download** button
- **Fullscreen** toggle

For non-PDF content types a fallback download button is shown.

---

## Task Portal User Flow

### Uploading a document

1. Task performer opens a task with a `fileUpload` field.
2. They drag & drop or browse to select a file.
3. Client-side validation runs (extension, size).
4. The file is uploaded via `POST /api/documents?taskId=X&formFieldKey=receipt`.
5. The server returns a UUID which is stored as the field value.
6. The field shows the uploaded filename with a remove button.
7. On **Complete Task**, the UUID is submitted as a process variable along with other field values.

### Downloading a document

1. A subsequent task (or the same task in read-only mode) has a `fileDownload` field.
2. The process variable holds the document UUID from the earlier upload.
3. The portal loads document metadata (filename, size, type).
4. A download link is rendered pointing to `GET /api/documents/{id}/download`.

### Previewing a PDF

1. A task form has a `pdfViewer` field whose value is a PDF document UUID.
2. The portal loads metadata and renders an `<iframe src="/api/documents/{id}/preview">`.
3. The user can scroll, zoom, open in new tab, or download.

---

## BPMN Modeler — Adding Document Fields

1. In the **Easy BPMN Modeler**, open the **Form Modeler** tab.
2. The left palette now includes three document field types:
   - 📤 **File Upload**
   - 📥 **File Download**
   - 📄 **PDF Viewer**
3. Click to add the field to the current tab.
4. Select the field and configure in the **Properties** panel:
   - **Field Title** — display name
   - **Variable Name (ID)** — the process variable key (will hold the UUID)
   - For `fileUpload`: set **Allowed Extensions** and **Max File Size (MB)**
   - Mark the field as **Required** if needed
5. **Deploy Form** to publish the schema to `POST /forms`.

The generated JSON schema correctly emits `format: fileUpload | fileDownload | pdfViewer` so the Task Portal knows which component to render.

---

## Security

All document endpoints require authentication via JWT bearer token.  
The required permission is `ACCESS_PROCESS_PORTAL` or `ACCESS_BPM_ADMIN` (same as task endpoints).

**Validation enforced server-side:**
- Content type allowlist (cannot be bypassed client-side)
- File extension allowlist
- Max file size: 20 MB
- Filename sanitization (path traversal prevention)

> ⚠️ The current implementation does not restrict document access by task/process ownership.  
> Any authenticated user with `ACCESS_PROCESS_PORTAL` can access any document UUID.  
> Fine-grained per-instance authorization is planned for a future phase.

---

## Testing

### Backend unit tests

| Class | Coverage |
|---|---|
| `DocumentServiceTest` | File validation, replace semantics, metadata, content streaming, list by task, delete, filename sanitization |
| `DocumentControllerTest` | All endpoints, success and error paths, content disposition for PDF vs non-PDF |

### Integration tests

| Class | Scenario |
|---|---|
| `DocumentIntegrationTest` | Unauthenticated upload returns 401 |
| | Authenticated upload persists document |
| | Rejected content type returns 400 |
| | Empty file returns 400 |
| | Download returns attachment header |
| | Preview returns inline header for PDF |
| | Preview returns attachment header for non-PDF (image) |
| | Delete removes document |
| | List without taskId returns 400 |
| | Replace: second upload for same task+field deletes first |

Run all tests:
```bash
./gradlew test
```

---

## Limitations and Future Enhancements

| Item | Status |
|---|---|
| Document versioning | 🔜 Future |
| Per-process-instance access control | 🔜 Future |
| Virus scanning | 🔜 Future |
| OCR extraction | 🔜 Future |
| Bulk uploads | 🔜 Future |
| Document expiration / TTL | 🔜 Future |
| External DMS integration (S3, SharePoint) | 🔜 Future |
| Image preview (non-PDF) | 🔜 Future |
