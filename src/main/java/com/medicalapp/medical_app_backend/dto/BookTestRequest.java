package com.medicalapp.medical_app_backend.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request body for booking a test — used by both:
 *   - POST /api/admin/book-test-with-notification  (patient via mobile app)
 *   - POST /api/admin/book-test                    (admin via admin panel)
 */
public class BookTestRequest {

    // ── Required ─────────────────────────────────────────────────────────────

    @NotNull(message = "Patient ID is required")
    private Long patientId;

    @NotNull(message = "Test type is required")
    private String testType;

    // ── Schedule ─────────────────────────────────────────────────────────────

    /** Date portion — "2025-06-15" mapped to LocalDate */
    private LocalDate scheduledDate;

    /** Time portion — "09:30" mapped to LocalTime */
    private LocalTime scheduledTime;

    // ── Optional metadata ────────────────────────────────────────────────────

    private String notes;
    private String priority; // "normal" | "urgent" | "emergency"

    // ── Payment fields (set by mobile booking flow) ──────────────────────────

    /**
     * Display price string copied directly from the services list.
     * e.g. "₦7,000.00"
     */
    private String price;

    /**
     * Payment status at the time of booking:
     *   UNPAID              – no payment option selected yet
     *   PENDING_CONFIRMATION – patient transferred money and clicked "I Have Paid"
     *   PAY_ON_ARRIVAL      – patient chose to pay when they arrive
     *
     * Maps to Appointment.PaymentStatus via the string overload on the entity.
     */
    private String paymentStatus;

    /**
     * How the patient intends to pay:
     *   PAY_NOW      – bank transfer (Sterling Bank)
     *   PAY_ON_ARRIVAL – cash/card on the day
     */
    private String paymentMethod;

    // ── Constructors ──────────────────────────────────────────────────────────

    public BookTestRequest() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }

    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
}