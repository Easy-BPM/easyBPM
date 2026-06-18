---
title: Modeler
---

# Modeler

The Easy BPM Modeler is the customer workspace for designing process definitions and forms.

![Easy BPM Modeler](/img/screenshots/modeler-home.png)

## What developers use it for

| Area | Purpose |
| --- | --- |
| Process canvas | Add events, user tasks, API tasks, code tasks, AI tasks, gateways, and subprocesses. |
| Properties panel | Configure node IDs, labels, assignees, forms, variables, integrations, and routing behavior. |
| Form library | Create reusable dynamic forms that can be attached to human tasks. |
| Code Task tools | Upload JARs, browse discovered classes, and bind methods to workflow nodes. |
| Deploy action | Publish the process JSON to the backend as a new version. |

## Recommended workflow

1. Create or update forms first when user tasks need form rendering.
2. Build the process graph with stable node IDs.
3. Configure variable mappings on user tasks, API tasks, call activities, and code tasks.
4. Validate the model.
5. Deploy to the target Easy BPM backend.
6. Start a test instance from the Task Portal or API.

## Access

Users need `ACCESS_BPM_MODELER` or another configured administrative permission that allows process and form endpoints.

Set the backend URL with:

```bash
VITE_API_BASE_URL=https://api.customer.example
```
