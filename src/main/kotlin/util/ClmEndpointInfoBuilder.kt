package com.andrew.smart_greenhouse.clm.util

import greenhouse_api.clm_service.ClientService
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import streamig_api.clam.RestApiInfo
import streamig_api.clam.ServiceEndpoint
import util.annotation.RestApi
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.findAnnotation

@Component
class ClmEndpointInfoBuilder {
    private val logger = LoggerFactory.getLogger(ClmEndpointInfoBuilder::class.java)
    init {
        logger.info("ClmEndpointInfoBuilder created")
    }

    @PostConstruct
    fun initClm() {
        val serviceInfo = build()
        logger.info("Found ${serviceInfo.apiInfo.size} endpoints:")
        serviceInfo.apiInfo.forEach { logger.info(" → ${it.httpMethod} ${it.path}") }
        logger.info("Endpoints: ${serviceInfo.apiInfo}")
    }

    fun build(): ServiceEndpoint {
        val infoList: MutableList<RestApiInfo> = mutableListOf()
        val clmServiceClass = ClientService::class

        clmServiceClass.declaredFunctions.forEach { function ->
            val restApi = function.findAnnotation<RestApi>()

            if(restApi != null) {
                infoList.add(
                    RestApiInfo(
                        type = restApi.type,
                        path = restApi.path,
                        httpMethod = restApi.method,
                        roles = restApi.roles.asList()
                    )
                )
            }
        }

        return ServiceEndpoint(
            serviceName = "greenhouse-clm",
            apiInfo = infoList
        )
    }
}