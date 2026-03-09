package com.andrew.smart_greenhouse.clm.util.jwt

import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import util.security.keys.KeyType
import util.security.keys.KeysUtil
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*

@Component
class JwtUtils (
    @Value("\${secret.keys.path}")
    private val keysPath: String = "",
    private val keysUtil: KeysUtil
) {
    private val publicKey: PublicKey by lazy {
        val keyString = keysUtil.readKey(KeyType.Public, keysPath)
        val keyBytes = Base64.getDecoder().decode(keyString)
        val keySpec = X509EncodedKeySpec(keyBytes)
        KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    fun getRoles(token: String): List<String> =
        getClaims(token).get("roles", List::class.java).map {
            it.toString()
        }

    fun getClientId(token: String): String =
        getClaims(token).get("sub", String::class.java)

    private fun getClaims(token: String) =
        Jwts.parserBuilder()
            .setSigningKey(publicKey)
            .build()
            .parseClaimsJws(token)
            .body
}