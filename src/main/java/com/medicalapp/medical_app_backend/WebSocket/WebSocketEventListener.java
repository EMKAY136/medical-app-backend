package com.medicalapp.medical_app_backend.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class WebSocketEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    private static final Set<String> connectedUsers = new CopyOnWriteArraySet<>();
    
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId").toString();
        connectedUsers.add(sessionId);
        logger.info("✅ WebSocket client connected: {}", sessionId);
        logger.info("   Total connected users: {}", connectedUsers.size());
    }
    
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId").toString();
        connectedUsers.remove(sessionId);
        logger.info("❌ WebSocket client disconnected: {}", sessionId);
        logger.info("   Total connected users: {}", connectedUsers.size());
    }
    
    public int getConnectedUsersCount() {
        return connectedUsers.size();
    }
}