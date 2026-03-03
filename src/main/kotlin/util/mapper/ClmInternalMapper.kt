package com.andrew.smart_greenhouse.clm.util.mapper

import greenhouse_api.clm_model.dto.rq.ClmClientUpdateRequest
import greenhouse_api.clm_model.entity.AmndState
import greenhouse_api.clm_model.entity.Client
import greenhouse_api.clm_model.entity.CompositeId
import streamig_api.clm.*
import streamig_api.common.ActiveDate
import streamig_api.common.AmndStateStreaming
import streamig_api.common.ObjectState
import java.time.LocalDateTime

fun Client.copy(state: AmndState): Client {
    return Client(
        login = this.login,
        email = this.email,
        surname = this.surname,
        name = this.name,
        birthDate = this.birthDate
    ).apply {
        id = CompositeId().apply {
            id = this@copy.id.id
            version = this@copy.id.version + 1
        }
        patronymic = this@copy.patronymic
        phoneNumber = this@copy.phoneNumber
        amndState = state
        originalDate = this@copy.originalDate
        activeDateTo = this@copy.activeDateFrom ?: LocalDateTime.now()
        activeDateFrom = null
        prevVersion = this@copy.id.version
        location = this@copy.location
    }
}

fun Client.update(updateReq: ClmClientUpdateRequest): Client {
    return Client(
        login = this.login,
        email = updateReq.contacts?.email ?: this.email,
        surname = updateReq.personalInfo?.surname ?: this.surname,
        name = updateReq.personalInfo?.name ?: this.name,
        birthDate = updateReq.personalInfo?.birthDate ?: this.birthDate
    ).apply {
        id = CompositeId().apply {
            id = this@update.id.id
            version = this@update.id.version + 1
        }
        patronymic = updateReq.personalInfo?.patronymic ?: this@update.patronymic
        phoneNumber = updateReq.contacts?.phone ?: this@update.phoneNumber
        amndState = AmndState.ACTIVE
        originalDate = this@update.originalDate
        activeDateTo = this@update.activeDateFrom ?: LocalDateTime.now()
        activeDateFrom = null
        prevVersion = this@update.id.version
        location = this@update.location//updateReq.clientLocation?.location?.toEntity() ?: this@update.location
    }
}


fun Client.toCreateStreamingMessage(): ClientKafkaMessage {
    return ClientKafkaMessage(
        messageTime = LocalDateTime.now(),
        id = this.id.id,
        objectState = ObjectState(
            amndState = this.amndState.toStreamingAmndState(),
            lastUpdated = this.amndDate,
            originalDate = this.originalDate,
            activeDate = ActiveDate(
                to = this.activeDateTo,
                from = this.activeDateFrom
            ),
            prevVersion = this.prevVersion,
            version = this.id.version
        )
    )
}

fun AmndState.toStreamingAmndState(): AmndStateStreaming {
    return when (this) {
        AmndState.ACTIVE -> AmndStateStreaming.ACTIVE
        AmndState.CLOSED -> AmndStateStreaming.CLOSED
        AmndState.WAITING -> AmndStateStreaming.WAITING
        AmndState.INACTIVE -> AmndStateStreaming.INACTIVE
    }
}

fun AmndStateStreaming.toAmndState(): AmndState {
    return when (this) {
        AmndStateStreaming.ACTIVE -> AmndState.ACTIVE
        AmndStateStreaming.CLOSED -> AmndState.CLOSED
        AmndStateStreaming.WAITING -> AmndState.WAITING
        AmndStateStreaming.INACTIVE -> AmndState.INACTIVE
    }
}