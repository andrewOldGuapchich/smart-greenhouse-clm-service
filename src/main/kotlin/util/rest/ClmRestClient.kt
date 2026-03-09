package com.andrew.smart_greenhouse.clm.util.rest

import clam_model.dto.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import greenhouse_api.clam_model.dto.rq.ClamClientCreateRequest
import greenhouse_api.clam_model.dto.rq.ClamCredentialCreateRequest
import greenhouse_api.clam_model.dto.rs.ClamClientResponse
import greenhouse_api.clam_model.dto.rs.ClamCredentialResponse
import greenhouse_api.clam_model.dto.rs.ClamResponse
import greenhouse_api.clam_model.dto.rs.ClamStatusResponse
import greenhouse_api.util.exception.ClmException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import util.http.HttpResponse
import util.http.PathAware
import util.http.RequestPath
import util.http.RestHandler

@Component
class ClmRestClient @Autowired constructor(
    private val restHandler: RestHandler,
    @Value("\${rest.base-prefix}")
    private val prefix: String
) : PathAware {
    override var currentPath: String = ""
    val mapper: ObjectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }
    private var logger = LoggerFactory.getLogger(ClmRestClient::class.java)

    @RequestPath("/clam/api/v1/clients")
    fun clamCredCreateRequest(clamRequest: ClamClientCreateRequest): Pair<HttpStatus, ClamResponse> {
        try {
            val rs = restHandler.post<Any?> {
                endpoint = pathBuilder()
                requestBody = clamRequest
                port = 20101
            }
            if (rs.body == null || rs.body.toString().isBlank()) {
                throw IllegalStateException("Empty response from CLM service")
            }
            return Pair(
                first = rs.statusCode,
                second = if (rs.statusCode.is2xxSuccessful) {
                    try {
                        mapper.readValue(rs.body, ClamClientResponse::class.java)
                    } catch (e: Exception) {
                        logger.error("Failed to parse successful response: {}", e.message)
                        mapper.readValue(rs.body, ClamStatusResponse::class.java)
                    }
                } else {
                    mapper.readValue(rs.body, ClamStatusResponse::class.java)
                }
            )
        } catch (e: Exception) {
            logger.error("Unexpected error in CLAM client", e)
            throw ClmException("CLAM service error: ${e.message}")
        }
    }

    private fun pathBuilder(): String = "$prefix$currentPath"
}