package com.andrew.smart_greenhouse.clm.service

import com.andrew.smart_greenhouse.clm.repository.LocationRepository
import greenhouse_api.clm_model.dto.ClmDto
import greenhouse_api.clm_model.dto.LocationDto
import greenhouse_api.clm_model.entity.Region
import greenhouse_api.clm_service.LocationService
import greenhouse_api.util.exception.ClmNotExistObjectException
import greenhouse_api.util.mapper.ClmInternalMapper
import greenhouse_api.util.message.ClmResponseMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class LocationServiceImpl @Autowired constructor(
    private val locationRepository: LocationRepository,
    private val clmInternalMapper: ClmInternalMapper
) : LocationService {
    override fun createLocation(locationDto: LocationDto): ClmDto {
        TODO("Not yet implemented")
    }

    override fun findLocation(id: String): ClmDto {
        val location = locationRepository.findById(id)
            ?: throw ClmNotExistObjectException(ClmResponseMessage.LOCATION_NOT_FOUND.toString())

        return clmInternalMapper.toDto(location)
    }

    override fun findLocationByRegion(region: Region): List<ClmDto> {
        TODO("Not yet implemented")
    }
}