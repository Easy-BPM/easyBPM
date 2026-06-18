---
title: Schemas
---

# Schemas

These schemas come from the backend OpenAPI components and the Kotlin DTOs that back the public API examples.

## AICredentialCreateRequestDto

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `providerId` | string | No |  |
| `credentialType` | string | No |  |
| `token` | string | No |  |

Example:

```json
{
  "providerId": "openai",
  "credentialType": "API_KEY",
  "token": "sk-live-redacted-example"
}
```

## AICredentialResponseDto

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | string | No |  |
| `providerId` | string | No |  |
| `credentialType` | string | No |  |
| `maskedToken` | string | No |  |
| `createdAt` | string | No |  |
| `updatedAt` | string | No |  |
| `lastUsedAt` | string | No |  |
| `permissions` | string[] | No |  |

Example:

```json
{
  "id": "3f3c7af7-34ae-4dd4-96e4-cbcba52c6b8f",
  "providerId": "openai",
  "credentialType": "API_KEY",
  "maskedToken": "sk-***...mple",
  "createdAt": "2026-06-17T10:00:00",
  "updatedAt": "2026-06-17T10:00:00",
  "lastUsedAt": null,
  "permissions": []
}
```

## AssignProcessVariablesRequest

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `variables` | object | No | Map of variable names to values. Supports primitive and nested JSON values. |

Example:

```json
{
  "variables": {
    "approved": true,
    "amount": 1250.75,
    "currency": "USD",
    "requester": {
      "id": 42,
      "name": "Alice"
    }
  }
}
```

## CallActivityMappingResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer(int64) | No |  |
| `parentInstanceId` | integer(int64) | No |  |
| `childInstanceId` | integer(int64) | No |  |
| `callActivityNodeId` | string | No |  |
| `inputMappings` | object | No |  |
| `outputMappings` | object | No |  |
| `propagateAllVariables` | boolean | No |  |

Example:

```json
{
  "id": 9,
  "parentInstanceId": 100,
  "childInstanceId": 101,
  "callActivityNodeId": "run-kyc",
  "inputMappings": {
    "customerId": "customerId"
  },
  "outputMappings": {
    "kycStatus": "kycStatus"
  },
  "propagateAllVariables": false
}
```

## ClassMetadataResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `className` | string | No |  |
| `methods` | MethodMetadataResponse[] | No |  |

Example:

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

## CodeTaskExecutionAuditResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `executionId` | integer(int64) | No |  |
| `instanceId` | integer(int64) | No |  |
| `nodeId` | string | No |  |
| `jarId` | integer(int64) | No |  |
| `className` | string | No |  |
| `methodName` | string | No |  |
| `inputVariables` | string | No |  |
| `outputVariables` | string | No |  |
| `executionTimeMs` | integer(int32) | No |  |
| `status` | string | No |  |
| `errorMessage` | string | No |  |
| `executedAt` | string | No |  |

Example:

```json
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
```

## CodeTaskJarUploadResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `jarId` | integer(int64) | No |  |
| `fileName` | string | No |  |
| `fileHash` | string | No |  |
| `uploadedAt` | string | No |  |
| `classCount` | integer(int32) | No |  |
| `methodCount` | integer(int32) | No |  |
| `classes` | string[] | No |  |

Example:

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

## CreateGroupRequest

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `code` | string | No |  |
| `name` | string | No |  |
| `permissionCodes` | string[] | No |  |

Example:

```json
{
  "code": "PROCESS_OPERATORS",
  "name": "Process Operators",
  "permissionCodes": [
    "ACCESS_BPM_ADMIN",
    "ACCESS_PROCESS_PORTAL"
  ]
}
```

## CreateUserRequest

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `username` | string | No |  |
| `password` | string | No |  |
| `enabled` | boolean | No |  |
| `groupIds` | integer[] | No |  |
| `permissionCodes` | string[] | No |  |

Example:

```json
{
  "username": "modeler.user",
  "password": "change-me-now",
  "enabled": true,
  "groupIds": [
    2
  ],
  "permissionCodes": [
    "ACCESS_BPM_MODELER"
  ]
}
```

## CurrentUserResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `username` | string | No |  |
| `groups` | string[] | No |  |
| `permissions` | string[] | No |  |

Example:

```json
{
  "username": "admin",
  "groups": [
    "ADMIN"
  ],
  "permissions": [
    "ACCESS_BPM_ADMIN",
    "ACCESS_BPM_MODELER",
    "ACCESS_PROCESS_PORTAL"
  ]
}
```

## DeployFormRequest

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `formId` | string | No |  |
| `name` | string | No |  |
| `schema` | JsonNode | No |  |

Example:

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

## DocumentResponseDto

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | string(uuid) | No |  |
| `fileName` | string | No |  |
| `contentType` | string | No |  |
| `fileSize` | integer(int64) | No |  |
| `taskId` | integer(int64) | No |  |
| `processInstanceId` | integer(int64) | No |  |
| `formFieldKey` | string | No |  |
| `uploadedBy` | string | No |  |
| `createdAt` | string(date-time) | No |  |

Example:

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

## ExecutionAuditPageResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `content` | CodeTaskExecutionAuditResponse[] | No |  |
| `totalElements` | integer(int64) | No |  |
| `totalPages` | integer(int32) | No |  |
| `currentPage` | integer(int32) | No |  |

Example:

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

## Form

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer(int64) | No |  |
| `formId` | string | No |  |
| `name` | string | No |  |
| `schema` | JsonNode | No |  |
| `version` | integer(int32) | No |  |
| `createdAt` | string(date-time) | No |  |

Example:

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

## GroupResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer(int64) | No |  |
| `code` | string | No |  |
| `name` | string | No |  |
| `permissions` | string[] | No |  |

Example:

```json
{
  "id": 3,
  "code": "PROCESS_OPERATORS",
  "name": "Process Operators",
  "permissions": [
    "ACCESS_BPM_ADMIN",
    "ACCESS_PROCESS_PORTAL"
  ]
}
```

## JarClassesResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `jarId` | integer(int64) | No |  |
| `fileName` | string | No |  |
| `classes` | string[] | No |  |

Example:

```json
{
  "jarId": 1,
  "fileName": "customer-rules.jar",
  "classes": [
    "com.example.Rules"
  ]
}
```

## JsonNode

Type: `object`


## LoginRequest

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `username` | string | No |  |
| `password` | string | No |  |

Example:

```json
{
  "username": "admin",
  "password": "admin"
}
```

## LoginResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `token` | string | No |  |
| `tokenType` | string | No |  |
| `username` | string | No |  |
| `groups` | string[] | No |  |
| `permissions` | string[] | No |  |

Example:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.example-token",
  "tokenType": "Bearer",
  "username": "admin",
  "groups": [
    "ADMIN"
  ],
  "permissions": [
    "ACCESS_BPM_ADMIN",
    "ACCESS_BPM_MODELER",
    "ACCESS_PROCESS_PORTAL",
    "MANAGE_USERS",
    "MANAGE_GROUPS"
  ]
}
```

## MethodMetadataResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `methodName` | string | No |  |
| `returnType` | string | No |  |
| `signature` | string | No |  |
| `parameters` | string[] | No |  |
| `parameterNames` | string[] | No |  |
| `static` | boolean | No |  |

Example:

```json
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
```

## MoveNodeRequest

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `fromNode` | string | No | Current node id where the token is located |
| `toNode` | string | No | Target node id where the token should move |
| `reason` | string | No | Business reason for manual intervention |

Example:

```json
{
  "fromNode": "manual-review",
  "toNode": "approve-request",
  "reason": "SLA escalation approved by supervisor"
}
```

## Pageable

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `page` | integer(int32) | No |  |
| `size` | integer(int32) | No |  |
| `sort` | string[] | No |  |

Example:

```json
{
  "page": 1,
  "size": 1,
  "sort": [
    "string"
  ]
}
```

## PageableObject

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `offset` | integer(int64) | No |  |
| `sort` | SortObject | No |  |
| `unpaged` | boolean | No |  |
| `paged` | boolean | No |  |
| `pageNumber` | integer(int32) | No |  |
| `pageSize` | integer(int32) | No |  |

Example:

```json
{
  "offset": 123,
  "sort": {
    "empty": true,
    "unsorted": true,
    "sorted": true
  },
  "unpaged": true,
  "paged": true,
  "pageNumber": 1,
  "pageSize": 1
}
```

## PageProcessDefinition

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `totalElements` | integer(int64) | No |  |
| `totalPages` | integer(int32) | No |  |
| `first` | boolean | No |  |
| `last` | boolean | No |  |
| `size` | integer(int32) | No |  |
| `content` | ProcessDefinition[] | No |  |
| `number` | integer(int32) | No |  |
| `sort` | SortObject | No |  |
| `numberOfElements` | integer(int32) | No |  |
| `pageable` | PageableObject | No |  |
| `empty` | boolean | No |  |

Example:

```json
{
  "content": [
    {
      "id": 10,
      "key": "expense-approval",
      "processName": "Expense Approval",
      "description": "Review and approve expense requests.",
      "version": 3,
      "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 1,
  "empty": false,
  "sort": {
    "empty": true,
    "sorted": false,
    "unsorted": true
  },
  "pageable": {
    "offset": 0,
    "pageNumber": 0,
    "pageSize": 20,
    "paged": true,
    "unpaged": false,
    "sort": {
      "empty": true,
      "sorted": false,
      "unsorted": true
    }
  }
}
```

## PageProcessInstance

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `totalElements` | integer(int64) | No |  |
| `totalPages` | integer(int32) | No |  |
| `first` | boolean | No |  |
| `last` | boolean | No |  |
| `size` | integer(int32) | No |  |
| `content` | ProcessInstance[] | No |  |
| `number` | integer(int32) | No |  |
| `sort` | SortObject | No |  |
| `numberOfElements` | integer(int32) | No |  |
| `pageable` | PageableObject | No |  |
| `empty` | boolean | No |  |

Example:

```json
{
  "content": [
    {
      "id": 456,
      "processDefinition": {
        "id": 10,
        "key": "expense-approval",
        "processName": "Expense Approval",
        "description": "Review and approve expense requests.",
        "version": 3,
        "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
      },
      "status": "ACTIVE",
      "currentNode": [
        "manager-review"
      ],
      "nodeHistory": [
        "start",
        "manager-review"
      ],
      "createdAt": "2026-06-17T10:00:00",
      "updatedAt": "2026-06-17T10:00:01",
      "parentInstanceId": null,
      "callActivityNodeId": null,
      "nestingLevel": 0,
      "completionNodeId": null
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 1,
  "empty": false,
  "sort": {
    "empty": true,
    "sorted": false,
    "unsorted": true
  },
  "pageable": {
    "offset": 0,
    "pageNumber": 0,
    "pageSize": 20,
    "paged": true,
    "unpaged": false,
    "sort": {
      "empty": true,
      "sorted": false,
      "unsorted": true
    }
  }
}
```

## PageTaskResponseDto

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `totalElements` | integer(int64) | No |  |
| `totalPages` | integer(int32) | No |  |
| `first` | boolean | No |  |
| `last` | boolean | No |  |
| `size` | integer(int32) | No |  |
| `content` | TaskResponseDto[] | No |  |
| `number` | integer(int32) | No |  |
| `sort` | SortObject | No |  |
| `numberOfElements` | integer(int32) | No |  |
| `pageable` | PageableObject | No |  |
| `empty` | boolean | No |  |

Example:

```json
{
  "content": [
    {
      "id": 123,
      "title": "Manager Review",
      "name": "Manager Review",
      "description": "Review the expense request.",
      "processInstanceId": 456,
      "nodeId": "manager-review",
      "assignee": "manager",
      "candidateUsers": [],
      "candidateGroups": [
        "FINANCE"
      ],
      "status": "PENDING",
      "createdAt": "2026-06-17T09:30:00",
      "completedAt": null,
      "formDbId": 12,
      "formId": "expenseReview",
      "variables": {
        "amount": 1250.75,
        "requester": "Alice"
      }
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true,
  "size": 20,
  "number": 0,
  "numberOfElements": 1,
  "empty": false,
  "sort": {
    "empty": true,
    "sorted": false,
    "unsorted": true
  },
  "pageable": {
    "offset": 0,
    "pageNumber": 0,
    "pageSize": 20,
    "paged": true,
    "unpaged": false,
    "sort": {
      "empty": true,
      "sorted": false,
      "unsorted": true
    }
  }
}
```

## ProcessDefinition

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer(int64) | No |  |
| `description` | string | No |  |
| `version` | integer(int32) | No |  |
| `definitionJson` | string | No |  |
| `key` | string | No |  |
| `processName` | string | No |  |

Example:

```json
{
  "id": 10,
  "key": "expense-approval",
  "processName": "Expense Approval",
  "description": "Review and approve expense requests.",
  "version": 3,
  "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
}
```

## ProcessInstance

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer(int64) | No |  |
| `processDefinition` | ProcessDefinition | No |  |
| `status` | string | No |  |
| `currentNode` | string[] | No |  |
| `nodeHistory` | string[] | No |  |
| `createdAt` | string(date-time) | No |  |
| `updatedAt` | string(date-time) | No |  |
| `parentInstanceId` | integer(int64) | No |  |
| `callActivityNodeId` | string | No |  |
| `nestingLevel` | integer(int32) | No |  |
| `completionNodeId` | string | No |  |

Example:

```json
{
  "id": 456,
  "processDefinition": {
    "id": 10,
    "key": "expense-approval",
    "processName": "Expense Approval",
    "description": "Review and approve expense requests.",
    "version": 3,
    "definitionJson": "{\"processId\":\"expense-approval\",\"nodes\":[{\"id\":\"start\",\"type\":\"StartEvent\"},{\"id\":\"manager-review\",\"type\":\"HumanTask\"},{\"id\":\"end\",\"type\":\"EndEvent\"}],\"flows\":[{\"source\":\"start\",\"target\":\"manager-review\"},{\"source\":\"manager-review\",\"target\":\"end\"}]}"
  },
  "status": "ACTIVE",
  "currentNode": [
    "manager-review"
  ],
  "nodeHistory": [
    "start",
    "manager-review"
  ],
  "createdAt": "2026-06-17T10:00:00",
  "updatedAt": "2026-06-17T10:00:01",
  "parentInstanceId": null,
  "callActivityNodeId": null,
  "nestingLevel": 0,
  "completionNodeId": null
}
```

## ProcessVariable

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer(int64) | No |  |
| `processInstanceId` | integer(int64) | No |  |
| `name` | string | No |  |
| `value` | JsonNode | No |  |
| `createdAt` | string(date-time) | No |  |
| `updatedAt` | string(date-time) | No |  |

Example:

```json
{
  "id": 88,
  "processInstanceId": 456,
  "name": "approved",
  "value": true,
  "createdAt": "2026-06-17T10:00:00",
  "updatedAt": "2026-06-17T10:00:00"
}
```

## ResetPasswordRequest

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `password` | string | No |  |

Example:

```json
{
  "password": "new-temporary-password"
}
```

## SortObject

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `empty` | boolean | No |  |
| `unsorted` | boolean | No |  |
| `sorted` | boolean | No |  |

Example:

```json
{
  "empty": true,
  "unsorted": true,
  "sorted": true
}
```

## TaskResponseDto

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer(int64) | No |  |
| `title` | string | No |  |
| `name` | string | No |  |
| `description` | string | No |  |
| `processInstanceId` | integer(int64) | No |  |
| `nodeId` | string | No |  |
| `assignee` | string | No |  |
| `candidateUsers` | string[] | No |  |
| `candidateGroups` | string[] | No |  |
| `status` | string | No |  |
| `createdAt` | string(date-time) | No |  |
| `completedAt` | string(date-time) | No |  |
| `formDbId` | integer(int64) | No |  |
| `formId` | string | No |  |
| `variables` | object | No |  |

Example:

```json
{
  "id": 123,
  "title": "Manager Review",
  "name": "Manager Review",
  "description": "Review the expense request.",
  "processInstanceId": 456,
  "nodeId": "manager-review",
  "assignee": "manager",
  "candidateUsers": [],
  "candidateGroups": [
    "FINANCE"
  ],
  "status": "PENDING",
  "createdAt": "2026-06-17T09:30:00",
  "completedAt": null,
  "formDbId": 12,
  "formId": "expenseReview",
  "variables": {
    "amount": 1250.75,
    "requester": "Alice"
  }
}
```

## UpdateGroupRequest

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `name` | string | No |  |
| `permissionCodes` | string[] | No |  |

Example:

```json
{
  "name": "Process Operators",
  "permissionCodes": [
    "ACCESS_BPM_ADMIN",
    "ACCESS_PROCESS_PORTAL"
  ]
}
```

## UpdateGroupUsersRequest

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `userIds` | integer[] | No |  |

Example:

```json
{
  "userIds": [
    7,
    8
  ]
}
```

## UpdateUserRequest

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `enabled` | boolean | No |  |
| `groupIds` | integer[] | No |  |
| `permissionCodes` | string[] | No |  |

Example:

```json
{
  "enabled": true,
  "groupIds": [
    2,
    3
  ],
  "permissionCodes": [
    "ACCESS_BPM_MODELER",
    "ACCESS_PROCESS_PORTAL"
  ]
}
```

## UserResponse

| Property | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | integer(int64) | No |  |
| `username` | string | No |  |
| `enabled` | boolean | No |  |
| `groups` | string[] | No |  |
| `permissions` | string[] | No |  |

Example:

```json
{
  "id": 7,
  "username": "modeler.user",
  "enabled": true,
  "groups": [
    "MODELERS"
  ],
  "permissions": [
    "ACCESS_BPM_MODELER"
  ]
}
```
