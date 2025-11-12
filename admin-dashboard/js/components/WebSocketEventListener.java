package com.medicalapp.medical_app_backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    // Maps sessionId → username
    private static final Map<String, String> connectedUserSessions = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        Principal principal = event.getUser();
        String username = (principal != null) ? principal.getName() : "Unknown";

        connectedUserSessions.put(sessionId, username);

        logger.info("✅ WebSocket connected | Session ID: {} | User: {}", sessionId, username);
        logger.info("   Total connected users: {}", connectedUserSessions.size());
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        String username = connectedUserSessions.remove(sessionId);

        logger.info("❌ WebSocket disconnected | Session ID: {} | User: {}", sessionId, username != null ? username : "Unknown");
        logger.info("   Total connected users: {}", connectedUserSessions.size());
    }

    /** Returns the number of connected sessions */
    public int getConnectedUsersCount() {
        return connectedUserSessions.size();
    }

    /** Returns a set of currently connected usernames */
    public Set<String> getConnectedUsernames() {
        return new HashSet<>(connectedUserSessions.values());
    }
}
