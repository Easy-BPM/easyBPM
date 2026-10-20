package com.easy.bpm.decision

import jakarta.persistence.*
import java.time.Instant

data class DecisionInput(val id: String, val label: String = "", val expression: String, val type: String)
data class DecisionOutput(val id: String, val name: String, val type: String)
data class DecisionRule(val id: String, val description: String = "", val inputEntries: List<String>, val outputEntries: List<String>)
data class DecisionTable(val key: String, val name: String, val hitPolicy: String = "UNIQUE", val inputs: List<DecisionInput>, val outputs: List<DecisionOutput>, val rules: List<DecisionRule>)
data class DecisionIssue(val severity: String, val code: String, val message: String, val ruleId: String? = null, val columnId: String? = null)
data class DecisionValidation(val valid: Boolean, val issues: List<DecisionIssue>, val table: DecisionTable? = null)
data class ConditionExplanation(val inputId: String, val expression: String, val matched: Boolean)
data class RuleExplanation(val ruleId: String, val matched: Boolean, val conditions: List<ConditionExplanation>)
data class EvaluationResult(val decisionKey: String, val version: Int? = null, val hitPolicy: String, val matchedRuleIds: List<String>, val outputs: Any?, val explanations: List<RuleExplanation>, val durationMs: Long)
class DecisionException(val code: String, message: String, val status: Int = 400) : RuntimeException(message)

@Entity
@Table(name = "decision_definition", uniqueConstraints = [UniqueConstraint(columnNames = ["decision_key", "version"])])
data class DecisionDefinition(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "decision_key", nullable = false) val key: String = "",
    @Column(nullable = false) val name: String = "",
    @Column(nullable = false) val version: Int = 1,
    @Column(nullable = false) val state: String = "PUBLISHED",
    @Column(name = "dmn_xml", columnDefinition = "TEXT", nullable = false) val dmnXml: String = "",
    @Column(nullable = false) val createdAt: Instant = Instant.now()
)

@Entity @Table(name = "decision_task_pin", uniqueConstraints = [UniqueConstraint(columnNames = ["instance_id", "node_id"])])
data class DecisionTaskPin(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "instance_id", nullable = false) val instanceId: Long = 0,
    @Column(name = "node_id", nullable = false) val nodeId: String = "",
    @Column(name = "definition_id", nullable = false) val definitionId: Long = 0
)

@Entity @Table(name = "decision_test_case")
data class DecisionTestCase(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "decision_key", nullable = false) val decisionKey: String = "",
    @Column(nullable = false) val name: String = "",
    @Column(columnDefinition = "TEXT", nullable = false) val inputsJson: String = "{}",
    @Column(columnDefinition = "TEXT", nullable = false) val expectedOutputsJson: String = "null"
)

@Entity @Table(name = "decision_execution")
data class DecisionExecution(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    val decisionKey: String = "",
    val decisionVersion: Int? = null,
    val processInstanceId: Long? = null,
    val nodeId: String? = null,
    val status: String = "COMPLETED",
    val durationMs: Long = 0,
    @Column(columnDefinition = "TEXT") val matchedRuleIdsJson: String = "[]",
    @Column(columnDefinition = "TEXT") val inputsJson: String = "{}",
    @Column(columnDefinition = "TEXT") val outputsJson: String = "null",
    @Column(columnDefinition = "TEXT") val explanationsJson: String = "[]",
    val errorCode: String? = null,
    val executedAt: Instant = Instant.now()
)
