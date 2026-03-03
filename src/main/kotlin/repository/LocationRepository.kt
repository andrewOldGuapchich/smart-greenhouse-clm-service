package com.andrew.smart_greenhouse.clm.repository

import greenhouse_api.clm_model.entity.CompositeId
import greenhouse_api.clm_model.entity.Location
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface LocationRepository : JpaRepository<Location, CompositeId> {
    @Query("SELECT l FROM Location l WHERE l.amndState='ACTIVE' and l.id.id=:id")
    fun findById(@Param("id") id: String) : Location?
}