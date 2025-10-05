package com.andrew.smart_greenhouse.clm.repository

import greenhouse_api.clm_model.entity.Device
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface DeviceRepository : JpaRepository<Device, Long>{
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM clm_device WHERE client_id = :id AND amnd_state = 'ACTIVE'"
    )
    fun getDevicesByClientExternalId(@Param("id") id: String): List<Device>
}