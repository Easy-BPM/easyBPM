package com.easy.bpm.handler

import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class AgentProcessInvocationExecutor {
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    fun <T> execute(timeout: Duration?, action: () -> T): T {
        if (timeout == null) return action()

        val future = executor.submit<T> { action() }
        return try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            throw AgentProcessTimeoutException(timeout)
        } catch (exception: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            throw IllegalStateException("Agent process execution was interrupted", exception)
        } catch (exception: ExecutionException) {
            val cause = exception.cause
            if (cause is RuntimeException) throw cause
            throw IllegalStateException("Agent process execution failed", cause)
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }
}

class AgentProcessTimeoutException(timeout: Duration) :
    IllegalStateException("Agent process execution exceeded the configured timeout of ${timeout.toSeconds()} seconds")
