package com.andrew.smart_greenhouse.clm.util

import greenhouse_api.clm_model.dto.ClmDto
import greenhouse_api.clm_model.dto.rs.ClmClientResponse
import greenhouse_api.clm_model.dto.rs.ClmResponse
import greenhouse_api.clm_model.dto.rs.ClmStatusResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

fun createOk(body: ClmDto): ResponseEntity<ClmResponse> {
    return ResponseEntity.ok().body(
        ClmClientResponse(
            data = body
        )
    )
}

fun createBadRequest(message: String): ResponseEntity<ClmResponse> {
    return ResponseEntity.badRequest().body(
        ClmStatusResponse(
            code = HttpStatus.BAD_REQUEST.value(),
            message = message
        )
    )
}

fun createNotFound(message: String): ResponseEntity<ClmResponse> {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        ClmStatusResponse(
            code = HttpStatus.NOT_FOUND.value(),
            message = message
        )
    )
}

fun createInternalServer(message: String): ResponseEntity<ClmResponse> {
    return ResponseEntity.internalServerError().body(
        ClmStatusResponse(
            code = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            message = message
        )
    )
}