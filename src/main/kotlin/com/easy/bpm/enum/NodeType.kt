package com.easy.bpm.enum

enum class NodeType(val typeName: String) {
    StartEvent("StartEvent"),
    EndEvent("EndEvent"),
    UserTask("HumanTask"),
    Integration("Integration"),
    InclusiveGateway("InclusiveGateway"),
    ExclusiveGateway("ExclusiveGateway"),
    ParallelGateway("ParallelGateway"),
    APITask("APITask"),
    ServiceTask("ServiceTask"),
    ScriptTask("ScriptTask"),
    AiTask("AiTask"),
    AgentProcessCall("AgentProcessCall"),
    CodeTask("CodeTask"),
    TimerEvent("TimerEvent"),
    MessageEvent("MessageEvent"),
    MessageStartEvent("MessageStartEvent"),
    MessageIntermediateCatchEvent("MessageIntermediateCatchEvent"),
    MessageIntermediateThrowEvent("MessageIntermediateThrowEvent"),
    ErrorBoundaryEvent("ErrorBoundaryEvent"),
    Participant("Participant"),
    CallActivity("CallActivity");

    companion object {
        private val map = entries.associateBy { it.typeName } + mapOf(
            // Backward compatibility for existing process definitions and imports.
            "UserTask" to UserTask,
            "Pool" to Participant
        )
        fun fromString(s: String): NodeType = map[s]
            ?: throw IllegalArgumentException("Invalid node type '$s'")
    }
}
