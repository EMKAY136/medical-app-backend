package com.medicalapp.medical_app_backend.service;
import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.entity.SupportTicket;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import com.medicalapp.medical_app_backend.entity.Notification;
import com.medicalapp.medical_app_backend.entity.SecuritySettings;
import com.medicalapp.medical_app_backend.repository.NotificationRepository;
import com.medicalapp.medical_app_backend.repository.SecuritySettingsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.http.HttpHeaders;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

@Service
public class NotificationService {

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    @Autowired(required = false)
    private JavaMailSender mailSender;
    @Autowired
private RestTemplate restTemplate;

@Autowired
private SimpMessagingTemplate messagingTemplate;  // ← ADD THIS LINE

// Add this method to your NotificationService.java

/**
 * CRITICAL: Save notification to database AND broadcast via WebSocket
 * This is called when admin uploads a medical result
 */
@Transactional
public void createAndSendResultNotification(Long patientId, Long resultId, String testName, String status) {
    try {
        logger.info("\n");
        logger.info("===========================================");
        logger.info("🔔 CREATING & SENDING RESULT NOTIFICATION");
        logger.info("===========================================");
        logger.info("Patient ID: {}", patientId);
        logger.info("Result ID: {}", resultId);
        logger.info("Test Name: {}", testName);
        logger.info("Status: {}", status);
        
        // 1. GET THE USER
        User patient = userRepository.findById(patientId)
            .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + patientId));
        
        logger.info("✓ Found patient: {} {}", patient.getFirstName(), patient.getLastName());
        
        // 2. CREATE NOTIFICATION ENTITY
        Notification notification = new Notification();
        notification.setUser(patient);
        notification.setType(Notification.TYPE_RESULT); // Use the constant
        notification.setTitle("New Test Result Available");
        notification.setMessage("Your " + testName + " results are ready to view");
        notification.setReferenceType("MEDICAL_RESULT");
        notification.setReferenceId(resultId);
        notification.setPriority(Notification.Priority.HIGH);
        notification.setRead(false);
        notification.setSent(true);
        notification.setSentAt(LocalDateTime.now());
        notification.setDeliveryStatus(Notification.DeliveryStatus.DELIVERED);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setUpdatedAt(LocalDateTime.now());
        
        // Add metadata
        try {
            String metadataJson = objectMapper.writeValueAsString(Map.of(
                "testName", testName,
                "status", status,
                "resultId", resultId,
                "patientId", patientId
            ));
            notification.setMetadata(metadataJson);
            logger.info("✓ Metadata added to notification");
        } catch (Exception e) {
            logger.warn("Could not set metadata: {}", e.getMessage());
        }
        
        // 3. SAVE TO DATABASE FIRST - THIS IS CRITICAL!
        notification = notificationRepository.save(notification);
        logger.info("✅ Notification saved to database with ID: {}", notification.getId());
        
        // 4. PREPARE WEBSOCKET PAYLOAD (matching your frontend expectations)
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", notification.getId());
        payload.put("type", notification.getType());
        payload.put("title", notification.getTitle());
        payload.put("message", notification.getMessage());
        payload.put("priority", notification.getPriority().name());
        payload.put("isRead", notification.isRead());
        payload.put("read", notification.isRead()); // Some frontends use "read" instead of "isRead"
        payload.put("createdAt", notification.getCreatedAt().toString());
        payload.put("referenceType", notification.getReferenceType());
        payload.put("referenceId", notification.getReferenceId());
        
        // Additional data for the frontend
        Map<String, Object> data = new HashMap<>();
        data.put("testName", testName);
        data.put("status", status);
        data.put("patientId", patientId);
        data.put("resultId", resultId);
        payload.put("data", data);
        
        logger.info("Notification Payload:");
        logger.info("  - id: {}", notification.getId());
        logger.info("  - title: {}", notification.getTitle());
        logger.info("  - message: {}", notification.getMessage());
        logger.info("  - type: {}", notification.getType());
        logger.info("  - priority: {}", notification.getPriority().name());
        logger.info("  - referenceId: {}", resultId);
        
        // 5. BROADCAST VIA WEBSOCKET
        if (messagingTemplate != null) {
            try {
                String userId = String.valueOf(patientId);
                
                logger.info("📡 Broadcasting WebSocket notification to user: {}", userId);
                
                // Send to primary notification queue
                messagingTemplate.convertAndSendToUser(
                    userId, 
                    "/queue/notifications", 
                    payload
                );
                logger.info("✅ Sent to /user/{}/queue/notifications", userId);
                
                // Also send to topic (backup channel)
                messagingTemplate.convertAndSendToUser(
                    userId, 
                    "/topic/notifications", 
                    payload
                );
                logger.info("✅ Sent to /user/{}/topic/notifications", userId);
                
            } catch (Exception e) {
                logger.error("❌ Failed to broadcast WebSocket: {}", e.getMessage(), e);
                // Don't fail the whole operation if WebSocket fails
            }
        } else {
            logger.error("❌ SimpMessagingTemplate is NULL! WebSocket not configured!");
        }
        
        logger.info("===========================================");
        logger.info("🎉 NOTIFICATION CREATED & SENT SUCCESSFULLY");
        logger.info("===========================================\n");
        
    } catch (Exception e) {
        logger.error("\n");
        logger.error("❌❌❌ CRITICAL ERROR creating notification ❌❌❌");
        logger.error("Exception: {}", e.getClass().getName());
        logger.error("Message: {}", e.getMessage());
        logger.error("Stack Trace:", e);
        logger.error("❌❌❌ END ERROR ❌❌❌\n");
        throw new RuntimeException("Failed to create notification: " + e.getMessage());
    }
}

@Autowired
private NotificationRepository notificationRepository;

public Map<String, Object> getUserNotifications(UserDetails userDetails) {
    try {
        logger.info("=== GET USER NOTIFICATIONS ===");
        logger.info("User: {}", userDetails.getUsername());
        
        Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            logger.warn("User not found: {}", userDetails.getUsername());
            return Map.of("success", false, "message", "User not found");
        }

        User currentUser = userOpt.get();
        // 🔥 UPDATED: Order by creation date descending (newest first)
        List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(currentUser);
        
        logger.info("✅ Found {} notifications for user {}", notifications.size(), currentUser.getId());
        
        return Map.of("success", true, "notifications", notifications);
    } catch (Exception e) {
        logger.error("❌ Error fetching notifications: {}", e.getMessage());
        return Map.of("success", false, "message", e.getMessage());
    }
}

// ==================== SUPPORT SYSTEM NOTIFICATIONS ====================

/**
 * Notify support team about new support ticket
 * Called from SupportService when ticket is created
     */
    public void notifyNewSupportTicket(SupportTicket ticket) {
        try {
            String title = "New Medical Support Ticket";
            String message = String.format(
                "Ticket #%s from %s\nSubject: %s\nPriority: %s\nCategory: %s", 
                ticket.getTicketNumber(), 
                ticket.getName(),
                ticket.getSubject(),
                ticket.getPriority().getDisplayName(),
                ticket.getCategory() != null ? ticket.getCategory() : "General"
            );
            
            // Log for support team monitoring
            System.out.println(String.format(
                "[%s] SUPPORT TEAM ALERT - New Ticket: %s from %s (%s)", 
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                ticket.getTicketNumber(), 
                ticket.getName(),
                ticket.getEmail()
            ));
            
            // In production, you would:
            // 1. Send email to support team distribution list
            // 2. Send Slack/Teams notification to support channel  
            // 3. Create dashboard alert for support agents
            // 4. Send SMS for high priority tickets
            
            // Mock email notification to support team
            sendSupportTeamEmail(ticket);
            
            // Mock Slack notification
            sendSlackNotification(ticket);
            
        } catch (Exception e) {
            System.err.println("Error notifying support team about new ticket: " + e.getMessage());
        }
    }

    /**
     * Notify user when support agent replies to their chat/ticket
     * Called from SupportService when agent sends reply
     */
    public void notifyUserOfAgentReply(User user, String message, String agentName) {
        try {
            // Check if user has support notifications enabled
            // Using appointmentReminders as proxy for support notifications
            if (!isNotificationTypeEnabled("appointmentReminders", user)) {
                System.out.println("Support notifications disabled for user: " + user.getUsername());
                return;
            }
            
            // Check quiet hours (but allow override for urgent medical support)
            if (!shouldSendNotification("support", user)) {
                System.out.println("Support reply blocked by quiet hours for user: " + user.getUsername());
                return;
            }
            
            String title = "Medical Support Reply";
            String notificationMessage = String.format(
                "%s from our medical support team has replied to your message", 
                agentName
            );
            
            // Send push notification
            boolean pushSent = sendPushNotification(user, title, notificationMessage, "support_reply");
            
            // Send email notification for important support replies
            boolean emailSent = sendUserEmail(user, title, String.format(
                "Hello %s,\n\n%s from our medical support team has replied to your inquiry:\n\n\"%s\"\n\nPlease check the app for the full conversation.\n\nBest regards,\nQualitest Medical Support Team",
                user.getFirstName(),
                agentName,
                truncateMessage(message, 200)
            ));
            
            System.out.println(String.format(
                "[%s] USER NOTIFICATION - Agent reply sent to %s (Push: %s, Email: %s)",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                user.getEmail(),
                pushSent ? "SUCCESS" : "FAILED",
                emailSent ? "SUCCESS" : "FAILED"
            ));
            
        } catch (Exception e) {
            System.err.println("Error notifying user of agent reply: " + e.getMessage());
        }
    }

    // ==================== EXISTING NOTIFICATION METHODS ====================

    // Default notification settings
    private Map<String, Object> getDefaultNotificationSettings() {
        Map<String, Object> defaults = new HashMap<>();
        
        // Medical & Health
        defaults.put("testResults", true);
        defaults.put("appointmentReminders", true);
        defaults.put("medicationAlerts", true);
        defaults.put("healthTips", false);
        defaults.put("supportNotifications", true); // Added for support system
        
        // Security & Account
        defaults.put("loginAlerts", true);
        defaults.put("securityUpdates", true);
        defaults.put("accountChanges", true);
        
        // App Updates
        defaults.put("appUpdates", true);
        defaults.put("featureAnnouncements", false);
        defaults.put("maintenanceNotices", true);
        
        // Marketing
        defaults.put("promotions", false);
        defaults.put("newsletters", false);
        defaults.put("surveys", false);
        
        return defaults;
    }

    public boolean sendPushNotification(User recipient, String title, String message, String type) {
        try {
            logger.info("=== SENDING PUSH NOTIFICATION ===");
            logger.info("User: {}", recipient.getUsername());
            logger.info("Title: {}", title);
            logger.info("Message: {}", message);
            logger.info("Type: {}", type);
            
            if (recipient.getDeviceToken() == null || recipient.getDeviceToken().isEmpty()) {
                logger.warn("No device token registered for user: {}", recipient.getUsername());
                return false;
            }
            
            Map<String, Object> notification = buildExpoNotificationPayload(
                recipient.getDeviceToken(), 
                title, 
                message, 
                type
            );
            
            boolean sent = sendToExpoPushService(notification);
            
            if (sent) {
                logger.info("✅ Push notification sent successfully to {}", recipient.getUsername());
                logNotificationToDatabase(recipient, title, message, type, "SENT");
                return true;
            } else {
                logger.warn("⚠️ Failed to send push notification to {}", recipient.getUsername());
                logNotificationToDatabase(recipient, title, message, type, "FAILED");
                return false;
            }
            
        } catch (Exception e) {
            logger.error("❌ Error sending push notification: {}", e.getMessage());
            if (recipient != null) {
                logNotificationToDatabase(recipient, title, message, type, "ERROR");
            }
            return false;
        }
    }

    
public void sendSMS(String phoneNumber, String message) {
    try {
        logger.info("=== SENDING SMS ===");
        logger.info("To: {}", phoneNumber);
        logger.info("Message: {}", message);
        
        // TODO: Implement actual SMS sending using Twilio or similar service
        // For now, just log the SMS (simulated sending)
        
        // Example Twilio integration (uncomment when ready):
        /*
        Twilio.init(ACCOUNT_SID, AUTH_TOKEN);
        Message twilioMessage = Message.creator(
            new PhoneNumber(phoneNumber),
            new PhoneNumber(FROM_PHONE_NUMBER),
            message
        ).create();
        
        logger.info("SMS sent successfully. SID: {}", twilioMessage.getSid());
        */
        
        logger.info("SMS sent successfully (simulated)");
        
    } catch (Exception e) {
        logger.error("Failed to send SMS to {}: {}", phoneNumber, e.getMessage());
        throw new RuntimeException("SMS sending failed: " + e.getMessage());
    }
}

public void sendEmail(String toEmail, String subject, String content) {
        try {
            logger.info("=== SENDING REAL EMAIL ===");
            logger.info("To: {}", toEmail);
            logger.info("Subject: {}", subject);
            
            if (mailSender == null) {
                logger.error("JavaMailSender is not configured!");
                return;
            }
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("qualitestmedical@gmail.com");
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(content);
            
            mailSender.send(message);
            
            logger.info("EMAIL SENT SUCCESSFULLY to: {}", toEmail);
            
        } catch (Exception e) {
            logger.error("FAILED to send email to {}: {}", toEmail, e.getMessage());
        }
}


public void sendPasswordChangeNotification(String email) {
    try {
        logger.info("=== SENDING PASSWORD CHANGE NOTIFICATION ===");
        logger.info("To: {}", email);
        
        String subject = "Password Changed - Medical App";
        String content = "Your password has been successfully changed. " +
                        "If you didn't make this change, please contact support immediately.\n\n" +
                        "Time: " + LocalDateTime.now() + "\n" +
                        "Medical App Security Team";
        
        sendEmail(email, subject, content);
        
        logger.info("Password change notification sent successfully");
        
    } catch (Exception e) {
        logger.error("Failed to send password change notification to {}: {}", email, e.getMessage());
        // Don't throw exception - this is just a notification
    }
}

    // Default schedule settings
    private Map<String, Object> getDefaultScheduleSettings() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("quietHoursEnabled", true);
        defaults.put("quietStart", "22:00");
        defaults.put("quietEnd", "07:00");
        defaults.put("weekendQuietHours", true);
        defaults.put("emergencyOverride", true);
        return defaults;
    }

    // Get user's notification settings
    public Map<String, Object> getNotificationSettings(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            Map<String, Object> notificationSettings = getDefaultNotificationSettings();
            Map<String, Object> scheduleSettings = getDefaultScheduleSettings();
            
            // Parse stored settings if they exist
            if (user.getNotificationSettings() != null && !user.getNotificationSettings().isEmpty()) {
                try {
                    Map<String, Object> storedNotificationSettings = objectMapper.readValue(
                        user.getNotificationSettings(), Map.class);
                    notificationSettings.putAll(storedNotificationSettings);
                } catch (Exception e) {
                    // Use defaults if parsing fails
                    System.err.println("Error parsing notification settings: " + e.getMessage());
                }
            }
            
            if (user.getScheduleSettings() != null && !user.getScheduleSettings().isEmpty()) {
                try {
                    Map<String, Object> storedScheduleSettings = objectMapper.readValue(
                        user.getScheduleSettings(), Map.class);
                    scheduleSettings.putAll(storedScheduleSettings);
                } catch (Exception e) {
                    // Use defaults if parsing fails
                    System.err.println("Error parsing schedule settings: " + e.getMessage());
                }
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("notificationSettings", notificationSettings);
            data.put("scheduleSettings", scheduleSettings);
            
            response.put("success", true);
            response.put("data", data);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching notification settings: " + e.getMessage());
        }
        
        return response;
    }

    // Update user's notification settings
    public Map<String, Object> updateNotificationSettings(Map<String, Object> settingsData, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            // Extract notification and schedule settings
            Map<String, Object> notificationSettings = (Map<String, Object>) settingsData.get("notificationSettings");
            Map<String, Object> scheduleSettings = (Map<String, Object>) settingsData.get("scheduleSettings");
            
            // Update notification settings if provided
            if (notificationSettings != null) {
                try {
                    String notificationJson = objectMapper.writeValueAsString(notificationSettings);
                    user.setNotificationSettings(notificationJson);
                } catch (Exception e) {
                    response.put("success", false);
                    response.put("message", "Error saving notification settings");
                    return response;
                }
            }
            
            // Update schedule settings if provided
            if (scheduleSettings != null) {
                try {
                    String scheduleJson = objectMapper.writeValueAsString(scheduleSettings);
                    user.setScheduleSettings(scheduleJson);
                } catch (Exception e) {
                    response.put("success", false);
                    response.put("message", "Error saving schedule settings");
                    return response;
                }
            }
            
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            
            response.put("success", true);
            response.put("message", "Notification settings updated successfully");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error updating notification settings: " + e.getMessage());
        }
        
        return response;
    }

    private boolean sendToExpoPushService(Map<String, Object> notification) {
    try {
        logger.info("Sending to Expo Push Service...");
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(notification, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                "https://exp.host/--/api/v2/push/send",
                entity,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("✅ Expo API response: {}", response.getBody());
                return true;
            } else {
                logger.warn("Expo API returned status: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.error("Expo API call failed: {}", e.getMessage());
            return false;
        }
        
    } catch (Exception e) {
        logger.error("Error communicating with Expo Push Service: {}", e.getMessage());
        return false;
    }
}

    // Send test notification
    public Map<String, Object> sendTestNotification(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            // Check if test notifications should be sent during quiet hours
            if (!shouldSendNotification("test", user)) {
                response.put("success", false);
                response.put("message", "Test notification blocked by quiet hours settings");
                return response;
            }
            
            // In a real implementation, you would send an actual push notification here
            // For now, we'll simulate it
            boolean notificationSent = sendPushNotification(user, "Test Notification", 
                "This is a test notification from Qualitest Medical", "test");
            
            if (notificationSent) {
                response.put("success", true);
                response.put("message", "Test notification sent successfully");
            } else {
                response.put("success", false);
                response.put("message", "Failed to send test notification");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending test notification: " + e.getMessage());
        }
        
        return response;
    }

    // Reset settings to defaults
    public Map<String, Object> resetToDefaults(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            // Reset to default settings
            Map<String, Object> defaultNotificationSettings = getDefaultNotificationSettings();
            Map<String, Object> defaultScheduleSettings = getDefaultScheduleSettings();
            
            try {
                String notificationJson = objectMapper.writeValueAsString(defaultNotificationSettings);
                String scheduleJson = objectMapper.writeValueAsString(defaultScheduleSettings);
                
                user.setNotificationSettings(notificationJson);
                user.setScheduleSettings(scheduleJson);
                user.setUpdatedAt(LocalDateTime.now());
                
                userRepository.save(user);
                
                Map<String, Object> data = new HashMap<>();
                data.put("notificationSettings", defaultNotificationSettings);
                data.put("scheduleSettings", defaultScheduleSettings);
                
                response.put("success", true);
                response.put("message", "Settings reset to defaults successfully");
                response.put("data", data);
                
            } catch (Exception e) {
                response.put("success", false);
                response.put("message", "Error saving default settings");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error resetting settings: " + e.getMessage());
        }
        
        return response;
    }

    // Send appointment reminder notification
    public Map<String, Object> sendAppointmentReminder(Long appointmentId, String reminderType, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            // Check if appointment reminders are enabled
            if (!isNotificationTypeEnabled("appointmentReminders", user)) {
                response.put("success", false);
                response.put("message", "Appointment reminders are disabled");
                return response;
            }
            
            // Create notification message based on reminder type
            String title = "Appointment Reminder";
            String message = "";
            
            switch (reminderType) {
                case "24h":
                    message = "You have an appointment tomorrow. Don't forget to prepare any required documents.";
                    break;
                case "1h":
                    message = "Your appointment is in 1 hour. Please arrive 15 minutes early.";
                    break;
                case "15m":
                    message = "Your appointment is in 15 minutes. Please check in when you arrive.";
                    break;
                default:
                    message = "You have an upcoming appointment.";
            }
            
            boolean sent = sendPushNotification(user, title, message, "appointment");
            
            response.put("success", sent);
            response.put("message", sent ? "Appointment reminder sent" : "Failed to send reminder");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending appointment reminder: " + e.getMessage());
        }
        
        return response;
    }

    // Send medical result notification
    public Map<String, Object> sendResultNotification(Long resultId, String testName, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            // Check if test result notifications are enabled
            if (!isNotificationTypeEnabled("testResults", user)) {
                response.put("success", false);
                response.put("message", "Test result notifications are disabled");
                return response;
            }
            
            String title = "Test Results Available";
            String message = String.format("Your %s results are now available in the app.", testName);
            
            boolean sent = sendPushNotification(user, title, message, "result");
            
            response.put("success", sent);
            response.put("message", sent ? "Result notification sent" : "Failed to send notification");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending result notification: " + e.getMessage());
        }
        
        return response;
    }

    // Send security alert notification
    public Map<String, Object> sendSecurityAlert(String alertType, String message, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            // Security alerts are critical and should bypass most settings
            String title = "Security Alert";
            
            // Determine if this should bypass quiet hours
            boolean isCritical = alertType.equals("login") || alertType.equals("password_change") || 
                               alertType.equals("account_locked");
            
            if (!isCritical && !shouldSendNotification("security", user)) {
                response.put("success", false);
                response.put("message", "Security alert blocked by quiet hours");
                return response;
            }
            
            boolean sent = sendPushNotification(user, title, message, "security");
            
            response.put("success", sent);
            response.put("message", sent ? "Security alert sent" : "Failed to send alert");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending security alert: " + e.getMessage());
        }
        
        return response;
    }

    // Get notification history (placeholder - you'd implement with a Notification entity)
    public Map<String, Object> getNotificationHistory(UserDetails userDetails, int page, int size) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // In a real implementation, you would query a Notification entity
            // For now, return mock data
            List<Map<String, Object>> notifications = new ArrayList<>();
            
            // Mock notification history
            for (int i = 0; i < size; i++) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("id", i + 1 + (page * size));
                notification.put("title", "Sample Notification " + (i + 1));
                notification.put("message", "This is a sample notification message");
                notification.put("type", "test");
                notification.put("read", i % 2 == 0);
                notification.put("createdAt", LocalDateTime.now().minusHours(i));
                notifications.add(notification);
            }
            
            response.put("success", true);
            response.put("notifications", notifications);
            response.put("page", page);
            response.put("size", size);
            response.put("totalElements", 100); // Mock total
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching notification history: " + e.getMessage());
        }
        
        return response;
    }

    // Mark notification as read (placeholder)
    @Transactional
public Map<String, Object> markAllAsRead(UserDetails userDetails) {
    Map<String, Object> response = new HashMap<>();
    
    try {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Get all unread notifications for this user
        List<Notification> unreadNotifications = notificationRepository.findByUserAndReadFalse(user);
        
        // Mark them all as read
        for (Notification notification : unreadNotifications) {
            notification.markAsRead();
        }
        
        // Save all at once
        notificationRepository.saveAll(unreadNotifications);
        
        logger.info("✅ Marked {} notifications as read for user {}", unreadNotifications.size(), user.getUsername());
        
        response.put("success", true);
        response.put("message", "All notifications marked as read");
        response.put("count", unreadNotifications.size());
        
    } catch (Exception e) {
        logger.error("❌ Error marking all as read: {}", e.getMessage());
        response.put("success", false);
        response.put("message", "Error marking all notifications as read: " + e.getMessage());
    }
    
    return response;
}

    // Delete notification (placeholder)
    @Transactional
public Map<String, Object> deleteNotification(Long notificationId, UserDetails userDetails) {
    Map<String, Object> response = new HashMap<>();
    
    try {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        // Verify ownership
        if (!notification.getUser().getId().equals(user.getId())) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return response;
        }
        
        // Delete the notification
        notificationRepository.delete(notification);
        
        logger.info("✅ Deleted notification {} for user {}", notificationId, user.getUsername());
        
        response.put("success", true);
        response.put("message", "Notification deleted");
        
    } catch (Exception e) {
        logger.error("❌ Error deleting notification: {}", e.getMessage());
        response.put("success", false);
        response.put("message", "Error deleting notification: " + e.getMessage());
    }
    
    return response;
}

// Update device token for push notifications
public Map<String, Object> updateDeviceToken(String deviceToken, String platform, UserDetails userDetails) {
    Map<String, Object> response = new HashMap<>();
    
    try {
        logger.info("=== UPDATING DEVICE TOKEN ===");
        logger.info("User: {}", userDetails.getUsername());
        logger.info("Platform: {}", platform);
        logger.info("Token: {}", deviceToken.substring(0, Math.min(20, deviceToken.length())) + "...");
        
        Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "User not found");
            return response;
        }

        User user = userOpt.get();
        
        // Store the device token and platform
        user.setDeviceToken(deviceToken);
        user.setDevicePlatform(platform);
        user.setUpdatedAt(LocalDateTime.now());
        
        userRepository.save(user);
        
        logger.info("✅ Device token updated successfully for user: {}", userDetails.getUsername());
        
        response.put("success", true);
        response.put("message", "Device token registered successfully");
        response.put("platform", platform);
        
    } catch (Exception e) {
        logger.error("❌ Error updating device token: {}", e.getMessage());
        response.put("success", false);
        response.put("message", "Error updating device token: " + e.getMessage());
    }
    
    return response;
}

    // Get preferences summary
    public Map<String, Object> getPreferencesSummary(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> settingsResponse = getNotificationSettings(userDetails);
            if (!(Boolean) settingsResponse.get("success")) {
                return settingsResponse;
            }
            
            Map<String, Object> data = (Map<String, Object>) settingsResponse.get("data");
            Map<String, Object> notificationSettings = (Map<String, Object>) data.get("notificationSettings");
            Map<String, Object> scheduleSettings = (Map<String, Object>) data.get("scheduleSettings");
            
            // Count enabled notifications
            long enabledCount = notificationSettings.values().stream()
                .mapToLong(v -> (Boolean) v ? 1 : 0)
                .sum();
            
            Map<String, Object> summary = new HashMap<>();
            summary.put("totalNotifications", notificationSettings.size());
            summary.put("enabledNotifications", enabledCount);
            summary.put("disabledNotifications", notificationSettings.size() - enabledCount);
            summary.put("quietHoursEnabled", scheduleSettings.get("quietHoursEnabled"));
            summary.put("emergencyOverride", scheduleSettings.get("emergencyOverride"));
            
            response.put("success", true);
            response.put("summary", summary);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error getting preferences summary: " + e.getMessage());
        }
        
        return response;
    }

    @Transactional
public Map<String, Object> markAsRead(Long notificationId, UserDetails userDetails) {
    Map<String, Object> response = new HashMap<>();
    
    try {
        // Find notification
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        // Verify ownership
        if (!notification.getUser().getUsername().equals(userDetails.getUsername())) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return response;
        }
        
        // Mark as read
        notification.markAsRead();
        
        // Save the notification to persist changes
        notificationRepository.save(notification);
        
        logger.info("✅ Notification {} marked as read by {}", notificationId, userDetails.getUsername());
        
        response.put("success", true);
        response.put("message", "Notification marked as read");
        
    } catch (Exception e) {
        logger.error("❌ Error marking notification as read: {}", e.getMessage());
        response.put("success", false);
        response.put("message", "Error: " + e.getMessage());
    }
    
    return response;
}
    // Check if notification is enabled for category
    public Map<String, Object> isNotificationEnabled(String category, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            boolean enabled = isNotificationTypeEnabled(category, user);
            
            response.put("success", true);
            response.put("enabled", enabled);
            response.put("category", category);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error checking notification status: " + e.getMessage());
        }
        
        return response;
    }

    private String getScreenForNotificationType(String type) {
    switch (type) {
        case "appointment":
            return "appointments";
        case "result":
            return "test-results";
        case "testResults":
            return "test-results";
        case "medicationAlerts":
            return "medications";
        case "security":
            return "security";
        case "support_reply":
            return "support";
        default:
            return "home";
    }
}

private void logNotificationToDatabase(User user, String title, String message, 
                                       String type, String status) {
    try {
        // Example: would save to Notification entity
        /*
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setStatus(status);
        notification.setCreatedAt(LocalDateTime.now());
        notification.setRead(false);
        
        notificationRepository.save(notification);
        */
        
        logger.debug("Notification logged: {} - {} - {}", type, title, status);
        
    } catch (Exception e) {
        logger.warn("Failed to log notification to database: {}", e.getMessage());
        // Don't fail the entire operation if logging fails
    }
}


    private Map<String, Object> buildExpoNotificationPayload(String deviceToken, String title, 
                                                         String message, String type) {
    Map<String, Object> payload = new HashMap<>();
    
    // Expo expects these fields
    payload.put("to", deviceToken);
    
    // Notification content
    Map<String, Object> notification = new HashMap<>();
    notification.put("title", title);
    notification.put("body", message);
    notification.put("sound", "default");
    notification.put("badge", 1);
    
    payload.put("notification", notification);
    
    // Custom data payload (for app-specific handling)
    Map<String, String> data = new HashMap<>();
    data.put("type", type);
    data.put("timestamp", LocalDateTime.now().toString());
    data.put("screen", getScreenForNotificationType(type));
    
    payload.put("data", data);
    
    // Priority settings
    payload.put("priority", "high");
    
    logger.debug("Notification payload: {}", payload);
    
    return payload;
}


    // ==================== PRIVATE HELPER METHODS ====================

    // Check if a specific notification type is enabled for the user
    private boolean isNotificationTypeEnabled(String notificationType, User user) {
        try {
            if (user.getNotificationSettings() == null || user.getNotificationSettings().isEmpty()) {
                Map<String, Object> defaults = getDefaultNotificationSettings();
                return (Boolean) defaults.getOrDefault(notificationType, false);
            }
            
            Map<String, Object> settings = objectMapper.readValue(user.getNotificationSettings(), Map.class);
            return (Boolean) settings.getOrDefault(notificationType, false);
        } catch (Exception e) {
            // Return default if parsing fails
            Map<String, Object> defaults = getDefaultNotificationSettings();
            return (Boolean) defaults.getOrDefault(notificationType, false);
        }
    }

    // Check if notification should be sent based on quiet hours
    private boolean shouldSendNotification(String notificationType, User user) {
        try {
            Map<String, Object> scheduleSettings = getDefaultScheduleSettings();
            
            if (user.getScheduleSettings() != null && !user.getScheduleSettings().isEmpty()) {
                Map<String, Object> userScheduleSettings = objectMapper.readValue(
                    user.getScheduleSettings(), Map.class);
                scheduleSettings.putAll(userScheduleSettings);
            }
            
            boolean quietHoursEnabled = (Boolean) scheduleSettings.get("quietHoursEnabled");
            if (!quietHoursEnabled) {
                return true; // No quiet hours, always send
            }
            
            boolean emergencyOverride = (Boolean) scheduleSettings.get("emergencyOverride");
            if (emergencyOverride && isCriticalNotification(notificationType)) {
                return true; // Critical notifications bypass quiet hours
            }
            
            LocalTime now = LocalTime.now();
            LocalTime quietStart = LocalTime.parse((String) scheduleSettings.get("quietStart"));
            LocalTime quietEnd = LocalTime.parse((String) scheduleSettings.get("quietEnd"));
            
            // Check if current time is within quiet hours
            if (quietStart.isAfter(quietEnd)) {
                // Quiet hours span midnight (e.g., 22:00 to 07:00)
                return !(now.isAfter(quietStart) || now.isBefore(quietEnd));
            } else {
                // Quiet hours within same day
                return !(now.isAfter(quietStart) && now.isBefore(quietEnd));
            }
            
        } catch (Exception e) {
            // If there's an error, allow the notification to be sent
            return true;
        }
    }

    // Check if notification type is critical
    private boolean isCriticalNotification(String notificationType) {
        Set<String> criticalTypes = Set.of(
            "testResults", "appointmentReminders", "medicationAlerts",
            "loginAlerts", "securityUpdates", "accountChanges", "maintenanceNotices",
            "support", "support_reply" // Added support notifications as critical
        );
        return criticalTypes.contains(notificationType);
    }

    private void sendSupportTeamEmail(SupportTicket ticket) {
        try {
            // In production, implement actual email sending
            System.out.println(String.format(
                "EMAIL TO SUPPORT TEAM:\nTo: support@qualitest.com\nSubject: New Support Ticket #%s\n" +
                "Body: New ticket from %s (%s)\nSubject: %s\nPriority: %s\nCategory: %s\n" +
                "Please respond within 4-6 hours during business hours.",
                ticket.getTicketNumber(),
                ticket.getName(),
                ticket.getEmail(), 
                ticket.getSubject(),
                ticket.getPriority().getDisplayName(),
                ticket.getCategory()
            ));
        } catch (Exception e) {
            System.err.println("Error sending support team email: " + e.getMessage());
        }
    }

    /**
     * Send Slack notification to support channel
     */
    private void sendSlackNotification(SupportTicket ticket) {
        try {
            // In production, integrate with Slack API
            System.out.println(String.format(
                "SLACK NOTIFICATION:\nChannel: #medical-support\n" +
                "Message: 🎫 New support ticket #%s from %s\n" +
                "📋 Subject: %s\n⚡ Priority: %s\n📂 Category: %s\n" +
                "👤 Assign to available agent",
                ticket.getTicketNumber(),
                ticket.getName(),
                ticket.getSubject(),
                ticket.getPriority().getDisplayName(),
                ticket.getCategory()
            ));
        } catch (Exception e) {
            System.err.println("Error sending Slack notification: " + e.getMessage());
        }
    }

    /**
     * Send email to user about agent reply
     */
    private boolean sendUserEmail(User user, String title, String message) {
        try {
            // In production, implement actual email sending using Spring Mail
            System.out.println(String.format(
                "EMAIL TO USER:\nTo: %s\nSubject: %s\nBody: %s",
                user.getEmail(),
                title,
                message
            ));
            return true;
        } catch (Exception e) {
            System.err.println("Error sending user email: " + e.getMessage());
            return false;
        }
    }

    /**
     * Truncate message for email/notification display
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength - 3) + "...";
    }
    // ==================== SECURITY NOTIFICATIONS ====================

@Autowired(required = false)
private SecuritySettingsRepository securitySettingsRepository;

/**
 * Send login notification email
 */
public void sendLoginNotification(String email, String deviceInfo, String ipAddress, String location) {
    try {
        logger.info("=== SENDING LOGIN NOTIFICATION ===");
        logger.info("To: {}", email);
        
        // Check if user has login notifications enabled
        if (securitySettingsRepository != null) {
            Optional<SecuritySettings> settingsOpt = securitySettingsRepository.findByEmail(email);
            if (settingsOpt.isPresent() && !settingsOpt.get().isLoginNotifications()) {
                logger.info("Login notifications disabled for: {}", email);
                return;
            }
        }

        if (mailSender == null) {
            logger.error("JavaMailSender is not configured!");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm a");
        String timestamp = now.format(formatter);
        
        String subject = "🔐 New Login to Your Qualitest Medical Account";
        String content = String.format(
            "Hello,\n\n" +
            "We detected a new login to your Qualitest Medical account.\n\n" +
            "Login Details:\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Time: %s\n" +
            "Device: %s\n" +
            "IP Address: %s\n" +
            "Location: %s\n\n" +
            "If this was you, you can safely ignore this email.\n\n" +
            "⚠️ If you didn't log in, please:\n" +
            "1. Change your password immediately\n" +
            "2. Review your account activity\n" +
            "3. Contact our support team\n\n" +
            "Stay secure,\n" +
            "Qualitest Medical Security Team\n\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "To disable login notifications, visit your account security settings.",
            timestamp, deviceInfo, ipAddress, location
        );
        
        sendEmail(email, subject, content);
        
        logger.info("✅ Login notification sent successfully to: {}", email);
        
    } catch (Exception e) {
        logger.error("❌ Failed to send login notification to {}: {}", email, e.getMessage());
    }
}

/**
 * Send suspicious activity alert
 */
public void sendSuspiciousActivityAlert(String email, String activityType, String details) {
    try {
        logger.info("=== SENDING SUSPICIOUS ACTIVITY ALERT ===");
        logger.info("To: {}", email);
        
        // Check if user has suspicious activity alerts enabled
        if (securitySettingsRepository != null) {
            Optional<SecuritySettings> settingsOpt = securitySettingsRepository.findByEmail(email);
            if (settingsOpt.isPresent() && !settingsOpt.get().isSuspiciousActivityAlerts()) {
                logger.info("Suspicious activity alerts disabled for: {}", email);
                return;
            }
        }

        if (mailSender == null) {
            logger.error("JavaMailSender is not configured!");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' hh:mm a");
        String timestamp = now.format(formatter);
        
        String subject = "⚠️ Suspicious Activity Detected on Your Account";
        String content = String.format(
            "⚠️ SECURITY ALERT\n\n" +
            "We detected suspicious activity on your Qualitest Medical account.\n\n" +
            "Activity Details:\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "Type: %s\n" +
            "Time: %s\n" +
            "Details: %s\n\n" +
            "🔒 IMMEDIATE ACTIONS REQUIRED:\n" +
            "1. Change your password immediately\n" +
            "2. Review your recent account activity\n" +
            "3. Enable two-factor authentication\n" +
            "4. Contact our security team if needed\n\n" +
            "If you recognize this activity, you can safely ignore this alert.\n\n" +
            "Qualitest Medical Security Team\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "This is an automated security alert. To manage your security settings, visit your account page.",
            activityType, timestamp, details
        );
        
        sendEmail(email, subject, content);
        
        logger.info("✅ Suspicious activity alert sent successfully to: {}", email);
        
    } catch (Exception e) {
        logger.error("❌ Failed to send suspicious activity alert to {}: {}", email, e.getMessage());
    }
}

/**
 * Check if login notifications are enabled for a user
 */
public boolean areLoginNotificationsEnabled(String email) {
    try {
        if (securitySettingsRepository == null) {
            return true; // Default to enabled if repository not available
        }
        
        Optional<SecuritySettings> settings = securitySettingsRepository.findByEmail(email);
        return settings.map(SecuritySettings::isLoginNotifications).orElse(true);
    } catch (Exception e) {
        logger.error("Error checking login notification settings: {}", e.getMessage());
        return true; // Default to enabled on error
    }
}

/**
 * Check if suspicious activity alerts are enabled for a user
 */
public boolean areSuspiciousActivityAlertsEnabled(String email) {
    try {
        if (securitySettingsRepository == null) {
            return true; // Default to enabled if repository not available
        }
        
        Optional<SecuritySettings> settings = securitySettingsRepository.findByEmail(email);
        return settings.map(SecuritySettings::isSuspiciousActivityAlerts).orElse(true);
    } catch (Exception e) {
        logger.error("Error checking suspicious activity alert settings: {}", e.getMessage());
        return true; // Default to enabled on error
    }
}
}