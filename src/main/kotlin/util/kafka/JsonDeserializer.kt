package com.andrew.smart_greenhouse.clm.util.kafka

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer

class JsonDeserializer<T : Any>(
    private val targetClass: Class<T>
) : Deserializer<T>{

    private val objectMapper = jacksonObjectMapper().apply {
        registerModules(JavaTimeModule())
    }

    override fun deserialize(topic: String?, data: ByteArray?): T? {
        return if (data == null) null else try {
            objectMapper.readValue(data, targetClass)
        } catch (e: Exception) {
            throw SerializationException("Error deserializing JSON message", e)
        }
    }

    override fun close() = Unit
}