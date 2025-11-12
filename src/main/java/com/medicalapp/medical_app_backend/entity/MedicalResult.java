package com.medicalapp.medical_app_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "medical_results")
public class MedicalResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;
    
    @Column(name = "test_name", nullable = false)
    private String testName;
    
    @Column(name = "test_type")
    private String testType;
    
    @Column(name = "category")
    private String category;
    
    @Column(name = "result_value", columnDefinition = "TEXT")
    private String result;
    
    @Column(name = "reference_range")
    private String normalRange;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ResultStatus status = ResultStatus.NORMAL;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private ResultPriority priority = ResultPriority.NORMAL;
    
    @Column(name = "test_date")
    private LocalDateTime testDate;
    
    @Column(name = "doctor_name")
    private String doctorName;
    
    @Column(name = "technician_name")
    private String technicianName;
    
    @Column(name = "uploaded_by")
    private String uploadedBy;
    
    @Column(name = "file_path", length = 1000)
    private String filePath;
    
    @Column(name = "file_name", length = 1000)
    private String fileName;
    
    @Column(name = "file_type", length = 255)
    private String fileType;
    
    @Column(name = "mime_type", length = 255)
    private String mimeType;
    
    @Column(name = "file_size")
    private Long fileSize = 0L;
    
    @Column(name = "is_visible")
    private boolean isVisible = true;
    
    @Column(name = "is_downloadable")
    private boolean isDownloadable = true;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // Constructors
    public MedicalResult() {}
    
    public MedicalResult(User patient, String testName, String result) {
        this.patient = patient;
        this.testName = testName;
        this.result = result;
        this.testDate = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public User getPatient() { return patient; }
    public void setPatient(User patient) { this.patient = patient; }
    
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    
    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }
    
    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    
    public String getNormalRange() { return normalRange; }
    public void setNormalRange(String normalRange) { this.normalRange = normalRange; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public ResultStatus getStatus() { return status; }
    public void setStatus(ResultStatus status) { this.status = status; }
    
    public ResultPriority getPriority() { return priority; }
    public void setPriority(ResultPriority priority) { this.priority = priority; }
    
    public LocalDateTime getTestDate() { return testDate; }
    public void setTestDate(LocalDateTime testDate) { this.testDate = testDate; }
    
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    
    public String getTechnicianName() { return technicianName; }
    public void setTechnicianName(String technicianName) { this.technicianName = technicianName; }
    
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    
    public boolean isVisible() { return isVisible; }
    public void setVisible(boolean visible) { isVisible = visible; }
    
    public boolean isDownloadable() { return isDownloadable; }
    public void setDownloadable(boolean downloadable) { isDownloadable = downloadable; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Enums with display names
    public enum ResultStatus {
        NORMAL("Normal"), 
        ABNORMAL("Abnormal"), 
        ELEVATED("Elevated"), 
        LOW("Low"), 
        HIGH("High"), 
        CRITICAL("Critical"), 
        PENDING("Pending Review"), 
        REVIEWED("Reviewed"),
        COMPLETED("Completed");
        
        private final String displayName;
        
        ResultStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public enum ResultPriority {
        LOW("Low Priority"),
        NORMAL("Normal Priority"),
        HIGH("High Priority"),
        URGENT("Urgent"),
        CRITICAL("Critical");
        
        private final String displayName;
        
        ResultPriority(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}