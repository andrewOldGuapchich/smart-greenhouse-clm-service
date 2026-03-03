package com.andrew.smart_greenhouse.clm.util.kafka

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import streamig_api.common.Topic
import util.kafka.producer.KafkaProducer

@Component
class ClmKafkaProducer (
    private val kafkaProducer: KafkaProducer
) {
    private val logger = LoggerFactory.getLogger(ClmKafkaProducer::class.java)
    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    fun sendMessage(topic: Topic, key: String = "empty_key", value: Any, headers: Map<String, String>) {
        logger.info("Send message. [topic = ${topic}, content-type: ${headers["ContentType"]}]")
        kafkaProducer.send(
            topic.toString(),
            key,
            value,
            headers
        )
    }


    /*
    topic: String,
            key: String = EMPTY_KEY,
             value: Any,
             headers: Map<String, String>
     */
    //        kafkaProducer.send(
//            "clm-outgoing",
//            payload.hashCode().toString(),
//            payload,
//            mapOf("ContentType" to "client-action-request")
//        )


//    suspend fun sendMessage(topic: String, key: String, value: Any): RecordMetadata {
//        return suspendCoroutine { continuation ->
//            val record: ProducerRecord<String, Any> = ProducerRecord(topic, key, objectMapper.writeValueAsString(value))
//
//            producer.send(record) { metadata, exception ->
//                if (exception != null) {
//                    continuation.resumeWithException(exception)
//                } else {
//                    continuation.resume(metadata)
//                }
//            }
//        }
//    }
}

