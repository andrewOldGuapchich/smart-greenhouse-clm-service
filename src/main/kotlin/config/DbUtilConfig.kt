package com.andrew.smart_greenhouse.clm.config

import greenhouse_api.util.mapper.ClmInternalMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import util.db.IdGenerator
import javax.sql.DataSource

@Configuration
class DbUtilConfig {
    @Bean
    fun idGenerator(): IdGenerator = IdGenerator()

    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate {
        return JdbcTemplate(dataSource)
    }

    @Bean
    fun clmInternalMapper(): ClmInternalMapper = ClmInternalMapper(idGenerator())
}