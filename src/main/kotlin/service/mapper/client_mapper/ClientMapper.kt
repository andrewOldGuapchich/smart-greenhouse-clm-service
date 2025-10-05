package com.andrew.smart_greenhouse.clm.service.mapper.client_mapper

import greenhouse_api.clm_model.entity.Client
import greenhouse_api.clm_model.model.*
import org.springframework.stereotype.Component

@Component
class ClientMapper {
    companion object {
        fun Client.createResponse(): ClmResponse {
            return createDtoFromClient(this)
        }

        private fun createDtoFromClient(client: Client): ClmResponse {
            return ClmClientGetResponse().apply {
                id = Id().apply {
                    clientId = client.id
                }
                login = client.login
                personalInfo = PersonalInfo.PersonalInfoCreate().apply {
                    name = client.name
                    surname = client.surname
                    client.patronymic?.let{
                        patronymic = it
                    }
                    birthDate = client.birthDate
                }
                contacts = Contacts.ContactsCreate().apply {
                    phone = client.phoneNumber
                    email = client.emailAddress
                }
                location = Location().apply {
                    region = client.region?.name
                    city = client.city
                }
            }
        }
    }
}