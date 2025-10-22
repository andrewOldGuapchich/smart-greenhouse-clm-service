package com.andrew.smart_greenhouse.clm.util.kafka.producer

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class KafkaProducer (
    bootstrapServers: String
){
    private val producer: KafkaProducer<String, Any>
    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    init {
        val props = Properties().apply {
            put("bootstrap.servers", bootstrapServers)
            put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
            //put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
            put("value.serializer", "com.andrew.smart_greenhouse.clm.util.kafka.JsonSerializer")
            put("acks", "all")
            put("retries", 3)
            put("max.in.flight.requests.per.connection", 1)
            put("enable.idempotence", true)
        }
        producer = KafkaProducer(props)
    }

    suspend fun sendMessage(topic: String, key: String, value: Any): RecordMetadata {
        return suspendCoroutine { continuation ->
            val record: ProducerRecord<String, Any> = ProducerRecord(topic, key, objectMapper.writeValueAsString(value))

            producer.send(record) { metadata, exception ->
                if (exception != null) {
                    continuation.resumeWithException(exception)
                } else {
                    continuation.resume(metadata)
                }
            }
        }
    }
}

