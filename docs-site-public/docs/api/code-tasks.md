---
title: Code Tasks API
---

# Code Tasks API

Use the Code Tasks API to upload JVM JARs, discover classes and methods, and inspect execution audit history.

## Operations

| Method | Path | Summary |
| --- | --- | --- |
| `GET` | `/code-tasks/executions` | getExecutions |
| `GET` | `/code-tasks/jar/{jarId}/classes` | getJarClasses |
| `GET` | `/code-tasks/jar/{jarId}/classes/{className}/methods` | getClassMethods |
| `POST` | `/code-tasks/upload` | uploadJar |

<a id="get-code-tasks-executions"></a>
## GET /code-tasks/executions

| Property | Value |
| --- | --- |
| Operation ID | `getExecutions` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [ExecutionAuditPageResponse](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `instanceId` | query | No | integer(int64) |  |
| `status` | query | No | string |  |
| `page` | query | No | integer(int32) |  |
| `size` | query | No | integer(int32) |  |
| `pageable` | query | Yes | Pageable |  |

### Example request

```bash
curl -X GET "http://localhost:8080/code-tasks/executions?instanceId=456&status=COMPLETED&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ExecutionAuditPageResponse](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "content": [
    {
      "executionId": 55,
      "instanceId": 456,
      "nodeId": "calculate-risk",
      "jarId": 1,
      "className": "com.example.Rules",
      "methodName": "calculateRisk",
      "inputVariables": "{\"amount\":1250.75,\"customerTier\":\"GOLD\"}",
      "outputVariables": "{\"riskScore\":12}",
      "executionTimeMs": 38,
      "status": "COMPLETED",
      "errorMessage": null,
      "executedAt": "2026-06-17T10:00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "currentPage": 0
}
```

<a id="get-code-tasks-jar-jarid-classes"></a>
## GET /code-tasks/jar/\{jarId\}/classes

| Property | Value |
| --- | --- |
| Operation ID | `getJarClasses` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [JarClassesResponse](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `jarId` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/code-tasks/jar/1/classes" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [JarClassesResponse](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "jarId": 1,
  "fileName": "customer-rules.jar",
  "classes": [
    "com.example.Rules"
  ]
}
```

<a id="get-code-tasks-jar-jarid-classes-classname-methods"></a>
## GET /code-tasks/jar/\{jarId\}/classes/\{className\}/methods

| Property | Value |
| --- | --- |
| Operation ID | `getClassMethods` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [ClassMetadataResponse](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `jarId` | path | Yes | integer(int64) |  |
| `className` | path | Yes | string |  |

### Example request

```bash
curl -X GET "http://localhost:8080/code-tasks/jar/1/classes/com.example.Rules/methods" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [ClassMetadataResponse](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "className": "com.example.Rules",
  "methods": [
    {
      "methodName": "calculateRisk",
      "returnType": "java.lang.Integer",
      "signature": "calculateRisk(java.math.BigDecimal, java.lang.String)",
      "parameters": [
        "java.math.BigDecimal",
        "java.lang.String"
      ],
      "parameterNames": [
        "param0",
        "param1"
      ],
      "static": false
    }
  ]
}
```

<a id="post-code-tasks-upload"></a>
## POST /code-tasks/upload

| Property | Value |
| --- | --- |
| Operation ID | `uploadJar` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | `object` |
| Request content type | `application/json` |
| Response DTO | [CodeTaskJarUploadResponse](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `description` | query | No | string |  |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| No | `application/json` | `object` |

Example request body:

```json
{
  "jarFile": "@contract.pdf"
}
```

### Example request

```bash
curl -X POST "http://localhost:8080/code-tasks/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "jarFile=@customer-rules.jar" \
  -F "description=Customer rules library"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [CodeTaskJarUploadResponse](./schemas) |

### Example response

Status: `200 OK`

```json
{
  "jarId": 1,
  "fileName": "customer-rules.jar",
  "fileHash": "b6f2f2d0f1a5c3e4d7a8b9c0e1f23456789abcdef0123456789abcdef012345",
  "uploadedAt": "2026-06-17T10:00:00",
  "classCount": 1,
  "methodCount": 2,
  "classes": [
    "com.example.Rules"
  ]
}
```
