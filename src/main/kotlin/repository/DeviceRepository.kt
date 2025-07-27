package com.andrew.smart_greenhouse.clm.repository

import com.andrew.smart_greenhouse.clm.util.annotation.Repository
import greenhouse_api.clm_model.entity.Device
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

@Repository
interface DeviceRepository : JpaRepository<Device, Long>{
    @Query(
        nativeQuery = true,
        value = "SELECT * FROM clm_device WHERE client_id = :externalId AND amnd_state = 'ACTIVE'"
    )
    fun getDevicesByClientExternalId(@Param("externalId") externalId: String): List<Device>
}