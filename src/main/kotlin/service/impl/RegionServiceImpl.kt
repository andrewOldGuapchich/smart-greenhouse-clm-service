package com.andrew.smart_greenhouse.clm.service.impl

import com.andrew.smart_greenhouse.clm.repository.RegionRepository
import com.andrew.smart_greenhouse.clm.util.exception.ClmException
import greenhouse_api.clm_model.entity.Region
import greenhouse_api.clm_service.RegionService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class RegionServiceImpl @Autowired constructor(
    private val regionRepository: RegionRepository
) : RegionService {
    override fun findRegionByName(regionName: String): Region {
        return regionRepository.findRegionByName(regionName)
            ?: throw ClmException("Region not found")
    }
}