package com.andrew.smart_greenhouse.clm.service.impl

import com.andrew.smart_greenhouse.clm.repository.DeviceRepository
import com.andrew.smart_greenhouse.clm.service.mapper.device_mapper.DeviceMapper.Companion.map
import greenhouse_api.clm_model.entity.Client
import greenhouse_api.clm_model.model.DeviceInfo
import greenhouse_api.clm_service.DeviceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class DeviceServiceImpl @Autowired constructor(
    private val deviceRepository: DeviceRepository
) : DeviceService{
    override fun getClmDevices(client: Client, mode: String): MutableList<DeviceInfo> {
        val devices = deviceRepository.getDevicesByClientExternalId(client.externalId)
            .onEach {it.client = client}
            .map {it.map()}
            .toMutableList()
        return devices
    }
}