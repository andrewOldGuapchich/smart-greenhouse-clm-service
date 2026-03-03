package com.andrew.smart_greenhouse.clm.service

import com.andrew.smart_greenhouse.clm.repository.RegionRepository
import greenhouse_api.clm_model.dto.ClmDto
import greenhouse_api.clm_model.dto.RegionDto
import greenhouse_api.clm_service.RegionService
import greenhouse_api.util.exception.ClmException
import greenhouse_api.util.mapper.ClmInternalMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RegionServiceImpl @Autowired constructor(
    private val regionRepository: RegionRepository,
    private val clmInternalMapper: ClmInternalMapper
) : RegionService {
    override fun createRegion(regionDto: RegionDto): ClmDto {
        TODO("Not yet implemented")
    }

    override fun findAllRegion(): List<ClmDto> {
        TODO("Not yet implemented")
    }

    override fun findRegion(id: String): ClmDto {
        val region = regionRepository.findRegionById(id)
            ?: throw ClmException("Region not found")
        return clmInternalMapper.toDto(region)
    }
}