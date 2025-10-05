package com.andrew.smart_greenhouse.clm.service.mapper.device_mapper

import greenhouse_api.clm_model.entity.Device
import greenhouse_api.clm_model.model.*
import org.springframework.stereotype.Component

@Component
class DeviceMapper {
    companion object {
        fun Device.map(/*mode: Mode*/): DeviceInfo {
            //when (mode) -> ...
            return createDeviceInfoBase(this)
        }

        private fun createDeviceInfoBase(device: Device): DeviceInfo {
            return DeviceBase().apply {
                deviceId = Id().apply {
                    clientId = device.id
                }
                serialNumber = device.serialNumber
                type = device.deviceType
                description = device.description
            }
        }
    }
}