package com.easy.bpm.service.process

import com.easy.bpm.service.*

import com.easy.bpm.model.process.ProcessInstanceEvent
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class ProcessInstanceTimelineService(
    private val eventRepository: ProcessInstanceEventRepository
) {
    fun getTimeline(processInstanceId: Long): List<ProcessInstanceEvent> =
        eventRepository.findByProcessInstanceIdOrderByCreatedAtAscIdAsc(processInstanceId)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun record(
        processInstanceId: Long,
        eventType: ProcessInstanceEventType,
        message: String,
        nodeId: String? = null,
        actor: String? = null,
        details: String? = null
    ): ProcessInstanceEvent =
        eventRepository.save(
            ProcessInstanceEvent(
                processInstanceId = processInstanceId,
                nodeId = nodeId,
                eventType = eventType,
                message = message.take(4000),
                actor = actor,
                details = details?.take(8000)
            )
        )
}
