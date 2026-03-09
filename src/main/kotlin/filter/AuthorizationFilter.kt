package com.andrew.smart_greenhouse.clm.filter

import com.andrew.smart_greenhouse.clm.util.jwt.JwtUtils
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class AuthorizationFilter (
    private val jwtUtils: JwtUtils
) : OncePerRequestFilter() {
    private val filterLogger = LoggerFactory.getLogger(AuthorizationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val path = request.requestURI
        val method = request.method

        try {
            val token = extractToken(request)

            filterLogger.info("=== Request: {} {} ===", method, path)

            if (token != null) {
                val clientId = jwtUtils.getClientId(token)
                val roles = jwtUtils.getRoles(token)

                filterLogger.info("Authenticated user: {} with roles: {}", clientId, roles)

                val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
                filterLogger.info("Granted authorities: {}", authorities.map { it.authority })

                val authentication = JwtAuthenticationToken(
                    clientId = clientId,
                    roles = roles,
                    authorities = authorities
                )

                SecurityContextHolder.getContext().authentication = authentication

                val checkAuth = SecurityContextHolder.getContext().authentication
                filterLogger.info("SecurityContext contains: {}",
                    checkAuth?.authorities?.map { it.authority })
            } else {
                filterLogger.info("No token provided for: {} {}", method, path)
            }

            filterChain.doFilter(request, response)

            filterLogger.info("=== Response sent for: {} {} ===", method, path)

        } catch (e: Exception) {
            filterLogger.error("Authentication failed for: {} {} - {}", method, path, e.message)
            sendError(response, HttpStatus.UNAUTHORIZED.value(), "Authentication failed!")
        }
    }

    private fun sendError(response: HttpServletResponse, status: Int, message: String) {
        response.status = status
        response.contentType = "application/json"
        response.writer.write("""{"error": "$message"}""")
    }

    private fun extractToken(request: HttpServletRequest): String? {
        return request.getHeader("X-Token")
    }
}

class JwtAuthenticationToken(
    private val clientId: String,
    private val roles: List<String>,
    authorities: Collection<GrantedAuthority>
) : AbstractAuthenticationToken(authorities) {

    override fun getCredentials(): Any? = null
    override fun getPrincipal(): Any = clientId
    override fun isAuthenticated(): Boolean = true

    fun getClientId(): String = clientId
    fun getRoles(): List<String> = roles
}