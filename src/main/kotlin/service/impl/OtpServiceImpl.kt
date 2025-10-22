package com.andrew.smart_greenhouse.clm.service.impl

import com.andrew.smart_greenhouse.clm.repository.RedisRepository
import greenhouse_api.clm_model.model.Payload
import greenhouse_api.clm_model.model.RedisMessage
import greenhouse_api.clm_service.OtpService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import util.LoggerUtil
import kotlin.random.Random

@Service
class OtpServiceImpl @Autowired constructor(
    private val redisRepository: RedisRepository
) : OtpService {
    val logger = LoggerUtil.getLogger(OtpService::class.java)

    override fun delete(key: String) {
        redisRepository.delete("otp::$key")
    }

    override fun findOtp(key: String): String? {
        try {
            val findingOtp = redisRepository.get("otp::$key")

            return findingOtp
        } catch (e: Exception) {
            throw Exception(e)
        }
    }

    override fun saveOtp(key: String, otp: String) {
        redisRepository.save(
            RedisMessage().apply {
                entityKey = key
                payload = Payload().apply {
                    data = otp
                }
            }
        )
    }

    override fun generateOtp(): String {
        return Random.nextInt(100000, 1000000).toString()
    }
}