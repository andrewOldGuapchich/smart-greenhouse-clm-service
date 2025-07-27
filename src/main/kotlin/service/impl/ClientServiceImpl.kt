package com.andrew.smart_greenhouse.clm.service.impl

import com.andrew.smart_greenhouse.clm.repository.ClientRepository
import com.andrew.smart_greenhouse.clm.service.mapper.client_mapper.ClientMapper.Companion.createResponse
import com.andrew.smart_greenhouse.clm.util.exception.ClmException
import greenhouse_api.clm_controller.model.ClmControllerRequestDto
import greenhouse_api.clm_model.entity.Client
import greenhouse_api.clm_model.model.*
import greenhouse_api.clm_service.ClientService
import greenhouse_api.clm_service.DeviceService
import greenhouse_api.clm_service.RegionService
import greenhouse_api.util.*
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import util.ExternalIdGenerator.Companion.generateNextExtId

@Service
class ClientServiceImpl @Autowired constructor(
    private val clientRepository: ClientRepository,
    private val regionService: RegionService,
    private val deviceService: DeviceService,
    private val entityManager: EntityManager,
): ClientService {
    override fun createClient(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        val headers = req.headers
        try {
            val reqBody = req.body as ClmClientCreateRequest
            if (clientRepository.findClientByLogin(reqBody.login, listOf(AmndState.ACTIVE, AmndState.WAITING)) != null
                || clientRepository.findClientByEmail(reqBody.contacts.email, listOf(AmndState.ACTIVE, AmndState.WAITING) ) != null) {
                return ResponseEntity.badRequest().body(
                    ClmBadResponse().apply {
                        message = RegisterResponseMessageCode.ALREADY_EXISTS.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            }
            val client = reqBody.createClient()
            client.region = reqBody.location?.region?.let {
                regionService.findRegionByName(it)
            }
            client.externalId = resolveExtId()
            save(client)

            //send streaming message on kafka: pub.clm-outgoing
            //send http req to CLAM

            return ResponseEntity.ok().body(
                ClmOkResponse().apply{
                    message = RegisterResponseMessageCode.WAITING_ACTIVATION_CODE.toString()
                    status = HttpStatus.OK.value()
                }
            )
        } catch (exception: RuntimeException) {
            return ResponseEntity.internalServerError().body(
                ClmBadResponse().apply {
                    message = ClientActionMessageCode.INTERNAL_ERROR.toString()
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            )
        }
    }

    override fun activateClmClient(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        try {
            val reqBody = req.body as ClmClientActivationRequest
            val waitingClient = clientRepository.findWaitingClient(reqBody.login, reqBody.clientAction)
                ?: return ResponseEntity.badRequest().body(
                    ClmBadResponse().apply {
                        message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )

            val otp = 1234
            //getOtp() ?: return ResponseEntity.badRequest().body(
//            ClmBadResponse(
//                message = ActivationClientMessageCode.CODE_IS_EXPIRED.toString(),
//                status = HttpStatus.BAD_REQUEST.value()
//            )
//            )

            return if(otp != reqBody.verifyCode)
                ResponseEntity.badRequest().body(
                    ClmBadResponse().apply {
                        message = ClientActionMessageCode.CODE_MATCH_ERROR.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            else {
                val activeClient = when (reqBody.clientAction) {
                    ClientAction.CREATE -> waitingClient.copyClient(AmndState.ACTIVE, ClientAction.CREATE)
                    ClientAction.UPDATE -> waitingClient.copyClient(AmndState.ACTIVE, ClientAction.UPDATE)
                    ClientAction.DELETE -> waitingClient.copyClient(AmndState.CLOSED, ClientAction.DELETE)
                }
                save(activeClient)
                //send kafka-message on CLAM, CDM, NTM, MEAM
                ResponseEntity.ok().body(
                    activeClient.createResponse()
                )
            }
        } catch (e: Exception) {
            return ResponseEntity.internalServerError().body(
                ClmBadResponse().apply {
                    message = ClientActionMessageCode.INTERNAL_ERROR.toString()
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            )
        }
    }

    override fun deleteClmClient(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        val headers = req.headers
        try {
            val clientId = req.queryPathVariable["client-id"]
                ?: throw ClmException(message = "The client-id header is missing!")
            val client = clientRepository.findByClientId(clientId)
                ?: return ResponseEntity.badRequest().body(
                    ClmBadResponse().apply {
                        message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            val waitingClient = client.copyClient(AmndState.WAITING, ClientAction.DELETE)
            save(waitingClient)

            //send streaming message on kafka: pub.clm-outgoing
            return ResponseEntity.ok().body(
                ClmOkResponse().apply {
                    message = RegisterResponseMessageCode.WAITING_ACTIVATION_CODE.toString()
                    status = HttpStatus.OK.value()
                }
            )
        } catch (e: Exception) {
            println(e.message)
            return ResponseEntity.badRequest().body(
                ClmBadResponse().apply {
                    message = ClientActionMessageCode.INTERNAL_ERROR.toString()
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            )
        }
    }

    override fun getClmClient(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        try {
            val clientId = req.queryPathVariable["client-id"]
                ?: throw ClmException(message = "The client-id header is missing!")
            val client = clientRepository.findByClientId(clientId)
                ?: return ResponseEntity.badRequest().body(
                    ClmBadResponse().apply {
                        message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            return ResponseEntity.ok().body(
                client.createResponse()
            )
        } catch (e: Exception) {
            println(e.message)
            return ResponseEntity.badRequest().body(
                ClmBadResponse().apply {
                    message = ClientActionMessageCode.INTERNAL_ERROR.toString()
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            )
        }
    }

    override fun getClmClientWithDevices(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        val clientId = req.queryPathVariable["client-id"]
            ?: throw ClmException(message = "The client-id header is missing!")
        val client = clientRepository.findByClientId(clientId)
            ?: return ResponseEntity.badRequest().body(
                ClmBadResponse().apply {
                    message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                    status = HttpStatus.BAD_REQUEST.value()
                }
            )
        return ResponseEntity.ok(
            ClmClientDevicesBaseResponse().apply {
                clientInfo = Id().apply {
                    id = client.id
                    externalId = client.externalId
                }
                devices = deviceService.getClmDevices(client, "base")
            }
        )
    }

    override fun updateClmClient(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        val headers = req.headers
        try {
            val clientId = req.queryPathVariable["client-id"]
                ?: throw ClmException(message = "The client-id header is missing!")
            val client = clientRepository.findByClientId(clientId)
                ?: return ResponseEntity.badRequest().body(
                    ClmBadResponse().apply {
                        message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            //update email
            val reqBody = req.body as ClmClientUpdateRequest
            val updateClient = save(client.updateClient(reqBody))
            reqBody.contacts?.email?.let {
                //send kafka message
                return ResponseEntity.ok().body(
                    ClmOkResponse().apply {
                        message = RegisterResponseMessageCode.WAITING_ACTIVATION_CODE.toString()
                        status = HttpStatus.OK.value()
                    }
                )
            }

            //send streaming message on kafka: pub.clm-outgoing
            return ResponseEntity.ok().body(
                updateClient.createResponse()
            )
        } catch (e: Exception) {
            println(e.message)
            return ResponseEntity.badRequest().body(
                ClmBadResponse().apply {
                    message = ClientActionMessageCode.INTERNAL_ERROR.toString()
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            )
        }
    }

    @Transactional
    private fun save(client: Client): Client = clientRepository.save(client)

    private fun resolveExtId():String {
        val next = entityManager
            .createNativeQuery("SELECT NEXTVAL('id_generator_seq')")
            .singleResult as Long
        return next.generateNextExtId("CLM")
    }

    private fun Client.updateClient(updateReq: ClmClientUpdateRequest): Client {
        val updateClient = this.copyClient(AmndState.ACTIVE, ClientAction.UPDATE)
        updateReq.personalInfo?.let {
            it.name?.let { name -> updateClient.name = name}
            it.surname?.let { surname -> updateClient.surname = surname }
            it.patronymic?.let { patronymic -> updateClient.patronymic = patronymic}
            it.birthDate?.let { birthDate -> updateClient.birthDate = birthDate }
        }
        updateReq.contacts?.let {
            it.phone?.let { phone -> updateClient.phoneNumber = phone }
            it.email?.let { email ->
                updateClient.emailAddress = email
                updateClient.amndState = AmndState.WAITING
            }
        }
        updateReq.location?.let {
            it.region?.let { region -> {
                    updateClient.region = regionService.findRegionByName(region)
                }
            }
            it.city?.let { city -> updateClient.city = city }
        }
        return updateClient
    }

    private fun ClmClientCreateRequest.createClient(): Client {
        return Client().apply {
            login = this@createClient.login
            surname = this@createClient.personalInfo.surname
            name = this@createClient.personalInfo.name
            patronymic = this@createClient.personalInfo.patronymic
            phoneNumber = this@createClient.contacts.phone
            emailAddress = this@createClient.contacts.email
            city = this@createClient.location?.city
            birthDate = this@createClient.personalInfo.birthDate
        }
    }

    fun Client.copyClient(state: AmndState, act: ClientAction): Client {
        return Client().apply {
            amndState = state
            externalId = this@copyClient.externalId
            login = this@copyClient.login
            emailAddress = this@copyClient.emailAddress
            surname = this@copyClient.surname
            name = this@copyClient.name
            patronymic = this@copyClient.patronymic
            phoneNumber = this@copyClient.phoneNumber
            city = this@copyClient.city
            birthDate = this@copyClient.birthDate
            action = act
            version = this@copyClient.version + 1
            region = this@copyClient.region
        }
    }
}
