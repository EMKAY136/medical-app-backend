package com.medicalapp.medical_app_backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "auto_notifications")
public class AutoNotification {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "`trigger`", nullable = false)
    private String trigger;
    
    @NotBlank
    @Size(max = 200)
    @Column(nullable = false)
    private String title;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;
    
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false)
    private String type; // "appointment", "results", "alert", "reminder"
    
    @Column(nullable = false)
    private Boolean enabled = true;
    
    @Column(name = "delay_minutes")
    private Integer delayMinutes = 0; // Delay before sending (for reminders)
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @Column(name = "created_by")
    private String createdBy; // Admin username who created this rule
    
    @Column(name = "times_triggered")
    private Long timesTriggered = 0L; // How many times this rule has been triggered
    
    @Column(name = "last_triggered")
    private LocalDateTime lastTriggered; // When was this rule last triggered
    
    // Constructors
    public AutoNotification() {}
    
    public AutoNotification(String trigger, String title, String message, String type) {
        this.trigger = trigger;
        this.title = title;
        this.message = message;
        this.type = type;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTrigger() {
        return trigger;
    }
    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }
    
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public Integer getDelayMinutes() {
        return delayMinutes;
    }
    public void setDelayMinutes(Integer delayMinutes) {
        this.delayMinutes = delayMinutes;
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
    
    public String getCreatedBy() {
        return createdBy;
    }
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    
    public Long getTimesTriggered() {
        return timesTriggered;
    }
    public void setTimesTriggered(Long timesTriggered) {
        this.timesTriggered = timesTriggered;
    }
    
    public LocalDateTime getLastTriggered() {
        return lastTriggered;
    }
    public void setLastTriggered(LocalDateTime lastTriggered) {
        this.lastTriggered = lastTriggered;
    }
    
    // Lifecycle callbacks
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }
    
    // Utility methods
    public void recordTrigger() {
        this.timesTriggered++;
        this.lastTriggered = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isActive() {
        return this.enabled != null && this.enabled;
    }
    
    public void toggle() {
        this.enabled = !this.enabled;
        this.updatedAt = LocalDateTime.now();
    }
    
    // Trigger type constants
    public static final String TRIGGER_APPOINTMENT_SCHEDULED = "appointment_scheduled";
    public static final String TRIGGER_APPOINTMENT_CONFIRMED = "appointment_confirmed";
    public static final String TRIGGER_APPOINTMENT_CANCELLED = "appointment_cancelled";
    public static final String TRIGGER_APPOINTMENT_COMPLETED = "appointment_completed";
    public static final String TRIGGER_APPOINTMENT_REMINDER = "appointment_reminder";
    public static final String TRIGGER_RESULTS_READY = "results_ready";
    public static final String TRIGGER_TEST_BOOKED = "test_booked";
    public static final String TRIGGER_MEDICATION_REMINDER = "medication_reminder";
    public static final String TRIGGER_FOLLOWUP_REQUIRED = "followup_required";
}