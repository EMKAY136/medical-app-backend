package com.medicalapp.medical_app_backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @NotBlank
    @Size(max = 200)
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String message;
    
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false)
    private String type; // "appointment", "result", "security", "test", "medication", etc.
    
    @Column(name = "is_read")
    private boolean read = false;
    
    @Column(name = "is_sent")
    private boolean sent = false;
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    @Column(name = "read_at")
    private LocalDateTime readAt;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // Priority level for notification
    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.NORMAL;
    
    // Optional reference to related entities
    @Column(name = "reference_id")
    private Long referenceId; // ID of appointment, result, etc.
    
    @Column(name = "reference_type")
    private String referenceType; // "appointment", "medical_result", etc.
    
    // Metadata for additional notification data
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON string for additional data
    
    // Push notification delivery status
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status")
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;
    
    @Column(name = "delivery_attempts")
    private int deliveryAttempts = 0;
    
    @Column(name = "last_delivery_attempt")
    private LocalDateTime lastDeliveryAttempt;
    
    @Column(name = "delivery_error")
    private String deliveryError;
    
    // Constructors
    public Notification() {}
    
    public Notification(User user, String title, String message, String type) {
        this.user = user;
        this.title = title;
        this.message = message;
        this.type = type;
    }
    
    public Notification(User user, String title, String message, String type, Priority priority) {
        this.user = user;
        this.title = title;
        this.message = message;
        this.type = type;
        this.priority = priority;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
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
    
    public boolean isRead() {
        return read;
    }
    public void setRead(boolean read) {
        this.read = read;
        if (read && this.readAt == null) {
            this.readAt = LocalDateTime.now();
        }
    }
    
    public boolean isSent() {
        return sent;
    }
    public void setSent(boolean sent) {
        this.sent = sent;
        if (sent && this.sentAt == null) {
            this.sentAt = LocalDateTime.now();
        }
    }
    
    public LocalDateTime getSentAt() {
        return sentAt;
    }
    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
    
    public LocalDateTime getReadAt() {
        return readAt;
    }
    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
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
    
    public Priority getPriority() {
        return priority;
    }
    public void setPriority(Priority priority) {
        this.priority = priority;
    }
    
    public Long getReferenceId() {
        return referenceId;
    }
    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }
    
    public String getReferenceType() {
        return referenceType;
    }
    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }
    
    public String getMetadata() {
        return metadata;
    }
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }
    public void setDeliveryStatus(DeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }
    
    public int getDeliveryAttempts() {
        return deliveryAttempts;
    }
    public void setDeliveryAttempts(int deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }
    
    public LocalDateTime getLastDeliveryAttempt() {
        return lastDeliveryAttempt;
    }
    public void setLastDeliveryAttempt(LocalDateTime lastDeliveryAttempt) {
        this.lastDeliveryAttempt = lastDeliveryAttempt;
    }
    
    public String getDeliveryError() {
        return deliveryError;
    }
    public void setDeliveryError(String deliveryError) {
        this.deliveryError = deliveryError;
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
    public void markAsRead() {
        this.read = true;
        this.readAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public void markAsSent() {
        this.sent = true;
        this.sentAt = LocalDateTime.now();
        this.deliveryStatus = DeliveryStatus.DELIVERED;
        this.updatedAt = LocalDateTime.now();
    }
    
    public void markDeliveryFailed(String error) {
        this.deliveryStatus = DeliveryStatus.FAILED;
        this.deliveryError = error;
        this.deliveryAttempts++;
        this.lastDeliveryAttempt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public void incrementDeliveryAttempt() {
        this.deliveryAttempts++;
        this.lastDeliveryAttempt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public boolean isCritical() {
        return this.priority == Priority.HIGH || this.priority == Priority.URGENT;
    }
    
    public boolean shouldRetryDelivery() {
        return this.deliveryStatus == DeliveryStatus.FAILED && this.deliveryAttempts < 3;
    }
    
    public void setReference(String referenceType, Long referenceId) {
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }
    
    // Enums
    public enum Priority {
        LOW,      // Non-critical notifications (newsletters, tips)
        NORMAL,   // Standard notifications (general updates)
        HIGH,     // Important notifications (appointment reminders, results)
        URGENT    // Critical notifications (security alerts, emergency)
    }
    
    public enum DeliveryStatus {
        PENDING,    // Waiting to be sent
        DELIVERED,  // Successfully delivered
        FAILED,     // Delivery failed
        CANCELLED   // Delivery cancelled
    }
    
    // Notification type constants
    public static final String TYPE_APPOINTMENT = "appointment";
    public static final String TYPE_RESULT = "result";
    public static final String TYPE_SECURITY = "security";
    public static final String TYPE_MEDICATION = "medication";
    public static final String TYPE_SYSTEM = "system";
    public static final String TYPE_TEST = "test";
    public static final String TYPE_MARKETING = "marketing";
    public static final String TYPE_HEALTH_TIP = "health_tip";
}