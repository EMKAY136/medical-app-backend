// PasswordResetController.java
package com.medicalapp.medical_app_backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.medicalapp.medical_app_backend.service.PasswordResetService;
import com.medicalapp.medical_app_backend.service.NotificationService;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetController.class);

    @Autowired
    private PasswordResetService passwordResetService;

    @Autowired
    private NotificationService notificationService;

    // Send password reset code to email or SMS
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        logger.info("=== FORGOT PASSWORD REQUEST ===");
        logger.info("Request from: {}", httpRequest.getRemoteAddr());
        
        try {
            String email = request.get("email");
            String method = request.getOrDefault("method", "email"); // "email" or "sms"
            
            if (email == null || email.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Email is required");
                errorResponse.put("timestamp", LocalDateTime.now());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            logger.info("Password reset request for email: {} via {}", email, method);
            
            Map<String, Object> response = passwordResetService.initiatePasswordReset(email, method);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error in forgot password: ", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error processing password reset request");
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Verify reset code
    @PostMapping("/verify-reset-code")
    public ResponseEntity<Map<String, Object>> verifyResetCode(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        logger.info("=== VERIFY RESET CODE REQUEST ===");
        logger.info("Request from: {}", httpRequest.getRemoteAddr());
        
        try {
            String email = request.get("email");
            String code = request.get("code");
            
            if (email == null || code == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Email and code are required");
                errorResponse.put("timestamp", LocalDateTime.now());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            if (code.length() != 6 || !code.matches("\\d{6}")) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Code must be 6 digits");
                errorResponse.put("timestamp", LocalDateTime.now());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            logger.info("Verifying reset code for email: {}", email);
            
            Map<String, Object> response = passwordResetService.verifyResetCode(email, code);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error verifying reset code: ", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error verifying reset code");
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Reset password with verified code
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        logger.info("=== RESET PASSWORD REQUEST ===");
        logger.info("Request from: {}", httpRequest.getRemoteAddr());
        
        try {
            String email = request.get("email");
            String code = request.get("code");
            String newPassword = request.get("newPassword");
            
            if (email == null || code == null || newPassword == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Email, code, and new password are required");
                errorResponse.put("timestamp", LocalDateTime.now());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            logger.info("Resetting password for email: {}", email);
            
            Map<String, Object> response = passwordResetService.resetPasswordWithCode(email, code, newPassword);
            
            if ((Boolean) response.get("success")) {
                // Send notification about password change
                try {
                    notificationService.sendPasswordChangeNotification(email);
                } catch (Exception e) {
                    logger.warn("Failed to send password change notification: {}", e.getMessage());
                }
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error resetting password: ", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error resetting password");
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Resend reset code
    @PostMapping("/resend-reset-code")
    public ResponseEntity<Map<String, Object>> resendResetCode(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        logger.info("=== RESEND RESET CODE REQUEST ===");
        logger.info("Request from: {}", httpRequest.getRemoteAddr());
        
        try {
            String email = request.get("email");
            String method = request.getOrDefault("method", "email");
            
            if (email == null || email.trim().isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Email is required");
                errorResponse.put("timestamp", LocalDateTime.now());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            logger.info("Resending reset code for email: {} via {}", email, method);
            
            Map<String, Object> response = passwordResetService.resendResetCode(email, method);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error resending reset code: ", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error resending reset code");
            errorResponse.put("timestamp", LocalDateTime.now());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
