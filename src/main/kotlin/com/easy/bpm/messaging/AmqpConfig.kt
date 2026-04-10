package com.easy.bpm.messaging

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.TopicExchange
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AmqpConfig {
    companion object {
        const val EXCHANGE = "bpm.exchange"
        const val SERVICE_TASK_REQUESTS_QUEUE = "service-task-requests"
        const val SERVICE_TASK_COMPLETIONS_QUEUE = "service-task-completions"
        const val REQUEST_ROUTING_KEY = "service.task.request"
        const val COMPLETION_ROUTING_KEY = "service.task.completed"
        const val TASK_CREATED_QUEUE = "task-created"
        const val TASK_COMPLETED_QUEUE = "task-completed"
        const val TASK_CREATED_ROUTING_KEY = "task.created"
        const val TASK_COMPLETED_ROUTING_KEY = "task.completed"
        const val MESSAGE_EVENTS_QUEUE = "message-events"
        const val MESSAGE_EVENTS_ROUTING_KEY = "message.event.received"
    }

    @Bean
    fun serviceTaskRequestsQueue() = Queue(SERVICE_TASK_REQUESTS_QUEUE, true)

    @Bean
    fun serviceTaskCompletionsQueue() = Queue(SERVICE_TASK_COMPLETIONS_QUEUE, true)

    @Bean
    fun taskCreatedQueue() = Queue(TASK_CREATED_QUEUE, true)

    @Bean
    fun taskCompletedQueue() = Queue(TASK_COMPLETED_QUEUE, true)

    @Bean
    fun messageEventsQueue() = Queue(MESSAGE_EVENTS_QUEUE, true)

    @Bean
    fun exchange() = TopicExchange(EXCHANGE)

    @Bean
    fun bindingRequests(@Qualifier("serviceTaskRequestsQueue") queue: Queue, exchange: TopicExchange): Binding =
        BindingBuilder.bind(queue).to(exchange).with(REQUEST_ROUTING_KEY)

    @Bean
    fun bindingCompletions(@Qualifier("serviceTaskCompletionsQueue") queue: Queue, exchange: TopicExchange): Binding =
        BindingBuilder.bind(queue).to(exchange).with(COMPLETION_ROUTING_KEY)

    @Bean
    fun bindingTaskCreated(@Qualifier("taskCreatedQueue") queue: Queue, exchange: TopicExchange): Binding =
        BindingBuilder.bind(queue).to(exchange).with(TASK_CREATED_ROUTING_KEY)

    @Bean
    fun bindingTaskCompleted(@Qualifier("taskCompletedQueue") queue: Queue, exchange: TopicExchange): Binding =
        BindingBuilder.bind(queue).to(exchange).with(TASK_COMPLETED_ROUTING_KEY)

    @Bean
    fun bindingMessageEvents(@Qualifier("messageEventsQueue") queue: Queue, exchange: TopicExchange): Binding =
        BindingBuilder.bind(queue).to(exchange).with(MESSAGE_EVENTS_ROUTING_KEY)

    @Bean
    fun messageConverter() = Jackson2JsonMessageConverter()
}
