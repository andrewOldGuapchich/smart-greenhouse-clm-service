package com.andrew.smart_greenhouse.clm.service

import com.andrew.smart_greenhouse.clm.repository.ClientRepository
import com.andrew.smart_greenhouse.clm.util.kafka.ClmKafkaProducer
import com.andrew.smart_greenhouse.clm.util.mapper.copy
import com.andrew.smart_greenhouse.clm.util.mapper.toCreateStreamingMessage
import com.andrew.smart_greenhouse.clm.util.mapper.toStreamingAmndState
import com.andrew.smart_greenhouse.clm.util.mapper.update
import com.andrew.smart_greenhouse.clm.util.rest.ClmRestClient
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import greenhouse_api.clam_model.dto.ClamEntityState
import greenhouse_api.clam_model.dto.ClientDto
import greenhouse_api.clam_model.dto.rq.ClamClientCreateRequest
import greenhouse_api.clam_model.dto.rs.ClamStatusResponse
import greenhouse_api.clm_controller.*
import greenhouse_api.clm_model.dto.ClmDto
import greenhouse_api.clm_model.dto.LocationDto
import greenhouse_api.clm_model.dto.rq.ClmClientActivationRequest
import greenhouse_api.clm_model.dto.rq.ClmClientCreateRequest
import greenhouse_api.clm_model.dto.rq.ClmClientUpdateRequest
import greenhouse_api.clm_model.entity.AmndState
import greenhouse_api.clm_model.entity.Client
import greenhouse_api.clm_service.*
import greenhouse_api.util.ClamException
import greenhouse_api.util.exception.ClmAlreadyExistObject
import greenhouse_api.util.exception.ClmException
import greenhouse_api.util.exception.ClmIllegalArgumentException
import greenhouse_api.util.exception.ClmNotExistObjectException
import greenhouse_api.util.mapper.ClmInternalMapper
import greenhouse_api.util.message.ClmResponseMessage
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import streamig_api.clm.OtpSendKafkaMessage
import streamig_api.common.ActiveDate
import streamig_api.common.ContentType
import streamig_api.common.ObjectState
import streamig_api.common.Topic
import java.time.LocalDateTime

@Service
class ClientServiceImpl @Autowired constructor(
    private val clientRepository: ClientRepository,
    private val locationService: LocationService,
    private val otpService: OtpService,
    private val kafkaProducer: ClmKafkaProducer,
    private val restClient: ClmRestClient,
    private val clmInternalMapper: ClmInternalMapper
): ClientService {
    private val logger = LoggerFactory.getLogger(ClientService::class.java)

    private val objectMapper = jacksonObjectMapper().apply {
        registerModules(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    override suspend fun createClient(req: ClmControllerRequestDto): ClmDto {
        val headers = req.headers
        val requestBody = req.body as ClmClientCreateRequest
        if (clientRepository.findClientByLogin(
                requestBody.login,
                listOf(AmndState.ACTIVE, AmndState.WAITING)
            ) != null
            || clientRepository.findClientByEmail(
                requestBody.contacts.email!!,
                listOf(AmndState.ACTIVE, AmndState.WAITING)
            ) != null
        )
            throw ClmAlreadyExistObject(ClmResponseMessage.ALREADY_EXISTS.toString())

        val newClient: Client

        logger.info(objectMapper.writeValueAsString(requestBody))
        logger.info("location id length: ${requestBody.clientLocation.location?.id?.length}")

        try {
            newClient = save(
                clmInternalMapper.toEntity(requestBody)
                    .apply {
                        location = clmInternalMapper.toEntity(
                            locationService.findLocation(requestBody.clientLocation.location?.id!!) as LocationDto
                        )
                    }
            )
        } catch (e: Exception) {
            logger.error(e.message)
            throw ClmException(e.message!!)
        }

        val clientCreateStreaming = objectMapper.writeValueAsString(newClient.toCreateStreamingMessage())
        logger.info("Kafka message: $clientCreateStreaming")

        //send CLAM-message
        val clamResponse = restClient.clamCredCreateRequest(ClamClientCreateRequest(
            client = ClientDto(id = newClient.id.id,
                clientState = ClamEntityState(
                    amndState = greenhouse_api.clam_model.entity.AmndState.WAITING,
                    lastUpdated = newClient.amndDate,
                    originalDate = newClient.originalDate,
                    activeDate = greenhouse_api.clam_model.dto.ActiveDate(
                        to = newClient.activeDateTo,
                        from = newClient.activeDateFrom
                    ),
                    prevVersion = newClient.prevVersion,
                    version = newClient.id.version
                )
            )
        ))

        if(!clamResponse.first.is2xxSuccessful)
            throw ClamException((clamResponse.second as ClamStatusResponse).message)

        //saga
//        kafkaProducer.sendMessage(
//            Topic.ClmOutgoing,
//            clientCreateStreaming.hashCode().toString(),
//            clientCreateStreaming,
//            mapOf("ContentType" to ContentType.ClientCreate.toString())
//        )

        //send NTM-message
        val generateOtp = otpService.generateOtp()
        otpService.saveOtp(newClient.id.id, generateOtp)
        val otpSendMessage = objectMapper.writeValueAsString(
            OtpSendKafkaMessage(
                clientId = newClient.id.id,
                email = newClient.email,
                otp = generateOtp
            )
        )
        kafkaProducer.sendMessage(
            Topic.ClmOutgoing,
            otpSendMessage.hashCode().toString(),
            otpSendMessage,
            mapOf("ContentType" to ContentType.OtpSend.toString())
        )
        return clmInternalMapper.toDto(newClient)
    }

    override fun activateClmClient(req: ClmControllerRequestDto): ClmDto {
        val requestBody = req.body as ClmClientActivationRequest
        val clientId = req.queryPathVariable["client-id"]
            ?: throw ClmIllegalArgumentException(ClmResponseMessage.PATH_VARIABLE_MISSING.format("client-id"))

        val waitingClient = clientRepository.findClientById(
            clientId = clientId,
            states = listOf(AmndState.WAITING)
        ) ?: throw ClmNotExistObjectException(ClmResponseMessage.CLIENT_NOT_FOUND.toString())

        val actualOtp = otpService.findOtp(clientId)
            ?: throw ClmNotExistObjectException(ClmResponseMessage.CODE_IS_EXPIRED.toString())

        if(actualOtp != requestBody.verifyCode.toString())
            throw ClmIllegalArgumentException(ClmResponseMessage.CODE_MATCH_ERROR.toString())

        waitingClient.apply {
            amndState = AmndState.INACTIVE
            activeDateFrom = LocalDateTime.now()
        }

        val activeClient = waitingClient.copy(AmndState.ACTIVE)
        logger.info("Client id ${activeClient.id.id}")
        otpService.delete(clientId)

        try {
            val savedActiveClient= save(activeClient)
            val clientActivateStreaming = objectMapper.writeValueAsString(activeClient.toCreateStreamingMessage())
            logger.info("Activation. Kafka message: $clientActivateStreaming")

            kafkaProducer.sendMessage(
                Topic.ClmOutgoing,
                clientActivateStreaming.hashCode().toString(),
                clientActivateStreaming,
                mapOf("ContentType" to ContentType.ClientActivation.toString())
            )

            save(waitingClient)
            return clmInternalMapper.toDto(savedActiveClient)
        } catch (e: Exception) {
            logger.error(e.message)
            throw ClmException(ClmResponseMessage.INTERNAL_ERROR.toString())
        }
    }

    override fun deleteClmClient(req: ClmControllerRequestDto): ClmDto {
        //resolve headers todo
        val headers = req.headers
        val clientId = req.queryPathVariable["client-id"]
            ?: throw ClmIllegalArgumentException(ClmResponseMessage.PATH_VARIABLE_MISSING.format("client-id"))

        val activeClient = clientRepository.findByClientId(clientId)
            ?: throw ClmNotExistObjectException(ClmResponseMessage.CLIENT_NOT_FOUND.toString())

        activeClient.apply {
            amndState = AmndState.INACTIVE
            activeDateFrom = LocalDateTime.now()
        }

        val deletedClient: Client

        try {
            deletedClient = save(activeClient.copy(AmndState.CLOSED))
            save(activeClient)

            val clientDeleteStreaming = objectMapper.writeValueAsString(deletedClient.toCreateStreamingMessage())
            logger.info("Deleting. Kafka message: $clientDeleteStreaming")

            kafkaProducer.sendMessage(
                Topic.ClmOutgoing,
                clientDeleteStreaming.hashCode().toString(),
                clientDeleteStreaming,
                mapOf("ContentType" to ContentType.ClientUpdate.toString())
            )

            return clmInternalMapper.toDto(deletedClient)
        } catch (e: Exception) {
            logger.error(e.message)
            throw ClmException(ClmResponseMessage.INTERNAL_ERROR.toString())
        }
    }

    override fun getClmClient(req: ClmControllerRequestDto): ClmDto {
        val clientId = req.queryPathVariable["client-id"]
            ?: throw ClmIllegalArgumentException(ClmResponseMessage.PATH_VARIABLE_MISSING.format("client-id"))

        val client = clientRepository.findByClientId(clientId)
            ?: throw ClmNotExistObjectException(ClmResponseMessage.CLIENT_NOT_FOUND.toString())

        return clmInternalMapper.toDto(client)
    }

    override fun resetClientActivationCode(req: ClmControllerRequestDto): ClmDto {
        val clientId = req.queryPathVariable["client-id"]
            ?: throw ClmIllegalArgumentException(ClmResponseMessage.PATH_VARIABLE_MISSING.format("client-id"))

        val waitingClient = clientRepository.findClientById(clientId, listOf(AmndState.WAITING))
            ?: throw ClmNotExistObjectException(ClmResponseMessage.CLIENT_NOT_FOUND.toString())

        val generateOtp = otpService.generateOtp()
        otpService.delete(waitingClient.id.id)
        otpService.saveOtp(waitingClient.id.id, generateOtp)

        otpService.saveOtp(waitingClient.id.id, generateOtp)
        val otpSendMessage = objectMapper.writeValueAsString(
            OtpSendKafkaMessage(
                clientId = waitingClient.id.id,
                email = waitingClient.email,
                otp = generateOtp
            )
        )

        kafkaProducer.sendMessage(
            Topic.ClmOutgoing,
            otpSendMessage.hashCode().toString(),
            otpSendMessage,
            mapOf("ContentType" to ContentType.OtpSend.toString())
        )

        return clmInternalMapper.toDto(waitingClient)
    }

    override fun updateClmClient(req: ClmControllerRequestDto): ClmDto {
        val headers = req.headers
        val requestBody = req.body as ClmClientUpdateRequest

        val clientId = req.queryPathVariable["client-id"]
            ?: throw ClmIllegalArgumentException(ClmResponseMessage.PATH_VARIABLE_MISSING.format("client-id"))

        val activeClient = clientRepository.findByClientId(clientId)
            ?: throw ClmNotExistObjectException(ClmResponseMessage.CLIENT_NOT_FOUND.toString())

        //update client info
        activeClient.apply {
            activeDateFrom = LocalDateTime.now()
            amndState = AmndState.INACTIVE
        }

        logger.info("Updating. Request email: ${requestBody.contacts?.email}")
        logger.info("Updating. Client email: ${activeClient.email}")

        val updatedClient = if(!requestBody.contacts?.email.isNullOrEmpty()) {
            logger.info("Updating email")
            updateClientEmail(activeClient, activeClient.update(requestBody))
        } else {
            updateClientInfo(activeClient, activeClient.update(requestBody))
        }

        val clientDeleteStreaming = objectMapper.writeValueAsString(updatedClient.toCreateStreamingMessage())
        logger.info("Updating. Kafka message: $clientDeleteStreaming")

        kafkaProducer.sendMessage(
            Topic.ClmOutgoing,
            clientDeleteStreaming.hashCode().toString(),
            clientDeleteStreaming,
            mapOf("ContentType" to ContentType.ClientUpdate.toString())
        )

        return clmInternalMapper.toDto(updatedClient)
    }

    private fun updateClientInfo(activeClient: Client, updatedClient: Client): Client {
        save(activeClient)
        return save(updatedClient)
    }

    private fun updateClientEmail(activeClient: Client, updatedClient: Client): Client {
        updatedClient.apply {
            amndState = AmndState.WAITING
        }

        val generateOtp = otpService.generateOtp()
        otpService.saveOtp(updatedClient.id.id, generateOtp)
        val otpSendMessage = objectMapper.writeValueAsString(
            OtpSendKafkaMessage(
                clientId = updatedClient.id.id,
                email = updatedClient.email,
                otp = generateOtp
            )
        )
        kafkaProducer.sendMessage(
            Topic.ClmOutgoing,
            otpSendMessage.hashCode().toString(),
            otpSendMessage,
            mapOf("ContentType" to ContentType.OtpSend.toString())
        )

        save(activeClient)
        return save(updatedClient)
    }



    @Transactional
    private fun save(client: Client): Client = clientRepository.save(client)
}
