---
title: First Login
---

# First Login

Easy BPM creates a first administrator user on startup. Use this account to sign in, create real customer users, and assign permissions.

## Default local account

| Username | Password |
| --- | --- |
| `admin` | `admin` |

Change these values before running a shared environment.

## Sign in through an app

Open one of the web apps and sign in:

| App | Typical local URL |
| --- | --- |
| Modeler | `http://localhost:3000` |
| Admin Console | `http://localhost:3001` |
| Task Portal | `http://localhost:3002` |

## Sign in through the API

```bash
TOKEN=$(curl -s http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' \
  | jq -r '.token')
```

Use the token:

```bash
curl http://localhost:8080/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

## Recommended first setup

1. Create customer groups for operators, modelers, and portal users.
2. Assign only the permissions each group needs.
3. Create named user accounts instead of sharing the bootstrap administrator.
4. Rotate the bootstrap password or disable the account if your operating model allows it.
