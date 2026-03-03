package com.andrew.smart_greenhouse.clm.repository

import greenhouse_api.clm_model.entity.RedisMessage
import org.slf4j.Logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import util.LoggerUtil
import java.time.Duration

@Component
class RedisRepository @Autowired constructor(
    val redisTemplate: StringRedisTemplate
) {
    fun save(redisMessage: RedisMessage) {
        try {
            redisTemplate.opsForValue()
                .set(
                    createKey(redisMessage.entityKey),
                    redisMessage.payload.data,
                    Duration.ofMinutes(10)
                )
        } catch (e: Exception){
            logger.error("Error otp!", e)
        }
    }

    fun get(key: String): String? {
        return redisTemplate.opsForValue().get(key)
    }

    fun delete(key: String) {
        redisTemplate.delete(key)
    }

    private fun createKey(k: String): String {
        return OTP_KEY
            .plus("::")
            .plus(k)
    }

    val logger: Logger = LoggerUtil.getLogger(RedisRepository::class.java)
    private val OTP_KEY = "otp"
}