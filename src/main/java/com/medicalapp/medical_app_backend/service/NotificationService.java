package com.medicalapp.medical_app_backend.service;
import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.entity.SupportTicket;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.medicalapp.medical_app_backend.repository.UserRepository;

import com.medicalapp.medical_app_backend.service.EmailService;

import com.medicalapp.medical_app_backend.entity.Notification;
import com.medicalapp.medical_app_backend.entity.SecuritySettings;
import com.medicalapp.medical_app_backend.repository.NotificationRepository;
import com.medicalapp.medical_app_backend.repository.SecuritySettingsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
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

   @Autowired
    private EmailService emailService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired(required = false)
    private SecuritySettingsRepository securitySettingsRepository;

    // ==================== EMAIL METHODS ====================

    // REPLACE the entire sendEmail() method with this:
public void sendEmail(String toEmail, String subject, String content) {
    try {
        logger.info("=== SENDING EMAIL === To: {}", toEmail);
        emailService.sendEmail(toEmail, subject, content);
        logger.info("EMAIL SENT SUCCESSFULLY to: {}", toEmail);
    } catch (Exception e) {
        logger.error("FAILED to send email to {}: {}", toEmail, e.getMessage());
    }
}

    // ==================== EMAIL VERIFICATION CODE ====================

    /**
     * Send 6-digit email verification code after signup.
     * Fires alongside the login notification so both emails arrive together.
     */
    public void sendEmailVerificationCode(String email, String firstName, String otp) {
        try {
            logger.info("=== SENDING EMAIL VERIFICATION CODE ===");
            logger.info("To: {}", email);

            String subject = "🔐 Your Qualitest Medical Verification Code";
            String content = String.format(
                "Hello %s,\n\n" +
                "Thank you for registering with Qualitest Medical!\n\n" +
                "Your email verification code is:\n\n" +
                "        %s\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "⏰ This code expires in 10 minutes.\n\n" +
                "If you did not create an account with us, you can safely ignore this email.\n\n" +
                "Welcome aboard,\n" +
                "Qualitest Medical Team\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "This is an automated message. Please do not reply.",
                firstName, otp
            );

            sendEmail(email, subject, content);

            logger.info("✅ Verification code email sent to: {}", email);

        } catch (Exception e) {
            logger.error("❌ Failed to send verification code email to {}: {}", email, e.getMessage());
        }
    }

    // ==================== LOGIN NOTIFICATION ====================

    public void sendLoginNotification(String email, String deviceInfo, String ipAddress, String location) {
        try {
            logger.info("=== SENDING LOGIN NOTIFICATION ===");
            logger.info("To: {}", email);

            if (securitySettingsRepository != null) {
                Optional<SecuritySettings> settingsOpt = securitySettingsRepository.findByEmail(email);
                if (settingsOpt.isPresent() && !settingsOpt.get().isLoginNotifications()) {
                    logger.info("Login notifications disabled for: {}", email);
                    return;
                }
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

    // ==================== PASSWORD CHANGE NOTIFICATION ====================

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
        }
    }

    // ==================== SUSPICIOUS ACTIVITY ALERT ====================

    public void sendSuspiciousActivityAlert(String email, String activityType, String details) {
        try {
            logger.info("=== SENDING SUSPICIOUS ACTIVITY ALERT ===");
            logger.info("To: {}", email);

            if (securitySettingsRepository != null) {
                Optional<SecuritySettings> settingsOpt = securitySettingsRepository.findByEmail(email);
                if (settingsOpt.isPresent() && !settingsOpt.get().isSuspiciousActivityAlerts()) {
                    logger.info("Suspicious activity alerts disabled for: {}", email);
                    return;
                }
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
                "This is an automated security alert.",
                activityType, timestamp, details
            );

            sendEmail(email, subject, content);

            logger.info("✅ Suspicious activity alert sent successfully to: {}", email);

        } catch (Exception e) {
            logger.error("❌ Failed to send suspicious activity alert to {}: {}", email, e.getMessage());
        }
    }

    // ==================== SMS ====================

    public void sendSMS(String phoneNumber, String message) {
        try {
            logger.info("=== SENDING SMS ===");
            logger.info("To: {}", phoneNumber);
            logger.info("Message: {}", message);
            logger.info("SMS sent successfully (simulated)");
        } catch (Exception e) {
            logger.error("Failed to send SMS to {}: {}", phoneNumber, e.getMessage());
            throw new RuntimeException("SMS sending failed: " + e.getMessage());
        }
    }

    // ==================== WEBSOCKET RESULT NOTIFICATION ====================

    @Transactional
    public void createAndSendResultNotification(Long patientId, Long resultId, String testName, String status) {
        try {
            logger.info("=== CREATING & SENDING RESULT NOTIFICATION ===");
            logger.info("Patient ID: {}, Result ID: {}, Test: {}, Status: {}", patientId, resultId, testName, status);

            User patient = userRepository.findById(patientId)
                    .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + patientId));

            Notification notification = new Notification();
            notification.setUser(patient);
            notification.setType(Notification.TYPE_RESULT);
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

            try {
                String metadataJson = objectMapper.writeValueAsString(Map.of(
                        "testName", testName,
                        "status", status,
                        "resultId", resultId,
                        "patientId", patientId
                ));
                notification.setMetadata(metadataJson);
            } catch (Exception e) {
                logger.warn("Could not set metadata: {}", e.getMessage());
            }

            notification = notificationRepository.save(notification);
            logger.info("✅ Notification saved to database with ID: {}", notification.getId());

            Map<String, Object> payload = new HashMap<>();
            payload.put("id", notification.getId());
            payload.put("type", notification.getType());
            payload.put("title", notification.getTitle());
            payload.put("message", notification.getMessage());
            payload.put("priority", notification.getPriority().name());
            payload.put("isRead", notification.isRead());
            payload.put("read", notification.isRead());
            payload.put("createdAt", notification.getCreatedAt().toString());
            payload.put("referenceType", notification.getReferenceType());
            payload.put("referenceId", notification.getReferenceId());

            Map<String, Object> data = new HashMap<>();
            data.put("testName", testName);
            data.put("status", status);
            data.put("patientId", patientId);
            data.put("resultId", resultId);
            payload.put("data", data);

            if (messagingTemplate != null) {
                String userId = String.valueOf(patientId);
                messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", payload);
                messagingTemplate.convertAndSendToUser(userId, "/topic/notifications", payload);
                logger.info("✅ WebSocket notification broadcast to user: {}", userId);
            } else {
                logger.error("❌ SimpMessagingTemplate is NULL! WebSocket not configured!");
            }

            logger.info("✅ Result notification created and sent successfully");

        } catch (Exception e) {
            logger.error("❌ CRITICAL ERROR creating notification: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create notification: " + e.getMessage());
        }
    }

    // ==================== USER NOTIFICATIONS ====================

    public Map<String, Object> getUserNotifications(UserDetails userDetails) {
        try {
            logger.info("=== GET USER NOTIFICATIONS === User: {}", userDetails.getUsername());

            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                return Map.of("success", false, "message", "User not found");
            }

            User currentUser = userOpt.get();
            List<Notification> notifications = notificationRepository.findByUserOrderByCreatedAtDesc(currentUser);

            logger.info("✅ Found {} notifications for user {}", notifications.size(), currentUser.getId());
            return Map.of("success", true, "notifications", notifications);

        } catch (Exception e) {
            logger.error("❌ Error fetching notifications: {}", e.getMessage());
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> markAsRead(Long notificationId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new RuntimeException("Notification not found"));

            if (!notification.getUser().getUsername().equals(userDetails.getUsername())) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return response;
            }

            notification.markAsRead();
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

    @Transactional
    public Map<String, Object> markAllAsRead(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<Notification> unreadNotifications = notificationRepository.findByUserAndReadFalse(user);
            for (Notification notification : unreadNotifications) {
                notification.markAsRead();
            }
            notificationRepository.saveAll(unreadNotifications);

            logger.info("✅ Marked {} notifications as read for user {}", unreadNotifications.size(), user.getUsername());
            response.put("success", true);
            response.put("message", "All notifications marked as read");
            response.put("count", unreadNotifications.size());

        } catch (Exception e) {
            logger.error("❌ Error marking all as read: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }

    @Transactional
    public Map<String, Object> deleteNotification(Long notificationId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Notification notification = notificationRepository.findById(notificationId)
                    .orElseThrow(() -> new RuntimeException("Notification not found"));

            if (!notification.getUser().getId().equals(user.getId())) {
                response.put("success", false);
                response.put("message", "Unauthorized");
                return response;
            }

            notificationRepository.delete(notification);

            logger.info("✅ Deleted notification {} for user {}", notificationId, user.getUsername());
            response.put("success", true);
            response.put("message", "Notification deleted");

        } catch (Exception e) {
            logger.error("❌ Error deleting notification: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }

    // ==================== PUSH NOTIFICATIONS ====================

    public boolean sendPushNotification(User recipient, String title, String message, String type) {
        try {
            logger.info("=== SENDING PUSH NOTIFICATION === User: {}, Title: {}, Type: {}",
                    recipient.getUsername(), title, type);

            if (recipient.getDeviceToken() == null || recipient.getDeviceToken().isEmpty()) {
                logger.warn("No device token registered for user: {}", recipient.getUsername());
                return false;
            }

            Map<String, Object> notification = buildExpoNotificationPayload(
                    recipient.getDeviceToken(), title, message, type);

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

    private boolean sendToExpoPushService(Map<String, Object> notification) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(notification, headers);

            try {
                ResponseEntity<String> response = restTemplate.postForEntity(
                        "https://exp.host/--/api/v2/push/send", entity, String.class);

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

    private Map<String, Object> buildExpoNotificationPayload(String deviceToken, String title,
                                                              String message, String type) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("to", deviceToken);

        Map<String, Object> notification = new HashMap<>();
        notification.put("title", title);
        notification.put("body", message);
        notification.put("sound", "default");
        notification.put("badge", 1);
        payload.put("notification", notification);

        Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("timestamp", LocalDateTime.now().toString());
        data.put("screen", getScreenForNotificationType(type));
        payload.put("data", data);

        payload.put("priority", "high");
        return payload;
    }

    private void logNotificationToDatabase(User user, String title, String message,
                                           String type, String status) {
        try {
            logger.debug("Notification logged: {} - {} - {}", type, title, status);
        } catch (Exception e) {
            logger.warn("Failed to log notification to database: {}", e.getMessage());
        }
    }

    // ==================== DEVICE TOKEN ====================

    public Map<String, Object> updateDeviceToken(String deviceToken, String platform, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            logger.info("=== UPDATING DEVICE TOKEN === User: {}, Platform: {}", userDetails.getUsername(), platform);

            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            user.setDeviceToken(deviceToken);
            user.setDevicePlatform(platform);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            logger.info("✅ Device token updated for user: {}", userDetails.getUsername());
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

    // ==================== NOTIFICATION SETTINGS ====================

    private Map<String, Object> getDefaultNotificationSettings() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("testResults", true);
        defaults.put("appointmentReminders", true);
        defaults.put("medicationAlerts", true);
        defaults.put("healthTips", false);
        defaults.put("supportNotifications", true);
        defaults.put("loginAlerts", true);
        defaults.put("securityUpdates", true);
        defaults.put("accountChanges", true);
        defaults.put("appUpdates", true);
        defaults.put("featureAnnouncements", false);
        defaults.put("maintenanceNotices", true);
        defaults.put("promotions", false);
        defaults.put("newsletters", false);
        defaults.put("surveys", false);
        return defaults;
    }

    private Map<String, Object> getDefaultScheduleSettings() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("quietHoursEnabled", true);
        defaults.put("quietStart", "22:00");
        defaults.put("quietEnd", "07:00");
        defaults.put("weekendQuietHours", true);
        defaults.put("emergencyOverride", true);
        return defaults;
    }

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

            if (user.getNotificationSettings() != null && !user.getNotificationSettings().isEmpty()) {
                try {
                    Map<String, Object> stored = objectMapper.readValue(user.getNotificationSettings(), Map.class);
                    notificationSettings.putAll(stored);
                } catch (Exception e) {
                    logger.warn("Error parsing notification settings: {}", e.getMessage());
                }
            }

            if (user.getScheduleSettings() != null && !user.getScheduleSettings().isEmpty()) {
                try {
                    Map<String, Object> stored = objectMapper.readValue(user.getScheduleSettings(), Map.class);
                    scheduleSettings.putAll(stored);
                } catch (Exception e) {
                    logger.warn("Error parsing schedule settings: {}", e.getMessage());
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
            Map<String, Object> notificationSettings = (Map<String, Object>) settingsData.get("notificationSettings");
            Map<String, Object> scheduleSettings = (Map<String, Object>) settingsData.get("scheduleSettings");

            if (notificationSettings != null) {
                user.setNotificationSettings(objectMapper.writeValueAsString(notificationSettings));
            }
            if (scheduleSettings != null) {
                user.setScheduleSettings(objectMapper.writeValueAsString(scheduleSettings));
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
            Map<String, Object> defaultNotificationSettings = getDefaultNotificationSettings();
            Map<String, Object> defaultScheduleSettings = getDefaultScheduleSettings();

            user.setNotificationSettings(objectMapper.writeValueAsString(defaultNotificationSettings));
            user.setScheduleSettings(objectMapper.writeValueAsString(defaultScheduleSettings));
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
            response.put("message", "Error resetting settings: " + e.getMessage());
        }
        return response;
    }

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

            if (!shouldSendNotification("test", user)) {
                response.put("success", false);
                response.put("message", "Test notification blocked by quiet hours settings");
                return response;
            }

            boolean sent = sendPushNotification(user, "Test Notification",
                    "This is a test notification from Qualitest Medical", "test");

            response.put("success", sent);
            response.put("message", sent ? "Test notification sent successfully" : "Failed to send test notification");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending test notification: " + e.getMessage());
        }
        return response;
    }

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

            if (!isNotificationTypeEnabled("appointmentReminders", user)) {
                response.put("success", false);
                response.put("message", "Appointment reminders are disabled");
                return response;
            }

            String title = "Appointment Reminder";
            String message = switch (reminderType) {
                case "24h" -> "You have an appointment tomorrow. Don't forget to prepare any required documents.";
                case "1h"  -> "Your appointment is in 1 hour. Please arrive 15 minutes early.";
                case "15m" -> "Your appointment is in 15 minutes. Please check in when you arrive.";
                default    -> "You have an upcoming appointment.";
            };

            boolean sent = sendPushNotification(user, title, message, "appointment");
            response.put("success", sent);
            response.put("message", sent ? "Appointment reminder sent" : "Failed to send reminder");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending appointment reminder: " + e.getMessage());
        }
        return response;
    }

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
            boolean isCritical = alertType.equals("login") || alertType.equals("password_change")
                    || alertType.equals("account_locked");

            if (!isCritical && !shouldSendNotification("security", user)) {
                response.put("success", false);
                response.put("message", "Security alert blocked by quiet hours");
                return response;
            }

            boolean sent = sendPushNotification(user, "Security Alert", message, "security");
            response.put("success", sent);
            response.put("message", sent ? "Security alert sent" : "Failed to send alert");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending security alert: " + e.getMessage());
        }
        return response;
    }

    public Map<String, Object> getNotificationHistory(UserDetails userDetails, int page, int size) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> notifications = new ArrayList<>();
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
            response.put("totalElements", 100);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching notification history: " + e.getMessage());
        }
        return response;
    }

    public Map<String, Object> isNotificationEnabled(String category, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            boolean enabled = isNotificationTypeEnabled(category, userOpt.get());
            response.put("success", true);
            response.put("enabled", enabled);
            response.put("category", category);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error checking notification status: " + e.getMessage());
        }
        return response;
    }

    public Map<String, Object> getPreferencesSummary(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> settingsResponse = getNotificationSettings(userDetails);
            if (!(Boolean) settingsResponse.get("success")) return settingsResponse;

            Map<String, Object> data = (Map<String, Object>) settingsResponse.get("data");
            Map<String, Object> notificationSettings = (Map<String, Object>) data.get("notificationSettings");
            Map<String, Object> scheduleSettings = (Map<String, Object>) data.get("scheduleSettings");

            long enabledCount = notificationSettings.values().stream()
                    .mapToLong(v -> (Boolean) v ? 1 : 0).sum();

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

    // ==================== SUPPORT NOTIFICATIONS ====================

    public void notifyNewSupportTicket(SupportTicket ticket) {
        try {
            sendSupportTeamEmail(ticket);
            sendSlackNotification(ticket);
        } catch (Exception e) {
            logger.error("Error notifying support team about new ticket: {}", e.getMessage());
        }
    }

    public void notifyUserOfAgentReply(User user, String message, String agentName) {
        try {
            if (!isNotificationTypeEnabled("appointmentReminders", user)) return;
            if (!shouldSendNotification("support", user)) return;

            String title = "Medical Support Reply";
            String notificationMessage = String.format(
                    "%s from our medical support team has replied to your message", agentName);

            sendPushNotification(user, title, notificationMessage, "support_reply");
            sendUserEmail(user, title, String.format(
                    "Hello %s,\n\n%s from our medical support team has replied to your inquiry:\n\n\"%s\"\n\n" +
                    "Please check the app for the full conversation.\n\nBest regards,\nQualitest Medical Support Team",
                    user.getFirstName(), agentName, truncateMessage(message, 200)));

        } catch (Exception e) {
            logger.error("Error notifying user of agent reply: {}", e.getMessage());
        }
    }

    // ==================== SECURITY SETTINGS HELPERS ====================

    public boolean areLoginNotificationsEnabled(String email) {
        try {
            if (securitySettingsRepository == null) return true;
            Optional<SecuritySettings> settings = securitySettingsRepository.findByEmail(email);
            return settings.map(SecuritySettings::isLoginNotifications).orElse(true);
        } catch (Exception e) {
            logger.error("Error checking login notification settings: {}", e.getMessage());
            return true;
        }
    }

    public boolean areSuspiciousActivityAlertsEnabled(String email) {
        try {
            if (securitySettingsRepository == null) return true;
            Optional<SecuritySettings> settings = securitySettingsRepository.findByEmail(email);
            return settings.map(SecuritySettings::isSuspiciousActivityAlerts).orElse(true);
        } catch (Exception e) {
            logger.error("Error checking suspicious activity alert settings: {}", e.getMessage());
            return true;
        }
    }

    // ==================== PRIVATE HELPERS ====================

    private boolean isNotificationTypeEnabled(String notificationType, User user) {
        try {
            if (user.getNotificationSettings() == null || user.getNotificationSettings().isEmpty()) {
                return (Boolean) getDefaultNotificationSettings().getOrDefault(notificationType, false);
            }
            Map<String, Object> settings = objectMapper.readValue(user.getNotificationSettings(), Map.class);
            return (Boolean) settings.getOrDefault(notificationType, false);
        } catch (Exception e) {
            return (Boolean) getDefaultNotificationSettings().getOrDefault(notificationType, false);
        }
    }

    private boolean shouldSendNotification(String notificationType, User user) {
        try {
            Map<String, Object> scheduleSettings = getDefaultScheduleSettings();
            if (user.getScheduleSettings() != null && !user.getScheduleSettings().isEmpty()) {
                Map<String, Object> userSchedule = objectMapper.readValue(user.getScheduleSettings(), Map.class);
                scheduleSettings.putAll(userSchedule);
            }

            boolean quietHoursEnabled = (Boolean) scheduleSettings.get("quietHoursEnabled");
            if (!quietHoursEnabled) return true;

            boolean emergencyOverride = (Boolean) scheduleSettings.get("emergencyOverride");
            if (emergencyOverride && isCriticalNotification(notificationType)) return true;

            LocalTime now        = LocalTime.now();
            LocalTime quietStart = LocalTime.parse((String) scheduleSettings.get("quietStart"));
            LocalTime quietEnd   = LocalTime.parse((String) scheduleSettings.get("quietEnd"));

            if (quietStart.isAfter(quietEnd)) {
                return !(now.isAfter(quietStart) || now.isBefore(quietEnd));
            } else {
                return !(now.isAfter(quietStart) && now.isBefore(quietEnd));
            }
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isCriticalNotification(String notificationType) {
        return Set.of(
            "testResults", "appointmentReminders", "medicationAlerts",
            "loginAlerts", "securityUpdates", "accountChanges",
            "maintenanceNotices", "support", "support_reply"
        ).contains(notificationType);
    }

    private String getScreenForNotificationType(String type) {
        return switch (type) {
            case "appointment"    -> "appointments";
            case "result",
                 "testResults"    -> "test-results";
            case "medicationAlerts" -> "medications";
            case "security"       -> "security";
            case "support_reply"  -> "support";
            default               -> "home";
        };
    }

    private void sendSupportTeamEmail(SupportTicket ticket) {
        try {
            logger.info("Support team email queued for ticket: {}", ticket.getTicketNumber());
        } catch (Exception e) {
            logger.error("Error sending support team email: {}", e.getMessage());
        }
    }

    private void sendSlackNotification(SupportTicket ticket) {
        try {
            logger.info("Slack notification queued for ticket: {}", ticket.getTicketNumber());
        } catch (Exception e) {
            logger.error("Error sending Slack notification: {}", e.getMessage());
        }
    }

    private boolean sendUserEmail(User user, String title, String message) {
        try {
            sendEmail(user.getEmail(), title, message);
            return true;
        } catch (Exception e) {
            logger.error("Error sending user email: {}", e.getMessage());
            return false;
        }
    }

    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength - 3) + "...";
    }
}