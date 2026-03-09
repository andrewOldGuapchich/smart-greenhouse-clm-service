package com.andrew.smart_greenhouse.clm.controller

import com.andrew.smart_greenhouse.clm.util.createBadRequest
import com.andrew.smart_greenhouse.clm.util.createInternalServer
import com.andrew.smart_greenhouse.clm.util.createNotFound
import com.andrew.smart_greenhouse.clm.util.createOk
import greenhouse_api.clm_controller.ClmControllerRequestDto
import greenhouse_api.clm_model.dto.ClmDto
import greenhouse_api.clm_model.dto.rq.ClmClientActivationRequest
import greenhouse_api.clm_model.dto.rq.ClmClientCreateRequest
import greenhouse_api.clm_model.dto.rq.ClmClientUpdateRequest
import greenhouse_api.clm_model.dto.rs.ClmResponse
import greenhouse_api.clm_model.dto.rs.ClmStatusResponse
import greenhouse_api.clm_service.ClientService
import greenhouse_api.util.exception.ClmAlreadyExistObject
import greenhouse_api.util.exception.ClmException
import greenhouse_api.util.exception.ClmIllegalArgumentException
import greenhouse_api.util.exception.ClmNotExistObjectException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
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
    suspend fun createClmClient(
        @RequestHeader
        httpHeaders: MutableMap<String, String>,
        @RequestBody
        req: ClmClientCreateRequest
    ) : ResponseEntity<ClmResponse> {
        return try {
            createOk(
                clientService.createClient(req = ClmControllerRequestDto(headers = httpHeaders, body = req))
            )
        } catch (e: ClmAlreadyExistObject) {
            createBadRequest(e.message!!)
        } catch (e: ClmException) {
            createInternalServer(e.message!!)
        }
    }

    /*
    catch (e: CdmAlreadyExistObject) {
            createBadRequest(e.message!!)
        } catch (e: CdmNotExistObjectException) {
            createBadRequest(e.message!!)
        } catch (e: Exception) {
            createInternalServer(e.message!!)
        }
     */
    //ClmAlreadyExistObject
    //ClmException

    @PostMapping("/v1/clients/{client-id}/activate")
    fun activateClmClient(
        @RequestHeader
        httpHeaders: MutableMap<String, String>,
        @PathVariable("client-id")
        clientId: String,
        @RequestBody
        req: ClmClientActivationRequest
    ) : ResponseEntity<ClmResponse> {
        return try {
            createOk(
                clientService.activateClmClient(req = ClmControllerRequestDto(headers = httpHeaders, body = req,
                    queryPathVariable = mutableMapOf("client-id" to clientId)
                ))
            )
        } catch (e: ClmIllegalArgumentException) {
            createBadRequest(e.message!!)
        } catch (e: ClmNotExistObjectException) {
            createNotFound(e.message!!)
        } catch (e: ClmAlreadyExistObject) {
            createBadRequest(e.message!!)
        } catch (e: ClmException) {
            createInternalServer(e.message!!)
        }
    }

    @PatchMapping("/v1/clients/{client-id}")
    fun updateClmClient(
        @PathVariable("client-id")
        clientId: String,
        @RequestBody
        req: ClmClientUpdateRequest
    ) : ResponseEntity<ClmResponse> {
        return try {
            createOk(
                clientService.updateClmClient(
                    req = ClmControllerRequestDto( body = req,
                        queryPathVariable = mutableMapOf("client-id" to clientId)
                    )
                )
            )
        } catch (e: ClmIllegalArgumentException) {
            createBadRequest(e.message!!)
        } catch (e: ClmAlreadyExistObject) {
            createBadRequest(e.message!!)
        } catch (e: ClmNotExistObjectException) {
            createNotFound(e.message!!)
        } catch (e: ClmException) {
            createInternalServer(e.message!!)
        }
    }
//
//    @PatchMapping("/v1/clients/{client-id}/status")
//    fun setClientStatus(
//        @RequestHeader
//        httpHeaders: Map<String, String>,
//        @PathVariable("client-id")
//        clientId: String,
//        @RequestBody
//        req: ClmClientSetStatus
//    ): ResponseEntity<ClmResponse> {
//        return clientService.setClientStatus(
//            req = ClmControllerRequestDto(
//                headers = httpHeaders,
//                queryPathVariable = mapOf("client-id" to clientId),
//                body = req
//            )
//        )
//    }
//
//    @PostMapping("/v1/clients/{client-id}/reset-otp")
//    suspend fun resetClientOtp(
//        @RequestHeader
//        httpHeaders: Map<String, String>,
//        @PathVariable("client-id")
//        clientId: String
//    ) : ResponseEntity<ClmResponse> {
//        return clientService.resetClientActivationCode(
//            req = ClmControllerRequestDto(headers = httpHeaders, queryPathVariable = mapOf("client-id" to clientId))
//        )
//    }
//
    @DeleteMapping("/v1/clients/{client-id}")
    fun deleteClmClient(
        @PathVariable("client-id")
        clientId: String
    ) : ResponseEntity<ClmResponse> {
        return try {
            createOk(
                clientService.deleteClmClient(
                    req = ClmControllerRequestDto(
                        queryPathVariable = mutableMapOf("client-id" to clientId)
                    )
                )
            )
        } catch (e: ClmIllegalArgumentException) {
            createBadRequest(e.message!!)
        } catch (e: ClmNotExistObjectException) {
            createNotFound(e.message!!)
        } catch (e: ClmException) {
            createInternalServer(e.message!!)
        }
    }

    @GetMapping("/v1/clients/{client-id}")
    fun getClmClient(
        @PathVariable("client-id")
        clientId: String
    ) : ResponseEntity<ClmResponse> {
        return try {
            createOk(
                clientService.getClmClient(
                    req = ClmControllerRequestDto(
                        queryPathVariable = mutableMapOf("client-id" to clientId)
                    )
                )
            )
        } catch (e: ClmIllegalArgumentException) {
            createBadRequest(e.message!!)
        } catch (e: ClmNotExistObjectException) {
            createNotFound(e.message!!)
        } catch (e: ClmException) {
            createInternalServer(e.message!!)
        }
    }
}