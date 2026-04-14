package com.medicalapp.medical_app_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointments")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private User patient;

    @Column(name = "appointment_date")
    private LocalDateTime appointmentDate;

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
    private String priority = "normal";

    private String reason;
    private String notes;

    @Column(name = "doctor_name")
    private String doctorName;

    @Column(name = "department")
    private String department;

    // ── NEW: Price ───────────────────────────────────────────────────────────
    @Column(name = "price")
    private String price;

    // ── NEW: Payment status ──────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    // ── NEW: Payment method ──────────────────────────────────────────────────
    @Column(name = "payment_method")
    private String paymentMethod; // PAY_NOW | PAY_ON_ARRIVAL

    // ── NEW: When admin approved payment ────────────────────────────────────
    @Column(name = "payment_approved_at")
    private LocalDateTime paymentApprovedAt;

    // ── NEW: Admin who approved ──────────────────────────────────────────────
    @Column(name = "payment_approved_by")
    private String paymentApprovedBy;

    // ── Refund ────────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status")
    private RefundStatus refundStatus = RefundStatus.NONE;

    @Column(name = "refund_reason", length = 1000)
    private String refundReason;

    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @Column(name = "refund_approved_at")
    private LocalDateTime refundApprovedAt;

    @Column(name = "refund_approved_by")
    private String refundApprovedBy;

    // ── Reschedule ────────────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "reschedule_status")
    private RescheduleStatus rescheduleStatus = RescheduleStatus.NONE;

    @Column(name = "reschedule_reason", length = 1000)
    private String rescheduleReason;

    @Column(name = "reschedule_preferred_date")
    private String reschedulePreferredDate;

    @Column(name = "reschedule_preferred_time")
    private String reschedulePreferredTime;

    @Column(name = "reschedule_requested_at")
    private LocalDateTime rescheduleRequestedAt;

    @Column(name = "reschedule_approved_at")
    private LocalDateTime rescheduleApprovedAt;

    @Column(name = "reschedule_approved_by")
    private String rescheduleApprovedBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ────────────────────────────────────────────────────────────────────────
    // Constructors
    // ────────────────────────────────────────────────────────────────────────

    public Appointment() {}

    public Appointment(User patient, LocalDateTime appointmentDate, String reason) {
        this.patient = patient;
        this.appointmentDate = appointmentDate;
        this.reason = reason;
        if (appointmentDate != null) {
            this.scheduledDate = appointmentDate.toLocalDate();
            this.scheduledTime = appointmentDate.toLocalTime();
        }
        this.testType = reason;
    }

    public Appointment(User patient, LocalDate scheduledDate, LocalTime scheduledTime, String testType) {
        this.patient = patient;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
        this.testType = testType;
        this.reason = testType;
        if (scheduledDate != null && scheduledTime != null) {
            this.appointmentDate = LocalDateTime.of(scheduledDate, scheduledTime);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Getters & Setters
    // ────────────────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getPatient() { return patient; }
    public void setPatient(User patient) { this.patient = patient; }

    /** Alias so AdminService / AutoNotificationService can call getUser() */
    public User getUser() { return this.patient; }
    public void setUser(User user) { this.patient = user; }

    public LocalDateTime getAppointmentDate() { return appointmentDate; }
    public void setAppointmentDate(LocalDateTime appointmentDate) {
        this.appointmentDate = appointmentDate;
        if (appointmentDate != null) {
            this.scheduledDate = appointmentDate.toLocalDate();
            this.scheduledTime = appointmentDate.toLocalTime();
        }
    }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
        syncAppointmentDate();
    }

    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) {
        this.scheduledTime = scheduledTime;
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
        if (this.reason == null) this.reason = testType;
    }

    public String getAppointmentType() { return appointmentType; }
    public void setAppointmentType(String appointmentType) { this.appointmentType = appointmentType; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    /** String overload used by AdminService */
    public void setStatus(String status) {
        try {
            this.status = Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            switch (status.toLowerCase()) {
                case "scheduled"         -> this.status = Status.SCHEDULED;
                case "completed"         -> this.status = Status.COMPLETED;
                case "cancelled"         -> this.status = Status.CANCELLED;
                case "missed"            -> this.status = Status.MISSED;
                case "no-show","no_show" -> this.status = Status.NO_SHOW;
                default                  -> this.status = Status.SCHEDULED;
            }
        }
    }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getReason() { return reason; }
    public void setReason(String reason) {
        this.reason = reason;
        if (this.testType == null) this.testType = reason;
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    // ── Payment getters/setters ──────────────────────────────────────────────

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }

    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }

    /** String overload for convenience */
    public void setPaymentStatus(String paymentStatus) {
        try {
            this.paymentStatus = PaymentStatus.valueOf(paymentStatus.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.paymentStatus = PaymentStatus.UNPAID;
        }
    }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public LocalDateTime getPaymentApprovedAt() { return paymentApprovedAt; }
    public void setPaymentApprovedAt(LocalDateTime paymentApprovedAt) { this.paymentApprovedAt = paymentApprovedAt; }

    public String getPaymentApprovedBy() { return paymentApprovedBy; }
    public void setPaymentApprovedBy(String paymentApprovedBy) { this.paymentApprovedBy = paymentApprovedBy; }

    // Refund getters/setters
    public RefundStatus getRefundStatus() { return refundStatus; }
    public void setRefundStatus(RefundStatus refundStatus) { this.refundStatus = refundStatus; }

    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }

    public LocalDateTime getRefundRequestedAt() { return refundRequestedAt; }
    public void setRefundRequestedAt(LocalDateTime refundRequestedAt) { this.refundRequestedAt = refundRequestedAt; }

    public LocalDateTime getRefundApprovedAt() { return refundApprovedAt; }
    public void setRefundApprovedAt(LocalDateTime refundApprovedAt) { this.refundApprovedAt = refundApprovedAt; }

    public String getRefundApprovedBy() { return refundApprovedBy; }
    public void setRefundApprovedBy(String refundApprovedBy) { this.refundApprovedBy = refundApprovedBy; }

    // Reschedule getters/setters
    public RescheduleStatus getRescheduleStatus() { return rescheduleStatus; }
    public void setRescheduleStatus(RescheduleStatus rescheduleStatus) { this.rescheduleStatus = rescheduleStatus; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // ────────────────────────────────────────────────────────────────────────
    // JPA lifecycle
    // ────────────────────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        syncAppointmentDate();
        if (testType == null && reason != null) testType = reason;
        if (reason == null && testType != null) reason = testType;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        syncAppointmentDate();
    }

    // ────────────────────────────────────────────────────────────────────────
    // Enums
    // ────────────────────────────────────────────────────────────────────────

    public enum Status {
        SCHEDULED, COMPLETED, CANCELLED, NO_SHOW,
        /** NEW — appointment date passed without completion */
        MISSED
    }

    public enum PaymentStatus {
        /** Patient has not paid and chose no option yet */
        UNPAID,
        /** Patient transferred money, waiting for admin to confirm */
        PENDING_CONFIRMATION,
        /** Admin confirmed the bank transfer was received */
        PAID,
        /** Patient chose to pay when they arrive */
        PAY_ON_ARRIVAL
    }

    public enum RefundStatus {
        NONE, REQUESTED, APPROVED, REJECTED
    }

    public enum RescheduleStatus {
        NONE, REQUESTED, APPROVED, REJECTED
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    public String getStatusString() {
        return status != null ? status.name().toLowerCase() : "scheduled";
    }

    public boolean isToday() {
        return scheduledDate != null && scheduledDate.equals(LocalDate.now());
    }

    public boolean isUpcoming() {
        if (appointmentDate != null) return appointmentDate.isAfter(LocalDateTime.now());
        if (scheduledDate != null) {
            return scheduledDate.isAfter(LocalDate.now()) ||
                   (scheduledDate.equals(LocalDate.now()) &&
                    scheduledTime != null && scheduledTime.isAfter(LocalTime.now()));
        }
        return false;
    }
}