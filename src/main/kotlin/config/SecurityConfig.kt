package com.andrew.smart_greenhouse.clm.config

import com.andrew.smart_greenhouse.clm.filter.AuthorizationFilter
import greenhouse_api.clm_service.ClientService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import util.security.endpoint.buildRules
import util.security.keys.KeysUtil

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    @Lazy
    private val jwtAuthorizationFilter: AuthorizationFilter
) {
    private val logger = LoggerFactory.getLogger(SecurityConfig::class.java)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val rules = buildRules(arrayOf(ClientService::class))
        rules.forEach {
            logger.info("Accept rule: endpoint {}, type {}, roles {}", it.path, it.type, it.roles)
        }
        return http
            .csrf { it.disable() }
            .sessionManagement { session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .authorizeHttpRequests { requests ->
                rules.forEach { rule ->
                    if (rule.roles.contains("ALL")) {
                        logger.info("PUBLIC: {} {}", rule.httpMethod, rule.path)
                        requests.requestMatchers(
                            rule.httpMethod.toSpringMethod(),
                            rule.path
                        ).permitAll()
                    } else {
                        val authorities = rule.roles.map { "ROLE_$it" }
                        logger.info(
                            "PROTECTED: {} {} authorities: {}",
                            rule.httpMethod, rule.path, authorities
                        )
                        requests.requestMatchers(
                            rule.httpMethod.toSpringMethod(),
                            rule.path
                        ).hasAnyAuthority(*authorities.toTypedArray())
                    }
                }
            }
            .addFilterBefore(jwtAuthorizationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    fun authenticationManager(
        config: AuthenticationConfiguration
    ): AuthenticationManager {
        return config.authenticationManager
    }

    @Bean
    fun keyUtil(): KeysUtil = KeysUtil()

    private fun util.annotation.HttpMethod.toSpringMethod(): HttpMethod {
        return when (this) {
            util.annotation.HttpMethod.GET -> HttpMethod.GET
            util.annotation.HttpMethod.POST -> HttpMethod.POST
            util.annotation.HttpMethod.PUT -> HttpMethod.PUT
            util.annotation.HttpMethod.DELETE -> HttpMethod.DELETE
            util.annotation.HttpMethod.PATCH -> HttpMethod.PATCH
            util.annotation.HttpMethod.HEAD -> HttpMethod.HEAD
        }
    }
}