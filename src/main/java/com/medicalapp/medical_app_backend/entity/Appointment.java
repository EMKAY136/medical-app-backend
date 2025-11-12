package com.medicalapp.medical_app_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointments")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})  // ✅ Added this line
public class Appointment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Keep both field names for compatibility
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private User patient;
    
    // Original field - keep for backward compatibility
    @Column(name = "appointment_date")
    private LocalDateTime appointmentDate;
    
    // New fields for admin dashboard functionality
    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;
    
    @Column(name = "scheduled_time")
    private LocalTime scheduledTime;
    
    @Column(name = "test_type")
    private String testType;
    
    @Column(name = "appointment_type")
    private String appointmentType;
    
    @Enumerated(EnumType.STRING)
    private Status status = Status.SCHEDULED;
    
    @Column(name = "priority")
    private String priority = "normal"; // normal, urgent, emergency
    
    // Keep original field
    private String reason;
    
    private String notes;
    
    @Column(name = "doctor_name")
    private String doctorName;
    
    @Column(name = "department")
    private String department;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // Constructors
    public Appointment() {}
    
    // Keep original constructor for backward compatibility
    public Appointment(User patient, LocalDateTime appointmentDate, String reason) {
        this.patient = patient;
        this.appointmentDate = appointmentDate;
        this.reason = reason;
        
        // Auto-populate new fields from appointmentDate
        if (appointmentDate != null) {
            this.scheduledDate = appointmentDate.toLocalDate();
            this.scheduledTime = appointmentDate.toLocalTime();
        }
        this.testType = reason; // Map reason to testType for compatibility
    }
    
    // New constructor for admin dashboard
    public Appointment(User patient, LocalDate scheduledDate, LocalTime scheduledTime, String testType) {
        this.patient = patient;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
        this.testType = testType;
        this.reason = testType; // Keep compatibility
        
        // Auto-populate appointmentDate from scheduled date/time
        if (scheduledDate != null && scheduledTime != null) {
            this.appointmentDate = LocalDateTime.of(scheduledDate, scheduledTime);
        }
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public User getPatient() { return patient; }
    public void setPatient(User patient) { this.patient = patient; }
    
    // Add getUser() method for AdminService compatibility
    public User getUser() { return this.patient; }
    public void setUser(User user) { this.patient = user; }
    
    public LocalDateTime getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDateTime appointmentDate) { 
        this.appointmentDate = appointmentDate;
        // Auto-sync with new fields
        if (appointmentDate != null) {
            this.scheduledDate = appointmentDate.toLocalDate();
            this.scheduledTime = appointmentDate.toLocalTime();
        }
    }
    
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { 
        this.scheduledDate = scheduledDate;
        // Auto-sync with appointmentDate
        syncAppointmentDate();
    }
    
    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { 
        this.scheduledTime = scheduledTime;
        // Auto-sync with appointmentDate
        syncAppointmentDate();
    }
    
    private void syncAppointmentDate() {
        if (scheduledDate != null && scheduledTime != null) {
            this.appointmentDate = LocalDateTime.of(scheduledDate, scheduledTime);
        }
    }
    
    public String getTestType() { return testType; }
    public void setTestType(String testType) { 
        this.testType = testType;
        // Keep reason in sync for backward compatibility
        if (this.reason == null) {
            this.reason = testType;
        }
    }
    
    public String getAppointmentType() { return appointmentType; }
    public void setAppointmentType(String appointmentType) { this.appointmentType = appointmentType; }
    
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    
    // Add string status support for AdminService
    public void setStatus(String status) {
        try {
            this.status = Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Map common admin status strings to enum values
            switch (status.toLowerCase()) {
                case "scheduled":
                    this.status = Status.SCHEDULED;
                    break;
                case "completed":
                    this.status = Status.COMPLETED;
                    break;
                case "cancelled":
                    this.status = Status.CANCELLED;
                    break;
                case "no-show":
                case "no_show":
                    this.status = Status.NO_SHOW;
                    break;
                default:
                    this.status = Status.SCHEDULED;
            }
        }
    }
    
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { 
        this.reason = reason;
        // Keep testType in sync if not set
        if (this.testType == null) {
            this.testType = reason;
        }
    }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    // JPA lifecycle methods
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Ensure data consistency
        syncAppointmentDate();
        if (testType == null && reason != null) {
            testType = reason;
        }
        if (reason == null && testType != null) {
            reason = testType;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        syncAppointmentDate();
    }
    
    public enum Status {
        SCHEDULED, COMPLETED, CANCELLED, NO_SHOW
    }
    
    // Helper methods for admin dashboard
    public String getStatusString() {
        return status != null ? status.name().toLowerCase() : "scheduled";
    }
    
    public boolean isToday() {
        return scheduledDate != null && scheduledDate.equals(LocalDate.now());
    }
    
    public boolean isUpcoming() {
        if (appointmentDate != null) {
            return appointmentDate.isAfter(LocalDateTime.now());
        }
        if (scheduledDate != null) {
            return scheduledDate.isAfter(LocalDate.now()) || 
                   (scheduledDate.equals(LocalDate.now()) && 
                    scheduledTime != null && scheduledTime.isAfter(LocalTime.now()));
        }
        return false;
    }
}