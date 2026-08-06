---
title: Admin Security API
---

# Admin Security API

Use the Admin Security API to manage users, groups, passwords, group membership, and permission assignments.

## Operations

| Method | Path | Summary |
| --- | --- | --- |
| `GET` | `/admin/groups` | listGroups |
| `POST` | `/admin/groups` | createGroup |
| `PUT` | `/admin/groups/{id}` | updateGroup |
| `DELETE` | `/admin/groups/{id}` | deleteGroup |
| `GET` | `/admin/groups/{id}/users` | getGroupUsers |
| `PUT` | `/admin/groups/{id}/users` | updateGroupUsers |
| `GET` | `/admin/users` | listUsers |
| `POST` | `/admin/users` | createUser |
| `PUT` | `/admin/users/{id}` | updateUser |
| `DELETE` | `/admin/users/{id}` | deleteUser |
| `PUT` | `/admin/users/{id}/password` | resetPassword |

<a id="get-admin-groups"></a>
## GET /admin/groups

| Property | Value |
| --- | --- |
| Operation ID | `listGroups` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [GroupResponse](./schemas)[] |

### Example request

```bash
curl -X GET "http://localhost:8080/admin/groups" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [GroupResponse](./schemas)[] |

### Example response

Status: `200 OK`

```json
[
  {
    "id": 3,
    "code": "PROCESS_OPERATORS",
    "name": "Process Operators",
    "permissions": [
      "ACCESS_BPM_ADMIN",
      "ACCESS_PROCESS_PORTAL"
    ]
  }
]
```

<a id="post-admin-groups"></a>
## POST /admin/groups

| Property | Value |
| --- | --- |
| Operation ID | `createGroup` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | [CreateGroupRequest](./schemas) |
| Request content type | `application/json` |
| Response DTO | [GroupResponse](./schemas) |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | [CreateGroupRequest](./schemas) |

Example request body:

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

### Example request

```bash
curl -X POST "http://localhost:8080/admin/groups" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "code": "PROCESS_OPERATORS",
  "name": "Process Operators",
  "permissionCodes": [
    "ACCESS_BPM_ADMIN",
    "ACCESS_PROCESS_PORTAL"
  ]
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [GroupResponse](./schemas) |

### Example response

Status: `200 OK`

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

<a id="put-admin-groups-id"></a>
## PUT /admin/groups/\{id\}

| Property | Value |
| --- | --- |
| Operation ID | `updateGroup` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | [UpdateGroupRequest](./schemas) |
| Request content type | `application/json` |
| Response DTO | [GroupResponse](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | [UpdateGroupRequest](./schemas) |

Example request body:

```json
{
  "name": "Process Operators",
  "permissionCodes": [
    "ACCESS_BPM_ADMIN",
    "ACCESS_PROCESS_PORTAL"
  ]
}
```

### Example request

```bash
curl -X PUT "http://localhost:8080/admin/groups/123" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "name": "Process Operators",
  "permissionCodes": [
    "ACCESS_BPM_ADMIN",
    "ACCESS_PROCESS_PORTAL"
  ]
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [GroupResponse](./schemas) |

### Example response

Status: `200 OK`

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

<a id="delete-admin-groups-id"></a>
## DELETE /admin/groups/\{id\}

| Property | Value |
| --- | --- |
| Operation ID | `deleteGroup` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | `No body` |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X DELETE "http://localhost:8080/admin/groups/123" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | - |

### Example response

Status: `204 No Content`

_No response body._

<a id="get-admin-groups-id-users"></a>
## GET /admin/groups/\{id\}/users

| Property | Value |
| --- | --- |
| Operation ID | `getGroupUsers` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [UserResponse](./schemas)[] |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X GET "http://localhost:8080/admin/groups/123/users" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [UserResponse](./schemas)[] |

### Example response

Status: `200 OK`

```json
[
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
]
```

<a id="put-admin-groups-id-users"></a>
## PUT /admin/groups/\{id\}/users

| Property | Value |
| --- | --- |
| Operation ID | `updateGroupUsers` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | [UpdateGroupUsersRequest](./schemas) |
| Request content type | `application/json` |
| Response DTO | [UserResponse](./schemas)[] |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | [UpdateGroupUsersRequest](./schemas) |

Example request body:

```json
{
  "userIds": [
    7,
    8
  ]
}
```

### Example request

```bash
curl -X PUT "http://localhost:8080/admin/groups/123/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "userIds": [
    7,
    8
  ]
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [UserResponse](./schemas)[] |

### Example response

Status: `200 OK`

```json
[
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
]
```

<a id="get-admin-users"></a>
## GET /admin/users

| Property | Value |
| --- | --- |
| Operation ID | `listUsers` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [UserResponse](./schemas)[] |

### Example request

```bash
curl -X GET "http://localhost:8080/admin/users" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [UserResponse](./schemas)[] |

### Example response

Status: `200 OK`

```json
[
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
]
```

<a id="post-admin-users"></a>
## POST /admin/users

| Property | Value |
| --- | --- |
| Operation ID | `createUser` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | [CreateUserRequest](./schemas) |
| Request content type | `application/json` |
| Response DTO | [UserResponse](./schemas) |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | [CreateUserRequest](./schemas) |

Example request body:

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

### Example request

```bash
curl -X POST "http://localhost:8080/admin/users" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "username": "modeler.user",
  "password": "change-me-now",
  "enabled": true,
  "groupIds": [
    2
  ],
  "permissionCodes": [
    "ACCESS_BPM_MODELER"
  ]
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [UserResponse](./schemas) |

### Example response

Status: `200 OK`

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

<a id="put-admin-users-id"></a>
## PUT /admin/users/\{id\}

| Property | Value |
| --- | --- |
| Operation ID | `updateUser` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | [UpdateUserRequest](./schemas) |
| Request content type | `application/json` |
| Response DTO | [UserResponse](./schemas) |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | [UpdateUserRequest](./schemas) |

Example request body:

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

### Example request

```bash
curl -X PUT "http://localhost:8080/admin/users/123" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "enabled": true,
  "groupIds": [
    2,
    3
  ],
  "permissionCodes": [
    "ACCESS_BPM_MODELER",
    "ACCESS_PROCESS_PORTAL"
  ]
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [UserResponse](./schemas) |

### Example response

Status: `200 OK`

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

<a id="delete-admin-users-id"></a>
## DELETE /admin/users/\{id\}

| Property | Value |
| --- | --- |
| Operation ID | `deleteUser` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | `No body` |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Example request

```bash
curl -X DELETE "http://localhost:8080/admin/users/123" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | - |

### Example response

Status: `204 No Content`

_No response body._

<a id="put-admin-users-id-password"></a>
## PUT /admin/users/\{id\}/password

| Property | Value |
| --- | --- |
| Operation ID | `resetPassword` |
| Auth | Bearer token required unless security is disabled. |
| Request DTO | [ResetPasswordRequest](./schemas) |
| Request content type | `application/json` |
| Response DTO | `No body` |

### Parameters

| Name | In | Required | Type | Description |
| --- | --- | --- | --- | --- |
| `id` | path | Yes | integer(int64) |  |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | [ResetPasswordRequest](./schemas) |

Example request body:

```json
{
  "password": "new-temporary-password"
}
```

### Example request

```bash
curl -X PUT "http://localhost:8080/admin/users/123/password" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
  "password": "new-temporary-password"
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | - |

### Example response

Status: `204 No Content`

_No response body._
