package com.andrew.smart_greenhouse.clm.controller

import greenhouse_api.clm_controller.ClmControllerRequestDto
import greenhouse_api.clm_model.model.*
import greenhouse_api.clm_service.ClientService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/smart-greenhouse/internal/clm/api")
class ClmControllerImpl @Autowired constructor(
    val clientService: ClientService
) {
    @PostMapping("/v1/clients")
    fun createClmClient(
        @RequestHeader
        httpHeaders: Map<String, String>,
        @RequestBody
        req: ClmClientCreateRequest
    ) : ResponseEntity<ClmResponse> {
        return clientService.createClient(
            req = ClmControllerRequestDto(headers = httpHeaders, body = req)
        )
    }

    @PostMapping("/v1/clients/{client-id}/activate")
    fun activateClmClient(
        @RequestHeader
        httpHeaders: Map<String, String>,
        @PathVariable("client-id")
        clientId: String,
        @RequestBody
        req: ClmClientActivationRequest
    ) : ResponseEntity<ClmResponse> {
        return clientService.activateClmClient(
            req = ClmControllerRequestDto(headers = httpHeaders, queryPathVariable = mapOf("client-id" to clientId), body = req)
        )
    }

    @PatchMapping("/v1/clients/{client-id}")
    fun updateClmClient(
        @RequestHeader
        httpHeaders: Map<String, String>,
        @PathVariable("client-id")
        clientId: String,
        @RequestBody
        req: ClmClientUpdateRequest
    ) : ResponseEntity<ClmResponse> {
        return clientService.updateClmClient(
            req = ClmControllerRequestDto(headers = httpHeaders, queryPathVariable = mapOf("client-id" to clientId), body = req)
        )
    }

    @PostMapping("/v1/clients/{client-id}/status")
    fun setClientStatus(
        @RequestHeader
        httpHeaders: Map<String, String>,
        @PathVariable("client-id")
        clientId: String,
        @RequestBody
        req: ClmClientSetStatus
    ): ResponseEntity<ClmResponse> {
        return clientService.setClientStatus(
            req = ClmControllerRequestDto(
                headers = httpHeaders,
                queryPathVariable = mapOf("client-id" to clientId),
                body = req
            )
        )
    }

    @DeleteMapping("/v1/clients/{client-id}")
    fun deleteClmClient(
        @RequestHeader
        httpHeaders: Map<String, String>,
        @PathVariable("client-id")
        clientId: String
    ) : ResponseEntity<ClmResponse> {
        return clientService.deleteClmClient(
            req = ClmControllerRequestDto(
                headers = httpHeaders, queryPathVariable = mapOf("client-id" to clientId)
            )
        )
    }

    @GetMapping("/v1/clients/{client-id}")
    fun getClmClient(
        @RequestHeader
        httpHeaders: Map<String, String>,
        @PathVariable("client-id")
        clientId: String
    ) : ResponseEntity<ClmResponse> {
        return clientService.getClmClient(
            req = ClmControllerRequestDto(headers = httpHeaders, queryPathVariable = mapOf("client-id" to clientId)
            )
        )
    }

    @GetMapping("/v1/clients/{client-id}/devices")
    fun getClmClientWithDevices(
        @RequestHeader
        httpHeaders: Map<String, String>,
        @PathVariable("client-id")
        clientId: String
    ) : ResponseEntity<ClmResponse> {
        return clientService.getClmClientWithDevices(
            req = ClmControllerRequestDto(headers = httpHeaders, queryPathVariable = mapOf("client-id" to clientId))
        )
    }
}