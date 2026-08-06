---
title: API Overview
---

# API Overview

This API reference is generated from the backend OpenAPI contract exposed at `/v3/api-docs` and enriched with DTO-based request and response examples from the Easy BPM backend controllers. The same contract powers Swagger UI at `/swagger-ui.html`.

## Base URL

Local development:

```text
http://localhost:8080
```

Customer environments should replace this with the HTTPS API origin for that deployment.

## Authentication

Most product endpoints require a JWT bearer token. Sign in with `POST /auth/login` and send the returned token as:

```http
Authorization: Bearer <token>
```

## Interactive API tools

| Resource | URL |
| --- | --- |
| Swagger UI | `/swagger-ui.html` |
| OpenAPI JSON | `/v3/api-docs` |
| Static OpenAPI copy in this docs site | [Download JSON](/openapi/easybpm-openapi.json) |

## API groups

| Group | Operations | Page |
| --- | ---: | --- |
| Authentication | 2 | [Authentication API](./authentication) |
| Agent Processes | 4 | [Agent Processes API](./agent-processes) |
| Processes | 15 | [Processes API](./processes) |
| Tasks | 5 | [Tasks API](./tasks) |
| Forms | 4 | [Forms API](./forms) |
| Documents | 6 | [Documents API](./documents) |
| Code Tasks | 4 | [Code Tasks API](./code-tasks) |
| Admin Security | 11 | [Admin Security API](./admin-security) |
| AI Credentials | 5 | [AI Credentials API](./ai-credentials) |

## Endpoint index

| Method | Path | Operation | Request DTO | Response DTO | Group |
| --- | --- | --- | --- | --- | --- |
| `POST` | `/auth/login` | [login](./authentication) | [LoginRequest](./schemas) | [LoginResponse](./schemas) | [Authentication API](./authentication) |
| `GET` | `/auth/me` | [me](./authentication) | - | [CurrentUserResponse](./schemas) | [Authentication API](./authentication) |
| `GET` | `/agent-processes` | [Get latest agent process definitions](./agent-processes) | - | `AgentProcessDefinition[]` | [Agent Processes API](./agent-processes) |
| `POST` | `/agent-processes` | [Deploy an agent process definition](./agent-processes) | `Agent Process JSON` | `AgentProcessDefinition` | [Agent Processes API](./agent-processes) |
| `GET` | `/agent-processes/{key}` | [Get latest agent process definition by key](./agent-processes) | - | `AgentProcessDefinition` | [Agent Processes API](./agent-processes) |
| `GET` | `/agent-processes/{key}/versions` | [Get all versions for an agent process key](./agent-processes) | - | `AgentProcessDefinition[]` | [Agent Processes API](./agent-processes) |
| `GET` | `/processes` | [Get latest process definitions](./processes) | - | [PageProcessDefinition](./schemas) | [Processes API](./processes) |
| `POST` | `/processes` | [Deploy a process definition](./processes) | `Easy BPM process JSON` | [ProcessDefinition](./schemas) | [Processes API](./processes) |
| `POST` | `/processes/{processId}/start` | [Start a process instance](./processes) | - | [ProcessInstance](./schemas) | [Processes API](./processes) |
| `GET` | `/processes/definitions/{id}` | [Get process definition by ID](./processes) | - | [ProcessDefinition](./schemas) | [Processes API](./processes) |
| `GET` | `/processes/instances` | [Get process instances](./processes) | - | [PageProcessInstance](./schemas) | [Processes API](./processes) |
| `GET` | `/processes/instances/{id}` | [Get process instance by ID](./processes) | - | [ProcessInstance](./schemas) | [Processes API](./processes) |
| `DELETE` | `/processes/instances/{id}` | [Delete process instance](./processes) | - | `No body` | [Processes API](./processes) |
| `GET` | `/processes/instances/{id}/children` | [Get child process instances](./processes) | - | [ProcessInstance](./schemas)[] | [Processes API](./processes) |
| `POST` | `/processes/instances/{id}/move-node` | [Move process token](./processes) | [MoveNodeRequest](./schemas) | [ProcessInstance](./schemas) | [Processes API](./processes) |
| `GET` | `/processes/instances/{id}/parent` | [Get parent process instance](./processes) | - | [ProcessInstance](./schemas) | [Processes API](./processes) |
| `POST` | `/processes/instances/{id}/stop` | [Stop process instance](./processes) | - | [ProcessInstance](./schemas) | [Processes API](./processes) |
| `GET` | `/processes/instances/{id}/variables` | [Get process variables](./processes) | - | [ProcessVariable](./schemas)[] | [Processes API](./processes) |
| `PUT` | `/processes/instances/{id}/variables` | [Assign process variables](./processes) | [AssignProcessVariablesRequest](./schemas) | [ProcessVariable](./schemas)[] | [Processes API](./processes) |
| `GET` | `/processes/instances/{parentId}/children/{childId}/mapping` | [Get call activity mapping](./processes) | - | [CallActivityMappingResponse](./schemas) | [Processes API](./processes) |
| `POST` | `/processes/messages` | [Send a message](./processes) | `Message correlation payload` | `Message correlation response` | [Processes API](./processes) |
| `GET` | `/tasks` | [Get all tasks](./tasks) | - | [PageTaskResponseDto](./schemas) | [Tasks API](./tasks) |
| `GET` | `/tasks/{id}` | [Get task by ID](./tasks) | - | [TaskResponseDto](./schemas) | [Tasks API](./tasks) |
| `POST` | `/tasks/{id}/claim` | [Claim a task](./tasks) | - | [TaskResponseDto](./schemas) | [Tasks API](./tasks) |
| `POST` | `/tasks/{id}/complete` | [Complete a task](./tasks) | `Task completion payload` | `string` | [Tasks API](./tasks) |
| `GET` | `/tasks/search` | [Search tasks](./tasks) | - | [PageTaskResponseDto](./schemas) | [Tasks API](./tasks) |
| `GET` | `/forms` | [Get all form versions](./forms) | - | [Form](./schemas)[] | [Forms API](./forms) |
| `POST` | `/forms` | [Deploy a form](./forms) | [DeployFormRequest](./schemas) | [Form](./schemas) | [Forms API](./forms) |
| `GET` | `/forms/{id}` | [Get form by ID](./forms) | - | [Form](./schemas) | [Forms API](./forms) |
| `GET` | `/forms/latest` | [Get latest form version](./forms) | - | [Form](./schemas) | [Forms API](./forms) |
| `GET` | `/api/documents` | [List documents](./documents) | - | [DocumentResponseDto](./schemas)[] | [Documents API](./documents) |
| `POST` | `/api/documents` | [Upload a document](./documents) | `object` | [DocumentResponseDto](./schemas) | [Documents API](./documents) |
| `GET` | `/api/documents/{id}` | [Get document metadata](./documents) | - | [DocumentResponseDto](./schemas) | [Documents API](./documents) |
| `DELETE` | `/api/documents/{id}` | [Delete a document](./documents) | - | `No body` | [Documents API](./documents) |
| `GET` | `/api/documents/{id}/download` | [Download a document](./documents) | - | `binary file` | [Documents API](./documents) |
| `GET` | `/api/documents/{id}/preview` | [Preview a document inline](./documents) | - | `binary file` | [Documents API](./documents) |
| `GET` | `/code-tasks/executions` | [getExecutions](./code-tasks) | - | [ExecutionAuditPageResponse](./schemas) | [Code Tasks API](./code-tasks) |
| `GET` | `/code-tasks/jar/{jarId}/classes` | [getJarClasses](./code-tasks) | - | [JarClassesResponse](./schemas) | [Code Tasks API](./code-tasks) |
| `GET` | `/code-tasks/jar/{jarId}/classes/{className}/methods` | [getClassMethods](./code-tasks) | - | [ClassMetadataResponse](./schemas) | [Code Tasks API](./code-tasks) |
| `POST` | `/code-tasks/upload` | [uploadJar](./code-tasks) | `object` | [CodeTaskJarUploadResponse](./schemas) | [Code Tasks API](./code-tasks) |
| `GET` | `/admin/groups` | [listGroups](./admin-security) | - | [GroupResponse](./schemas)[] | [Admin Security API](./admin-security) |
| `POST` | `/admin/groups` | [createGroup](./admin-security) | [CreateGroupRequest](./schemas) | [GroupResponse](./schemas) | [Admin Security API](./admin-security) |
| `PUT` | `/admin/groups/{id}` | [updateGroup](./admin-security) | [UpdateGroupRequest](./schemas) | [GroupResponse](./schemas) | [Admin Security API](./admin-security) |
| `DELETE` | `/admin/groups/{id}` | [deleteGroup](./admin-security) | - | `No body` | [Admin Security API](./admin-security) |
| `GET` | `/admin/groups/{id}/users` | [getGroupUsers](./admin-security) | - | [UserResponse](./schemas)[] | [Admin Security API](./admin-security) |
| `PUT` | `/admin/groups/{id}/users` | [updateGroupUsers](./admin-security) | [UpdateGroupUsersRequest](./schemas) | [UserResponse](./schemas)[] | [Admin Security API](./admin-security) |
| `GET` | `/admin/users` | [listUsers](./admin-security) | - | [UserResponse](./schemas)[] | [Admin Security API](./admin-security) |
| `POST` | `/admin/users` | [createUser](./admin-security) | [CreateUserRequest](./schemas) | [UserResponse](./schemas) | [Admin Security API](./admin-security) |
| `PUT` | `/admin/users/{id}` | [updateUser](./admin-security) | [UpdateUserRequest](./schemas) | [UserResponse](./schemas) | [Admin Security API](./admin-security) |
| `DELETE` | `/admin/users/{id}` | [deleteUser](./admin-security) | - | `No body` | [Admin Security API](./admin-security) |
| `PUT` | `/admin/users/{id}/password` | [resetPassword](./admin-security) | [ResetPasswordRequest](./schemas) | `No body` | [Admin Security API](./admin-security) |
| `GET` | `/ai/credentials` | [listCredentials](./ai-credentials) | - | [AICredentialResponseDto](./schemas)[] | [AI Credentials API](./ai-credentials) |
| `POST` | `/ai/credentials` | [createCredential](./ai-credentials) | [AICredentialCreateRequestDto](./schemas) | [AICredentialResponseDto](./schemas) | [AI Credentials API](./ai-credentials) |
| `GET` | `/ai/credentials/{id}` | [getCredential](./ai-credentials) | - | [AICredentialResponseDto](./schemas) | [AI Credentials API](./ai-credentials) |
| `DELETE` | `/ai/credentials/{id}` | [deleteCredential](./ai-credentials) | - | `No body` | [AI Credentials API](./ai-credentials) |
| `GET` | `/ai/credentials/{id}/valid` | [isCredentialValid](./ai-credentials) | - | `Map<String, Boolean>` | [AI Credentials API](./ai-credentials) |
