package com.easy.bpm.integration

import com.easy.bpm.enum.ProcessStatus
import com.easy.bpm.enum.TaskStatus
import com.easy.bpm.model.process.ProcessDefinition
import com.easy.bpm.model.process.ProcessInstance
import com.easy.bpm.model.security.AppUser
import com.easy.bpm.model.security.Permission
import com.easy.bpm.model.security.UserGroup
import com.easy.bpm.model.task.Task
import com.easy.bpm.messaging.RabbitPublisher
import com.easy.bpm.repository.process.ProcessDefinitionRepository
import com.easy.bpm.repository.process.ProcessInstanceRepository
import com.easy.bpm.repository.security.AppUserRepository
import com.easy.bpm.repository.security.PermissionRepository
import com.easy.bpm.repository.security.UserGroupRepository
import com.easy.bpm.repository.task.TaskRepository
import com.easy.bpm.repository.variable.TaskVariableRepository
import com.easy.bpm.security.AppUserDetailsService
import com.easy.bpm.service.TaskService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.access.AccessDeniedException
import org.springframework.transaction.annotation.Transactional

@Transactional
class RbacTaskClaimIntegrationTest : IntegrationTestBase() {

    @MockitoBean
    private lateinit var rabbitPublisher: RabbitPublisher

    @Autowired
    private lateinit var taskService: TaskService

    @Autowired
    private lateinit var taskRepository: TaskRepository

    @Autowired
    private lateinit var taskVariableRepository: TaskVariableRepository

    @Autowired
    private lateinit var processDefinitionRepository: ProcessDefinitionRepository

    @Autowired
    private lateinit var processInstanceRepository: ProcessInstanceRepository

    @Autowired
    private lateinit var appUserRepository: AppUserRepository

    @Autowired
    private lateinit var userGroupRepository: UserGroupRepository

    @Autowired
    private lateinit var permissionRepository: PermissionRepository

    @Autowired
    private lateinit var userDetailsService: AppUserDetailsService

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Test
    fun `permissions are inherited from group memberships`() {
        val permission = permissionRepository.findByCode("ACCESS_PROCESS_PORTAL")
            ?: permissionRepository.save(Permission(code = "ACCESS_PROCESS_PORTAL", name = "Portal access"))

        val group = userGroupRepository.save(
            UserGroup(code = "FINANCE", name = "Finance", permissions = mutableSetOf(permission))
        )

        appUserRepository.save(
            AppUser(
                username = "finance-user",
                passwordHash = passwordEncoder.encode("secret123"),
                enabled = true,
                groups = mutableSetOf(group),
                createdBy = "test",
                updatedBy = "test"
            )
        )

        val loaded = userDetailsService.loadUserByUsername("finance-user")
        val authorities = loaded.authorities.map { it.authority }.toSet()
        assertEquals(setOf("ACCESS_PROCESS_PORTAL"), authorities)
    }

    @Test
    fun `user cannot claim task outside assigned candidate groups`() {
        val task = createTask(candidateGroups = mutableSetOf("FINANCE"))

        assertThrows(AccessDeniedException::class.java) {
            taskService.claimTask(task.id, "alice", setOf("HR"))
        }
    }

    @Test
    fun `user can claim task when in candidate group`() {
        val task = createTask(candidateGroups = mutableSetOf("FINANCE"))

        val claimed = taskService.claimTask(task.id, "alice", setOf("FINANCE"))

        assertEquals("alice", claimed.assignee)
        assertEquals("alice", taskRepository.findById(task.id).orElseThrow().assignee)
    }

    @Test
    fun `user cannot claim task already claimed by another user`() {
        val task = createTask(candidateGroups = mutableSetOf("FINANCE"))
        taskService.claimTask(task.id, "alice", setOf("FINANCE"))

        assertThrows(AccessDeniedException::class.java) {
            taskService.claimTask(task.id, "bob", setOf("FINANCE"))
        }
    }

    @Test
    fun `user can unclaim their task`() {
        val task = createTask(candidateGroups = mutableSetOf("FINANCE"))
        taskService.claimTask(task.id, "alice", setOf("FINANCE"))

        val unclaimed = taskService.unclaimTask(task.id, "alice", setOf("FINANCE"))

        assertEquals(null, unclaimed.assignee)
        assertEquals(null, taskRepository.findById(task.id).orElseThrow().assignee)
    }

    @Test
    fun `saving draft persists variables while keeping assignee`() {
        val task = createTask(candidateGroups = mutableSetOf("FINANCE"))
        taskService.claimTask(task.id, "alice", setOf("FINANCE"))

        val draft = taskService.saveTaskDraft(
            task.id,
            "alice",
            setOf("FINANCE"),
            mapOf("approvalComment" to "Needs more evidence", "approved" to false)
        )

        val variables = taskVariableRepository.findByTaskId(task.id).associate { it.name to it.value }
        assertEquals("alice", draft.assignee)
        assertEquals("Needs more evidence", variables["approvalComment"]?.asText())
        assertEquals(false, variables["approved"]?.asBoolean())
        assertEquals("alice", taskRepository.findById(task.id).orElseThrow().assignee)
    }

    @Test
    fun `admin reassignment updates existing assignee field`() {
        val task = createTask(candidateGroups = mutableSetOf("FINANCE"))
        taskService.claimTask(task.id, "alice", setOf("FINANCE"))

        val reassigned = taskService.reassignTask(task.id, "bob")

        assertEquals("bob", reassigned.assignee)
        assertEquals("bob", taskRepository.findById(task.id).orElseThrow().assignee)
    }

    private fun createTask(candidateGroups: MutableSet<String> = mutableSetOf(), candidateUsers: MutableSet<String> = mutableSetOf()): Task {
        val definition = processDefinitionRepository.save(
            ProcessDefinition(
                key = "rbac-test-${System.nanoTime()}",
                processName = "RBAC Test",
                description = "RBAC test",
                version = 1,
                definitionJson = "{\"nodes\":[],\"flows\":[]}"
            )
        )

        val instance = processInstanceRepository.save(
            ProcessInstance(
                processDefinition = definition,
                status = ProcessStatus.ACTIVE,
                currentNode = listOf("task-node"),
                nodeHistory = listOf("start", "task-node")
            )
        )

        return taskRepository.save(
            Task(
                processInstanceId = instance.id,
                title = "Group Review",
                nodeId = "task-node",
                status = TaskStatus.PENDING,
                candidateUsers = candidateUsers,
                candidateGroups = candidateGroups
            )
        )
    }
}




