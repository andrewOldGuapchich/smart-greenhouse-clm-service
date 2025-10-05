package com.andrew.smart_greenhouse.clm.repository

import greenhouse_api.clm_model.entity.IdGenerator
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface GeneratorRepository : JpaRepository<IdGenerator, String>{
    @Query(nativeQuery = true,
        value = "SELECT * FROM id_generator where id_key='clm'")
    fun getCurrentId(): IdGenerator
}