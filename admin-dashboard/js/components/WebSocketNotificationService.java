package com.medicalapp.medical_app_backend.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

@Service
public class WebSocketNotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketNotificationService.class);
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    /**
     * Send notification to specific user via WebSocket
     * FIXED: Now sends to correct destination /user/{userId}/topic/notifications
     */
    public void notifyUser(Long userId, String title, String message, String type) {
        try {
            logger.info("\n========== SENDING WEBSOCKET NOTIFICATION ==========");
            logger.info("🎯 Target User ID: {}", userId);
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("event", "NOTIFICATION");
            notification.put("title", title);
            notification.put("message", message);
            notification.put("type", type);
            notification.put("timestamp", LocalDateTime.now().toString());
            
            // FIXED: Use convertAndSendToUser with correct pattern
            String destination = "/user/" + userId + "/topic/notifications";
            logger.info("📍 Destination: {}", destination);
            logger.info("📦 Payload: {}", notification);
            
            messagingTemplate.convertAndSendToUser(
                userId.toString(),              // User identifier
                "/topic/notifications",         // Destination suffix
                notification
            );
            
            logger.info("✅ WebSocket notification sent successfully to user {}", userId);
            logger.info("====================================================\n");
        } catch (Exception e) {
            logger.error("❌ Error sending WebSocket notification to user {}: {}", userId, e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Broadcast notification to all connected users
     */
    public void notifyAll(String title, String message, String type) {
        try {
            logger.info("\n📢 Broadcasting notification to all users");
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("event", "BROADCAST");
            notification.put("title", title);
            notification.put("message", message);
            notification.put("type", type);
            notification.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSend(
                "/topic/notifications", 
                notification
            );
            
            logger.info("✅ WebSocket broadcast sent to all users\n");
        } catch (Exception e) {
            logger.error("❌ Error sending broadcast notification: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Notify about new test result - THIS IS THE KEY METHOD FOR YOUR USE CASE
     * Called when admin uploads a medical result
     */
    public void notifyNewTestResult(Long patientId, String testName, Long resultId) {
        try {
            logger.info("\n🔔🔔🔔 SENDING TEST RESULT NOTIFICATION 🔔🔔🔔");
            logger.info("Patient ID: {}", patientId);
            logger.info("Test Name: {}", testName);
            logger.info("Result ID: {}", resultId);
            
            Map<String, Object> notification = new HashMap<>();
            
            // CRITICAL: Use exact event name that React Native checks for
            notification.put("event", "TEST_RESULT_READY");
            notification.put("title", "Test Result Available");
            notification.put("message", testName + " results are ready to view");
            notification.put("type", "results");
            notification.put("testName", testName);
            notification.put("resultId", resultId);
            notification.put("timestamp", LocalDateTime.now().toString());
            
            String topicDestination = "/user/" + patientId + "/topic/notifications";
            String queueDestination = "/user/" + patientId + "/queue/messages";
            
            logger.info("📍 Topic Destination: {}", topicDestination);
            logger.info("📍 Queue Destination: {}", queueDestination);
            logger.info("📦 Notification Payload: {}", notification);
            
            // Send to TOPIC channel (primary)
            messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/topic/notifications",
                notification
            );
            logger.info("✅ Sent to TOPIC channel");
            
            // ALSO send to QUEUE channel (backup/redundancy)
            messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/queue/messages",
                notification
            );
            logger.info("✅ Sent to QUEUE channel");
            
            logger.info("🎉 Test result notification successfully sent to patient: {}", patientId);
            logger.info("=========================================================\n");
            
        } catch (Exception e) {
            logger.error("\n❌❌❌ ERROR SENDING TEST RESULT NOTIFICATION ❌❌❌");
            logger.error("Patient ID: {}", patientId);
            logger.error("Error: {}", e.getMessage());
            e.printStackTrace();
            logger.error("=========================================================\n");
        }
    }
    
    /**
     * Notify about new appointment
     */
    public void notifyNewAppointment(Long patientId, String testType, String scheduledDate, String scheduledTime) {
        try {
            logger.info("\n📅 Sending APPOINTMENT notification to patient: {}", patientId);
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("event", "APPOINTMENT_BOOKED");
            notification.put("title", "Appointment Confirmed");
            notification.put("message", "Your " + testType + " has been scheduled for " + scheduledDate + " at " + scheduledTime);
            notification.put("type", "appointment");
            notification.put("testType", testType);
            notification.put("scheduledDate", scheduledDate);
            notification.put("scheduledTime", scheduledTime);
            notification.put("timestamp", LocalDateTime.now().toString());
            
            // Send to both channels
            messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/topic/notifications",
                notification
            );
            
            messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/queue/messages",
                notification
            );
            
            logger.info("✅ Appointment notification sent to patient: {}\n", patientId);
        } catch (Exception e) {
            logger.error("❌ Error sending appointment notification: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Notify about appointment status change
     */
    public void notifyAppointmentStatusChange(Long patientId, String status, Long appointmentId, String reason) {
        try {
            logger.info("\n🔄 Sending STATUS CHANGE notification to patient: {}", patientId);
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("event", "APPOINTMENT_STATUS_CHANGE");
            notification.put("title", "Appointment Status Updated");
            notification.put("message", "Your appointment status has been changed to: " + status);
            notification.put("type", "appointment");
            notification.put("status", status);
            notification.put("appointmentId", appointmentId);
            notification.put("reason", reason);
            notification.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/topic/notifications",
                notification
            );
            
            messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/queue/messages",
                notification
            );
            
            logger.info("✅ Status change notification sent to patient: {}\n", patientId);
        } catch (Exception e) {
            logger.error("❌ Error sending status change notification: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Send reminder notification
     */
    public void sendReminder(Long patientId, String title, String message) {
        try {
            logger.info("\n⏰ Sending REMINDER to patient: {}", patientId);
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("event", "REMINDER");
            notification.put("title", title);
            notification.put("message", message);
            notification.put("type", "reminder");
            notification.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/topic/notifications",
                notification
            );
            
            messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/queue/messages",
                notification
            );
            
            logger.info("✅ Reminder sent to patient: {}\n", patientId);
        } catch (Exception e) {
            logger.error("❌ Error sending reminder: {}", e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Send urgent/alert notification
     */
    public void sendAlert(Long patientId, String title, String message) {
        try {
            logger.info("\n🚨 Sending ALERT to patient: {}", patientId);
            
            Map<String, Object> notification = new HashMap<>();
            notification.put("event", "ALERT");
            notification.put("title", title);
            notification.put("message", message);
            notification.put("type", "alert");
            notification.put("priority", "high");
            notification.put("timestamp", LocalDateTime.now().toString());
            
            messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/topic/notifications",
                notification
            );
            
            messagingTemplate.convertAndSendToUser(
                patientId.toString(),
                "/queue/messages",
                notification
            );
            
            logger.info("✅ Alert sent to patient: {}\n", patientId);
        } catch (Exception e) {
            logger.error("❌ Error sending alert: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}