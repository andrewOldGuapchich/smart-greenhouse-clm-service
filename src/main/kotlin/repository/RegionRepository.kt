package com.andrew.smart_greenhouse.clm.repository

import greenhouse_api.clm_model.entity.CompositeId
import greenhouse_api.clm_model.entity.Region
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RegionRepository : JpaRepository<Region, CompositeId> {
    @Query("SELECT * FROM region WHERE amndState='ACTIVE' and r.name = :name", nativeQuery = true)
    fun findRegionByName(name: String): Region?
    //@Query("SELECT l FROM Location l WHERE l.amndState='ACTIVE' and l.id.id=:id")

    @Query("SELECT r FROM Region r WHERE r.amndState='ACTIVE' and r.id.id=:id")
    fun findRegionById(id: String): Region?
}