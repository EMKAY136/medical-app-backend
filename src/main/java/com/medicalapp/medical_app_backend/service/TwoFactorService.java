package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class TwoFactorService {

    @Autowired
    private UserRepository userRepository;

    // Store temporary setup data (in production, use Redis or database)
    private final Map<String, TwoFactorSetup> setupCache = new HashMap<>();
    
    // Store sent SMS codes (in production, use Redis with expiration)
    private final Map<String, SMSCode> smsCodeCache = new HashMap<>();

    // Setup Two-Factor Authentication
    public Map<String, Object> setup2FA(String method, String phoneNumber, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            if ("sms".equals(method)) {
                return setupSMS2FA(phoneNumber, user, response);
            } else if ("app".equals(method)) {
                return setupApp2FA(user, response);
            } else {
                response.put("success", false);
                response.put("message", "Invalid method. Use 'sms' or 'app'");
                return response;
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error setting up 2FA: " + e.getMessage());
            return response;
        }
    }

    // Setup SMS-based 2FA
    private Map<String, Object> setupSMS2FA(String phoneNumber, User user, Map<String, Object> response) {
        try {
            // Validate phone number format
            if (!isValidPhoneNumber(phoneNumber)) {
                response.put("success", false);
                response.put("message", "Invalid phone number format");
                return response;
            }

            // Generate and send SMS code
            String code = generateSMSCode();
            
            // Store setup data temporarily
            TwoFactorSetup setup = new TwoFactorSetup();
            setup.userId = user.getId();
            setup.method = "sms";
            setup.phoneNumber = phoneNumber;
            setup.timestamp = Instant.now();
            
            setupCache.put(user.getUsername(), setup);
            
            // Store SMS code
            SMSCode smsCode = new SMSCode();
            smsCode.code = code;
            smsCode.phoneNumber = phoneNumber;
            smsCode.timestamp = Instant.now();
            smsCode.attempts = 0;
            
            smsCodeCache.put(user.getUsername(), smsCode);
            
            // In production, send actual SMS here
            boolean smsSent = sendSMSCode(phoneNumber, code);
            
            if (smsSent) {
                response.put("success", true);
                response.put("message", "SMS code sent successfully");
                response.put("phoneNumber", maskPhoneNumber(phoneNumber));
            } else {
                response.put("success", false);
                response.put("message", "Failed to send SMS code");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error setting up SMS 2FA: " + e.getMessage());
        }
        
        return response;
    }

    // Setup Authenticator App-based 2FA
    private Map<String, Object> setupApp2FA(User user, Map<String, Object> response) {
        try {
            // Generate secret key for TOTP
            String secret = generateTOTPSecret();
            
            // Generate QR code URL
            String qrCodeUrl = generateQRCodeUrl(user.getEmail(), secret);
            
            // Store setup data temporarily
            TwoFactorSetup setup = new TwoFactorSetup();
            setup.userId = user.getId();
            setup.method = "app";
            setup.secret = secret;
            setup.timestamp = Instant.now();
            
            setupCache.put(user.getUsername(), setup);
            
            response.put("success", true);
            response.put("message", "Authenticator app setup ready");
            response.put("secret", secret);
            response.put("qrCodeUrl", qrCodeUrl);
            response.put("manualEntryKey", secret);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error setting up authenticator app: " + e.getMessage());
        }
        
        return response;
    }

    // Verify Two-Factor Authentication Setup
    public Map<String, Object> verify2FA(String code, String method, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            TwoFactorSetup setup = setupCache.get(user.getUsername());
            
            if (setup == null) {
                response.put("success", false);
                response.put("message", "No 2FA setup in progress");
                return response;
            }
            
            // Check if setup has expired (30 minutes)
            if (setup.timestamp.isBefore(Instant.now().minusSeconds(1800))) {
                setupCache.remove(user.getUsername());
                response.put("success", false);
                response.put("message", "Setup session expired. Please restart setup");
                return response;
            }
            
            boolean isValidCode = false;
            
            if ("sms".equals(method)) {
                isValidCode = verifySMSCode(code, user.getUsername());
            } else if ("app".equals(method)) {
                isValidCode = verifyTOTPCode(code, setup.secret);
            }
            
            if (isValidCode) {
                // Enable 2FA for user
                user.setTwoFactorEnabled(true);
                user.setTwoFactorMethod(method);
                
                if ("sms".equals(method)) {
                    user.setPhone(setup.phoneNumber);
                } else if ("app".equals(method)) {
                    user.setTwoFactorSecret(setup.secret);
                }
                
                // Generate backup codes
                List<String> backupCodes = generateBackupCodes();
                user.setBackupCodes(String.join(",", backupCodes));
                
                userRepository.save(user);
                
                // Clean up temporary data
                setupCache.remove(user.getUsername());
                smsCodeCache.remove(user.getUsername());
                
                response.put("success", true);
                response.put("message", "Two-factor authentication enabled successfully");
                response.put("backupCodes", backupCodes);
                
            } else {
                response.put("success", false);
                response.put("message", "Invalid verification code");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error verifying 2FA: " + e.getMessage());
        }
        
        return response;
    }

    // Disable Two-Factor Authentication
    public Map<String, Object> disable2FA(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            // Disable 2FA
            user.setTwoFactorEnabled(false);
            user.setTwoFactorMethod(null);
            user.setTwoFactorSecret(null);
            user.setBackupCodes(null);
            
            userRepository.save(user);
            
            response.put("success", true);
            response.put("message", "Two-factor authentication disabled successfully");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error disabling 2FA: " + e.getMessage());
        }
        
        return response;
    }

    // Resend SMS Code
    public Map<String, Object> resendSMSCode(String phoneNumber, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            SMSCode existingCode = smsCodeCache.get(userDetails.getUsername());
            
            // Rate limiting: allow resend only after 60 seconds
            if (existingCode != null && 
                existingCode.timestamp.isAfter(Instant.now().minusSeconds(60))) {
                response.put("success", false);
                response.put("message", "Please wait before requesting another code");
                return response;
            }
            
            // Generate and send new SMS code
            String code = generateSMSCode();
            
            SMSCode smsCode = new SMSCode();
            smsCode.code = code;
            smsCode.phoneNumber = phoneNumber;
            smsCode.timestamp = Instant.now();
            smsCode.attempts = 0;
            
            smsCodeCache.put(userDetails.getUsername(), smsCode);
            
            boolean smsSent = sendSMSCode(phoneNumber, code);
            
            if (smsSent) {
                response.put("success", true);
                response.put("message", "SMS code resent successfully");
            } else {
                response.put("success", false);
                response.put("message", "Failed to send SMS code");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error resending SMS: " + e.getMessage());
        }
        
        return response;
    }

    // Generate New Backup Codes
    public Map<String, Object> generateBackupCodes(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            if (!user.isTwoFactorEnabled()) {
                response.put("success", false);
                response.put("message", "Two-factor authentication is not enabled");
                return response;
            }
            
            List<String> backupCodes = generateBackupCodes();
            user.setBackupCodes(String.join(",", backupCodes));
            
            userRepository.save(user);
            
            response.put("success", true);
            response.put("message", "New backup codes generated successfully");
            response.put("backupCodes", backupCodes);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error generating backup codes: " + e.getMessage());
        }
        
        return response;
    }

    // Get Two-Factor Authentication Status
    public Map<String, Object> get2FAStatus(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            Map<String, Object> status = new HashMap<>();
            status.put("enabled", user.isTwoFactorEnabled());
            status.put("method", user.getTwoFactorMethod());
            
            if (user.isTwoFactorEnabled() && user.getBackupCodes() != null) {
                String[] codes = user.getBackupCodes().split(",");
                status.put("backupCodesCount", codes.length);
            } else {
                status.put("backupCodesCount", 0);
            }
            
            response.put("success", true);
            response.put("status", status);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching 2FA status: " + e.getMessage());
        }
        
        return response;
    }

    // ==================== UTILITY METHODS ====================

    // Generate 6-digit SMS code
    private String generateSMSCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    // Generate TOTP secret (Base32)
    private String generateTOTPSecret() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Generate QR code URL for authenticator apps
    private String generateQRCodeUrl(String email, String secret) {
        return String.format(
            "otpauth://totp/Qualitest%%20Medical:%s?secret=%s&issuer=Qualitest%%20Medical",
            email, secret
        );
    }

    // Verify SMS code
    private boolean verifySMSCode(String inputCode, String username) {
        SMSCode storedCode = smsCodeCache.get(username);
        
        if (storedCode == null) {
            return false;
        }
        
        // Check if code has expired (10 minutes)
        if (storedCode.timestamp.isBefore(Instant.now().minusSeconds(600))) {
            smsCodeCache.remove(username);
            return false;
        }
        
        // Check attempt limit
        if (storedCode.attempts >= 5) {
            smsCodeCache.remove(username);
            return false;
        }
        
        storedCode.attempts++;
        
        return storedCode.code.equals(inputCode);
    }

    // Verify TOTP code (Time-based One-Time Password)
    private boolean verifyTOTPCode(String inputCode, String secret) {
        try {
            long timeWindow = Instant.now().getEpochSecond() / 30;
            
            // Check current time window and adjacent windows (±1)
            for (int i = -1; i <= 1; i++) {
                String expectedCode = generateTOTP(secret, timeWindow + i);
                if (expectedCode.equals(inputCode)) {
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // Generate TOTP code
    private String generateTOTP(String secret, long timeWindow) throws Exception {
        byte[] secretBytes = Base64.getDecoder().decode(secret);
        byte[] timeBytes = longToBytes(timeWindow);
        
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, "HmacSHA1");
        mac.init(secretKeySpec);
        
        byte[] hash = mac.doFinal(timeBytes);
        int offset = hash[hash.length - 1] & 0x0F;
        
        int binary = ((hash[offset] & 0x7F) << 24) |
                    ((hash[offset + 1] & 0xFF) << 16) |
                    ((hash[offset + 2] & 0xFF) << 8) |
                    (hash[offset + 3] & 0xFF);
        
        int otp = binary % 1000000;
        return String.format("%06d", otp);
    }

    // Convert long to byte array
    private byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return result;
    }

    // Generate backup codes
    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        SecureRandom random = new SecureRandom();
        
        for (int i = 0; i < 6; i++) {
            int code1 = 1000 + random.nextInt(9000);
            int code2 = 1000 + random.nextInt(9000);
            codes.add(code1 + "-" + code2);
        }
        
        return codes;
    }

    // Validate phone number format
    private boolean isValidPhoneNumber(String phoneNumber) {
        // Basic validation for international phone numbers
        String cleaned = phoneNumber.replaceAll("\\s+", "");
        return cleaned.matches("^\\+[1-9]\\d{10,14}$");
    }

    // Mask phone number for display
    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber.length() <= 4) {
            return phoneNumber;
        }
        String visible = phoneNumber.substring(phoneNumber.length() - 4);
        return "****" + visible;
    }

    // Send SMS code (mock implementation - integrate with SMS provider)
    private boolean sendSMSCode(String phoneNumber, String code) {
        // In production, integrate with SMS providers like:
        // - Twilio
        // - AWS SNS
        // - Firebase Cloud Messaging
        // - African SMS providers like Bulk SMS Nigeria
        
        System.out.println("SMS Code sent to " + phoneNumber + ": " + code);
        
        // Simulate SMS sending
        return true;
    }

    // ==================== INNER CLASSES ====================

    private static class TwoFactorSetup {
        Long userId;
        String method;
        String phoneNumber;
        String secret;
        Instant timestamp;
    }

    private static class SMSCode {
        String code;
        String phoneNumber;
        Instant timestamp;
        int attempts;
    }
}