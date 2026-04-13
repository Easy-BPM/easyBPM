package com.easy.bpm.enum

enum class NodeType(val typeName: String) {
    StartEvent("StartEvent"),
    EndEvent("EndEvent"),
    UserTask("UserTask"),
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
    CallActivity("CallActivity");

    companion object {
        private val map = values().associateBy { it.typeName }
        fun fromString(s: String): NodeType = map[s]
            ?: throw IllegalArgumentException("Invalid node type '$s'")
    }
}
