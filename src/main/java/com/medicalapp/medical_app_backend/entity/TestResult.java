package com.medicalapp.medical_app_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "test_results")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})  // ✅ Added this line
public class TestResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(name = "test_type")
    private String testType;
    
    @Column(name = "test_name")
    private String testName;
    
    @Column(name = "result", columnDefinition = "TEXT")
    private String result;
    
    @Column(name = "status", nullable = false)
    private String status;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "test_date")
    private LocalDate testDate;
    
    @Column(name = "lab_name")
    private String labName;
    
    @Column(name = "doctor_comments", columnDefinition = "TEXT")
    private String doctorComments;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Constructors
    public TestResult() {}
    
    public TestResult(User user, String testType, String testName, String result, String status) {
        this.user = user;
        this.testType = testType;
        this.testName = testName;
        this.result = result;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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
    
    public String getTestType() {
        return testType;
    }
    
    public void setTestType(String testType) {
        this.testType = testType;
    }
    
    public String getTestName() {
        return testName;
    }
    
    public void setTestName(String testName) {
        this.testName = testName;
    }
    
    public String getResult() {
        return result;
    }
    
    public void setResult(String result) {
        this.result = result;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public LocalDate getTestDate() {
        return testDate;
    }
    
    public void setTestDate(LocalDate testDate) {
        this.testDate = testDate;
    }
    
    public String getLabName() {
        return labName;
    }
    
    public void setLabName(String labName) {
        this.labName = labName;
    }
    
    public String getDoctorComments() {
        return doctorComments;
    }
    
    public void setDoctorComments(String doctorComments) {
        this.doctorComments = doctorComments;
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
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}