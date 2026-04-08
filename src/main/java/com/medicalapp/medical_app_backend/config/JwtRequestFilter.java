package com.medicalapp.medical_app_backend.config;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        final String requestPath = request.getRequestURI();
        final String method = request.getMethod();

        logger.debug("Processing request: {} {}", method, requestPath);

        // ✅ Skip WebSocket upgrade requests FIRST
        if (requestPath.startsWith("/ws")) {
            logger.info("✅ WebSocket endpoint detected - skipping JWT filter: {}", requestPath);
            chain.doFilter(request, response);
            return;
        }

        // ✅ Skip OPTIONS preflight requests
        if ("OPTIONS".equalsIgnoreCase(method)) {
            logger.debug("✅ OPTIONS preflight request - skipping JWT validation: {}", requestPath);
            chain.doFilter(request, response);
            return;
        }

        // ✅ Skip public endpoints — no JWT needed
        if (isPublicEndpoint(requestPath, method)) {
            logger.debug("✅ Public endpoint detected: {} {}", method, requestPath);
            chain.doFilter(request, response);
            return;
        }

        String username = null;
        String jwtToken = null;

        // Try Authorization header first
        final String requestTokenHeader = request.getHeader("Authorization");

        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);
            logger.debug("✅ JWT token found in Authorization header");
        }
        // Fallback: query parameter (for WebSocket connections)
        else if (request.getQueryString() != null && request.getQueryString().contains("token=")) {
            try {
                String queryString = request.getQueryString();
                String[] params = queryString.split("&");
                for (String param : params) {
                    if (param.startsWith("token=")) {
                        jwtToken = param.substring(6);
                        logger.debug("✅ JWT token found in query parameter");
                        break;
                    }
                }
            } catch (Exception e) {
                logger.warn("⚠️ Error extracting token from query parameter: {}", e.getMessage());
            }
        } else {
            logger.warn("❌ No JWT token found for protected endpoint: {}", requestPath);
        }

        // Extract username from token
        if (jwtToken != null) {
            try {
                username = jwtTokenUtil.getUsernameFromToken(jwtToken);
                logger.debug("✅ Username extracted from token: {}", username);
            } catch (IllegalArgumentException e) {
                logger.warn("❌ Unable to get JWT Token: {}", e.getMessage());
            } catch (ExpiredJwtException e) {
                logger.warn("❌ JWT Token has expired: {}", e.getMessage());
            } catch (MalformedJwtException e) {
                logger.warn("❌ JWT Token is malformed: {}", e.getMessage());
            } catch (Exception e) {
                logger.error("❌ Error processing JWT token: {}", e.getMessage());
            }
        }

        // Validate token and set authentication in security context
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.info("✅ Security context set for user: {}", username);
                } else {
                    logger.warn("❌ JWT token validation failed for user: {}", username);
                }
            } catch (Exception e) {
                logger.error("❌ Error loading user details for: {}", username, e);
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * Only truly public endpoints bypass JWT validation.
     * Admin and patient support endpoints are protected.
     */
    private boolean isPublicEndpoint(String requestPath, String method) {

        // ✅ Auth endpoints — login, signup, token refresh
        if (requestPath.startsWith("/api/auth/") || requestPath.startsWith("/auth/")) {
            logger.debug("✅ Public auth endpoint: {}", requestPath);
            return true;
        }

        // ✅ Only these THREE support endpoints are public — everything else under
        // /api/support/ is protected (admin/**, chat/**, tickets, stats, etc.)
        if (requestPath.equals("/api/support/status") ||
            requestPath.equals("/api/support/faq") ||
            requestPath.equals("/api/support/health")) {
            logger.debug("✅ Public support endpoint: {}", requestPath);
            return true;
        }

        // ✅ Health check endpoints
        if (requestPath.startsWith("/api/health") ||
            requestPath.startsWith("/actuator/health") ||
            requestPath.startsWith("/actuator/info")) {
            logger.debug("✅ Public health endpoint: {}", requestPath);
            return true;
        }

        // ✅ WebSocket handshake
        if (requestPath.startsWith("/ws")) {
            logger.debug("✅ WebSocket endpoint: {}", requestPath);
            return true;
        }

        // ✅ Error and root paths
        if (requestPath.startsWith("/error") || requestPath.equals("/")) {
            logger.debug("✅ Public error/root endpoint: {}", requestPath);
            return true;
        }

        // ✅ Other actuator endpoints
        if (requestPath.startsWith("/actuator/")) {
            logger.debug("✅ Public actuator endpoint: {}", requestPath);
            return true;
        }

        logger.debug("🔒 Protected endpoint requires JWT: {}", requestPath);
        return false;
    }
}