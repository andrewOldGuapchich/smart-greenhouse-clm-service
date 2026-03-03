package com.andrew.smart_greenhouse.clm.repository

import greenhouse_api.clm_model.entity.AmndState
import greenhouse_api.clm_model.entity.Client
import greenhouse_api.clm_model.entity.CompositeId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ClientRepository : JpaRepository<Client, CompositeId>{
    @Query("""
        SELECT c FROM Client c 
        LEFT JOIN FETCH c.location 
        WHERE c.amndState IN :states 
        AND c.login=:login
    """)
    fun findClientByLogin(
        @Param("login") login: String,
        @Param("states") states: List<AmndState>
    ) : Client?

    @Query("""
        SELECT c FROM Client c 
        LEFT JOIN FETCH c.location 
        WHERE c.amndState IN :states 
        AND c.email=:email
    """)
    fun findClientByEmail(
        @Param("email") email: String,
        @Param("states") states: List<AmndState>
    ) : Client?

    @Query("SELECT c FROM Client c WHERE c.id.id=:id AND c.amndState IN :states")
    fun findClientById(
        @Param("id") clientId: String,
        @Param("states") states: List<AmndState>
    ): Client?


    @Query("""
        SELECT c FROM Client c 
        LEFT JOIN FETCH c.location l
        LEFT JOIN FETCH l.region
        where c.id.id=:clientId AND c.amndState=AmndState.ACTIVE
    """)
    fun findByClientId(
        @Param("clientId") clientId: String
    ): Client?
}