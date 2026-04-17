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
    TimerEvent("TimerEvent"),
    MessageEvent("MessageEvent"),
    MessageIntermediateCatchEvent("MessageIntermediateCatchEvent"),
    MessageIntermediateThrowEvent("MessageIntermediateThrowEvent"),
    ErrorBoundaryEvent("ErrorBoundaryEvent"),
    CallActivity("CallActivity");

    companion object {
        private val map = values().associateBy { it.typeName } + mapOf(
            // Backward compatibility for existing process definitions and imports.
            "UserTask" to UserTask
        )
        fun fromString(s: String): NodeType = map[s]
            ?: throw IllegalArgumentException("Invalid node type '$s'")
    }
}
