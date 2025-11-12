// com/medicalapp/medical_app_backend/entity/SecuritySettings.java
package com.medicalapp.medical_app_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "security_settings")
public class SecuritySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "login_notifications")
    private boolean loginNotifications = true;

    @Column(name = "suspicious_activity_alerts")
    private boolean suspiciousActivityAlerts = true;

    @Column(name = "session_timeout")
    private String sessionTimeout = "30";

    @Column(name = "auto_login")
    private boolean autoLogin = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Constructors
    public SecuritySettings() {}

    public SecuritySettings(String email) {
        this.email = email;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isLoginNotifications() {
        return loginNotifications;
    }

    public void setLoginNotifications(boolean loginNotifications) {
        this.loginNotifications = loginNotifications;
    }

    public boolean isSuspiciousActivityAlerts() {
        return suspiciousActivityAlerts;
    }

    public void setSuspiciousActivityAlerts(boolean suspiciousActivityAlerts) {
        this.suspiciousActivityAlerts = suspiciousActivityAlerts;
    }

    public String getSessionTimeout() {
        return sessionTimeout;
    }

    public void setSessionTimeout(String sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }

    public boolean isAutoLogin() {
        return autoLogin;
    }

    public void setAutoLogin(boolean autoLogin) {
        this.autoLogin = autoLogin;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}