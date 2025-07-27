package com.andrew.smart_greenhouse.clm.repository

import greenhouse_api.clm_model.entity.Client
import greenhouse_api.util.AmndState
import greenhouse_api.util.ClientAction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ClientRepository : JpaRepository<Client, Long>{
    @Query("SELECT c FROM Client c WHERE c.amndState IN :states and c.login=:login")
    fun findClientByLogin(
        @Param("login") login: String,
        @Param("states") states: List<AmndState>
    ) : Client?

    @Query("SELECT c FROM Client c WHERE c.amndState IN :states and c.emailAddress=:email")
    fun findClientByEmail(
        @Param("email") email: String,
        @Param("states") states: List<AmndState>
    ) : Client?

    @Query("SELECT c FROM Client c WHERE c.login=:login and c.amndState='WAITING' and c.action=:action")
    fun findWaitingClient(
        @Param("login") login: String,
        @Param("action") action: ClientAction
    ): Client?

    @Query("SELECT c FROM Client c where c.externalId=:clientId and c.amndState='ACTIVE'")
    fun findByClientId(
        @Param("clientId") clientId: String
    ): Client?
}