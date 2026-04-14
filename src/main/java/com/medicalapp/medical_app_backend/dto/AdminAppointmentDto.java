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
    private LocalDateTime appointmentDate;
    private LocalDate     scheduledDate;
    private LocalTime     scheduledTime;

    private String status;
    private String priority;
    private String notes;
    private String doctorName;
    private String department;

    // ── Payment fields ───────────────────────────────────────────────────────
    private String price;
    private String paymentStatus;   // UNPAID | PENDING_CONFIRMATION | PAID | PAY_ON_ARRIVAL
    private String paymentMethod;   // PAY_NOW | PAY_ON_ARRIVAL

    // ── Refund fields ────────────────────────────────────────────────────────
    private String        refundStatus;
    private String        refundReason;
    private LocalDateTime refundRequestedAt;
    private LocalDateTime refundApprovedAt;
    private String        refundApprovedBy;

    // ── Reschedule fields ────────────────────────────────────────────────────
    private String        rescheduleStatus;         // NONE | REQUESTED | APPROVED | REJECTED
    private String        rescheduleReason;
    private String        reschedulePreferredDate;  // e.g. "2025-07-10"
    private String        reschedulePreferredTime;  // e.g. "10:00"
    private LocalDateTime rescheduleRequestedAt;
    private LocalDateTime rescheduleApprovedAt;
    private String        rescheduleApprovedBy;

    // ── Audit ────────────────────────────────────────────────────────────────
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

    // ── Getters & Setters — Core ──────────────────────────────────────────────

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

    // ── Payment getters/setters ──────────────────────────────────────────────

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    // ── Refund getters/setters ───────────────────────────────────────────────

    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }

    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }

    public LocalDateTime getRefundRequestedAt() { return refundRequestedAt; }
    public void setRefundRequestedAt(LocalDateTime refundRequestedAt) { this.refundRequestedAt = refundRequestedAt; }

    public LocalDateTime getRefundApprovedAt() { return refundApprovedAt; }
    public void setRefundApprovedAt(LocalDateTime refundApprovedAt) { this.refundApprovedAt = refundApprovedAt; }

    public String getRefundApprovedBy() { return refundApprovedBy; }
    public void setRefundApprovedBy(String refundApprovedBy) { this.refundApprovedBy = refundApprovedBy; }

    // ── Reschedule getters/setters ───────────────────────────────────────────

    public String getRescheduleStatus() { return rescheduleStatus; }
    public void setRescheduleStatus(String rescheduleStatus) { this.rescheduleStatus = rescheduleStatus; }

    public String getRescheduleReason() { return rescheduleReason; }
    public void setRescheduleReason(String rescheduleReason) { this.rescheduleReason = rescheduleReason; }

    public String getReschedulePreferredDate() { return reschedulePreferredDate; }
    public void setReschedulePreferredDate(String reschedulePreferredDate) { this.reschedulePreferredDate = reschedulePreferredDate; }

    public String getReschedulePreferredTime() { return reschedulePreferredTime; }
    public void setReschedulePreferredTime(String reschedulePreferredTime) { this.reschedulePreferredTime = reschedulePreferredTime; }

    public LocalDateTime getRescheduleRequestedAt() { return rescheduleRequestedAt; }
    public void setRescheduleRequestedAt(LocalDateTime rescheduleRequestedAt) { this.rescheduleRequestedAt = rescheduleRequestedAt; }

    public LocalDateTime getRescheduleApprovedAt() { return rescheduleApprovedAt; }
    public void setRescheduleApprovedAt(LocalDateTime rescheduleApprovedAt) { this.rescheduleApprovedAt = rescheduleApprovedAt; }

    public String getRescheduleApprovedBy() { return rescheduleApprovedBy; }
    public void setRescheduleApprovedBy(String rescheduleApprovedBy) { this.rescheduleApprovedBy = rescheduleApprovedBy; }

    // ── Audit getters/setters ────────────────────────────────────────────────

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}