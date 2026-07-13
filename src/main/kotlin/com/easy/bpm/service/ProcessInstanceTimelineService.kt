package com.easy.bpm.service

import com.easy.bpm.model.process.ProcessInstanceEvent
import com.easy.bpm.model.process.ProcessInstanceEventType
import com.easy.bpm.repository.process.ProcessInstanceEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import com.easy.bpm.tenant.TenantContext

@Service
class ProcessInstanceTimelineService(
    private val eventRepository: ProcessInstanceEventRepository
) {
    fun getTimeline(processInstanceId: Long): List<ProcessInstanceEvent> =
        eventRepository.findByTenantIdAndProcessInstanceIdOrderByCreatedAtAscIdAsc(TenantContext.getTenant(), processInstanceId)

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
                tenantId = TenantContext.getTenant(),
                processInstanceId = processInstanceId,
                nodeId = nodeId,
                eventType = eventType,
                message = message.take(4000),
                actor = actor,
                details = details?.take(8000)
            )
        )
}
