package com.medicalapp.medical_app_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

public class AddTestResultRequest {
    
    @NotNull(message = "Patient ID is required")
    private Long patientId;
    
    private String patientName;
    
    @NotBlank(message = "Test type is required")
    private String testType;
    
    @NotBlank(message = "Test result is required")
    private String result;
    
    @Pattern(regexp = "normal|abnormal|pending", message = "Status must be normal, abnormal, or pending")
    private String status = "normal";
    
    private String notes;
    
    @NotBlank(message = "Doctor name is required")
    private String doctorName;
    
    @NotNull(message = "Test date is required")
    private LocalDate testDate;
    
    private String labName;
    
    private String doctorComments;
    
    // Constructors
    public AddTestResultRequest() {}
    
    public AddTestResultRequest(Long patientId, String testType, String result, String status) {
        this.patientId = patientId;
        this.testType = testType;
        this.result = result;
        this.status = status;
        this.testDate = LocalDate.now();
    }
    
    // Getters and Setters
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    
    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }
    
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    
    public LocalDate getTestDate() { return testDate; }
    public void setTestDate(LocalDate testDate) { this.testDate = testDate; }
    
    public String getLabName() { return labName; }
    public void setLabName(String labName) { this.labName = labName; }
    
    public String getDoctorComments() { return doctorComments; }
    public void setDoctorComments(String doctorComments) { this.doctorComments = doctorComments; }
    
    @Override
    public String toString() {
        return "AddTestResultRequest{" +
                "patientId=" + patientId +
                ", testType='" + testType + '\'' +
                ", result='" + result + '\'' +
                ", status='" + status + '\'' +
                ", testDate=" + testDate +
                ", labName='" + labName + '\'' +
                ", doctorComments='" + doctorComments + '\'' +
                '}';
    }
}