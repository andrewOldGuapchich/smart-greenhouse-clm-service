package com.andrew.smart_greenhouse.clm.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import util.http.PathResolveAspect
import util.http.RestHandler

@Configuration
class WebConfig : WebMvcConfigurer {
    @Bean
    fun webClient(): WebClient = WebClient.builder().build()

    @Bean
    fun restHandler(
        webClient: WebClient,
        @Value("\${rest.base-url}") baseHost: String
    ): RestHandler = RestHandler(webClient, baseHost)

    @Bean
    fun pathResolve(): PathResolveAspect = PathResolveAspect()
}