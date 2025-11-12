package com.medicalapp.medical_app_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

public class BookTestRequest {
    
    @NotNull(message = "Patient ID is required")
    private Long patientId;
    
    private String patientName;
    
    @NotBlank(message = "Test type is required")
    private String testType;
    
    @NotNull(message = "Scheduled date is required")
    @Future(message = "Scheduled date must be in the future")
    private LocalDate scheduledDate;
    
    @NotNull(message = "Scheduled time is required")
    private LocalTime scheduledTime;
    
    private String notes;
    
    @Pattern(regexp = "normal|urgent|emergency", message = "Priority must be normal, urgent, or emergency")
    private String priority = "normal";
    
    // Additional fields for complete functionality
    private String status;
    private String doctorName;
    private String department;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructors
    public BookTestRequest() {}
    
    public BookTestRequest(Long patientId, String testType, LocalDate scheduledDate, LocalTime scheduledTime) {
        this.patientId = patientId;
        this.testType = testType;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
    }
    
    // Getters and Setters
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    
    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }
    
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    
    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    
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
    
    // Utility method to combine date and time
    public LocalDateTime getAppointmentDateTime() {
        if (scheduledDate != null && scheduledTime != null) {
            return scheduledDate.atTime(scheduledTime);
        }
        return null;
    }
    
    // Utility method to get reason (maps to testType for your Appointment entity)
    public String getReason() {
        return this.testType;
    }
}