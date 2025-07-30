package com.andrew.smart_greenhouse.clm

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication

@SpringBootApplication
@EntityScan("greenhouse_api.clm_model.entity")
//@EnableDiscoveryClient
class ClmMain
fun main(args: Array<String>){
    runApplication<ClmMain>(*args)
}