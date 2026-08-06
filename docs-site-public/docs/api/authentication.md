---
title: Authentication API
---

# Authentication API

Use the Authentication API to sign users in and inspect the current JWT session.

## Token flow

1. Call `POST /auth/login` with username and password.
2. Store the returned JWT in your client session.
3. Send `Authorization: Bearer <token>` on protected requests.
4. Call `GET /auth/me` to validate the current session and inspect permissions.

## Operations

| Method | Path | Summary |
| --- | --- | --- |
| `POST` | `/auth/login` | login |
| `GET` | `/auth/me` | me |

<a id="post-auth-login"></a>
## POST /auth/login

| Property | Value |
| --- | --- |
| Operation ID | `login` |
| Auth | No token required. |
| Request DTO | [LoginRequest](./schemas) |
| Request content type | `application/json` |
| Response DTO | [LoginResponse](./schemas) |

### Request body

| Required | Content type | DTO/schema |
| --- | --- | --- |
| Yes | `application/json` | [LoginRequest](./schemas) |

Example request body:

```json
{
  "username": "admin",
  "password": "admin"
}
```

### Example request

```bash
curl -X POST "http://localhost:8080/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
  "username": "admin",
  "password": "admin"
}'
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [LoginResponse](./schemas) |

### Example response

Status: `200 OK`

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

<a id="get-auth-me"></a>
## GET /auth/me

| Property | Value |
| --- | --- |
| Operation ID | `me` |
| Auth | Bearer token required unless security is disabled. |
| Response DTO | [CurrentUserResponse](./schemas) |

### Example request

```bash
curl -X GET "http://localhost:8080/auth/me" \
  -H "Authorization: Bearer $TOKEN"
```

### Responses

| Status | Description | Schema |
| --- | --- | --- |
| `200` | OK | [CurrentUserResponse](./schemas) |

### Example response

Status: `200 OK`

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
