package com.medicalapp.medical_app_backend.websocket;

import com.medicalapp.medical_app_backend.config.JwtTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        try {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                HttpServletRequest httpRequest = servletRequest.getServletRequest();
                URI uri = request.getURI();
                logger.info("🔥 Handshake interceptor triggered for request: {}", uri);

                // Extract token and userId from query parameters
                String query = uri.getQuery();
                String token = null;
                String userId = null;

                if (query != null) {
                    for (String param : query.split("&")) {
                        if (param.startsWith("token=")) {
                            token = param.substring(6);
                        } else if (param.startsWith("userId=")) {
                            userId = param.substring(7);
                        }
                    }
                }

                if (token == null) {
                    logger.warn("❌ Missing token in WebSocket handshake query.");
                    return false;
                }

                // Validate token
                String username = jwtTokenUtil.getUsernameFromToken(token);
                if (username == null) {
                    logger.warn("❌ Invalid JWT token: cannot extract username.");
                    return false;
                }

                logger.info("✅ Handshake authorized for user: {} (userId={})", username, userId);
                attributes.put("username", username);
                attributes.put("userId", userId);

                return true;
            }

        } catch (Exception ex) {
            logger.error("❌ Exception during WebSocket handshake: {}", ex.getMessage(), ex);
        }

        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // Optional: log successful completion
    }
}
