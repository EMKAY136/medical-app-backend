// com/medicalapp/medical_app_backend/controller/SecuritySettingsController.java
package com.medicalapp.medical_app_backend.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.medicalapp.medical_app_backend.service.SecuritySettingsService;
import com.medicalapp.medical_app_backend.entity.SecuritySettings;


import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/users")
public class SecuritySettingsController {

    private static final Logger logger = LoggerFactory.getLogger(SecuritySettingsController.class);

    @Autowired
    private SecuritySettingsService securitySettingsService;

    @GetMapping("/security-settings")
    public ResponseEntity<Map<String, Object>> getSecuritySettings(Authentication authentication) {
        try {
            if (authentication == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Authentication required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            String email = authentication.getName();
            logger.info("Fetching security settings for user: {}", email);

            SecuritySettings settings = securitySettingsService.getSecuritySettings(email);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("settings", Map.of(
                "loginNotifications", settings.isLoginNotifications(),
                "suspiciousActivityAlerts", settings.isSuspiciousActivityAlerts(),
                "sessionTimeout", settings.getSessionTimeout(),
                "autoLogin", settings.isAutoLogin()
            ));
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching security settings: ", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error fetching security settings");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PutMapping("/security-settings")
    public ResponseEntity<Map<String, Object>> updateSecuritySettings(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        try {
            if (authentication == null) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Authentication required");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
            }

            String email = authentication.getName();
            logger.info("Updating security settings for user: {}", email);

            SecuritySettings settings = new SecuritySettings();
            settings.setEmail(email);
            
            if (request.containsKey("loginNotifications")) {
                settings.setLoginNotifications((Boolean) request.get("loginNotifications"));
            }
            if (request.containsKey("suspiciousActivityAlerts")) {
                settings.setSuspiciousActivityAlerts((Boolean) request.get("suspiciousActivityAlerts"));
            }
            if (request.containsKey("sessionTimeout")) {
                settings.setSessionTimeout((String) request.get("sessionTimeout"));
            }
            if (request.containsKey("autoLogin")) {
                settings.setAutoLogin((Boolean) request.get("autoLogin"));
            }

            SecuritySettings updatedSettings = securitySettingsService.updateSecuritySettings(email, settings);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Security settings updated successfully");
            response.put("settings", Map.of(
                "loginNotifications", updatedSettings.isLoginNotifications(),
                "suspiciousActivityAlerts", updatedSettings.isSuspiciousActivityAlerts(),
                "sessionTimeout", updatedSettings.getSessionTimeout(),
                "autoLogin", updatedSettings.isAutoLogin()
            ));
            response.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error updating security settings: ", e);
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error updating security settings");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}