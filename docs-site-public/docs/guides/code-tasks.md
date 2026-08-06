---
title: Code Tasks
---

# Code Tasks

Code Tasks execute Java or Kotlin code packaged in a JVM JAR. They are useful when a workflow needs customer-specific calculations, normalization, validation, enrichment, or deterministic business logic that should run inside the platform boundary.

## Upload a JAR

```bash
curl -X POST http://localhost:8080/code-tasks/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "jarFile=@build/libs/customer-rules.jar" \
  -F "description=Customer rules library"
```

The response includes the `jarId`, file hash, discovered classes, and method count.

## Discover classes

```bash
curl http://localhost:8080/code-tasks/jar/1/classes \
  -H "Authorization: Bearer $TOKEN"
```

## Discover methods

```bash
curl "http://localhost:8080/code-tasks/jar/1/classes/com.example.Rules/methods" \
  -H "Authorization: Bearer $TOKEN"
```

## Configure a Code Task node

Use the uploaded `jarId`, discovered class name, method name, and input/output mappings in the Modeler. A representative node looks like this:

```json
{
  "id": "calculate-risk",
  "name": "Calculate Risk",
  "type": "CodeTask",
  "config": {
    "jarId": 1,
    "className": "com.example.Rules",
    "methodName": "calculateRisk",
    "inputs": [
      { "targetName": "amount", "source": "variable", "value": "amount" },
      { "targetName": "customerTier", "source": "variable", "value": "customerTier" }
    ],
    "outputs": [
      { "target": "process", "sourceName": "riskScore", "value": "riskScore" }
    ]
  }
}
```

## Audit executions

```bash
curl "http://localhost:8080/code-tasks/executions?status=COMPLETED&page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

Filter by process instance:

```bash
curl "http://localhost:8080/code-tasks/executions?instanceId=42" \
  -H "Authorization: Bearer $TOKEN"
```

## Packaging guidance

Keep Code Task methods deterministic and side-effect free when possible. For external HTTP calls, prefer API Tasks so retries, integration behavior, and worker execution remain visible in the process model.

Avoid bundling credentials into JARs. Use environment variables, configured secrets, API Tasks, or AI credential storage for sensitive values.
