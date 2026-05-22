# Phase 9.1 Sprint Plan: Core AI Infrastructure
**Sprint Duration**: 2 weeks (10 business days)  
**Target Release**: Post-Phase 8 (Early Q3 2026)  
**Team Size**: 3 backend developers, 1 QA engineer  
**Total Story Points**: 16 sp  
**Status**: PLANNING

---

## Sprint Goal
Establish backend architecture for pluggable AI providers with secure credential management and OpenAI integration.

---

## User Stories & Task Breakdown

### US-9.1.1: AI Provider Interface & Factory Pattern (5 sp)
**Owner**: Backend Developer 1  
**Duration**: 3 days

#### Dev Tasks
- [ ] Create `com.easy.bpm.ai.provider.AIProvider` abstract class
  - Method: `execute(request: AIExecutionRequest): AIExecutionResponse`
  - Method: `validateConfig(config: AIProviderConfig): ValidationResult`
  - Method: `getMetadata(): AIProviderMetadata`
  - Support async/future-based execution

- [ ] Create `com.easy.bpm.ai.model.AIProviderConfig` DTO
  - Properties: providerName, modelName, endpoint, apiVersion, timeout, streamingEnabled
  - Validation: required field checks, URL validation

- [ ] Create `com.easy.bpm.ai.model.AIExecutionRequest` DTO
  - Properties: promptTemplate, userPrompt, systemPrompt, variables, tuningParams, credentialId

- [ ] Create `com.easy.bpm.ai.model.AIExecutionResponse` DTO
  - Properties: responseText, tokensUsed, executionDurationMs, success, errorMessage, errorCode

- [ ] Create `com.easy.bpm.ai.model.AIProviderMetadata` DTO
  - Properties: providerId, providerName, supportedModels[], defaultModel, supportsStreaming

- [ ] Create `com.easy.bpm.ai.factory.AIProviderFactory` class
  - Method: `createProvider(providerName: String, config: AIProviderConfig, credentialVault: CredentialVault): AIProvider`
  - Validation: provider name registered check

- [ ] Create `com.easy.bpm.ai.factory.AIProviderRegistry` class
  - Map: providerName → Provider.class
  - Method: `register(providerName: String, providerClass: Class<? extends AIProvider>)`
  - Method: `getRegistry(): Map<String, AIProviderMetadata>`
  - Initialize with: OpenAI, Anthropic, Gemini, Azure, Custom REST

- [ ] Unit tests: Factory pattern, registry, provider instantiation (≥95% coverage)
  - Test valid provider instantiation
  - Test invalid provider error handling
  - Test registry reflection

#### Acceptance Criteria
- ✅ Factory can instantiate providers by name
- ✅ Registry queryable for available providers
- ✅ Extensible design (adding new provider is 1 file + 1 registration line)
- ✅ All providers must implement same contract
- ✅ Unit tests pass, 95%+ coverage

---

### US-9.1.2: Secret Management & Credential Storage (5 sp)
**Owner**: Backend Developer 2  
**Duration**: 4 days

#### Dev Tasks
- [ ] Create `com.easy.bpm.ai.entity.AICredential` JPA entity
  - Fields: id (UUID), providerId (String), credentialType (enum: API_KEY, BEARER, BASIC_AUTH), encryptedToken (String), ownerId (String), permissions (List of roles), createdAt, updatedAt, lastUsedAt
  - Unique constraint: providerId + ownerId (one credential per provider per user)
  - Index: ownerId, providerId, createdAt

- [ ] Create `com.easy.bpm.ai.repository.AICredentialRepository` JPA repository
  - Method: `findByOwnerIdAndProviderId(ownerId, providerId): Optional<AICredential>`
  - Method: `findByOwnerId(ownerId): List<AICredential>`
  - Method: `findByIdAndOwnerId(id, ownerId): Optional<AICredential>` (security check)

- [ ] Create `com.easy.bpm.ai.service.CredentialVault` service
  - Method: `encrypt(plaintext: String): String` (using Spring Security Crypto)
  - Method: `decrypt(ciphertext: String): String`
  - Method: `storeCredential(credential: AICredential): AICredential`
  - Method: `retrieveCredential(credentialId: UUID, userId: String): String` (with RBAC check)
  - Method: `maskCredential(credential: AICredential): AICredential` (show last 4 chars only)
  - Method: `deleteCredential(credentialId: UUID, userId: String): void` (with audit log)
  - Support environment variable references: `$ENV_VAR_NAME` → resolve at runtime

- [ ] Create `com.easy.bpm.ai.dto.AICredentialDto` (API response)
  - Masked token: show only last 4 chars
  - No decrypted tokens in response

- [ ] Unit tests: Encrypt/decrypt, storage, retrieval, masking (≥95% coverage)
  - Test encryption/decryption roundtrip
  - Test environment variable resolution
  - Test RBAC rejection for unauthorized users
  - Test credential masking in DTOs

#### Acceptance Criteria
- ✅ Tokens encrypted at rest (Spring Security Crypto)
- ✅ Tokens masked in API responses (last 4 chars visible)
- ✅ No tokens in execution logs
- ✅ RBAC enforces credential access (users can't access other users' credentials)
- ✅ Environment variable references resolved
- ✅ Unit tests pass, 95%+ coverage

---

### US-9.1.3: OpenAI Provider Implementation (6 sp)
**Owner**: Backend Developer 3  
**Duration**: 5 days

#### Dev Tasks
- [ ] Create `com.easy.bpm.ai.provider.openai.OpenAIProvider` class extending AIProvider
  - Implement: `execute(request: AIExecutionRequest): AIExecutionResponse`
  - HTTP client: `RestTemplate` or `WebClient`
  - Endpoint: `https://api.openai.com/v1/chat/completions`
  - Model selection: gpt-4, gpt-3.5-turbo, gpt-4-turbo-preview

- [ ] Implement parameter mapping:
  - temperature (0.0–2.0) → OpenAI temperature
  - topP (0.0–1.0) → OpenAI top_p
  - maxTokens → max_tokens
  - frequencyPenalty (−2.0–2.0) → frequency_penalty
  - presencePenalty (−2.0–2.0) → presence_penalty
  - timeout (ms) → HTTP timeout

- [ ] Streaming support:
  - If streamingEnabled: use `/stream` endpoint
  - Collect streamed tokens into response
  - Non-streaming: standard JSON response

- [ ] Error handling:
  - 401 Unauthorized → AUTH_ERROR
  - 429 Too Many Requests → RATE_LIMIT (retry-after header)
  - 500+ Server Errors → PROVIDER_ERROR
  - Socket timeout → TIMEOUT
  - JSON parse error → PARSE_ERROR
  - Log all errors (sanitized, no token exposure)

- [ ] Token consumption tracking:
  - OpenAI API returns usage.prompt_tokens, usage.completion_tokens
  - Sum = total tokens consumed
  - Store in execution log

- [ ] Create `com.easy.bpm.ai.provider.openai.OpenAIConfig` DTO
  - Fields: apiKey (from credential vault), model, endpoint (default: OpenAI public)

- [ ] Integration tests:
  - Mock OpenAI API responses (using WireMock or similar)
  - Test successful text generation
  - Test streaming
  - Test all tuning parameters
  - Test error scenarios (auth, rate limit, timeout)
  - Test token counting

#### Acceptance Criteria
- ✅ Provider passes full integration test suite
- ✅ All tuning parameters respected
- ✅ Streaming works
- ✅ Token counting accurate
- ✅ Error handling distinguishes error types
- ✅ No tokens in logs
- ✅ Performance: <5s average execution time

---

## Daily Standup Template

**Date**: [Date]  
**Team**: Dev 1, Dev 2, Dev 3, QA  

**Dev 1 (Factory & Registry)**
- [ ] Yesterday: [Task completed]
- [ ] Today: [Task to start]
- [ ] Blockers: [None/List]

**Dev 2 (Credential Vault)**
- [ ] Yesterday: [Task completed]
- [ ] Today: [Task to start]
- [ ] Blockers: [None/List]

**Dev 3 (OpenAI Provider)**
- [ ] Yesterday: [Task completed]
- [ ] Today: [Task to start]
- [ ] Blockers: [None/List]

**QA**
- [ ] Test plan prepared for: [Module]
- [ ] Test scenarios written: [Count]
- [ ] Blockers: [None/List]

---

## Testing Strategy

### Unit Tests (by Dev)
- Provider interface contract validation
- Encryption/decryption roundtrip
- Factory and registry tests
- Parameter validation
- Error code mapping

**Coverage Target**: 95%+  
**Test Framework**: JUnit 5 + Mockito

### Integration Tests (QA + Dev)
- Mock OpenAI API using WireMock
- Test full request/response cycle
- Test error scenarios
- Test streaming
- Test credential retrieval and injection

**Test Framework**: Spring Test + WireMock  
**Execution**: `./gradlew :ai-task:integrationTest`

### API Contract Tests (QA)
- Test credential endpoints (POST, GET, DELETE)
- Test request/response DTOs
- Test RBAC (unauthorized access)
- Test token masking in responses

---

## Deliverables

### Code
- [ ] `src/main/kotlin/com/easy/bpm/ai/provider/AIProvider.kt`
- [ ] `src/main/kotlin/com/easy/bpm/ai/factory/AIProviderFactory.kt`
- [ ] `src/main/kotlin/com/easy/bpm/ai/factory/AIProviderRegistry.kt`
- [ ] `src/main/kotlin/com/easy/bpm/ai/service/CredentialVault.kt`
- [ ] `src/main/kotlin/com/easy/bpm/ai/entity/AICredential.kt`
- [ ] `src/main/kotlin/com/easy/bpm/ai/provider/openai/OpenAIProvider.kt`
- [ ] `src/main/kotlin/com/easy/bpm/ai/dto/*.kt` (DTOs)
- [ ] `src/test/kotlin/com/easy/bpm/ai/**Test.kt` (Tests)

### Database
- [ ] Flyway migration: `V21__Create_AI_Credentials_Table.sql`
- [ ] Indexes for performance

### Documentation
- [ ] README: Architecture overview
- [ ] Code comments: Complex logic
- [ ] Test scenario descriptions

---

## Dependencies & Prerequisites

### External
- OpenAI API account with API key
- Spring Security Crypto library (already in project)
- RestTemplate or WebClient (already in project)

### Internal
- Existing authentication/RBAC system in Easy BPM
- Existing database connection pool
- RabbitMQ for async execution (Phase 9.3+)

### Known Risks
1. **OpenAI API rate limits** → Mitigated by retry logic + exponential backoff (Phase 9.5)
2. **Credential leaks** → Mitigated by encryption + audit logging
3. **Long execution times** → Mitigated by async worker (Phase 9.3)

---

## Sign-Off Checklist

- [ ] CTO approves architecture (provider abstraction, secret management)
- [ ] Backend lead approves code structure
- [ ] QA lead approves test plan
- [ ] All user stories pass acceptance criteria
- [ ] Integration tests 100% pass
- [ ] Code review approved (all 3 devs review each other's code)
- [ ] No credentials exposed in logs, DTOs, or error messages
- [ ] Documentation complete
- [ ] Sprint retrospective scheduled

---

## Next Sprint Preview (Phase 9.2)
- Modeler UI: AI task palette
- Provider configuration forms
- Prompt editor
- Will depend on Phase 9.1 backend APIs

