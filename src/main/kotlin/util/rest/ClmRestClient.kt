package com.andrew.smart_greenhouse.clm.util.rest

import clam_model.dto.*
import com.andrew.smart_greenhouse.clm.util.exception.ClmException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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

    @RequestPath(value = "/clam/api/v1/clients")
    fun createClamClientSendReq(rq: ClamRequest): Pair<HttpStatus, ClamResponse?> {
        try {
            val rs = restHandler.post<ClamRequest> {
                endpoint = "$prefix$currentPath"
                port = 20101
                requestBody = rq as ClamClientCreateRequest
            }
            return Pair(
                first = rs.statusCode,
                second = resolveBody(rs)
            )
        } catch (e: Exception) {
            throw Exception(e.message)
        }
    }

    @RequestPath(value = "/clam/api/v1/clients/{client-id}/activate")
    fun activateClamClientSendReq(clientId: String): Pair<HttpStatus, ClamResponse?> {
        try {
            currentPath = currentPath.replace("{client-id}", clientId)
            val rs = restHandler.post<ClamRequest> {
                endpoint = pathBuilder()
                port = 20101
            }
            return Pair(
                first = rs.statusCode,
                second = resolveBody(rs)
            )
        } catch (e: ClmException) {
            throw ClmException(e.message!!)
        }
    }

    private fun pathBuilder(): String = "$prefix$currentPath"
    private fun resolveBody(rs: HttpResponse): ClamResponse? {
        return if(rs.statusCode != HttpStatus.OK) mapper.readValue(rs.body, ClamStatusResponse::class.java) else null
    }
}