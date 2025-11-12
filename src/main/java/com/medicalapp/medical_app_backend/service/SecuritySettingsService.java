// com/medicalapp/medical_app_backend/service/SecuritySettingsService.java
package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.SecuritySettings;
import com.medicalapp.medical_app_backend.repository.SecuritySettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SecuritySettingsService {

    private static final Logger logger = LoggerFactory.getLogger(SecuritySettingsService.class);

    @Autowired
    private SecuritySettingsRepository securitySettingsRepository;

    /**
     * Get security settings for a user, create default if not exists
     */
    @Transactional
    public SecuritySettings getSecuritySettings(String email) {
        Optional<SecuritySettings> existingSettings = securitySettingsRepository.findByEmail(email);
        
        if (existingSettings.isPresent()) {
            logger.info("Found existing security settings for: {}", email);
            return existingSettings.get();
        }
        
        // Create default settings if none exist
        logger.info("Creating default security settings for: {}", email);
        SecuritySettings defaultSettings = new SecuritySettings(email);
        return securitySettingsRepository.save(defaultSettings);
    }

    /**
     * Update security settings for a user
     */
    @Transactional
    public SecuritySettings updateSecuritySettings(String email, SecuritySettings newSettings) {
        Optional<SecuritySettings> existingSettingsOpt = securitySettingsRepository.findByEmail(email);
        
        SecuritySettings settings;
        if (existingSettingsOpt.isPresent()) {
            settings = existingSettingsOpt.get();
            logger.info("Updating existing security settings for: {}", email);
        } else {
            settings = new SecuritySettings(email);
            logger.info("Creating new security settings for: {}", email);
        }
        
        // Update fields
        settings.setLoginNotifications(newSettings.isLoginNotifications());
        settings.setSuspiciousActivityAlerts(newSettings.isSuspiciousActivityAlerts());
        settings.setSessionTimeout(newSettings.getSessionTimeout());
        settings.setAutoLogin(newSettings.isAutoLogin());
        
        SecuritySettings savedSettings = securitySettingsRepository.save(settings);
        logger.info("Security settings saved for: {}", email);
        
        return savedSettings;
    }

    /**
     * Check if login notifications are enabled for a user
     */
    public boolean areLoginNotificationsEnabled(String email) {
        Optional<SecuritySettings> settings = securitySettingsRepository.findByEmail(email);
        return settings.map(SecuritySettings::isLoginNotifications).orElse(true);
    }

    /**
     * Check if suspicious activity alerts are enabled for a user
     */
    public boolean areSuspiciousActivityAlertsEnabled(String email) {
        Optional<SecuritySettings> settings = securitySettingsRepository.findByEmail(email);
        return settings.map(SecuritySettings::isSuspiciousActivityAlerts).orElse(true);
    }
}