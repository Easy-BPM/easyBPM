package com.easy.bpm.decision

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query

interface DecisionDefinitionRepository : JpaRepository<DecisionDefinition, Long> {
    fun findByKeyOrderByVersionDesc(key: String): List<DecisionDefinition>
    fun findByKeyAndVersion(key: String, version: Int): DecisionDefinition?
    fun findTopByKeyOrderByVersionDesc(key: String): DecisionDefinition?
    @Query("select d from DecisionDefinition d where d.version = (select max(d2.version) from DecisionDefinition d2 where d2.key = d.key) order by d.key")
    fun latest(): List<DecisionDefinition>
}
interface DecisionTaskPinRepository : JpaRepository<DecisionTaskPin, Long> {
    fun findByInstanceIdAndNodeId(instanceId: Long, nodeId: String): DecisionTaskPin?
}
interface DecisionTestCaseRepository : JpaRepository<DecisionTestCase, Long> {
    fun findByDecisionKeyOrderById(key: String): List<DecisionTestCase>
}
interface DecisionExecutionRepository : JpaRepository<DecisionExecution, Long>, JpaSpecificationExecutor<DecisionExecution>
