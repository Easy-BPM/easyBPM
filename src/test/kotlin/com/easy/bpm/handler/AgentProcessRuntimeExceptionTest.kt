package com.easy.bpm.handler

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AgentProcessRuntimeExceptionTest {
    @Test fun `untrusted error code cannot enter a diagnostic or cause chain`() {
        val error=AgentProcessRuntimeException("QA_SECRET_MARKER")
        assertEquals("PROVIDER_ERROR",error.errorCode)
        assertFalse(error.message.orEmpty().contains("QA_SECRET_MARKER"))
        assertNull(error.cause)
    }
    @Test fun `only controlled runtime messages expose an API error code`() {
        assertEquals("TIMEOUT",AgentProcessRuntimeException.codeFromMessage(AgentProcessRuntimeException("TIMEOUT").message))
        assertNull(AgentProcessRuntimeException.codeFromMessage("Transaction rolled back"))
        assertNull(AgentProcessRuntimeException.codeFromMessage("Agent runtime failure [QA_SECRET_MARKER]."))
    }
}
