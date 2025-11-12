package com.medicalapp.medical_app_backend.config;

import com.medicalapp.medical_app_backend.config.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        System.out.println("\n========== CONFIGURING MESSAGE BROKER ==========");
        
        config.enableSimpleBroker("/topic", "/queue");
        System.out.println("✅ Simple broker enabled for: /topic, /queue");
        
        config.setApplicationDestinationPrefixes("/app");
        System.out.println("✅ Application destination prefix set: /app");
        
        config.setUserDestinationPrefix("/user");
        System.out.println("✅ User destination prefix set: /user");
        
        System.out.println("========== MESSAGE BROKER CONFIGURED ==========\n");
    }

    // Create reusable handshake interceptor
    private HandshakeInterceptor createAuthInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler wsHandler,
                    Map<String, Object> attributes) throws Exception {
                
                System.out.println("\n========== WEBSOCKET HANDSHAKE ==========");
                
                if (request instanceof ServletServerHttpRequest) {
                    ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
                    
                    // Extract token and userId from query parameters
                    String token = servletRequest.getServletRequest().getParameter("token");
                    String userId = servletRequest.getServletRequest().getParameter("userId");
                    
                    System.out.println("🔑 Token from query: " + (token != null ? "Present" : "Missing"));
                    System.out.println("👤 UserId from query: " + userId);
                    
                    if (token != null) {
                        try {
                            // Validate token
                            String username = jwtTokenUtil.getUsernameFromToken(token);
                            System.out.println("✅ Token validated for user: " + username);
                            
                            // Store in session attributes for later use
                            attributes.put("token", token);
                            attributes.put("username", username);
                            
                            if (userId != null) {
                                attributes.put("userId", userId);
                                System.out.println("✅ UserId stored: " + userId);
                            }
                            
                            System.out.println("========== HANDSHAKE SUCCESSFUL ==========\n");
                            return true;
                            
                        } catch (Exception e) {
                            System.err.println("❌ Token validation failed: " + e.getMessage());
                            e.printStackTrace();
                            return false;
                        }
                    } else {
                        System.err.println("❌ No token provided in WebSocket URL");
                        return false;
                    }
                }
                
                System.err.println("❌ Invalid request type");
                return false;
            }
            
            @Override
            public void afterHandshake(
                    ServerHttpRequest request,
                    ServerHttpResponse response,
                    WebSocketHandler wsHandler,
                    Exception exception) {
                
                if (exception != null) {
                    System.err.println("❌ Handshake failed: " + exception.getMessage());
                } else {
                    System.out.println("✅ WebSocket handshake completed successfully");
                }
            }
        };
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        System.out.println("\n========== REGISTERING STOMP ENDPOINTS ==========");
        
        // Register native WebSocket endpoint
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(createAuthInterceptor());
        
        System.out.println("✅ Native WebSocket endpoint registered: /ws");
        
        // CRITICAL: Register SockJS fallback endpoint
        // This is necessary for proper STOMP protocol handling
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(createAuthInterceptor())
                .withSockJS();  // Enable SockJS transport
        
        System.out.println("✅ SockJS fallback endpoint registered: /ws");
        System.out.println("====================================================\n");
    }
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                
                // ===== ENHANCED LOGGING - LOG EVERY INCOMING MESSAGE =====
                System.out.println("\n🔵🔵🔵 INCOMING MESSAGE 🔵🔵🔵");
                System.out.println("Timestamp: " + new Date());
                System.out.println("Message Type: " + (message != null ? message.getClass().getSimpleName() : "null"));
                
                if (message != null && message.getPayload() != null) {
                    System.out.println("Payload Type: " + message.getPayload().getClass().getSimpleName());
                    String payload = String.valueOf(message.getPayload());
                    System.out.println("Payload (first 200 chars): " + 
                        payload.substring(0, Math.min(200, payload.length())));
                }
                
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                
                if (accessor != null) {
                    StompCommand command = accessor.getCommand();
                    
                    System.out.println("📨 STOMP Command: " + command);
                    System.out.println("Session ID: " + accessor.getSessionId());
                    System.out.println("Subscription ID: " + accessor.getSubscriptionId());
                    System.out.println("Destination: " + accessor.getDestination());
                    System.out.println("Native Headers: " + accessor.toNativeHeaderMap());
                    
                    // ===== HANDLE CONNECT COMMAND =====
                    if (StompCommand.CONNECT.equals(command)) {
                        System.out.println("\n========== STOMP CONNECT FRAME RECEIVED ==========");
                        
                        // Get username and userId from session attributes (set during handshake)
                        String username = null;
                        String userId = null;
                        
                        if (accessor.getSessionAttributes() != null) {
                            username = (String) accessor.getSessionAttributes().get("username");
                            userId = (String) accessor.getSessionAttributes().get("userId");
                            
                            System.out.println("📦 Session attributes: " + accessor.getSessionAttributes().keySet());
                            System.out.println("👤 Username from session: " + username);
                            System.out.println("🆔 UserId from session: " + userId);
                        } else {
                            System.err.println("⚠️ No session attributes found!");
                        }
                        
                        if (username != null) {
                            // Create authentication and set user principal
                            UsernamePasswordAuthenticationToken authentication = 
                                new UsernamePasswordAuthenticationToken(
                                    username, 
                                    null, 
                                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
                                );
                            
                            accessor.setUser(authentication);
                            System.out.println("✅ User principal set: " + username);
                            System.out.println("🔑 Session ID: " + accessor.getSessionId());
                            System.out.println("========== STOMP CONNECTION AUTHENTICATED ==========\n");
                            
                        } else {
                            System.err.println("❌ No username in session attributes - REJECTING CONNECTION");
                            System.err.println("💡 This usually means the WebSocket session wasn't properly initialized");
                            System.out.println("==========================================\n");
                            return null; // Reject connection
                        }
                    }
                    
                    // ===== HANDLE SUBSCRIBE COMMAND =====
                    if (StompCommand.SUBSCRIBE.equals(command)) {
                        String destination = accessor.getDestination();
                        String username = accessor.getUser() != null ? accessor.getUser().getName() : "unknown";
                        System.out.println("\n📬 ========== SUBSCRIBE ==========");
                        System.out.println("User: " + username);
                        System.out.println("Destination: " + destination);
                        System.out.println("Subscription ID: " + accessor.getSubscriptionId());
                        System.out.println("==================================\n");
                    }
                    
                    // ===== HANDLE SEND COMMAND =====
                    if (StompCommand.SEND.equals(command)) {
                        System.out.println("\n📤 ========== SEND ==========");
                        System.out.println("Destination: " + accessor.getDestination());
                        System.out.println("=============================\n");
                    }
                    
                    // ===== HANDLE DISCONNECT COMMAND =====
                    if (StompCommand.DISCONNECT.equals(command)) {
                        String username = accessor.getUser() != null ? accessor.getUser().getName() : "unknown";
                        System.out.println("\n👋 ========== DISCONNECT ==========");
                        System.out.println("User: " + username);
                        System.out.println("===================================\n");
                    }
                    
                } else {
                    System.err.println("\n⚠️⚠️⚠️ NO STOMP HEADER ACCESSOR! ⚠️⚠️⚠️");
                    System.err.println("This means the message is not a valid STOMP message");
                    System.err.println("The STOMP protocol layer is not parsing the WebSocket frames");
                    if (message != null) {
                        System.err.println("Raw message class: " + message.getClass().getName());
                        System.err.println("Headers: " + message.getHeaders());
                    }
                    System.err.println("==========================================\n");
                }
                
                System.out.println("==========================================\n");
                
                return message;
            }
        });
        
        System.out.println("✅ Client inbound channel interceptor configured with FULL LOGGING");
    }
}