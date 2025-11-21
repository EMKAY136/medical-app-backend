package com.medicalapp.medical_app_backend.controller;

import com.medicalapp.medical_app_backend.service.UserService;
import com.medicalapp.medical_app_backend.service.TwoFactorService;
import com.medicalapp.medical_app_backend.service.ContactVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;
    
    @Autowired
    private TwoFactorService twoFactorService;
    
    @Autowired
    private ContactVerificationService contactVerificationService;

    // ==================== PROFILE ENDPOINTS ====================

    // Get current user profile
    @GetMapping("/profile")
    public ResponseEntity<?> getUserProfile(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = userService.getUserProfile(userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching profile: " + e.getMessage());
        }
    }

    // Get user profile by ID (for admin dashboard)
    @GetMapping("/profile/{userId}")
    public ResponseEntity<?> getUserProfileById(@PathVariable Long userId,
                                               @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = userService.getUserProfileById(userId, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(403).body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching profile: " + e.getMessage());
        }
    }

    // Update user profile - USING SIMPLE METHOD WITH HEALTH FIELDS
    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody Map<String, Object> updates,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        try {
            // Use the simple method that now accepts health fields
            Map<String, Object> response = userService.updateUserProfileSimple(updates, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating profile: " + e.getMessage());
        }
    }

    // Update user profile with verification - FOR SENSITIVE CHANGES
    @PutMapping("/profile/verified")
    public ResponseEntity<?> updateUserProfileWithVerification(@RequestBody Map<String, Object> updates,
                                                              @AuthenticationPrincipal UserDetails userDetails) {
        try {
            // Use the complex method that requires verification for sensitive changes
            Map<String, Object> response = userService.updateUserProfile(updates, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating profile: " + e.getMessage());
        }
    }

    // Update health information specifically
    @PutMapping("/health-info")
    public ResponseEntity<?> updateHealthInfo(@RequestBody Map<String, Object> healthData,
                                            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = userService.updateHealthInfo(healthData, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error updating health info: " + e.getMessage());
        }
    }

    // Get health information by user ID (for admin)
    @GetMapping("/health-info/{userId}")
    public ResponseEntity<?> getHealthInfo(@PathVariable Long userId,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = userService.getHealthInfo(userId, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(403).body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching health info: " + e.getMessage());
        }
    }

    // Change password
    @PutMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> passwordData,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String currentPassword = passwordData.get("currentPassword");
            String newPassword = passwordData.get("newPassword");

            if (currentPassword == null || newPassword == null) {
                return ResponseEntity.badRequest().body("Current password and new password are required");
            }

            Map<String, Object> response = userService.changePassword(currentPassword, newPassword, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error changing password: " + e.getMessage());
        }
    }

    // Get user statistics
    @GetMapping("/stats")
    public ResponseEntity<?> getUserStatistics(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = userService.getUserStatistics(userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching statistics: " + e.getMessage());
        }
    }

    // Delete user account
    @DeleteMapping("/account")
    public ResponseEntity<?> deleteAccount(@RequestBody Map<String, String> passwordData,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String password = passwordData.get("password");

            if (password == null) {
                return ResponseEntity.badRequest().body("Password is required to delete account");
            }

            Map<String, Object> response = userService.deleteUserAccount(password, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error deleting account: " + e.getMessage());
        }
    }

    // ==================== CONTACT VERIFICATION ENDPOINTS ====================

    // Send verification code for contact updates
    @PostMapping("/send-verification")
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> requestData,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String type = requestData.get("type"); // "email", "phone", "backup"
            String newValue = requestData.get("newValue");

            if (type == null || newValue == null) {
                return ResponseEntity.badRequest().body("Type and new value are required");
            }

            Map<String, Object> response = contactVerificationService.sendVerificationCode(type, newValue, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error sending verification code: " + e.getMessage());
        }
    }

    // Verify code for contact updates
    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> requestData,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String type = requestData.get("type");
            String code = requestData.get("code");

            if (type == null || code == null) {
                return ResponseEntity.badRequest().body("Type and code are required");
            }

            if (code.length() != 6 || !code.matches("\\d{6}")) {
                return ResponseEntity.badRequest().body("Code must be 6 digits");
            }

            Map<String, Object> response = contactVerificationService.verifyCode(type, code, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error verifying code: " + e.getMessage());
        }
    }

    // Resend verification code
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerificationCode(@RequestBody Map<String, String> requestData,
                                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String type = requestData.get("type");

            if (type == null) {
                return ResponseEntity.badRequest().body("Type is required");
            }

            Map<String, Object> response = contactVerificationService.resendVerificationCode(type, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error resending verification code: " + e.getMessage());
        }
    }

    // ==================== TWO-FACTOR AUTHENTICATION ENDPOINTS ====================

    // Setup Two-Factor Authentication
    @PostMapping("/2fa/setup")
    public ResponseEntity<?> setup2FA(@RequestBody Map<String, String> setupData,
                                     @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String method = setupData.get("method"); // 'sms' or 'app'
            String phoneNumber = setupData.get("phoneNumber"); // for SMS method

            if (method == null) {
                return ResponseEntity.badRequest().body("Method is required (sms or app)");
            }

            if ("sms".equals(method) && phoneNumber == null) {
                return ResponseEntity.badRequest().body("Phone number is required for SMS method");
            }

            Map<String, Object> response = twoFactorService.setup2FA(method, phoneNumber, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error setting up 2FA: " + e.getMessage());
        }
    }

    // Verify Two-Factor Authentication Setup
    @PostMapping("/2fa/verify")
    public ResponseEntity<?> verify2FA(@RequestBody Map<String, String> verifyData,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String code = verifyData.get("code");
            String method = verifyData.get("method");

            if (code == null || method == null) {
                return ResponseEntity.badRequest().body("Code and method are required");
            }

            if (code.length() != 6 || !code.matches("\\d{6}")) {
                return ResponseEntity.badRequest().body("Code must be 6 digits");
            }

            Map<String, Object> response = twoFactorService.verify2FA(code, method, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error verifying 2FA: " + e.getMessage());
        }
    }

    // Disable Two-Factor Authentication
    @PostMapping("/2fa/disable")
    public ResponseEntity<?> disable2FA(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = twoFactorService.disable2FA(userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error disabling 2FA: " + e.getMessage());
        }
    }

    // Resend SMS Code
    @PostMapping("/2fa/resend")
    public ResponseEntity<?> resend2FACode(@RequestBody Map<String, String> resendData,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        try {
            String method = resendData.get("method");
            String phoneNumber = resendData.get("phoneNumber");

            if (!"sms".equals(method)) {
                return ResponseEntity.badRequest().body("Resend is only available for SMS method");
            }

            if (phoneNumber == null) {
                return ResponseEntity.badRequest().body("Phone number is required");
            }

            Map<String, Object> response = twoFactorService.resendSMSCode(phoneNumber, userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error resending code: " + e.getMessage());
        }
    }

    // Generate New Backup Codes
    @PostMapping("/2fa/backup-codes")
    public ResponseEntity<?> generateBackupCodes(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = twoFactorService.generateBackupCodes(userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error generating backup codes: " + e.getMessage());
        }
    }

    // Get Two-Factor Authentication Status
    @GetMapping("/2fa/status")
    public ResponseEntity<?> get2FAStatus(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = twoFactorService.get2FAStatus(userDetails);
            
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error fetching 2FA status: " + e.getMessage());
        }
    }
}