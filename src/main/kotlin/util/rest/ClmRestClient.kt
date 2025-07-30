package com.andrew.smart_greenhouse.clm.util.rest

import clam_model.dto.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import util.http.HttpResponse
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

    @PathHandler(path = "/clam/api/v1/clients")
    fun createClientSendReq(rq: ClamRequest): Pair<HttpStatus, ClamResponse?> {
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

    private fun resolveBody(rs: HttpResponse): ClamResponse? {
        return if(rs.statusCode != HttpStatus.OK) mapper.readValue(rs.body, ClamStatusResponse::class.java) else null
    }
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PathHandler(val path: String)

@Aspect
@Component
class PathHandlerAspect {
    @Around("@annotation(pathHandler)")
    fun handlePath(joinPoint: ProceedingJoinPoint, pathHandler: PathHandler): Any? {
        val path = pathHandler.path
        val target =
            joinPoint.target as? PathAware ?: return joinPoint.proceed()

        target.currentPath = path
        return joinPoint.proceed()
    }
}

interface PathAware {
    var currentPath: String
}