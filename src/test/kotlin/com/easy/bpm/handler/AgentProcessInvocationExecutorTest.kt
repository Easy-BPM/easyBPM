package com.easy.bpm.handler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class AgentProcessInvocationExecutorTest {
    @Test
    fun `interrupts provider invocation when configured timeout expires`() {
        val executor = AgentProcessInvocationExecutor()
        try {
            assertThrows<AgentProcessTimeoutException> {
                executor.execute(Duration.ofMillis(25)) {
                    Thread.sleep(5_000)
                    "too late"
                }
            }
        } finally {
            executor.shutdown()
        }
    }
}
