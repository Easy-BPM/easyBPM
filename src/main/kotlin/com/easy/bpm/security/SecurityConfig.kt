package com.easy.bpm.security

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.security.config.Customizer
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val userDetailsService: AppUserDetailsService,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    @Value("\${easybpm.security.enabled:true}") private val securityEnabled: Boolean
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val objectMapper = ObjectMapper()

        if (!securityEnabled) {
            http
                .csrf { it.disable() }
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests { it.anyRequest().permitAll() }
            return http.build()
        }

        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = "application/json"
                    response.writer.write(objectMapper.writeValueAsString(mapOf("status" to 401, "error" to "Unauthorized")))
                }
                it.accessDeniedHandler { _, response, _ ->
                    response.status = HttpServletResponse.SC_FORBIDDEN
                    response.contentType = "application/json"
                    response.writer.write(objectMapper.writeValueAsString(mapOf("status" to 403, "error" to "Forbidden")))
                }
            }
            .authorizeHttpRequests {
                it.requestMatchers("/auth/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                it.requestMatchers("/admin/**").hasAnyAuthority(AppPermissions.MANAGE_USERS, AppPermissions.MANAGE_GROUPS)
                it.requestMatchers("/code-tasks/**").hasAnyAuthority(AppPermissions.ACCESS_BPM_ADMIN, AppPermissions.ACCESS_BPM_MODELER)
                it.requestMatchers("/api/documents/**").hasAnyAuthority(AppPermissions.ACCESS_BPM_ADMIN, AppPermissions.ACCESS_PROCESS_PORTAL, AppPermissions.ACCESS_BPM_MODELER)
                it.requestMatchers("/tasks/**").hasAnyAuthority(AppPermissions.ACCESS_BPM_ADMIN, AppPermissions.ACCESS_PROCESS_PORTAL)
                it.requestMatchers("/forms/**").hasAnyAuthority(AppPermissions.ACCESS_BPM_ADMIN, AppPermissions.ACCESS_PROCESS_PORTAL, AppPermissions.ACCESS_BPM_MODELER)
                it.requestMatchers("/processes/**").hasAnyAuthority(AppPermissions.ACCESS_BPM_ADMIN, AppPermissions.ACCESS_PROCESS_PORTAL, AppPermissions.ACCESS_BPM_MODELER)
                it.anyRequest().authenticated()
            }
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun authenticationProvider(): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider()
        provider.setUserDetailsService(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder())
        return provider
    }

    @Bean
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager {
        return authenticationConfiguration.authenticationManager
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf(
            "http://localhost:*",
            "http://127.0.0.1:*"
        )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With")
        configuration.exposedHeaders = listOf("Authorization")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}


