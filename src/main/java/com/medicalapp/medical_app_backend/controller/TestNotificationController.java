package com.medicalapp.medical_app_backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DIAGNOSTIC ENDPOINT
 * Use this to manually test if WebSocket notifications work
 * 
 * To test:
 * POST http://localhost:8080/api/test/push-notification/2
 * 
 * This will send a test notification to user ID 2
 * If your React Native app receives it, the backend is working!
 */
@RestController
@RequestMapping("/test")
public class TestNotificationController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/push-notification/{userId}")
    public ResponseEntity<Map<String, Object>> pushTestNotification(@PathVariable Long userId) {
        
        System.out.println("\n🧪🧪🧪 MANUAL TEST NOTIFICATION 🧪🧪🧪");
        System.out.println("Time: " + LocalDateTime.now());
        System.out.println("Target User ID: " + userId);
        
        // Create test notification payload
        Map<String, Object> notification = new HashMap<>();
        notification.put("event", "TEST_RESULT_READY");
        notification.put("type", "result");
        notification.put("title", "🧪 Manual Test Notification");
        notification.put("message", "This is a test notification sent manually via REST API");
        notification.put("testName", "Manual Test");
        notification.put("resultId", 999);
        notification.put("timestamp", LocalDateTime.now().toString());
        
        Map<String, Object> data = new HashMap<>();
        data.put("testName", "Manual Test");
        data.put("status", "NORMAL");
        data.put("patientId", userId);
        data.put("resultId", 999);
        notification.put("data", data);
        
        // Send to BOTH channels (same as result upload does)
        String topicDest = "/user/" + userId + "/topic/notifications";
        String queueDest = "/user/" + userId + "/queue/messages";
        
        System.out.println("\n📤 Sending notification to:");
        System.out.println("  Topic: " + topicDest);
        System.out.println("  Queue: " + queueDest);
        System.out.println("  Payload: " + notification);
        
        try {
            messagingTemplate.convertAndSend(topicDest, notification);
            System.out.println("✅ Sent to topic");
            
            messagingTemplate.convertAndSend(queueDest, notification);
            System.out.println("✅ Sent to queue");
            
            System.out.println("🧪🧪🧪 TEST NOTIFICATION COMPLETE 🧪🧪🧪\n");
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Test notification sent to user " + userId);
            response.put("sent_to", new String[]{topicDest, queueDest});
            response.put("payload", notification);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("❌ Error sending test notification: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    @GetMapping("/websocket-status")
    public ResponseEntity<Map<String, Object>> getWebSocketStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("stomp_enabled", true);
        status.put("simple_broker_destinations", new String[]{"/topic", "/queue", "/user"});
        status.put("app_prefix", "/app");
        status.put("user_prefix", "/user");
        status.put("endpoint", "/ws");
        status.put("timestamp", LocalDateTime.now().toString());
        
        return ResponseEntity.ok(status);
    }
}