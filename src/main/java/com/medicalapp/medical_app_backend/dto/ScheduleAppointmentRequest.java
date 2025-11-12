package com.medicalapp.medical_app_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;

import java.time.LocalDate;
import java.time.LocalTime;

public class ScheduleAppointmentRequest {
    
    @NotNull(message = "Patient ID is required")
    private Long patientId;
    
    private String patientName;
    
    @NotBlank(message = "Appointment type is required")
    private String appointmentType;
    
    @NotNull(message = "Scheduled date is required")
    @Future(message = "Scheduled date must be in the future")
    private LocalDate scheduledDate;
    
    @NotNull(message = "Scheduled time is required")
    private LocalTime scheduledTime;
    
    private String notes;
    
    private String doctorName;
    
    private String department;
    
    // Constructors
    public ScheduleAppointmentRequest() {}
    
    public ScheduleAppointmentRequest(Long patientId, String appointmentType, LocalDate scheduledDate, LocalTime scheduledTime) {
        this.patientId = patientId;
        this.appointmentType = appointmentType;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
    }
    
    // Getters and Setters
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    
    public String getAppointmentType() { return appointmentType; }
    public void setAppointmentType(String appointmentType) { this.appointmentType = appointmentType; }
    
    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }
    
    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    
    @Override
    public String toString() {
        return "ScheduleAppointmentRequest{" +
                "patientId=" + patientId +
                ", appointmentType='" + appointmentType + '\'' +
                ", scheduledDate=" + scheduledDate +
                ", scheduledTime=" + scheduledTime +
                ", doctorName='" + doctorName + '\'' +
                '}';
    }
}