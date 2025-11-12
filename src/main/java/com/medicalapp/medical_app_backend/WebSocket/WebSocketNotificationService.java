package com.medicalapp.medical_app_backend.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ✅ WebSocketNotificationService
 * Handles all real-time push notifications via STOMP over WebSocket.
 */
@Service
public class WebSocketNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketNotificationService.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Send a structured WebSocket payload to a specific destination.
     */
    private void sendToDestination(String destination, Map<String, Object> payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
            logger.info("📨 Sent to [{}]: {}", destination, payload);
        } catch (Exception e) {
            logger.error("❌ Failed to send WebSocket message to [{}]: {}", destination, e.getMessage(), e);
        }
    }

    /**
     * ✅ Send notification to a specific user.
     * Sends to: /user/{userId}/topic/notifications
     */
    public void notifyUser(Long userId, String title, String message, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "user_notification");
        payload.put("type", type);
        payload.put("title", title);
        payload.put("message", message);
        payload.put("timestamp", LocalDateTime.now());

        sendToDestination("/user/" + userId + "/topic/notifications", payload);
    }

    /**
     * ✅ Broadcast notification to all connected users.
     * Sends to: /topic/notifications
     */
    public void notifyAll(String title, String message, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "broadcast");
        payload.put("type", type);
        payload.put("title", title);
        payload.put("message", message);
        payload.put("timestamp", LocalDateTime.now());

        sendToDestination("/topic/notifications", payload);
    }

    /**
     * ✅ Notify a patient that a new test result is available.
     * Sends to: /user/{patientId}/topic/results
     */
    public void notifyNewTestResult(Long patientId, Map<String, Object> resultData) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "TEST_RESULT_READY");
        payload.put("data", resultData);
        payload.put("message", "Your " + resultData.getOrDefault("testName", "medical test") + " results are ready.");
        payload.put("timestamp", LocalDateTime.now());

        sendToDestination("/user/" + patientId + "/topic/results", payload);
    }

    /**
     * ✅ Notify a patient of a new appointment.
     * Sends to: /user/{patientId}/topic/appointments
     */
    public void notifyNewAppointment(Long patientId, Map<String, Object> appointmentData) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "NEW_APPOINTMENT");
        payload.put("data", appointmentData);
        payload.put("message", "You have a new appointment scheduled.");
        payload.put("timestamp", LocalDateTime.now());

        sendToDestination("/user/" + patientId + "/topic/appointments", payload);
    }

    /**
     * ✅ Notify a patient when their appointment status changes.
     * Sends to: /user/{patientId}/topic/appointments
     */
    public void notifyAppointmentStatusChange(Long patientId, String status, String appointmentId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "APPOINTMENT_STATUS_CHANGE");
        payload.put("appointmentId", appointmentId);
        payload.put("status", status);
        payload.put("message", "Your appointment status has been updated to: " + status);
        payload.put("timestamp", LocalDateTime.now());

        sendToDestination("/user/" + patientId + "/topic/appointments", payload);
    }

    /**
     * ✅ Generic helper for custom real-time events.
     */
    public void sendCustomEvent(Long userId, String eventType, Map<String, Object> data) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", eventType);
        payload.put("data", data);
        payload.put("timestamp", LocalDateTime.now());

        sendToDestination("/user/" + userId + "/topic/notifications", payload);
    }
}
