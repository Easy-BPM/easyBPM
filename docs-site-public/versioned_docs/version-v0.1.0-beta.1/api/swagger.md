---
title: Swagger UI
---

# Swagger UI

The backend exposes interactive Swagger UI at:

```text
http://localhost:8080/swagger-ui.html
```

Use Swagger UI when you want to try requests against a running Easy BPM environment. Use this documentation site when you want a stable, customer-facing reference organized by API domain with DTO-based examples.

## OpenAPI JSON

The live backend contract is available at:

```text
http://localhost:8080/v3/api-docs
```

This public docs site also includes a static copy captured from the backend: [easybpm-openapi.json](/openapi/easybpm-openapi.json).

## Recommended workflow

1. Read the API group page for the concept, DTOs, request example, and response example.
2. Open Swagger UI for live execution against your environment.
3. Copy the generated curl request into your integration code or test suite.
4. Keep integrations pinned to stable endpoint paths and schema fields documented here.
