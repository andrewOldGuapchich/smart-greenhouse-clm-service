package com.andrew.smart_greenhouse.clm.service.impl

import clam_model.dto.*
import clm.AmndStateStreaming
import clm.ClientInfo
import clm.CreateClientStreaming
import com.andrew.smart_greenhouse.clm.repository.ClientRepository
import com.andrew.smart_greenhouse.clm.service.mapper.client_mapper.ClientMapper.Companion.createResponse
import com.andrew.smart_greenhouse.clm.util.exception.ClmException
import com.andrew.smart_greenhouse.clm.util.rest.ClmRestClient
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import greenhouse_api.clm_controller.*
import greenhouse_api.clm_model.entity.Client
import greenhouse_api.clm_model.model.*
import greenhouse_api.clm_service.*
import greenhouse_api.util.*
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import util.kafka.producer.KafkaProducer

@Service
class ClientServiceImpl @Autowired constructor(
    private val clientRepository: ClientRepository,
    private val regionService: RegionService,
    private val deviceService: DeviceService,
    private val generatorService: GeneratorService,
    private val otpService: OtpService,
    private val restClient: ClmRestClient,
    private val kafkaProducer: KafkaProducer
): ClientService {
    private val objectMapper = jacksonObjectMapper().apply {
        registerModules(JavaTimeModule())
    }

    override suspend fun createClient(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        val headers = req.headers
        try {
            val reqBody = req.body as ClmClientCreateRequest
            if (clientRepository.findClientByLogin(reqBody.login, listOf(AmndState.ACTIVE, AmndState.WAITING)) != null
                || clientRepository.findClientByEmail(reqBody.contacts.email, listOf(AmndState.ACTIVE, AmndState.WAITING) ) != null) {
                return ResponseEntity.badRequest().body(
                    ClmStatusResponse().apply {
                        message = RegisterResponseMessageCode.ALREADY_EXISTS.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            }
            val client = reqBody.createClient()
            client.region = reqBody.location?.region?.let {
                regionService.findRegionByName(it)
            }
            client.id = calculateNewClientId()

            val savedClient = save(client)

            val rs = restClient.createClamClientSendReq(ClamClientCreateRequest().apply {
                personalInfo = PersonalInfoDTO().apply {
                    login = client.login
                    email = client.emailAddress
                    //edit
                    id = client.id
                }
                cred = CredentialDTO().apply {
                    password = reqBody.credential.password
                    passwordConfirm = reqBody.credential.passwordConfirm
                }
            })

            if(rs.first != HttpStatus.OK) {
                savedClient.amndState = AmndState.INACTIVE
                save(savedClient)
                return ResponseEntity.badRequest().body(
                    ClmBadResponse().apply {
                        message = (rs.second as ClamStatusResponse).message
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            }

            val generateOtp = otpService.generateOtp()
            otpService.saveOtp(savedClient.id, generateOtp)

            val payload = objectMapper.writeValueAsString(
                CreateClientStreaming(
                    clientId = savedClient.id,
                    amndState = AmndStateStreaming.WAITING,
                    clientInfo = ClientInfo(
                        login = savedClient.login,
                        email = savedClient.emailAddress
                    ),
                    additionalInfo = mapOf("otp" to generateOtp),
                    clientVersion = 1
                )
            )

            kafkaProducer.send(
                "clm-outgoing",
                payload.hashCode().toString(),
                payload,
                mapOf("ContentType" to "client-action-request")
            )

            return ResponseEntity.ok().body(
                ClmClientCreateResponse().apply {
                    id = savedClient.id
                    status = AmndState.WAITING
                    login = savedClient.login
                    personalInfo = PersonalInfo.PersonalInfoCreate().apply {
                        surname = savedClient.surname
                        name = savedClient.name
                        patronymic = savedClient.patronymic
                        birthDate = savedClient.birthDate
                    }
                    contacts = Contacts.ContactsCreate().apply {
                        phone = savedClient.phoneNumber
                        email = savedClient.emailAddress
                    }
                    location = Location().apply {
                        region = savedClient.region?.name
                        city = savedClient.city
                    }
                    generatorService.updateCurrentId()
                }
            )
        } catch (exception: ClmException) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = exception.message!!
                    status = HttpStatus.BAD_REQUEST.value()
                }
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = "${ClientActionMessageCode.INTERNAL_ERROR}\n${exception.message}"
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            )
        }
    }

    override fun activateClmClient(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        try {
            val reqBody = req.body as ClmClientActivationRequest
            val clientId = req.queryPathVariable["client-id"]!!
            val waitingClient = clientRepository.findClientById(
                id = clientId,
                action = ClientAction.CREATE,
                states = listOf(AmndState.WAITING)
            )
                ?: return ResponseEntity.badRequest().body(
                    ClmStatusResponse().apply {
                        message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )

            val actualOtp = otpService.findOtp(clientId)
                ?: return ResponseEntity.badRequest().body(
                    ClmStatusResponse().apply {
                        message = ActivationClientMessageCode.CODE_IS_EXPIRED.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )

            if(actualOtp != reqBody.verifyCode.toString()) {
                return ResponseEntity.badRequest().body(
                    ClmStatusResponse().apply {
                        message = ActivationClientMessageCode.CODE_MATCH_ERROR.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            } else {
                val activeClient = when (reqBody.clientAction) {
                    ClientAction.CREATE -> waitingClient.copyClient(AmndState.ACTIVE, ClientAction.CREATE)
                    ClientAction.UPDATE -> waitingClient.copyClient(AmndState.ACTIVE, ClientAction.UPDATE)
                    ClientAction.DELETE -> waitingClient.copyClient(AmndState.CLOSED, ClientAction.DELETE)
                }
                save(activeClient)
                otpService.delete(activeClient.id)
                restClient.activateClamClientSendReq(activeClient.id)

                val payload = objectMapper.writeValueAsString(
                    CreateClientStreaming(
                        clientId = activeClient.id,
                        amndState = AmndStateStreaming.ACTIVE,
                        clientInfo = ClientInfo(
                            login = activeClient.login,
                            email = activeClient.emailAddress
                        ),
                        clientVersion = activeClient.version
                    )
                )

                kafkaProducer.send(
                    "clm-outgoing",
                    payload.hashCode().toString(),
                    payload,
                    mapOf("ContentType" to "client-action-request")
                )

                return ResponseEntity.ok().body(
                    activeClient.createResponse()
                )
            }
        } catch (exception: ClmException) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = exception.message!!
                    status = HttpStatus.BAD_REQUEST.value()
                }
            )
        } catch (exception: Exception) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = "${ClientActionMessageCode.INTERNAL_ERROR}\n${exception.message}"
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
                    ClmStatusResponse().apply {
                        message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            val waitingClient = client.copyClient(AmndState.WAITING, ClientAction.DELETE)
            save(waitingClient)

            //send streaming message on kafka: pub.clm-outgoing
            return ResponseEntity.ok().body(
                ClmStatusResponse().apply {
                    message = RegisterResponseMessageCode.WAITING_ACTIVATION_CODE.toString()
                    status = HttpStatus.OK.value()
                }
            )
        } catch (exception: ClmException) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = exception.message!!
                    status = HttpStatus.BAD_REQUEST.value()
                }
            )
        } catch (exception: Exception) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = "${ClientActionMessageCode.INTERNAL_ERROR}\n${exception.message}"
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
                    ClmStatusResponse().apply {
                        message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            return ResponseEntity.ok().body(
                client.createResponse()
            )
        } catch (exception: ClmException) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = exception.message!!
                    status = HttpStatus.BAD_REQUEST.value()
                }
            )
        } catch (exception: Exception) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = "${ClientActionMessageCode.INTERNAL_ERROR}\n${exception.message}"
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            )
        }
    }

    override fun getClmClientWithDevices(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        val id = req.queryPathVariable["client-id"]
            ?: throw ClmException(message = "The client-id header is missing!")
        val client = clientRepository.findByClientId(id)
            ?: return ResponseEntity.badRequest().body(
                ClmStatusResponse().apply {
                    message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                    status = HttpStatus.BAD_REQUEST.value()
                }
            )
        return ResponseEntity.ok(
            ClmClientDevicesBaseResponse().apply {
                clientInfo = Id().apply {
                    clientId = client.id
                }
                devices = deviceService.getClmDevices(client, "base")
            }
        )
    }

    override fun resetClientActivationCode(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        try {
            val clientId = req.queryPathVariable["client-id"]!!
            val waitingClient = clientRepository.findClientById(
                id = clientId,
                action = ClientAction.CREATE,
                states = listOf(AmndState.WAITING)
            )
                ?: return ResponseEntity.badRequest().body(
                    ClmStatusResponse().apply {
                        message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            val generateOtp = otpService.generateOtp()
            otpService.delete(waitingClient.id)
            otpService.saveOtp(waitingClient.id, generateOtp)

            val payload = objectMapper.writeValueAsString(
                CreateClientStreaming(
                    clientId = waitingClient.id,
                    amndState = AmndStateStreaming.WAITING,
                    clientInfo = ClientInfo(
                        login = waitingClient.login,
                        email = waitingClient.emailAddress
                    ),
                    additionalInfo = mapOf("otp" to generateOtp),
                    clientVersion = waitingClient.version
                )
            )

            kafkaProducer.send(
                "clm-outgoing",
                payload.hashCode().toString(),
                payload,
                mapOf("ContentType" to "client-action-request")
            )
            return ResponseEntity.ok().body(
                ClmClientCreateResponse().apply {
                    id = waitingClient.id
                    status = AmndState.WAITING
                    login = waitingClient.login
                    personalInfo = PersonalInfo.PersonalInfoCreate().apply {
                        surname = waitingClient.surname
                        name = waitingClient.name
                        patronymic = waitingClient.patronymic
                        birthDate = waitingClient.birthDate
                    }
                    contacts = Contacts.ContactsCreate().apply {
                        phone = waitingClient.phoneNumber
                        email = waitingClient.emailAddress
                    }
                    location = Location().apply {
                        region = waitingClient.region?.name
                        city = waitingClient.city
                    }
                }
            )
        } catch (exception: ClmException) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = exception.message!!
                    status = HttpStatus.BAD_REQUEST.value()
                }
            )
        } catch (exception: Exception) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = "${ClientActionMessageCode.INTERNAL_ERROR}\n${exception.message}"
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            )
        }
    }

    override fun setClientStatus(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        val headers = req.headers

        try {
            val clientId = req.queryPathVariable.resolveClientId()
            val client = clientRepository.findByClientId(clientId)
                ?: return ResponseEntity.badRequest().body(
                    ClmStatusResponse().apply {
                        message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            val reqBody = req.body as ClmClientSetStatus

            val newClient = save(
                client.copyClient(reqBody.state, reqBody.action)
            )

            return ResponseEntity.ok().body(
                newClient.createResponse()
            )
        } catch (exception: ClmException) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = exception.message!!
                    status = HttpStatus.BAD_REQUEST.value()
                }
            )
        } catch (exception: Exception) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = "${ClientActionMessageCode.INTERNAL_ERROR}\n${exception.message}"
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            )
        }
    }

    override fun updateClmClient(req: ClmControllerRequestDto): ResponseEntity<ClmResponse> {
        val headers = req.headers
        try {
            val clientId = req.queryPathVariable["client-id"]
                ?: throw ClmException(message = "The client-id header is missing!")
            val client = clientRepository.findByClientId(clientId)
                ?: return ResponseEntity.badRequest().body(
                    ClmStatusResponse().apply {
                        message = ClientActionMessageCode.CLIENT_NOT_FOUND.toString()
                        status = HttpStatus.BAD_REQUEST.value()
                    }
                )
            //update email
            val reqBody = req.body as ClmClientUpdateRequest
            val newClient = client.updateClient(reqBody)

            if (newClient == client) {
                return ResponseEntity.ok().body(
                    newClient.createResponse()
                )
            }

            val updateClient = save(client.updateClient(reqBody))
            reqBody.contacts?.email?.let {
                //send kafka message
                return ResponseEntity.ok().body(
                    ClmStatusResponse().apply {
                        message = RegisterResponseMessageCode.WAITING_ACTIVATION_CODE.toString()
                        status = HttpStatus.OK.value()
                    }
                )
            }

            //send streaming message on kafka: pub.clm-outgoing
            return ResponseEntity.ok().body(
                updateClient.createResponse()
            )
        } catch (exception: ClmException) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = exception.message!!
                    status = HttpStatus.BAD_REQUEST.value()
                }
            )
        } catch (exception: Exception) {
            return ResponseEntity.internalServerError().body(
                ClmStatusResponse().apply {
                    message = "${ClientActionMessageCode.INTERNAL_ERROR}\n${exception.message}"
                    status = HttpStatus.INTERNAL_SERVER_ERROR.value()
                }
            )
        }
    }

    @Transactional
    private fun save(client: Client): Client = clientRepository.save(client)

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

    private fun Client.copyClient(state: AmndState, act: ClientAction): Client {
        return Client().apply {
            amndState = state
            id = this@copyClient.id
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

    private fun calculateNewClientId(): String {
        val current = generatorService.getCurrentId()
        val totalNumbers = 10000

        val numberPart = (current % totalNumbers) + 1
        val letterIndex = current / totalNumbers

        val letter1 = 'A' + (letterIndex / (26 * 26 * 26)) % 26
        val letter2 = 'A' + (letterIndex / (26 * 26)) % 26
        val letter3 = 'A' + (letterIndex / 26) % 26
        val letter4 = 'A' + letterIndex % 26

        return "C-$letter1$letter2$letter3$letter4${"%04d".format(numberPart)}"
    }

    private fun Map<String, String>.resolveClientId():String {
        return this["client-id"]
            ?: throw ClmException(message = "The client-id path param is missing!")
    }

}
