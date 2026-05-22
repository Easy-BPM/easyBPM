# Phase 9.3: Backend Task Handler - Sprint Plan

**Duration**: 1 week (May 22-28, 2026)  
**Effort**: 12 story points  
**Status**: Ready to start  
**Dependencies**: Phase 9.1 (backend infrastructure) ✅, Phase 9.2 (frontend) ✅

---

## Overview

Implement the AITaskHandler service to execute AI tasks in the process engine. This bridges the frontend modeler configuration (Phase 9.2) with the backend AI provider infrastructure (Phase 9.1).

**Goal**: When a process instance encounters an AI task node, the engine:
1. Extracts AI task configuration from node properties
2. Invokes AITaskHandler with prompts and variables
3. Receives AI response from selected provider
4. Binds response to configured process variable
5. Advances to next node or triggers error boundary on failure

---

## Story Breakdown

### 9.3.1: AITaskHandler Infrastructure (5 sp) ⏳

**Objective**: Create core handler service with dependency injection and error handling

**Deliverables**:
1. `AITaskHandler.kt` - Core handler service (@Service)
   - executeAITask(instanceId, node, instance, definition): Map<String, Any?>
   - Dependency injection: AIProviderFactory, CredentialVault, ObjectMapper
   - Logging and metrics integration
   - Variable substitution for prompts

2. Update `NodeType.kt` - Add AITask enum value
   - AITask("AiTask") matching modeler export format
   - Register in fromString() mapping

3. Update `ProcessService.kt` - Add handler integration
   - Inject AITaskHandler
   - Add NodeType.AITask case in executeNode()
   - Route to handler with node config

**Acceptance Criteria**:
- [ ] AITaskHandler compiles without errors
- [ ] NodeType.AITask recognized by engine
- [ ] ProcessService routes AI task nodes to handler
- [ ] Handler receives correct parameters from node config

**Implementation Order**:
1. Update NodeType.kt
2. Create AITaskHandler.kt skeleton
3. Inject into ProcessService
4. Update executeNode() switch statement

---

### 9.3.2: AI Execution + Variable Binding (4 sp) ⏳

**Objective**: Implement task execution logic and response binding

**Deliverables**:
1. `AITaskHandler.kt` - Full execution implementation
   - Extract config from node.properties
   - Apply input variable substitution ({{varName}} → actual value)
   - Call AIProviderFactory.createProvider()
   - Build AIExecutionRequestDto with prompt template + variables
   - Execute provider.execute()
   - Extract response text
   - Bind response to outputVariable in process instance
   - Handle response format (plain text, JSON extraction)

2. Variable binding logic
   - processVariableRepository.findByProcessInstanceIdAndName()
   - Create/update ProcessVariable with response value
   - Handle type coercion (string response → number/boolean if configured)

3. Prompt template processing
   - Support {{variableName}} syntax from frontend
   - Fetch actual variable values from processVariableRepository
   - Replace placeholders with values
   - Log substituted prompt (with credentials redacted)

**Acceptance Criteria**:
- [ ] Handler executes AI task and receives response
- [ ] Response stored in configured output variable
- [ ] Variable substitution works for {{varName}} syntax
- [ ] Types are preserved (string/number/json)
- [ ] Logs include substituted prompt (credentials masked)

**Implementation Order**:
1. Implement prompt variable substitution
2. Implement provider execution
3. Implement response binding
4. Add logging

---

### 9.3.3: Error Handling + Retry Logic (3 sp) ⏳

**Objective**: Robust error handling with provider fallback and retry support

**Deliverables**:
1. `AITaskHandler.kt` - Error handling
   - Catch AIExecutionException and detailed error codes
   - Implement retry logic (using tuning params: retryCount, initialDelayMs, backoffMultiplier)
   - Exponential backoff: delay = initialDelayMs * (backoffMultiplier ^ attemptNumber)
   - Log each retry attempt with duration and status
   - Throw custom AITaskExecutionException on final failure

2. Error boundary integration
   - Match error code from provider response to error boundary error code
   - Pass error message to exceptionVariable if configured
   - Allow error boundary to catch and handle

3. Specific error scenarios
   - AUTH_ERROR (credentials invalid/expired) → fail immediately, don't retry
   - RATE_LIMIT (provider throttling) → retry with backoff
   - TIMEOUT (network timeout) → retry with backoff
   - PROVIDER_ERROR (generic provider error) → retry once, then fail
   - PARSE_ERROR (malformed response) → fail immediately

**Acceptance Criteria**:
- [ ] Retries work with exponential backoff
- [ ] Auth errors fail immediately without retry
- [ ] Transient errors retry with backoff
- [ ] Error boundary catches error codes
- [ ] Exception messages captured to variables
- [ ] Metrics recorded (attempt count, total duration)

**Implementation Order**:
1. Create custom exception types
2. Implement retry logic with backoff
3. Implement error classification
4. Integrate with error boundary

---

## Implementation Files

### New Files
- `src/main/kotlin/com/easy/bpm/handler/AITaskHandler.kt` (250-300 lines)
- `src/main/kotlin/com/easy/bpm/handler/exception/AITaskExecutionException.kt` (custom exception)

### Modified Files
- `src/main/kotlin/com/easy/bpm/enum/NodeType.kt` (add AITask)
- `src/main/kotlin/com/easy/bpm/service/ProcessService.kt` (add handler injection + case in executeNode)

### Tests (In Story 9.3.4)
- `src/test/kotlin/com/easy/bpm/handler/AITaskHandlerTest.kt` (15+ tests)

---

## Daily Standup Template

```
## Day X Standup

### Completed
- [ ] Story/task description

### In Progress
- [ ] Story/task description

### Blockers
- None / [description]

### Today's Focus
- [priority tasks]
```

---

## Risk Assessment

**Risks & Mitigation**:

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Provider API unavailable during test | High | Mock provider factory for tests; use testcontainers if needed |
| Variable substitution errors | Medium | Test with various {{var}} patterns; validate syntax |
| Retry loop infinite/infinite cost | High | Implement max retries (default 3); log each attempt |
| Credential issues in production | Medium | Mask credentials in logs; use CredentialVault (already encrypted) |

---

## Testing Strategy

**Unit Tests** (Story 9.3.4):
- Test prompt template variable substitution with various inputs
- Test retry logic with mock provider that fails then succeeds
- Test error classification and boundary routing
- Test variable binding with different data types
- Test configuration extraction from node properties

**Integration Tests** (Story 9.3.4):
- Execute AI task in full process context (with ProcessInstance)
- Verify response persisted to process variable
- Verify error boundary triggered on provider failure
- Verify metrics recorded

**Manual Testing**:
- Deploy process with AI task in modeler
- Start instance via API
- Monitor logs for prompt substitution
- Verify response in Admin UI process instance details

---

## Definition of Done

✅ Code compiles without errors  
✅ All tests pass (unit + integration)  
✅ SonarQube quality gates met  
✅ Logging includes enough detail for troubleshooting  
✅ Credentials never logged (masked)  
✅ Error messages helpful for users  
✅ Code reviewed by team  
✅ Documentation updated (code comments, implementation guide)

---

## Deployment Checklist

Before deploying to staging:
- [ ] All tests passing locally
- [ ] `./gradlew test` passes 100% (120+ tests)
- [ ] No SonarQube critical issues
- [ ] Database migrations applied (V22 if needed)
- [ ] Feature flag enabled (if applicable)
- [ ] Credentials vault configured in environment
- [ ] Logs redirect to proper sink (JSON structured logs)

---

## Next Phase (9.4)

After this phase completes:
- **Phase 9.4**: Admin UI for AI execution monitoring (5 sp)
  - Dashboard showing AI task execution history
  - Success/failure rates per provider
  - Token usage analytics
  - Response time metrics
  - Error frequency analysis

---

## References

**Backend Infrastructure** (Phase 9.1):
- `AIProvider.kt` - Abstract provider base
- `AIProviderFactory.kt` - Factory pattern
- `CredentialVault.kt` - Credential management
- `OpenAIProvider.kt` - Sample implementation

**Frontend Configuration** (Phase 9.2):
- `types.ts` - Node configuration structure
- `PropertiesPanel.tsx` - AI task UI panel

**Similar Handlers**:
- `CallActivityHandler.kt` - Subprocess execution pattern
- `CodeTaskHandler.kt` - Task execution lifecycle pattern

