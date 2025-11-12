package com.medicalapp.medical_app_backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    /**
     * ✅ Handle ping messages from client for health checks.
     * Clients send to: /app/ping
     * Server responds to: /topic/pong
     */
    @MessageMapping("/ping")
    @SendTo("/topic/pong")
    public Map<String, Object> handlePing(Map<String, Object> message) {
        logger.debug("📡 Ping received from client");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "pong");
        response.put("timestamp", System.currentTimeMillis());
        response.put("message", "WebSocket connection is alive");
        return response;
    }

    /**
     * ✅ Handle subscription confirmation for user-specific topics.
     * Clients send to: /app/subscribe/{userId}
     * Server responds to: /topic/user/{userId}
     */
    @MessageMapping("/subscribe/{userId}")
    @SendTo("/topic/user/{userId}")
    public Map<String, Object> handleSubscribe(
            @DestinationVariable String userId,
            SimpMessageHeaderAccessor headerAccessor,
            Principal principal
    ) {
        String sessionId = headerAccessor.getSessionId();
        String username = principal != null ? principal.getName() : "Anonymous";

        logger.info("🔔 Client subscribed to notifications | User ID: {} | Session: {} | Principal: {}", userId, sessionId, username);

        Map<String, Object> response = new HashMap<>();
        response.put("event", "subscribed");
        response.put("userId", userId);
        response.put("username", username);
        response.put("sessionId", sessionId);
        response.put("message", "✅ Successfully subscribed to notifications");
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }

    /**
     * Optional: Handle echo/test messages for debugging.
     * Clients can send JSON to /app/echo and receive the same payload back.
     */
    @MessageMapping("/echo")
    @SendTo("/topic/echo")
    public Map<String, Object> handleEcho(Map<String, Object> message) {
        logger.debug("🔁 Echo message received: {}", message);
        Map<String, Object> response = new HashMap<>(message);
        response.put("echo", true);
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
