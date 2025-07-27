package com.andrew.smart_greenhouse.clm.repository

import greenhouse_api.clm_model.entity.Region
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RegionRepository : JpaRepository<Region, Long> {
    @Query("SELECT * FROM region WHERE amnd_state='ACTIVE' and name = :name", nativeQuery = true)
    fun findRegionByName(name: String): Region?
}