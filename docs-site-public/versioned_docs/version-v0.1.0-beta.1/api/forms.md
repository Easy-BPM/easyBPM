---
title: Forms API
---

# Forms API

Use the Forms API to deploy and retrieve versioned form definitions by stable form ID.

## Operations

| Method | Path | Summary |
| --- | --- | --- |
| `GET` | `/forms` | Get all form versions |
| `POST` | `/forms` | Deploy a form |
| `GET` | `/forms/{id}` | Get form by ID |
| `GET` | `/forms/latest` | Get latest form version |

<a id="get-forms"></a>
## GET /forms

**Get all form versions**

Retrieve all versions of a form by formId. The name parameter is also supported for lookup by form name.

| Property | Value |
| --- | --- |
| Operation ID | `getAllVersions` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [Form](./schemas)[] |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `formId` | query | No | string |  |
| `name` | query | No | string |  |

### Example request

```bash
curl -X GET "http://localhost:8080/forms?formId=expenseReview" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [Form](./schemas)[] |

### Example response

Status: `200 OK`

```json
[
  {
    "id": 12,
    "formId": "expenseReview",
    "name": "Expense Review",
    "version": 1,
    "createdAt": "2026-06-17T10:00:00",
    "schema": {
      "type": "object",
      "title": "Expense Review",
      "properties": {
        "approved": {
          "type": "boolean"
        },
        "comment": {
          "type": "string"
        }
      }
    }
  }
]
```

<a id="post-forms"></a>
## POST /forms

**Deploy a form**

Create and deploy a new form definition

| Property | Value |
| --- | --- |
| Operation ID | `deployForm` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | [DeployFormRequest](./schemas) |
| Request content type | `application/json` |
| Response DTO | [Form](./schemas) |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | [DeployFormRequest](./schemas) |

Example request body:

```json
{
  "formId": "expenseReview",
  "name": "Expense Review",
  "schema": {
    "type": "object",
    "title": "Expense Review",
    "required": [
      "approved"
    ],
    "properties": {
      "approved": {
        "type": "boolean",
        "title": "Approve request"
      },
      "comment": {
        "type": "string",
        "title": "Manager comment"
      }
    }
  }
}
```

### Example request

```bash
curl -X POST "http://localhost:8080/forms" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "formId": "expenseReview",
  "name": "Expense Review",
  "schema": {
    "type": "object",
    "title": "Expense Review",
    "required": [
      "approved"
    ],
    "properties": {
      "approved": {
        "type": "boolean",
        "title": "Approve request"
      },
      "comment": {
        "type": "string",
        "title": "Manager comment"
      }
    }
  }
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [Form](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 12,
  "formId": "expenseReview",
  "name": "Expense Review",
  "version": 1,
  "createdAt": "2026-06-17T10:00:00",
  "schema": {
    "type": "object",
    "title": "Expense Review",
    "properties": {
      "approved": {
        "type": "boolean"
      },
      "comment": {
        "type": "string"
      }
    }
  }
}
```

<a id="get-forms-id"></a>
## GET /forms/\{id\}

**Get form by ID**

Retrieve a specific form by its ID

| Property | Value |
| --- | --- |
| Operation ID | `getById` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [Form](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/forms/123" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [Form](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 12,
  "formId": "expenseReview",
  "name": "Expense Review",
  "version": 1,
  "createdAt": "2026-06-17T10:00:00",
  "schema": {
    "type": "object",
    "title": "Expense Review",
    "properties": {
      "approved": {
        "type": "boolean"
      },
      "comment": {
        "type": "string"
      }
    }
  }
}
```

<a id="get-forms-latest"></a>
## GET /forms/latest

**Get latest form version**

Retrieve the latest version of a form by formId. The name parameter is also supported for lookup by form name.

| Property | Value |
| --- | --- |
| Operation ID | `getLatest` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [Form](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `formId` | query | No | string |  |
| `name` | query | No | string |  |

### Example request

```bash
curl -X GET "http://localhost:8080/forms/latest?formId=expenseReview" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [Form](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "id": 12,
  "formId": "expenseReview",
  "name": "Expense Review",
  "version": 1,
  "createdAt": "2026-06-17T10:00:00",
  "schema": {
    "type": "object",
    "title": "Expense Review",
    "properties": {
      "approved": {
        "type": "boolean"
      },
      "comment": {
        "type": "string"
      }
    }
  }
}
```
