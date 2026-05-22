# Easy BPM AI Task Component Implementation Guide

## Quick Start

### Understanding the Architecture

The AI task component is organized into layers:

```
┌─── REST API Layer (controllers/)
│    └─ AICredentialController: Manage encrypted credentials
│
├─── Service Layer (service/)
│    ├─ CredentialVault: Encrypt/decrypt tokens, RBAC checks
│    └─ AuditService: Log all credential operations
│
├─── Provider Layer (provider/)
│    ├─ AIProvider: Abstract base class (contract for all providers)
│    ├─ openai/OpenAIProvider: Concrete OpenAI implementation
│    └─ [anthropic, gemini, azure, custom]: Planned
│
├─── Factory Layer (factory/)
│    ├─ AIProviderFactory: Create provider instances
│    └─ AIProviderRegistry: Manage available providers
│
├─── Data Layer (entity/, repository/)
│    ├─ AICredential: JPA entity for encrypted tokens
│    └─ AICredentialRepository: Spring Data JPA repo
│
└─── DTOs (dto/)
     └─ AITaskDtos.kt: All request/response models
```

---

## Key Concepts

### 1. Credential Vault (Encryption)
```kotlin
// Create credential (encrypted before storage)
val credential = credentialVault.storeCredential(userId, request)

// Retrieve credential (decrypted for use)
val apiKey = credentialVault.retrieveCredential(credentialId, userId)

// Environment variable references
val apiKey = credentialVault.resolveCredentialRef("$OPENAI_API_KEY", userId)
```

**Security**: Tokens encrypted at rest using Spring Security Crypto.

### 2. Provider Factory (Pluggable)
```kotlin
// Create provider instance
val provider = factory.createProvider("openai", config, userId)

// Get provider metadata (no instantiation needed)
val metadata = factory.getProviderMetadata("openai")

// Validate config before using
val validation = factory.validateConfig("openai", config)
```

**Extensibility**: Adding a new provider = 1 new file + 1 factory case statement.

### 3. Execute AI Task
```kotlin
val request = AIExecutionRequestDto(
    promptTemplate = "Summarize: {{text}}",
    variables = mapOf("text" to customerComplaint),
    providerConfig = config,
    tuningParams = tuning
)

val response = provider.execute(request)
// response.responseText → AI output
// response.tokensUsed → Token consumption
// response.errorCode → Error classification (if failed)
```

---

## Adding a New AI Provider

### Step 1: Create Provider Class
```kotlin
// src/main/kotlin/com/easy/bpm/ai/provider/YOUR_PROVIDER/YourProvider.kt

class YourProvider(
    private val config: AIProviderConfigDto,
    private val credentialVault: CredentialVault,
    private val userId: String
) : AIProvider() {
    
    override fun execute(request: AIExecutionRequestDto): AIExecutionResponseDto {
        // Your implementation
    }
    
    override fun validateConfig(request: AIExecutionRequestDto): ValidationResultDto {
        // Validate config before execution
    }
    
    override fun getMetadata(): AIProviderMetadataDto {
        // Return provider capabilities and models
    }
    
    companion object {
        fun getStaticMetadata(): AIProviderMetadataDto { /* ... */ }
        fun validateConfig(config: AIProviderConfigDto): ValidationResultDto { /* ... */ }
    }
}
```

### Step 2: Register in Factory
```kotlin
// factory/AIProviderFactory.kt
fun createProvider(...): AIProvider {
    return when (normalizedId) {
        "openai" -> OpenAIProvider(...)
        "your-provider" -> YourProvider(...)  // ← Add here
        else -> ...
    }
}
```

### Step 3: Add to Registry
```kotlin
// factory/AIProviderRegistry.kt
init {
    register("your-provider", null)  // Phase X.Y
}
```

### Step 4: Write Tests
```kotlin
// src/test/kotlin/com/easy/bpm/ai/provider/your_provider/YourProviderTest.kt
class YourProviderTest {
    @Test
    fun `test metadata`() { /* ... */ }
    
    @Test
    fun `test validation`() { /* ... */ }
    
    @Test
    fun `test execute`() { /* ... */ }
}
```

---

## REST API Examples

### Create Credential
```bash
curl -X POST http://localhost:8080/ai/credentials \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "providerId": "openai",
    "credentialType": "API_KEY",
    "token": "sk-..."
  }'

# Response 201
{
  "id": "cred-uuid",
  "providerId": "openai",
  "credentialType": "API_KEY",
  "maskedToken": "sk-***...hfaX",
  "createdAt": "2026-05-22T13:31:27Z"
}
```

### List Credentials
```bash
curl http://localhost:8080/ai/credentials \
  -H "Authorization: Bearer <token>"

# Response 200
[
  {
    "id": "cred-1",
    "providerId": "openai",
    "maskedToken": "sk-***...xyz"
  },
  {
    "id": "cred-2",
    "providerId": "anthropic",
    "maskedToken": "claude-***...xyz"
  }
]
```

### Delete Credential
```bash
curl -X DELETE http://localhost:8080/ai/credentials/cred-uuid \
  -H "Authorization: Bearer <token>"

# Response 204 No Content
```

---

## Configuration & Environment Variables

### Encryption Key
```bash
# Set encryption password (default: "default-dev-key-change-in-prod")
export AI_ENCRYPTION_KEY="your-secure-password-here"
```

### OpenAI API Key (Test)
```bash
# Store credential via API (recommended)
# OR set env var for local testing:
export OPENAI_API_KEY="sk-..."
```

### Database Migration
```bash
# Flyway automatically applies V21__Create_AI_Credentials_Table.sql on startup
./gradlew bootRun

# Or manually:
./gradlew flywayMigrate
```

---

## Testing

### Run All Phase 9.1 Tests
```bash
./gradlew test --tests "*AIProvider*"
./gradlew test --tests "*CredentialVault*"
```

### Run Specific Test
```bash
./gradlew test --tests "*OpenAIProviderTest"
```

### Generate Coverage Report
```bash
./gradlew test jacocoTestReport
# View: build/reports/jacoco/test/html/index.html
```

---

## Debugging & Logs

### Enable Debug Logging
```properties
# application.properties
logging.level.com.easy.bpm.ai=DEBUG
```

### Common Issues

**1. Credential not found**
- Verify user ID matches security context
- Check credential ownership: `credentialRepository.findByIdAndOwnerId(id, userId)`

**2. Decryption failed**
- Ensure `AI_ENCRYPTION_KEY` env var is set and consistent
- Credential must have been encrypted with same key

**3. Provider not found**
- Check registry: `registry.getRegisteredProviders()`
- Verify provider ID is registered in `AIProviderRegistry.init()`

**4. OpenAI rate limit (429)**
- Implement retry logic (Phase 9.5)
- Or configure backoff strategy in tuning params

---

## Migration Path: Database

### V21: Initial AI Credentials Schema
- Creates `ai_credentials` table
- Creates `ai_credential_permissions` table (RBAC)
- Creates `ai_credential_audit_log` table
- Indexes for performance

### Future Versions
- V22: Add `ai_execution_logs` table (Phase 9.4)
- V23: Add AI task configuration columns to process definitions
- V24: Add provider health check table (Phase 9.4)

---

## Security Best Practices

✅ **DO**:
- Store API keys via POST /ai/credentials endpoint
- Use environment variables for dev-only credentials (`$VAR_NAME` syntax)
- Enable RBAC permissions when sharing credentials
- Audit logs for compliance

❌ **DON'T**:
- Hardcode API keys in code
- Expose tokens in logs (automatic sanitization)
- Bypass RBAC checks
- Store plaintext tokens in database

---

## Performance Tuning

### Credential Retrieval
- Indexed on: owner_id, provider_id, created_at
- Decryption: ~5ms per token
- Database query: <1ms (indexed)

### Provider Execution
- OpenAI avg: 2-5 seconds (network latency)
- Token counting: <1ms (in-memory)
- Error classification: <1ms

### Caching Strategy (Future)
- Cache provider metadata (getMetadata() result)
- Cache credential decrypt results per request
- Cache provider health checks (TTL 5min)

---

## Integration with Process Engine

### Phase 9.3: AI Task Handler
```kotlin
class AITaskHandler : TaskHandler {
    fun handle(aiTaskNode: BpmnNode, instance: ProcessInstance) {
        // 1. Resolve provider & credential
        val provider = factory.createProvider(...)
        
        // 2. Render prompt with process variables
        val request = AIExecutionRequestDto(...)
        
        // 3. Execute AI task
        val response = provider.execute(request)
        
        // 4. Store response in process variable
        instance.setVariable(outputVariable, response.responseText)
        
        // 5. Continue execution
        processService.executeNodes(instance)
    }
}
```

---

## Roadmap

**Phase 9.1** ✅ COMPLETE
- [x] Provider infrastructure
- [x] Credential management
- [x] OpenAI provider

**Phase 9.2** (Modeler UI)
- [ ] AI task palette
- [ ] Provider config form
- [ ] Prompt editor

**Phase 9.3** (Backend Execution)
- [ ] AITaskHandler
- [ ] Variable integration
- [ ] Error boundaries

**Phase 9.4** (Admin Monitoring)
- [ ] Execution logs
- [ ] Metrics dashboard
- [ ] Health checks

**Phase 9.5** (Resilience)
- [ ] Retry logic
- [ ] Timeout handling
- [ ] Fallback paths

**Phase 9.6** (Multi-Provider)
- [ ] Anthropic
- [ ] Gemini
- [ ] Azure OpenAI
- [ ] Custom REST

---

## Useful Links

- **Epic Document**: `EPIC-ai-task-component.md`
- **Sprint Plan**: `PHASE-9-1-SPRINT-PLAN.md`
- **Implementation Summary**: `PHASE-9-1-IMPLEMENTATION-SUMMARY.md`
- **Backend Controllers**: `src/main/kotlin/com/easy/bpm/ai/controller/`
- **DTOs**: `src/main/kotlin/com/easy/bpm/ai/dto/AITaskDtos.kt`

---

## Support & Questions

- **Backend Questions**: Ask in #backend-dev Slack channel
- **Code Review**: Use GitHub pull requests
- **Bug Reports**: Create issue in project backlog
- **Architecture Decisions**: CTO review required

---

**Last Updated**: 2026-05-22  
**Status**: Phase 9.1 Implementation Complete
