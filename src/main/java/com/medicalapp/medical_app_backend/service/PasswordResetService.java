// PasswordResetService.java
package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.entity.PasswordResetToken;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import com.medicalapp.medical_app_backend.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
public class PasswordResetService {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private NotificationService notificationService;

    // Generate 6-digit code
    private String generateResetCode() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    public Map<String, Object> initiatePasswordReset(String email, String method) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Always return success to prevent email enumeration attacks
            response.put("success", true);
            response.put("message", "If an account with this email exists, you will receive a reset code shortly.");
            response.put("timestamp", LocalDateTime.now());

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                logger.warn("Password reset requested for non-existent email: {}", email);
                return response; // Still return success for security
            }

            User user = userOpt.get();
            String resetCode = generateResetCode();

            // Delete any existing tokens for this user
            passwordResetTokenRepository.deleteByUser(user);

            // Create new reset token
            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(resetCode);
            resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(10)); // 10 minute expiry
            resetToken.setUsed(false);
            passwordResetTokenRepository.save(resetToken);

            // Send code via email or SMS
            if ("sms".equals(method) && user.getPhone() != null && !user.getPhone().isEmpty()) {
                notificationService.sendSMS(user.getPhone(), 
                    "Your password reset code is: " + resetCode + ". Valid for 10 minutes.");
                logger.info("Reset code sent via SMS to user: {}", user.getUsername());
            } else {
                notificationService.sendEmail(user.getEmail(), 
                    "Password Reset Code", 
                    "Your password reset code is: " + resetCode + ". This code is valid for 10 minutes. If you didn't request this, please ignore this email.");
                logger.info("Reset code sent via email to user: {}", user.getUsername());
            }

        } catch (Exception e) {
            logger.error("Error initiating password reset: ", e);
            // Still return success for security
        }

        return response;
    }

    public Map<String, Object> verifyResetCode(String email, String code) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Invalid email or code");
                response.put("timestamp", LocalDateTime.now());
                return response;
            }

            User user = userOpt.get();
            Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository
                .findByUserAndTokenAndUsed(user, code, false);

            if (tokenOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Invalid or expired reset code");
                response.put("timestamp", LocalDateTime.now());
                return response;
            }

            PasswordResetToken token = tokenOpt.get();
            if (token.getExpiryDate().isBefore(LocalDateTime.now())) {
                response.put("success", false);
                response.put("message", "Reset code has expired. Please request a new one.");
                response.put("timestamp", LocalDateTime.now());
                return response;
            }

            response.put("success", true);
            response.put("message", "Code verified successfully");
            response.put("timestamp", LocalDateTime.now());

            logger.info("Reset code verified for user: {}", user.getUsername());

        } catch (Exception e) {
            logger.error("Error verifying reset code: ", e);
            response.put("success", false);
            response.put("message", "Error verifying code");
            response.put("timestamp", LocalDateTime.now());
        }

        return response;
    }

    public Map<String, Object> resetPasswordWithCode(String email, String code, String newPassword) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Verify code first
            Map<String, Object> verifyResponse = verifyResetCode(email, code);
            if (!(Boolean) verifyResponse.get("success")) {
                return verifyResponse;
            }

            Optional<User> userOpt = userRepository.findByEmail(email);
            User user = userOpt.get();
            PasswordResetToken token = passwordResetTokenRepository
                .findByUserAndTokenAndUsed(user, code, false).get();

            // Validate new password
            if (newPassword.length() < 8) {
                response.put("success", false);
                response.put("message", "Password must be at least 8 characters long");
                response.put("timestamp", LocalDateTime.now());
                return response;
            }

            // Update password
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            // Mark token as used
            token.setUsed(true);
            passwordResetTokenRepository.save(token);

            response.put("success", true);
            response.put("message", "Password has been reset successfully");
            response.put("timestamp", LocalDateTime.now());

            logger.info("Password reset successfully for user: {}", user.getUsername());

        } catch (Exception e) {
            logger.error("Error resetting password: ", e);
            response.put("success", false);
            response.put("message", "Error resetting password");
            response.put("timestamp", LocalDateTime.now());
        }

        return response;
    }

    public Map<String, Object> resendResetCode(String email, String method) {
        // Clean up expired tokens first
        passwordResetTokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());
        
        return initiatePasswordReset(email, method);
    }
}
