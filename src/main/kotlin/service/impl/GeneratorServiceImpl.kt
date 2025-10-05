package com.andrew.smart_greenhouse.clm.service.impl

import com.andrew.smart_greenhouse.clm.repository.GeneratorRepository
import greenhouse_api.clm_model.entity.IdGenerator
import greenhouse_api.clm_service.GeneratorService
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class GeneratorServiceImpl @Autowired constructor(
    private val generatorRepository: GeneratorRepository
) : GeneratorService {
    override fun getCurrentId(): Int = generatorRepository.getCurrentId().current

    @Transactional
    override fun updateCurrentId() {
        val id = generatorRepository.getCurrentId()
        id.current.also { id.current = it + 1}
        generatorRepository.save(id)
    }
}