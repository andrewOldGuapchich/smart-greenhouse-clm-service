package com.andrew.smart_greenhouse.clm.util.kafka

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.serialization.Serializer
import org.hibernate.type.SerializationException

class JsonSerializer<T> : Serializer<T> {
    private val objectMapper = jacksonObjectMapper().apply {
        registerModules(JavaTimeModule())
    }

    override fun serialize(topic: String?, data: T?): ByteArray? {
        return if(data == null) null else try {
            objectMapper.writeValueAsBytes(data)
        } catch (exception: Exception) {
            throw SerializationException("Error serializing error: ", exception)
        }
    }

    override fun close() = Unit
}