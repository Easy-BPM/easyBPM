# Keycloak and OIDC Authentication

Easy BPM can keep local username/password authentication or validate external OIDC access tokens from Keycloak.

## Backend Configuration

Local authentication remains the default:

```yaml
easybpm:
  authentication:
    provider: local
```

To enable Keycloak or another OIDC provider:

```yaml
easybpm:
  authentication:
    provider: keycloak
    user-provisioning:
      enabled: true
      default-permission-codes: ACCESS_PROCESS_PORTAL
    oidc:
      issuer-uri: ${EASYBPM_OIDC_ISSUER_URI}
      client-id: ${EASYBPM_OIDC_CLIENT_ID}
      audience: ${EASYBPM_OIDC_AUDIENCE}
      group-claim: groups
      username-claim: preferred_username
```

The backend uses Spring Security Resource Server support to validate JWT signature, issuer and expiration. Audience validation is enabled when `EASYBPM_OIDC_AUDIENCE` is set.

## Identity Mapping

OIDC users are mapped by:

- `identityProvider`: `KEYCLOAK` when `provider=keycloak`
- `externalIdentityId`: OIDC `sub`
- `username`: configurable username claim, default `preferred_username`
- `email`, `given_name`, `family_name`: stored when available

Just-in-time provisioning is controlled by `easybpm.authentication.user-provisioning.enabled`.

## Groups and Roles

The configured group claim is exposed to Easy BPM task authorization. Candidate groups can therefore match token groups such as `customer-support`.

OIDC roles are mapped to Easy BPM permissions with `easybpm.authentication.oidc.role-mappings`.

Default mappings:

```yaml
easybpm-user: ACCESS_PROCESS_PORTAL
easybpm-modeler: ACCESS_BPM_MODELER
easybpm-admin: ACCESS_BPM_ADMIN
easybpm-admin-users-read: VIEW_USERS
easybpm-admin-users-manage: MANAGE_USERS
easybpm-admin-groups-read: VIEW_GROUPS
easybpm-admin-groups-manage: MANAGE_GROUPS
easybpm-admin-permissions-manage: MANAGE_PERMISSIONS
```

`easybpm-admin` only opens the Admin application. Use the fine-grained roles above to decide whether a Keycloak user can read or manage Easy BPM local users, groups and direct permissions.

## Current User

The frontend can use either endpoint:

- `GET /auth/me`
- `GET /api/users/me`

Both return the local Easy BPM user, mapped identity provider, groups and permissions.

## Local Keycloak

The main compose file keeps local EasyBPM username/password authentication as the default:

```powershell
docker compose up -d
```

Start the full stack with local Keycloak/OIDC enabled:

```powershell
$env:EASYBPM_KEYCLOAK_ADMIN="local-admin"
$env:EASYBPM_KEYCLOAK_ADMIN_PASSWORD="<choose-a-local-password>"
docker compose -f docker-compose.yml -f docker-compose.keycloak.yml up -d
```

The Keycloak compose override sets these backend values:

```powershell
$env:EASYBPM_AUTHENTICATION_PROVIDER="keycloak"
$env:EASYBPM_OIDC_ISSUER_URI="http://localhost:8081/realms/easybpm"
$env:EASYBPM_OIDC_JWK_SET_URI="http://keycloak:8080/realms/easybpm/protocol/openid-connect/certs"
$env:EASYBPM_OIDC_CLIENT_ID="easybpm"
$env:EASYBPM_OIDC_AUDIENCE="easybpm"
```

The separate `deploy/keycloak/docker-compose.keycloak.yml` remains available when you only want to run Keycloak next to a backend started outside Docker.

Create local development users in Keycloak and assign realm roles/groups as needed.
