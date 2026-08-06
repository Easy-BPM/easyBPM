package com.easy.bpm.service.process

import com.easy.bpm.service.admin.*
import com.easy.bpm.service.agent.*
import com.easy.bpm.service.auth.*
import com.easy.bpm.service.code.*
import com.easy.bpm.service.document.*
import com.easy.bpm.service.form.*
import com.easy.bpm.service.incident.*
import com.easy.bpm.service.integration.*
import com.easy.bpm.service.message.*
import com.easy.bpm.service.metrics.*
import com.easy.bpm.service.process.*
import com.easy.bpm.service.task.*
import com.easy.bpm.service.variable.*
import com.easy.bpm.service.worker.*

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
