package com.easy.bpm.service.process

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.model.incident.IncidentSource
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.variable.ProcessVariable
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.process.CallActivityMappingRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.fasterxml.jackson.databind.JsonNode
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

@Service
class ProcessService(
    private val processDefinitionRepository: ProcessDefinitionRepository,
    private val processInstanceRepository: ProcessInstanceRepository,
    private val processVariableRepository: ProcessVariableRepository,
    private val callActivityMappingRepository: CallActivityMappingRepository,
    private val pageableSanitizer: ProcessPageableSanitizer,
    private val variableManager: ProcessVariableManager,
    private val lifecycleManager: ProcessInstanceLifecycleManager,
    private val deploymentService: ProcessDeploymentService,
    private val instanceStarter: ProcessInstanceStarter,
    private val workerCallbackService: ProcessWorkerCallbackService,
    private val messageRuntimeService: ProcessMessageRuntimeService
) {

    companion object {
        const val INTERNAL_TIMER_MESSAGE_NAME = "__internal.timer__"
    }

    /* =========================
       DEPLOY
     ========================= */

    fun deployProcess(definitionJson: JsonNode): ProcessDefinition =
        deploymentService.deployProcess(definitionJson)

    fun deployProcess(definitionXml: String): ProcessDefinition =
        deploymentService.deployProcess(definitionXml)

    /* =========================
       START PROCESS
     ========================= */

    @Transactional
    fun startProcessInstance(processDefinitionId: Long): ProcessInstance {
        val definition = processDefinitionRepository.findById(processDefinitionId)
            .orElseThrow { IllegalArgumentException("Process definition not found") }

        return instanceStarter.startWithDefinition(definition)
    }


    // Used in subprocess
    fun startProcessInstance(processDefinitionId: Long, initialVariables: Map<String, Any>): ProcessInstance {
        val definition = processDefinitionRepository.findById(processDefinitionId)
            .orElseThrow { IllegalArgumentException("Process definition not found") }

        return instanceStarter.startWithDefinition(definition, initialVariables)
    }

    fun startProcessInstance(processId: String): ProcessInstance {
        val definition = processDefinitionRepository.findTopByKeyOrderByVersionDesc(processId)
            ?: throw IllegalArgumentException("Process definition not found for id: $processId")

        return instanceStarter.startWithDefinition(definition)
    }

    fun getProcessInstances(pageable: Pageable): Page<ProcessInstance> =
        processInstanceRepository.findAll(pageableSanitizer.sanitizeProcessInstances(pageable))

    fun getProcessInstanceById(id: Long): ProcessInstance? =
        processInstanceRepository.findById(id).orElse(null)

    fun getChildProcessInstances(parentInstanceId: Long): List<ProcessInstance> {
        processInstanceRepository.findById(parentInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        return processInstanceRepository.findByParentInstanceId(parentInstanceId)
    }

    fun getParentProcessInstance(childInstanceId: Long): ProcessInstance? {
        val child = processInstanceRepository.findById(childInstanceId).orElse(null) ?: return null
        val parentId = child.parentInstanceId ?: return null
        return processInstanceRepository.findById(parentId).orElse(null)
    }

    fun getCallActivityMapping(parentInstanceId: Long, childInstanceId: Long) =
        callActivityMappingRepository.findByParentInstanceIdAndChildInstanceId(parentInstanceId, childInstanceId)

    fun getProcessVariables(processInstanceId: Long): List<ProcessVariable> {
        processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        return processVariableRepository.findByProcessInstanceId(processInstanceId)
    }

    @Transactional
    fun assignProcessVariables(processInstanceId: Long, variables: Map<String, Any?>): List<ProcessVariable> {
        val instance = processInstanceRepository.findById(processInstanceId)
            .orElseThrow { IllegalArgumentException("Process instance not found") }

        if (instance.status == ProcessStatus.COMPLETED) {
            throw IllegalStateException("Cannot assign variables to a completed process instance")
        }

        return variableManager.assignProcessVariables(processInstanceId, variables)
    }

    @Transactional
    fun moveProcessNode(processInstanceId: Long, fromNode: String, toNode: String): ProcessInstance =
        lifecycleManager.moveProcessNode(processInstanceId, fromNode, toNode)

    fun getLatestProcessDefinitions(pageable: Pageable): Page<ProcessDefinition> =
        processDefinitionRepository.findLatestVersionProcesses(pageableSanitizer.sanitizeProcessDefinitions(pageable))

    fun getProcessDefinitionById(id: Long): ProcessDefinition? =
        processDefinitionRepository.findById(id).orElse(null)

    @Transactional
    fun stopProcessInstance(id: Long): ProcessInstance =
        lifecycleManager.stopProcessInstance(id)

    @Transactional
    fun deleteProcessInstance(id: Long) =
        lifecycleManager.deleteProcessInstance(id)

    @Transactional
    fun handleServiceTaskCompleted(processInstanceId: Long, nodeId: String, outputs: Map<String, String>) {
        workerCallbackService.handleServiceTaskCompleted(processInstanceId, nodeId, outputs)
    }

    @Transactional
    fun handleServiceTaskFailed(
        processInstanceId: Long,
        nodeId: String,
        errorMessage: String? = null,
        incidentSource: IncidentSource = IncidentSource.WORKER,
        externalReferenceId: String? = null
        ,
        createIncident: Boolean = true
    ) {
        workerCallbackService.handleServiceTaskFailed(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            errorMessage = errorMessage ?: "Service task failed",
            incidentSource = incidentSource,
            externalReferenceId = externalReferenceId
        )
    }

    fun markServiceTaskTimedOut(
        processInstanceId: Long,
        nodeId: String,
        errorMessage: String,
        externalReferenceId: String? = null
    ) {
        workerCallbackService.markServiceTaskTimedOut(
            processInstanceId = processInstanceId,
            nodeId = nodeId,
            errorMessage = errorMessage,
            externalReferenceId = externalReferenceId
        )
    }

    @Transactional
    fun handleMessageReceived(
        messageName: String,
        correlationKey: String,
        variables: Map<String, Any>? = null
    ) = messageRuntimeService.handleMessageReceived(messageName, correlationKey, variables)

    /**
     * Handle a message subscription timeout by routing to an attached ErrorBoundaryEvent
     * if present. Returns true if the timeout was handled by a boundary, false otherwise.
     */
    @Transactional
    fun handleSubscriptionTimeout(processInstanceId: Long, nodeId: String): Boolean =
        messageRuntimeService.handleSubscriptionTimeout(processInstanceId, nodeId)

    /**
     * Continue process execution after a TimerEvent timeout is reached.
     */
    @Transactional
    fun handleTimerTimeout(processInstanceId: Long, nodeId: String): Boolean =
        messageRuntimeService.handleTimerTimeout(processInstanceId, nodeId)
}
