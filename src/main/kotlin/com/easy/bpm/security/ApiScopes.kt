package com.easy.bpm.security

data class ApiScopeDefinition(val code: String, val name: String)

object ApiScopes {
    const val PROCESSES_READ = "processes:read"
    const val PROCESSES_WRITE = "processes:write"
    const val TASKS_READ = "tasks:read"
    const val TASKS_WRITE = "tasks:write"
    const val FORMS_READ = "forms:read"
    const val FORMS_WRITE = "forms:write"
    const val DOCUMENTS_READ = "documents:read"
    const val DOCUMENTS_WRITE = "documents:write"
    const val MESSAGES_PUBLISH = "messages:publish"

    val definitions = listOf(
        ApiScopeDefinition(PROCESSES_READ, "Read process definitions and instances"),
        ApiScopeDefinition(PROCESSES_WRITE, "Deploy and modify processes and instances"),
        ApiScopeDefinition(TASKS_READ, "Read and search tasks"),
        ApiScopeDefinition(TASKS_WRITE, "Claim, update, and complete tasks"),
        ApiScopeDefinition(FORMS_READ, "Read deployed forms"),
        ApiScopeDefinition(FORMS_WRITE, "Deploy forms"),
        ApiScopeDefinition(DOCUMENTS_READ, "Read and download documents"),
        ApiScopeDefinition(DOCUMENTS_WRITE, "Upload and delete documents"),
        ApiScopeDefinition(MESSAGES_PUBLISH, "Publish BPM messages")
    )

    val all: Set<String> = definitions.mapTo(linkedSetOf()) { it.code }

    fun authority(scope: String): String = "SCOPE_$scope"
}
