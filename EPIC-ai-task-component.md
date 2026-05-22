# EPIC: AI Task Component for Easy BPM Workflows
**Epic Name**: "Ask AI" — Intelligent Workflow Automation  
**Epic ID**: AI-TASK-EPIC  
**Status**: PLANNED  
**Target Release**: Phase 9 (Post-Code Task Implementation)  
**Effort**: ~80 story points (estimated)  
**Document Version**: 1.0  
**Last Updated**: 2026-05-22

---

## Epic Overview

Enable workflow designers to add an **"Ask AI"** component into Easy BPM workflows, allowing process automation through AI-powered tasks such as text generation, classification, summarization, extraction, decision support, and contextual responses.

The component must support:
- Configurable AI providers (OpenAI, Anthropic, Gemini, Ollama, Azure OpenAI, Custom REST)
- Authentication token management (encrypted, RBAC-protected)
- Model/engine selection per provider
- Prompt tuning and runtime parameter configuration
- Workflow variable integration (input injection, output mapping)
- Execution monitoring and error handling

**Business Value**: Make AI a first-class citizen inside Easy BPM process automation, enabling faster automation, reduced external integration complexity, intelligent decision-making, and flexible AI provider support.

---

## Phased Implementation Plan

### Phase 9.1: Core AI Infrastructure & Provider Abstraction Layer
**Effort**: 16 story points  
**Duration**: 2 weeks  
**Goal**: Establish backend architecture for pluggable AI providers

#### Story 9.1.1 — AI Provider Interface & Factory Pattern (5 sp)
**Description**: Create abstract AI provider interface and factory for pluggable connector architecture.

**Tasks**:
- [ ] Define `AIProvider` interface (abstract methods: `execute()`, `validateConfig()`, `getMetadata()`)
- [ ] Define `AIProviderConfig` DTO (provider name, endpoint, model, apiVersion, timeout, streaming)
- [ ] Create `AIProviderFactory` for provider instantiation
- [ ] Create `AIProviderRegistry` for registered providers
- [ ] Unit tests for factory and registry patterns

**Acceptance Criteria**:
- Factory can instantiate providers by name
- Extensible design supports future providers
- All providers implement same contract
- 95%+ code coverage

---

#### Story 9.1.2 — Secret Management & Credential Storage (5 sp)
**Description**: Implement encrypted token storage and retrieval with RBAC.

**Tasks**:
- [ ] Create `AICredential` entity (provider, credentialType, encryptedToken, ownerId, permissions)
- [ ] Create `CredentialVault` service for encrypt/decrypt/store/retrieve
- [ ] Create Flyway migration for AI credentials table
- [ ] Create REST endpoint `POST /ai/credentials` (with RBAC check)
- [ ] Create REST endpoint `GET /ai/credentials/{id}` (masked response)
- [ ] Create REST endpoint `DELETE /ai/credentials/{id}` (with audit logging)
- [ ] Support environment variable references (`$ENV_VAR_NAME`)
- [ ] Unit tests for encryption/decryption

**Acceptance Criteria**:
- Tokens encrypted at rest using Spring Security Crypto
- Tokens masked in API responses (show only last 4 chars)
- No tokens in execution logs or error messages
- RBAC enforces credential access
- Environment variable resolution works

---

#### Story 9.1.3 — OpenAI Provider Implementation (6 sp)
**Description**: Implement first concrete AI provider (OpenAI GPT-4, GPT-3.5-turbo).

**Tasks**:
- [ ] Create `OpenAIProvider` class implementing `AIProvider`
- [ ] Configure model selection (gpt-4, gpt-3.5-turbo, gpt-4-turbo)
- [ ] Implement `execute()` method with streaming support toggle
- [ ] Parameter mapping: temperature, topP, maxTokens, frequencyPenalty, presencePenalty
- [ ] Error handling and retry logic with exponential backoff
- [ ] Token consumption tracking
- [ ] Integration tests against OpenAI sandbox/mock

**Acceptance Criteria**:
- Provider can execute text generation, classification, summarization
- All tuning parameters respected
- Streaming works when enabled
- Token count tracked
- Proper error handling for rate limits, auth failures, timeouts

---

### Phase 9.2: Modeler UI & Workflow Component Configuration
**Effort**: 16 story points  
**Duration**: 2 weeks  
**Goal**: Enable designers to configure AI tasks in BPMN editor

#### Story 9.2.1 — AI Task Palette Component (4 sp)
**Description**: Add "Ask AI" icon and component to palette, render on canvas.

**Tasks**:
- [ ] Add `'ai-task'` as new `NodeType`
- [ ] Create AI task icon (Lucide: `Brain` or `Zap`)
- [ ] Update `Palette.tsx` to include AI task option
- [ ] Update `Canvas.tsx` rendering for AI task nodes
- [ ] Update validation to allow AI task in normal flows
- [ ] Add visual differentiation (color, styling) from other task types

**Acceptance Criteria**:
- AI task appears in palette
- Can drag and drop onto canvas
- Renders with distinctive icon and styling
- Can be connected like any BPM task
- Validation allows multiple AI tasks in same process

---

#### Story 9.2.2 — AI Provider Configuration Form (6 sp)
**Description**: Create dynamic form for selecting and configuring AI provider.

**Tasks**:
- [ ] Create `AIProviderConfigForm.tsx` component
- [ ] Dropdown to select provider: OpenAI, Anthropic, Gemini, Ollama, Azure, Custom
- [ ] Dynamic form rendering based on selected provider
- [ ] Configuration inputs:
  - Provider-specific: model/engine, endpoint URL, API version
  - Common: timeout, streaming toggle
- [ ] Credential selection UI (dropdown of stored credentials)
- [ ] Form validation
- [ ] Preview of selected provider metadata (models, capabilities)
- [ ] Integration with `PropertiesPanel.tsx` for AI task nodes

**Acceptance Criteria**:
- Provider dropdown updates form fields dynamically
- All required fields validated before save
- Credential selection shows available tokens per provider
- Form state preserved in node properties
- Modeler exports AI task config in process JSON

---

#### Story 9.2.3 — Prompt Editor with Variable Injection (4 sp)
**Description**: Create multiline prompt editor with workflow variable autocomplete.

**Tasks**:
- [ ] Create `PromptEditor.tsx` component (textarea + syntax highlighting)
- [ ] Support variable injection syntax: `{{variableName}}`
- [ ] Autocomplete for available workflow variables
- [ ] Support system prompt and user prompt (separate fields)
- [ ] Live preview showing variable substitution examples
- [ ] Validation: no unresolved variables with escape hatches
- [ ] Store prompt template in AI task node config

**Acceptance Criteria**:
- Multiline editor renders cleanly in properties panel
- Autocomplete triggered on `{{` 
- Variable names validated against process variables
- Preview updates as variables are selected
- Prompt stored in node properties

---

#### Story 9.2.4 — Tuning Parameters Configuration Panel (2 sp)
**Description**: Create UI for configuring AI tuning parameters (temperature, topP, maxTokens, etc.).

**Tasks**:
- [ ] Create `AITuningPanel.tsx` component
- [ ] Input fields for:
  - Temperature (0.0–2.0, slider)
  - Top P (0.0–1.0, slider)
  - Max Tokens (integer, with model limit validation)
  - Frequency Penalty (−2.0–2.0, slider)
  - Presence Penalty (−2.0–2.0, slider)
  - Timeout (milliseconds, numeric input)
  - Streaming (toggle)
- [ ] Validation: ranges checked, defaults applied
- [ ] Display parameter descriptions on hover
- [ ] Integration into properties panel

**Acceptance Criteria**:
- All parameters configurable via UI
- Values validated on input
- Defaults applied for missing values
- Sliders for continuous parameters, text for discrete
- Stored in node config

---

### Phase 9.3: Output Mapping & Variable Integration
**Effort**: 12 story points  
**Duration**: 1.5 weeks  
**Goal**: Map AI responses into workflow variables, enable downstream task consumption

#### Story 9.3.1 — Output Variable Mapping UI (4 sp)
**Description**: Allow designers to configure output variable for AI response.

**Tasks**:
- [ ] Create `AIOutputMappingPanel.tsx` component
- [ ] Dropdown to select target variable or create new
- [ ] Response format selector: PlainText, JSON, StructuredFields
- [ ] Option to auto-parse JSON responses
- [ ] Option to extract fields from JSON (e.g., extract `.result` field)
- [ ] Variable naming validation (no spaces, alphanumeric + underscore)
- [ ] Integration into properties panel

**Acceptance Criteria**:
- Output variable can be set per AI task
- Format auto-detection works for JSON
- Field extraction reduces boilerplate
- Variables created on-the-fly if needed
- Stored in node config

---

#### Story 9.3.2 — AI Response Processing & Variable Binding (5 sp)
**Description**: Backend logic to execute AI task and bind response to process variable.

**Tasks**:
- [ ] Create `AITaskHandler` class (similar to `APITaskHandler`, `CodeTaskHandler`)
- [ ] Implement prompt rendering (replace `{{variableName}}` with actual values)
- [ ] Call `AIProvider.execute()` with resolved config and prompt
- [ ] Parse response based on configured format
- [ ] Extract fields if specified
- [ ] Store result in process variable
- [ ] Handle errors gracefully (fallback variable, error boundary)
- [ ] Integration tests

**Acceptance Criteria**:
- Variables injected into prompt
- AI response stored in target variable
- JSON parsing works
- Field extraction works
- Process continues with populated variable

---

#### Story 9.3.3 — Variable Autocomplete in Prompt Editor (3 sp)
**Description**: Real-time autocomplete for workflow variables in prompt editor.

**Tasks**:
- [ ] Fetch available process variables from `PropertiesPanel` context
- [ ] Autocomplete on `{{` trigger
- [ ] Show variable name, type, and default value
- [ ] Select variable → insert `{{variableName}}`
- [ ] Validate variable exists at runtime

**Acceptance Criteria**:
- Autocomplete works in prompt editor
- Shows all process variables
- Insertion works correctly
- Validation catches typos

---

### Phase 9.4: Admin UI & Execution Monitoring
**Effort**: 12 story points  
**Duration**: 1.5 weeks  
**Goal**: Provide operators visibility into AI task executions

#### Story 9.4.1 — AI Execution History View (6 sp)
**Description**: Create admin dashboard for viewing AI task executions.

**Tasks**:
- [ ] Create `AIExecutionHistoryPage.tsx` component
- [ ] List table: Instance ID, AI Task Name, Provider, Model, Timestamp, Status, Duration
- [ ] Filters: Process Key, AI Task Name, Provider, Date Range, Status
- [ ] Sorting: Duration, Timestamp, Status
- [ ] Click row → detail modal
- [ ] Detail modal includes:
  - Prompt (user + system)
  - Response (first 500 chars with expand option)
  - Token consumption
  - Execution duration
  - Error message (if failed, sanitized)
  - Tuning parameters used
- [ ] Export to CSV
- [ ] Integration with `AdminService`

**Acceptance Criteria**:
- Table renders with 100+ records
- Filters work independently
- Detail modal shows all execution metadata
- No credentials exposed in UI
- Performance acceptable (lazy load, pagination)

---

#### Story 9.4.2 — AI Execution Logging & Audit Trail (4 sp)
**Description**: Backend logging for AI executions with audit trail.

**Tasks**:
- [ ] Create `AIExecutionLog` entity (processInstanceId, aiTaskNodeId, provider, model, promptHash, responseHash, tokenCount, duration, status, errorMessage, createdAt)
- [ ] Create Flyway migration for AI execution logs table
- [ ] Log all executions in `AITaskHandler`
- [ ] Hash prompts and responses (don't store full text in logs)
- [ ] Create `AIExecutionLogService` for querying
- [ ] Create REST endpoint `GET /ai/executions?filters...` with pagination
- [ ] Sanitize error messages (no credential leakage)

**Acceptance Criteria**:
- All executions logged
- Logs queryable by instance, task, provider, date
- Credentials not in logs
- Performance: queries <100ms for 1M records
- Audit trail supports compliance

---

#### Story 9.4.3 — AI Provider Health & Token Usage Dashboard (2 sp)
**Description**: Show provider health, token consumption trends, and quota alerts.

**Tasks**:
- [ ] Create `AIProviderMetricsPage.tsx`
- [ ] Chart: Daily token consumption by provider
- [ ] Chart: Success rate by provider
- [ ] Alert: Token quota warnings (if integrated)
- [ ] Table: Recent failures by provider
- [ ] Alerts for provider downtime (if health checks implemented)

**Acceptance Criteria**:
- Dashboard loads within 2s
- Charts render correctly
- Alerts trigger at 80% quota (configurable)

---

### Phase 9.5: Error Handling, Retry & Resilience
**Effort**: 12 story points  
**Duration**: 1.5 weeks  
**Goal**: Robust error handling, retry mechanisms, and fallback support

#### Story 9.5.1 — AI Task Error Boundary & Fallback (6 sp)
**Description**: Support BPMN error boundaries on AI tasks and fallback paths.

**Tasks**:
- [ ] Create `AITaskErrorHandler` class
- [ ] Support error boundary attachment to AI task nodes
- [ ] Route errors to error boundary handlers (existing BPMN error handling)
- [ ] Create `AITaskFallback` node type (alternative prompt or static response)
- [ ] Fallback configuration UI in properties panel
- [ ] Fallback execution if AI task fails
- [ ] Error codes: PROVIDER_ERROR, AUTH_ERROR, TIMEOUT, RATE_LIMIT, PARSE_ERROR
- [ ] Integration tests for error paths

**Acceptance Criteria**:
- Error boundaries catch AI failures
- Fallback paths execute correctly
- Error codes differentiated
- Errors propagate to BPMN error handlers

---

#### Story 9.5.2 — Retry Configuration & Backoff Strategy (4 sp)
**Description**: Configurable retry with exponential backoff for transient AI failures.

**Tasks**:
- [ ] Create `AITaskRetryConfig` DTO (maxRetries, backoffStrategy, backoffMultiplier, initialDelayMs)
- [ ] Add retry config UI to properties panel
- [ ] Implement exponential backoff in `AITaskHandler`
- [ ] Retry on transient errors: TIMEOUT, RATE_LIMIT, temporary network failures
- [ ] Don't retry on permanent errors: AUTH_ERROR, INVALID_PROMPT
- [ ] Log retry attempts
- [ ] Integration tests

**Acceptance Criteria**:
- Retries configurable (0–5 default)
- Exponential backoff works
- Transient errors retried, permanent errors fail immediately
- Logs show retry attempts
- Performance: backoff doesn't block process engine

---

#### Story 9.5.3 — Timeout Handling & Graceful Degradation (2 sp)
**Description**: Handle timeout scenarios and graceful degradation.

**Tasks**:
- [ ] Timeout configurable per AI task (default 30s)
- [ ] Cancel AI request if timeout exceeded
- [ ] Route to error boundary or fallback
- [ ] Log timeout with partial response (if available)
- [ ] Option to use cached previous response (if configured)

**Acceptance Criteria**:
- Timeouts respected
- Process continues (doesn't hang)
- Fallback executed on timeout
- Logs indicate timeout reason

---

### Phase 9.6: Additional Providers (Anthropic, Gemini, Azure OpenAI, Custom REST)
**Effort**: 16 story points  
**Duration**: 2 weeks  
**Goal**: Support enterprise and open-source AI providers

#### Story 9.6.1 — Anthropic Provider (4 sp)
**Description**: Implement Anthropic Claude provider.

**Tasks**:
- [ ] Create `AnthropicProvider` class
- [ ] Configure models: claude-3-opus, claude-3-sonnet, claude-3-haiku
- [ ] Parameter mapping: temperature, topP, maxTokens
- [ ] System prompt support
- [ ] Streaming support
- [ ] Integration tests

**Acceptance Criteria**:
- Provider passes same test suite as OpenAI
- All models selectable
- All parameters respected

---

#### Story 9.6.2 — Google Gemini Provider (4 sp)
**Description**: Implement Google Gemini provider.

**Tasks**:
- [ ] Create `GeminiProvider` class
- [ ] Configure models: gemini-pro, gemini-vision
- [ ] Implement text generation and vision support
- [ ] Parameter mapping
- [ ] Streaming support
- [ ] Integration tests

**Acceptance Criteria**:
- Provider implementation complete
- Both text and vision models work
- All parameters respected

---

#### Story 9.6.3 — Azure OpenAI Provider (4 sp)
**Description**: Implement Azure OpenAI with enterprise auth.

**Tasks**:
- [ ] Create `AzureOpenAIProvider` class
- [ ] Support Azure authentication (API key, managed identity)
- [ ] Azure endpoint configuration
- [ ] Model deployment name mapping
- [ ] All OpenAI parameters supported
- [ ] Integration tests

**Acceptance Criteria**:
- Azure auth works (API key, managed identity)
- Endpoint resolution correct
- All OpenAI parameters supported

---

#### Story 9.6.4 — Custom REST Provider & Ollama (4 sp)
**Description**: Generic REST provider for custom APIs and Ollama local LLMs.

**Tasks**:
- [ ] Create `CustomRESTProvider` class
- [ ] Configuration:
  - Endpoint URL
  - Auth type (None, Bearer, API Key, Basic)
  - Request body template (JSON)
  - Response body path (JSONPath)
  - Prompt field name
- [ ] Support Ollama as pre-configured template
- [ ] HTTP timeout handling
- [ ] Error handling for non-standard responses
- [ ] Integration tests

**Acceptance Criteria**:
- Custom REST endpoint works
- Auth types supported
- Ollama integration works out-of-box
- JSONPath extraction works

---

### Phase 9.7: Documentation & Examples
**Effort**: 8 story points  
**Duration**: 1 week  
**Goal**: Comprehensive user and developer guides

#### Story 9.7.1 — User Guide: "Ask AI" Quick Start (2 sp)
**Description**: Step-by-step guide for designers to add AI tasks.

**Content**:
- [ ] Getting started: Add AI task to palette, configure provider
- [ ] Screenshots of each configuration panel
- [ ] Common use cases: Summarization, classification, extraction, decision support
- [ ] Variable injection examples
- [ ] Error handling best practices

---

#### Story 9.7.2 — API Reference & Provider Integration (2 sp)
**Description**: API documentation for AI task endpoints and provider contracts.

**Content**:
- [ ] `POST /ai/credentials` reference
- [ ] `GET /ai/executions` reference
- [ ] `AIProvider` interface contract
- [ ] Provider implementation guide for custom providers
- [ ] Examples for each provider

---

#### Story 9.7.3 — Architecture & Design Decisions (2 sp)
**Description**: Document AI component architecture.

**Content**:
- [ ] Provider abstraction layer design
- [ ] Secret management approach
- [ ] Execution flow diagram
- [ ] Error handling strategy
- [ ] Performance considerations

---

#### Story 9.7.4 — Example Workflows (2 sp)
**Description**: Pre-built workflows demonstrating AI integration.

**Workflows**:
- [ ] Customer complaint summarization
- [ ] Email classification (spam/urgent/normal)
- [ ] Contract data extraction
- [ ] Decision support (approval recommendation)

---

---

## Dependency Graph & Critical Path

```
9.1.1 (AI Interface)
  ↓
9.1.2 (Secrets)  9.1.3 (OpenAI Provider)
  ↓               ↓
  └─────────┬─────┘
            ↓
        9.2.1 (Palette)
            ↓
        9.2.2 (Provider Config)
            ↓
        9.2.3 (Prompt Editor)  9.3.2 (Backend Handler)
            ↓                    ↓
        9.2.4 (Tuning)          └────────┬──────┘
            ↓                            ↓
        9.3.1 (Output Mapping)  9.4.1 (Execution Logs)
            ↓                    ↓
        9.3.3 (Variable Autocomplete)
            ↓
        9.5.1 (Error Boundary)
            ↓
        9.5.2 (Retry)
            ↓
        9.5.3 (Timeout)
            ↓
        9.6.x (Additional Providers) || 9.7.x (Documentation)
```

**Critical Path** (Phase 9.1 → 9.2 → 9.3 → 9.5) ≈ 6 weeks
**Full Epic** (Including all providers & docs) ≈ 8 weeks

---

## Tech Stack & Architecture

### Backend
- **AI Provider Interface**: Abstract class with pluggable implementations
- **Secret Management**: Spring Security Crypto + database encryption
- **Async Execution**: RabbitMQ integration (existing worker pattern)
- **Error Handling**: BPMN error boundaries + custom retry logic
- **Logging**: Flyway migrations for execution log tables, audit trail

### Frontend (Modeler)
- **React 19 + TypeScript**
- **Components**: `AIProviderConfigForm`, `PromptEditor`, `AITuningPanel`, `AIOutputMappingPanel`
- **Integration**: Properties panel for AI task nodes
- **Variable Autocomplete**: Lucide icons for variable search

### Frontend (Admin)
- **React 19 + TypeScript**
- **Components**: `AIExecutionHistoryPage`, `AIProviderMetricsPage`
- **Data**: Paginated REST endpoints for execution logs and metrics

### Database
- **AI Credentials Table**: `ai_credentials` (encrypted tokens, RBAC metadata)
- **AI Execution Logs**: `ai_execution_logs` (instance ID, task node ID, provider, metrics, status)
- **New Columns**: `ProcessDefinition.supportsAI`, `AITaskNode` properties

---

## Success Metrics & Acceptance Criteria

### Phase Completion
- All user stories pass acceptance criteria
- Integration tests pass (95%+ coverage)
- No credential leaks in logs/UI
- Response times < 5s for average AI task execution
- Modeler exports/imports AI task configs correctly

### Overall Epic Success
- Designers can add AI tasks to workflows without backend code changes
- At least 3 AI providers supported (OpenAI, Anthropic, Custom REST)
- Execution monitoring dashboard functional
- Zero credential exposure in logs or UI
- Documentation complete and tested

---

## Risk Assessment & Mitigations

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|-----------|
| AI Provider API changes | High | Medium | Abstraction layer allows isolation of changes |
| Rate limiting by providers | High | High | Retry logic + configurable backoff |
| Credential leaks | Critical | Low | Encryption at rest + masking in UI + audit logging |
| Long execution times block process | High | Medium | Async execution via RabbitMQ (existing worker) |
| Variable injection injection attacks | High | Low | Whitelist variable names, no code execution |
| Token consumption tracking overhead | Medium | Medium | Batch log writes, async persistence |

---

## Rollout & Deprecation

**Phase 9**: Launch with OpenAI, Anthropic, Custom REST  
**Phase 9+**: Add Gemini, Azure, Ollama  
**Backward Compatibility**: New `ai-task` node type doesn't affect existing workflows  
**Feature Flag**: `features.aiTasks.enabled` (default: true once Phase 9.5 complete)

---

## Related Epics & Roadmap

**Before AI Task Epic**:
- ✅ Phase 7: Call Activity & Subprocess Support
- ✅ Phase 8: Code Task & JAR Execution
- 📋 Phase 8.3–8.5: Admin UI, QA, Documentation

**After AI Task Epic**:
- Phase 10: Timer Events & Boundary Timers
- Phase 11: CORS & Auth Token Propagation
- Phase 12: Advanced Forms (conditionals, dependencies)

---

## Sign-Off

- **CTO**: Approves architecture (pluggable providers, secret management)
- **Scrum Master**: Tracks sprint progress, manages backlog
- **Backend Lead**: Owns provider implementations
- **Frontend Lead**: Owns modeler and admin UI components
- **QA Lead**: Prepares test scenarios, acceptance criteria

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-05-22 | Process Orchestrator Team | Initial epic definition, 5-phase breakdown, 16 user stories |

