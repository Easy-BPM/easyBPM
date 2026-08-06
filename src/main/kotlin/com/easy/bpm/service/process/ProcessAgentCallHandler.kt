package com.easy.bpm.service.process

import com.easy.bpm.handler.AgentProcessCallHandler
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component

@Component
class ProcessAgentCallHandler(
    private val agentProcessCallHandler: AgentProcessCallHandler,
    private val navigator: ProcessNavigator,
    private val timelineService: ProcessInstanceTimelineService
) {
    fun handleAgentProcessCall(
        instance: ProcessInstance,
        node: JsonNode,
        definition: JsonNode
    ): List<String> {
        val nodeId = node.get("id").asText()
        timelineService.record(
            processInstanceId = instance.id,
            nodeId = nodeId,
            eventType = ProcessInstanceEventType.AGENT_PROCESS_STARTED,
            message = "Agent process call started."
        )

        val execution = agentProcessCallHandler.execute(instance, node)

        timelineService.record(
            processInstanceId = instance.id,
            nodeId = nodeId,
            eventType = ProcessInstanceEventType.AGENT_PROCESS_COMPLETED,
            message = "Agent process call completed.",
            details = "agentExecutionId=${execution.id}; status=${execution.status}"
        )

        return navigator.getNextNodes(node, definition, instance)
    }
}
