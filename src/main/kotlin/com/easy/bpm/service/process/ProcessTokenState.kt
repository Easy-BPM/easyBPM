package com.easy.bpm.service.process

import com.easy.bpm.model.process.ProcessInstance

/** Mutate only the branch being advanced; callers hold the process-instance lock. */
internal object ProcessTokenState {
    fun advance(instance: ProcessInstance, completedNodeId: String?, nextNodes: List<String>, waiting: Boolean) {
        val remaining = if (completedNodeId == null) emptyList() else instance.currentNode.orEmpty().filter { it != completedNodeId }
        val destinations = if (waiting && completedNodeId != null) listOf(completedNodeId) else nextNodes
        instance.currentNode = (remaining + destinations).distinct()
    }
}
