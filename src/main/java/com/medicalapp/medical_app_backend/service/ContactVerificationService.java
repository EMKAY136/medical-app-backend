package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class ContactVerificationService {

    @Autowired
    private UserRepository userRepository;

    // Store verification codes temporarily (use Redis in production)
    private final Map<String, VerificationSession> verificationSessions = new HashMap<>();

    // Send verification code for contact updates
    public Map<String, Object> sendVerificationCode(String type, String newValue, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            // Validate the new value based on type
            if (!isValidContactValue(type, newValue)) {
                response.put("success", false);
                response.put("message", "Invalid " + type + " format");
                return response;
            }

            // Check if the new value is different from current
            if (isDuplicate(type, newValue, user)) {
                response.put("success", false);
                response.put("message", "New " + type + " must be different from current");
                return response;
            }

            // Generate verification code
            String code = generateVerificationCode();
            
            // Create verification session
            VerificationSession session = new VerificationSession();
            session.userId = user.getId();
            session.type = type;
            session.newValue = newValue;
            session.code = code;
            session.timestamp = Instant.now();
            session.attempts = 0;

            String sessionKey = user.getUsername() + "_" + type;
            verificationSessions.put(sessionKey, session);

            // Send verification code
            boolean sent = sendCode(type, newValue, code);
            
            if (sent) {
                response.put("success", true);
                response.put("message", "Verification code sent successfully");
                response.put("maskedTarget", maskContactValue(type, newValue));
            } else {
                response.put("success", false);
                response.put("message", "Failed to send verification code");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error sending verification: " + e.getMessage());
        }

        return response;
    }

    // Verify code for contact updates
    public Map<String, Object> verifyCode(String type, String code, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            String sessionKey = userDetails.getUsername() + "_" + type;
            VerificationSession session = verificationSessions.get(sessionKey);

            if (session == null) {
                response.put("success", false);
                response.put("message", "No verification session found");
                return response;
            }

            // Check if session expired (10 minutes)
            if (session.timestamp.isBefore(Instant.now().minusSeconds(600))) {
                verificationSessions.remove(sessionKey);
                response.put("success", false);
                response.put("message", "Verification code expired");
                return response;
            }

            // Check attempt limit
            if (session.attempts >= 3) {
                verificationSessions.remove(sessionKey);
                response.put("success", false);
                response.put("message", "Too many failed attempts");
                return response;
            }

            session.attempts++;

            // Verify code
            if (session.code.equals(code)) {
                session.verified = true;
                response.put("success", true);
                response.put("message", "Code verified successfully");
            } else {
                response.put("success", false);
                response.put("message", "Invalid verification code");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error verifying code: " + e.getMessage());
        }

        return response;
    }

    // Resend verification code
    public Map<String, Object> resendVerificationCode(String type, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            String sessionKey = userDetails.getUsername() + "_" + type;
            VerificationSession session = verificationSessions.get(sessionKey);

            if (session == null) {
                response.put("success", false);
                response.put("message", "No active verification session");
                return response;
            }

            // Rate limiting: allow resend only after 60 seconds
            if (session.timestamp.isAfter(Instant.now().minusSeconds(60))) {
                response.put("success", false);
                response.put("message", "Please wait before requesting another code");
                return response;
            }

            // Generate new code
            String newCode = generateVerificationCode();
            session.code = newCode;
            session.timestamp = Instant.now();
            session.attempts = 0;

            // Send new code
            boolean sent = sendCode(type, session.newValue, newCode);
            
            if (sent) {
                response.put("success", true);
                response.put("message", "Verification code resent successfully");
            } else {
                response.put("success", false);
                response.put("message", "Failed to resend verification code");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error resending code: " + e.getMessage());
        }

        return response;
    }

    // Check if contact update is verified (called from UserService)
    public boolean isContactUpdateVerified(String type, String newValue, UserDetails userDetails) {
        String sessionKey = userDetails.getUsername() + "_" + type;
        VerificationSession session = verificationSessions.get(sessionKey);
        
        if (session == null) {
            return false;
        }

        // Check if verified and values match
        return session.verified && session.newValue.equals(newValue);
    }

    // Clear verification session (called after successful update)
    public void clearVerificationSession(String type, UserDetails userDetails) {
        String sessionKey = userDetails.getUsername() + "_" + type;
        verificationSessions.remove(sessionKey);
    }

    // ==================== UTILITY METHODS ====================

    private String generateVerificationCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    private boolean isValidContactValue(String type, String value) {
        switch (type) {
            case "email":
            case "backup":
                return value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
            case "phone":
                return value.matches("^\\+[1-9]\\d{10,14}$");
            default:
                return false;
        }
    }

    private boolean isDuplicate(String type, String newValue, User user) {
        switch (type) {
            case "email":
                return newValue.equals(user.getEmail());
            case "phone":
                return newValue.equals(user.getPhone());
            case "backup":
                return newValue.equals(user.getBackupEmail());
            default:
                return false;
        }
    }

    private String maskContactValue(String type, String value) {
        if ("phone".equals(type)) {
            if (value.length() <= 4) return value;
            String visible = value.substring(value.length() - 4);
            return "****" + visible;
        } else {
            // Email masking
            int atIndex = value.indexOf('@');
            if (atIndex <= 1) return value;
            String username = value.substring(0, atIndex);
            String domain = value.substring(atIndex);
            String maskedUsername = username.charAt(0) + "***" + username.charAt(username.length() - 1);
            return maskedUsername + domain;
        }
    }

    private boolean sendCode(String type, String target, String code) {
        try {
            if ("phone".equals(type)) {
                // Send SMS - integrate with SMS provider
                System.out.println("SMS to " + target + ": Your verification code is " + code);
                return true;
            } else {
                // Send email - integrate with email provider
                System.out.println("Email to " + target + ": Your verification code is " + code);
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== INNER CLASS ====================

    private static class VerificationSession {
        Long userId;
        String type;
        String newValue;
        String code;
        Instant timestamp;
        int attempts;
        boolean verified = false;
    }
}