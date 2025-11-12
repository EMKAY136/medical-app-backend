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

        logger.debug("Processing request: {} {}", request.getMethod(), requestPath);

        String username = null;
        String jwtToken = null;

        // Check if this is a public endpoint
        if (isPublicEndpoint(requestPath)) {
            logger.debug("Public endpoint detected: {}", requestPath);
            chain.doFilter(request, response);
            return;
        }

        // 🔥 NEW: Try to get JWT token from Authorization header first
        final String requestTokenHeader = request.getHeader("Authorization");
        
        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);
            logger.debug("JWT token found in Authorization header");
        } 
        // 🔥 NEW: Fallback to query parameter (for WebSocket connections)
        else if (request.getQueryString() != null && request.getQueryString().contains("token=")) {
            try {
                String queryString = request.getQueryString();
                String[] params = queryString.split("&");
                for (String param : params) {
                    if (param.startsWith("token=")) {
                        jwtToken = param.substring(6); // Remove "token="
                        logger.debug("JWT token found in query parameter");
                        break;
                    }
                }
            } catch (Exception e) {
                logger.warn("Error extracting token from query parameter: {}", e.getMessage());
            }
        } else {
            logger.debug("No JWT token found in Authorization header or query parameters");
        }

        // Extract username from token
        if (jwtToken != null) {
            try {
                username = jwtTokenUtil.getUsernameFromToken(jwtToken);
            } catch (IllegalArgumentException e) {
                logger.warn("Unable to get JWT Token: {}", e.getMessage());
            } catch (ExpiredJwtException e) {
                logger.warn("JWT Token has expired: {}", e.getMessage());
            } catch (MalformedJwtException e) {
                logger.warn("JWT Token is malformed: {}", e.getMessage());
            } catch (Exception e) {
                logger.error("Error processing JWT token: {}", e.getMessage());
            }
        }

        // Validate token and set authentication
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                if (jwtTokenUtil.validateToken(jwtToken, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = 
                        new UsernamePasswordAuthenticationToken(userDetails, null, 
                                                               userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    logger.debug("Security context set for user: {}", username);
                } else {
                    logger.warn("JWT token validation failed for user: {}", username);
                }
            } catch (Exception e) {
                logger.error("Error loading user details for: {}", username, e);
            }
        }
        
        chain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String requestPath) {
    return requestPath.startsWith("/api/auth/") ||
           requestPath.equals("/api/support/status") ||
           requestPath.equals("/api/support/faq") ||
           requestPath.startsWith("/error") ||
           requestPath.startsWith("/actuator/") ||
           requestPath.equals("/");
           // REMOVED: requestPath.startsWith("/ws");  <- Delete this line
}
}