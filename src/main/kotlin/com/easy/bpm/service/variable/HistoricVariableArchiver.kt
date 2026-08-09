package com.easy.bpm.service.variable

import com.easy.bpm.model.task.Task
import com.easy.bpm.model.variable.HistoricProcessVariable
import com.easy.bpm.model.variable.HistoricTaskVariable
import com.easy.bpm.repository.variable.HistoricProcessVariableRepository
import com.easy.bpm.repository.variable.HistoricTaskVariableRepository
import com.easy.bpm.repository.variable.ProcessVariableRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class HistoricVariableArchiver(
    private val processVariableRepository: ProcessVariableRepository,
    private val taskVariableRepository: TaskVariableRepository,
    private val historicProcessVariableRepository: HistoricProcessVariableRepository,
    private val historicTaskVariableRepository: HistoricTaskVariableRepository
) {
    @Transactional
    fun archiveTaskVariables(task: Task): Int {
        val variables = taskVariableRepository.findByTaskId(task.id)
        if (variables.isEmpty()) return 0

        historicTaskVariableRepository.saveAll(
            variables.map { variable ->
                HistoricTaskVariable(
                    id = variable.id,
                    taskId = variable.taskId,
                    processInstanceId = variable.processInstanceId,
                    name = variable.name,
                    value = variable.value
                )
            }
        )

        taskVariableRepository.deleteByTaskId(task.id)
        return variables.size
    }

    @Transactional
    fun archiveProcessInstanceVariables(processInstanceId: Long): ArchivedVariableCounts {
        val processVariables = processVariableRepository.findByProcessInstanceId(processInstanceId)
        val existingHistoricProcessVariables = historicProcessVariableRepository.findByProcessInstanceId(processInstanceId)
        val runtimeHistoricProcessVariables = processVariables.map { variable ->
            HistoricProcessVariable(
                id = variable.id,
                processInstanceId = variable.processInstanceId,
                name = variable.name,
                value = variable.value
            )
        }
        val mergedProcessVariables = (existingHistoricProcessVariables + runtimeHistoricProcessVariables)
            .associateBy { it.name }
            .values

        if (mergedProcessVariables.isNotEmpty()) {
            historicProcessVariableRepository.deleteByProcessInstanceId(processInstanceId)
            historicProcessVariableRepository.flush()
            historicProcessVariableRepository.saveAll(mergedProcessVariables)
        }

        if (processVariables.isNotEmpty()) {
            processVariableRepository.deleteByProcessInstanceId(processInstanceId)
        }

        return ArchivedVariableCounts(
            processVariables = processVariables.size,
            taskVariables = 0
        )
    }
}

data class ArchivedVariableCounts(
    val processVariables: Int,
    val taskVariables: Int
)
