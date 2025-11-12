package com.medicalapp.medical_app_backend.dto;

import com.medicalapp.medical_app_backend.entity.MedicalResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class ResultDto {

    private Long id;

    @NotBlank(message = "Test name is required")
    @Size(max = 255, message = "Test name must not exceed 255 characters")
    private String testName;

    @NotBlank(message = "Result is required")
    private String result;

    private String normalRange;
    private String notes;
    private Long patientId;
    private String patientName;
    private Long appointmentId;
    
    private LocalDateTime testDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Core fields
    private String category;
    private String testType;
    private MedicalResult.ResultStatus status;
    private MedicalResult.ResultPriority priority;
    
    // File-related fields
    private String fileName;
    private String filePath;
    private Long fileSize;  // Changed from String to Long
    private String mimeType;
    private boolean hasFile;  // Added missing field
    
    // Display and control fields
    private boolean isVisible = true;
    private boolean isDownloadable = true;
    
    // Display names for enums
    private String statusDisplayName;
    private String priorityDisplayName;
    
    // Additional metadata
    private String doctorName;
    private String uploadedBy;
    private String technicianName;
    
    // Constructors
    public ResultDto() {}
    
    public ResultDto(String testName, String result) {
        this.testName = testName;
        this.result = result;
        this.testDate = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
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
    
    public String getNormalRange() {
        return normalRange;
    }
    
    public void setNormalRange(String normalRange) {
        this.normalRange = normalRange;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public Long getPatientId() {
        return patientId;
    }
    
    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }
    
    public String getPatientName() {
        return patientName;
    }
    
    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }
    
    public Long getAppointmentId() {
        return appointmentId;
    }
    
    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }
    
    public LocalDateTime getTestDate() {
        return testDate;
    }
    
    public void setTestDate(LocalDateTime testDate) {
        this.testDate = testDate;
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
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    public String getTestType() {
        return testType;
    }
    
    public void setTestType(String testType) {
        this.testType = testType;
    }
    
    public MedicalResult.ResultStatus getStatus() {
        return status;
    }
    
    public void setStatus(MedicalResult.ResultStatus status) {
        this.status = status;
        this.statusDisplayName = status != null ? status.getDisplayName() : null;
    }
    
    public MedicalResult.ResultPriority getPriority() {
        return priority;
    }
    
    public void setPriority(MedicalResult.ResultPriority priority) {
        this.priority = priority;
        this.priorityDisplayName = priority != null ? priority.getDisplayName() : null;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public String getFilePath() {
        return filePath;
    }
    
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    
    public Long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
    
    public String getMimeType() {
        return mimeType;
    }
    
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
    
    public boolean getHasFile() {
        return hasFile;
    }
    
    public void setHasFile(boolean hasFile) {
        this.hasFile = hasFile;
    }
    
    public boolean isVisible() {
        return isVisible;
    }
    
    public void setVisible(boolean visible) {
        isVisible = visible;
    }
    
    public boolean isDownloadable() {
        return isDownloadable;
    }
    
    public void setDownloadable(boolean downloadable) {
        isDownloadable = downloadable;
    }
    
    public String getStatusDisplayName() {
        return statusDisplayName;
    }
    
    public void setStatusDisplayName(String statusDisplayName) {
        this.statusDisplayName = statusDisplayName;
    }
    
    public String getPriorityDisplayName() {
        return priorityDisplayName;
    }
    
    public void setPriorityDisplayName(String priorityDisplayName) {
        this.priorityDisplayName = priorityDisplayName;
    }
    
    public String getDoctorName() {
        return doctorName;
    }
    
    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }
    
    public String getUploadedBy() {
        return uploadedBy;
    }
    
    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
    
    public String getTechnicianName() {
        return technicianName;
    }
    
    public void setTechnicianName(String technicianName) {
        this.technicianName = technicianName;
    }
    
    // Utility methods
    public boolean hasAttachedFile() {
        return fileName != null && !fileName.trim().isEmpty();
    }
    
    public boolean isPending() {
        return status == MedicalResult.ResultStatus.PENDING;
    }
    
    public boolean isCompleted() {
        return status == MedicalResult.ResultStatus.COMPLETED;
    }
    
    public String getDisplayStatus() {
        return statusDisplayName != null ? statusDisplayName : 
               (status != null ? status.getDisplayName() : "Unknown");
    }
    
    public String getDisplayPriority() {
        return priorityDisplayName != null ? priorityDisplayName : 
               (priority != null ? priority.getDisplayName() : "Normal");
    }
    
    public String getFormattedFileSize() {
        if (fileSize == null) return "N/A";
        
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        }
    }
    
    @Override
    public String toString() {
        return "ResultDto{" +
                "id=" + id +
                ", testName='" + testName + '\'' +
                ", category='" + category + '\'' +
                ", status=" + status +
                ", testDate=" + testDate +
                ", patientId=" + patientId +
                ", hasFile=" + hasFile +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ResultDto resultDto = (ResultDto) o;
        return id != null && id.equals(resultDto.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}