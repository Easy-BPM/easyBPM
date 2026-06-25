package com.easy.bpm.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerMethod

@Configuration
class OpenApiConfig {
    companion object {
        private const val BEARER_SCHEME = "bearerAuth"
    }

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(Info()
                .title("Easy BPM API")
                .description("Business Process Management - REST API Documentation")
                .version("0.0.1"))
            .components(
                Components().addSecuritySchemes(
                    BEARER_SCHEME,
                    SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )
            )
            .addSecurityItem(SecurityRequirement().addList(BEARER_SCHEME))
    }

    @Bean
    fun authOperationCustomizer(): OperationCustomizer =
        OperationCustomizer { operation, handlerMethod: HandlerMethod ->
            val requestMapping = handlerMethod.beanType.getAnnotation(org.springframework.web.bind.annotation.RequestMapping::class.java)
            val basePath = requestMapping?.value?.firstOrNull().orEmpty()
            val isLoginOperation = basePath == "/auth" && operation.operationId == "login"

            if (isLoginOperation) {
                operation.security(emptyList())
            }

            operation
        }
}

