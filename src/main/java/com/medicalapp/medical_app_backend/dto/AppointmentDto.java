package com.medicalapp.medical_app_backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class AppointmentDto {

    private Long id;

    // ── Date / time ──────────────────────────────────────────────────────────
    private LocalDateTime appointmentDate;
    private LocalDate     scheduledDate;     // "2025-06-15"
    private LocalTime     scheduledTime;     // "09:30"

    // ── Test / appointment info ──────────────────────────────────────────────
    private String reason;
    private String testType;
    private String notes;
    private String status;       // SCHEDULED | COMPLETED | MISSED | CANCELLED | NO_SHOW

    // ── Patient ──────────────────────────────────────────────────────────────
    private Long   patientId;
    private String patientName;

    // ── Payment fields ───────────────────────────────────────────────────────
    /** Display price string e.g. "₦7,000.00" */
    private String price;

    /**
     * UNPAID | PENDING_CONFIRMATION | PAID | PAY_ON_ARRIVAL
     * Defaults to UNPAID when not supplied.
     */
    private String paymentStatus;

    /**
     * PAY_NOW | PAY_ON_ARRIVAL
     * PAY_NOW  = patient did a bank transfer and clicked "I Have Paid"
     * PAY_ON_ARRIVAL = patient will pay when they arrive
     */
    private String paymentMethod;

    /** Timestamp set when admin approves the payment */
    private LocalDateTime paymentApprovedAt;

    /** Username of the admin who approved the payment */
    private String paymentApprovedBy;

    // ── Refund fields ────────────────────────────────────────────────────────
    private String        refundStatus;
    private String        refundReason;
    private LocalDateTime refundRequestedAt;
    private LocalDateTime refundApprovedAt;
    private String        refundApprovedBy;

    // ── Reschedule fields ────────────────────────────────────────────────────
    /**
     * NONE | REQUESTED | APPROVED | REJECTED
     * Defaults to NONE.
     */
    private String        rescheduleStatus;
    private String        rescheduleReason;
    private String        reschedulePreferredDate;   // stored as String (e.g. "2025-07-10")
    private String        reschedulePreferredTime;   // stored as String (e.g. "10:00")
    private LocalDateTime rescheduleRequestedAt;
    private LocalDateTime rescheduleApprovedAt;
    private String        rescheduleApprovedBy;

    // ── Audit ────────────────────────────────────────────────────────────────
    private LocalDateTime createdAt;

    // ── Constructors ─────────────────────────────────────────────────────────

    public AppointmentDto() {}

    // ── Getters & Setters — Core ──────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDateTime appointmentDate) { this.appointmentDate = appointmentDate; }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }

    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    // ── Payment getters/setters ──────────────────────────────────────────────

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDateTime getPaymentApprovedAt() { return paymentApprovedAt; }
    public void setPaymentApprovedAt(LocalDateTime paymentApprovedAt) { this.paymentApprovedAt = paymentApprovedAt; }

    public String getPaymentApprovedBy() { return paymentApprovedBy; }
    public void setPaymentApprovedBy(String paymentApprovedBy) { this.paymentApprovedBy = paymentApprovedBy; }

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
}