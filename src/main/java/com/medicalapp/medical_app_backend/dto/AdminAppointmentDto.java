package com.medicalapp.medical_app_backend.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

public class AdminAppointmentDto {

    private Long id;
    private Long patientId;
    private String patientName;
    private String patientEmail;
    private String testType;
    private String appointmentType;

    // ── Date / time ──────────────────────────────────────────────────────────
    private LocalDateTime appointmentDate;  // ✅ ADDED — full datetime for frontend display
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;

    private String status;
    private String priority;
    private String notes;
    private String doctorName;
    private String department;

    // ── Payment fields ───────────────────────────────────────────────────────
    private String price;           // ✅ ADDED
    private String paymentStatus;   // ✅ ADDED — UNPAID | PENDING_CONFIRMATION | PAID | PAY_ON_ARRIVAL
    private String paymentMethod;   // ✅ ADDED — PAY_NOW | PAY_ON_ARRIVAL

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Constructors ─────────────────────────────────────────────────────────

    public AdminAppointmentDto() {}

    public AdminAppointmentDto(Long id, Long patientId, String patientName, String testType) {
        this.id = id;
        this.patientId = patientId;
        this.patientName = patientName;
        this.testType = testType;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getPatientEmail() { return patientEmail; }
    public void setPatientEmail(String patientEmail) { this.patientEmail = patientEmail; }

    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }

    public String getAppointmentType() { return appointmentType; }
    public void setAppointmentType(String appointmentType) { this.appointmentType = appointmentType; }

    public LocalDateTime getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDateTime appointmentDate) { this.appointmentDate = appointmentDate; }

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

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}