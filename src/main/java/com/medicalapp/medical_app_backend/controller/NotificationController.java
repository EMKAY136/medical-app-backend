package com.medicalapp.medical_app_backend.controller;

import com.medicalapp.medical_app_backend.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    @Autowired
    private NotificationService notificationService;

    // Get user's notification settings
    @GetMapping("/settings")
    public ResponseEntity<?> getNotificationSettings(@AuthenticationPrincipal UserDetails userDetails,
                                                    HttpServletRequest request) {
        try {
            logger.info("=== NOTIFICATION SETTINGS REQUEST ===");
            logger.info("Request URI: {}", request.getRequestURI());
            logger.info("Request Method: {}", request.getMethod());
            logger.info("Authorization Header: {}", request.getHeader("Authorization"));
            logger.info("UserDetails: {}", userDetails != null ? userDetails.getUsername() : "NULL");
            logger.info("User Authorities: {}", userDetails != null ? userDetails.getAuthorities() : "NULL");
            
            if (userDetails == null) {
                logger.error("UserDetails is null - authentication failed");
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            Map<String, Object> response = notificationService.getNotificationSettings(userDetails);
            
            if ((Boolean) response.get("success")) {
                logger.info("Successfully retrieved notification settings for user: {}", userDetails.getUsername());
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Failed to retrieve notification settings: {}", response.get("message"));
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in getNotificationSettings: ", e);
            return ResponseEntity.badRequest().body("Error fetching notification settings: " + e.getMessage());
        }
    }

    // Add this to your NotificationController.java
@GetMapping
public ResponseEntity<?> getNotifications(@AuthenticationPrincipal UserDetails userDetails) {
    try {
        logger.info("=== GET NOTIFICATIONS REQUEST ===");
        logger.info("User: {}", userDetails != null ? userDetails.getUsername() : "null");
        
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        // Get current user's notifications from service
        Map<String, Object> response = notificationService.getUserNotifications(userDetails);
        
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        logger.error("❌ Error fetching notifications: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
    }
}

    // Update user's notification settings
    @PutMapping("/settings")
    public ResponseEntity<?> updateNotificationSettings(@RequestBody Map<String, Object> settingsData,
                                                       @AuthenticationPrincipal UserDetails userDetails,
                                                       HttpServletRequest request) {
        try {
            logger.info("=== UPDATE NOTIFICATION SETTINGS REQUEST ===");
            logger.info("Request URI: {}", request.getRequestURI());
            logger.info("UserDetails: {}", userDetails != null ? userDetails.getUsername() : "NULL");
            
            if (userDetails == null) {
                logger.error("UserDetails is null - authentication failed");
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            if (settingsData == null) {
                logger.warn("Settings data is null");
                return ResponseEntity.badRequest().body("Settings data is required");
            }

            Map<String, Object> response = notificationService.updateNotificationSettings(settingsData, userDetails);
            
            if ((Boolean) response.get("success")) {
                logger.info("Successfully updated notification settings for user: {}", userDetails.getUsername());
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Failed to update notification settings: {}", response.get("message"));
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in updateNotificationSettings: ", e);
            return ResponseEntity.badRequest().body("Error updating notification settings: " + e.getMessage());
        }
    }

    // Send test notification
    @PostMapping("/test")
    public ResponseEntity<?> sendTestNotification(@AuthenticationPrincipal UserDetails userDetails,
                                                 HttpServletRequest request) {
        try {
            logger.info("=== TEST NOTIFICATION REQUEST ===");
            logger.info("Request URI: {}", request.getRequestURI());
            logger.info("UserDetails: {}", userDetails != null ? userDetails.getUsername() : "NULL");
            
            if (userDetails == null) {
                logger.error("UserDetails is null - authentication failed");
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            Map<String, Object> response = notificationService.sendTestNotification(userDetails);
            
            if ((Boolean) response.get("success")) {
                logger.info("Successfully sent test notification for user: {}", userDetails.getUsername());
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Failed to send test notification: {}", response.get("message"));
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in sendTestNotification: ", e);
            return ResponseEntity.badRequest().body("Error sending test notification: " + e.getMessage());
        }
    }

    // Reset notification settings to defaults
    @PostMapping("/reset")
    public ResponseEntity<?> resetToDefaults(@AuthenticationPrincipal UserDetails userDetails,
                                            HttpServletRequest request) {
        try {
            logger.info("=== RESET NOTIFICATION SETTINGS REQUEST ===");
            logger.info("Request URI: {}", request.getRequestURI());
            logger.info("UserDetails: {}", userDetails != null ? userDetails.getUsername() : "NULL");
            
            if (userDetails == null) {
                logger.error("UserDetails is null - authentication failed");
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            Map<String, Object> response = notificationService.resetToDefaults(userDetails);
            
            if ((Boolean) response.get("success")) {
                logger.info("Successfully reset notification settings for user: {}", userDetails.getUsername());
                return ResponseEntity.ok(response);
            } else {
                logger.warn("Failed to reset notification settings: {}", response.get("message"));
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in resetToDefaults: ", e);
            return ResponseEntity.badRequest().body("Error resetting notification settings: " + e.getMessage());
        }
    }

    // Send appointment reminder notification
    @PostMapping("/appointment-reminder")
    public ResponseEntity<?> sendAppointmentReminder(@RequestBody Map<String, Object> appointmentData,
                                                    @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            Long appointmentId = Long.valueOf(appointmentData.get("appointmentId").toString());
            String reminderType = appointmentData.get("reminderType").toString();

            if (appointmentId == null || reminderType == null) {
                return ResponseEntity.badRequest().body("Appointment ID and reminder type are required");
            }

            Map<String, Object> response = notificationService.sendAppointmentReminder(
                appointmentId, reminderType, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in sendAppointmentReminder: ", e);
            return ResponseEntity.badRequest().body("Error sending appointment reminder: " + e.getMessage());
        }
    }

    // Send medical result notification
    @PostMapping("/result-available")
    public ResponseEntity<?> sendResultNotification(@RequestBody Map<String, Object> resultData,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            Long resultId = Long.valueOf(resultData.get("resultId").toString());
            String testName = resultData.get("testName").toString();

            if (resultId == null || testName == null) {
                return ResponseEntity.badRequest().body("Result ID and test name are required");
            }

            Map<String, Object> response = notificationService.sendResultNotification(
                resultId, testName, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in sendResultNotification: ", e);
            return ResponseEntity.badRequest().body("Error sending result notification: " + e.getMessage());
        }
    }

    // Send security alert notification
    @PostMapping("/security-alert")
    public ResponseEntity<?> sendSecurityAlert(@RequestBody Map<String, String> alertData,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            String alertType = alertData.get("alertType");
            String message = alertData.get("message");

            if (alertType == null || message == null) {
                return ResponseEntity.badRequest().body("Alert type and message are required");
            }

            Map<String, Object> response = notificationService.sendSecurityAlert(
                alertType, message, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in sendSecurityAlert: ", e);
            return ResponseEntity.badRequest().body("Error sending security alert: " + e.getMessage());
        }
    }

    // Get notification history
    @GetMapping("/history")
    public ResponseEntity<?> getNotificationHistory(@AuthenticationPrincipal UserDetails userDetails,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            if (page < 0 || size <= 0 || size > 100) {
                return ResponseEntity.badRequest().body("Invalid pagination parameters");
            }

            Map<String, Object> response = notificationService.getNotificationHistory(
                userDetails, page, size);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in getNotificationHistory: ", e);
            return ResponseEntity.badRequest().body("Error fetching notification history: " + e.getMessage());
        }
    }

    // Mark notification as read
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long notificationId,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            if (notificationId == null) {
                return ResponseEntity.badRequest().body("Notification ID is required");
            }

            Map<String, Object> response = notificationService.markAsRead(notificationId, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in markAsRead: ", e);
            return ResponseEntity.badRequest().body("Error marking notification as read: " + e.getMessage());
        }
    }

    // Delete notification
    @DeleteMapping("/{notificationId}")
    public ResponseEntity<?> deleteNotification(@PathVariable Long notificationId,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            if (notificationId == null) {
                return ResponseEntity.badRequest().body("Notification ID is required");
            }

            Map<String, Object> response = notificationService.deleteNotification(notificationId, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in deleteNotification: ", e);
            return ResponseEntity.badRequest().body("Error deleting notification: " + e.getMessage());
        }
    }

    // Update user's device token for push notifications
    @PostMapping("/device-token")
    public ResponseEntity<?> updateDeviceToken(@RequestBody Map<String, String> tokenData,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            String deviceToken = tokenData.get("deviceToken");
            String platform = tokenData.get("platform");

            if (deviceToken == null || platform == null) {
                return ResponseEntity.badRequest().body("Device token and platform are required");
            }

            Map<String, Object> response = notificationService.updateDeviceToken(
                deviceToken, platform, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in updateDeviceToken: ", e);
            return ResponseEntity.badRequest().body("Error updating device token: " + e.getMessage());
        }
    }

    // Get notification preferences summary
    @GetMapping("/preferences-summary")
    public ResponseEntity<?> getPreferencesSummary(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            Map<String, Object> response = notificationService.getPreferencesSummary(userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in getPreferencesSummary: ", e);
            return ResponseEntity.badRequest().body("Error fetching preferences summary: " + e.getMessage());
        }
    }

    // Bulk mark notifications as read
    @PutMapping("/mark-all-read")
    public ResponseEntity<?> markAllAsRead(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            Map<String, Object> response = notificationService.markAllAsRead(userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Exception in markAllAsRead: ", e);
            return ResponseEntity.badRequest().body("Error marking all notifications as read: " + e.getMessage());
        }
    }

    // Check if notifications are enabled for specific category
    @GetMapping("/enabled/{category}")
    public ResponseEntity<?> isNotificationEnabled(@PathVariable String category,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        try {
            if (userDetails == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }
            
            if (category == null || category.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Category is required");
            }

            Map<String, Object> response = notificationService.isNotificationEnabled(category, userDetails);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Exception in isNotificationEnabled: ", e);
            return ResponseEntity.badRequest().body("Error checking notification status: " + e.getMessage());
        }
    }
}