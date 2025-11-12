package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.TestResult;
import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.TestResultRepository;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class TestResultService {

    private static final Logger logger = LoggerFactory.getLogger(TestResultService.class);

    @Autowired
    private TestResultRepository testResultRepository;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AutoNotificationService autoNotificationService; // ✅ ADD THIS

    // ===== CREATE TEST RESULT WITH AUTO-NOTIFICATION =====
    
    public TestResult addTestResult(TestResult testResult) {
        try {
            logger.info("Adding test result for patient: {}", testResult.getUser().getId());
            
            // Set timestamps
            testResult.setCreatedAt(LocalDateTime.now());
            testResult.setUpdatedAt(LocalDateTime.now());
            
            // If status not set, default to "Completed"
            if (testResult.getStatus() == null) {
                testResult.setStatus("Completed");
            }
            
            // If test date not set, use today
            if (testResult.getTestDate() == null) {
                testResult.setTestDate(LocalDate.now());
            }
            
            // Save to database
            TestResult saved = testResultRepository.save(testResult);
            
            logger.info("Test result saved with ID: {}", saved.getId());
            
            // ✅ AUTOMATICALLY TRIGGER TEST RESULT NOTIFICATION
            autoNotificationService.onTestResultUploaded(saved);
            
            logger.info("✅ Auto-notification triggered for test result");
            
            return saved;
            
        } catch (Exception e) {
            logger.error("Error adding test result: {}", e.getMessage());
            throw e;
        }
    }

    // ===== GET TEST RESULT =====
    
    public Optional<TestResult> getTestResultById(Long id) {
        return testResultRepository.findById(id);
    }

    // ===== GET ALL TEST RESULTS FOR PATIENT =====
    
    public List<TestResult> getTestResultsByPatient(Long patientId) {
        try {
            Optional<User> userOpt = userRepository.findById(patientId);
            if (userOpt.isPresent()) {
                return testResultRepository.findByUser(userOpt.get());
            }
            return List.of();
        } catch (Exception e) {
            logger.error("Error fetching test results for patient {}: {}", patientId, e.getMessage());
            return List.of();
        }
    }

    // ===== UPDATE TEST RESULT =====
    
    public TestResult updateTestResult(Long resultId, TestResult updatedData) {
        try {
            Optional<TestResult> existingOpt = testResultRepository.findById(resultId);
            
            if (existingOpt.isPresent()) {
                TestResult existing = existingOpt.get();
                
                logger.info("Updating test result {}", resultId);
                
                // Update fields
                if (updatedData.getTestType() != null) {
                    existing.setTestType(updatedData.getTestType());
                }
                if (updatedData.getTestName() != null) {
                    existing.setTestName(updatedData.getTestName());
                }
                if (updatedData.getResult() != null) {
                    existing.setResult(updatedData.getResult());
                }
                if (updatedData.getStatus() != null) {
                    existing.setStatus(updatedData.getStatus());
                }
                if (updatedData.getNotes() != null) {
                    existing.setNotes(updatedData.getNotes());
                }
                if (updatedData.getTestDate() != null) {
                    existing.setTestDate(updatedData.getTestDate());
                }
                if (updatedData.getLabName() != null) {
                    existing.setLabName(updatedData.getLabName());
                }
                if (updatedData.getDoctorComments() != null) {
                    existing.setDoctorComments(updatedData.getDoctorComments());
                }
                
                existing.setUpdatedAt(LocalDateTime.now());
                
                TestResult saved = testResultRepository.save(existing);
                
                logger.info("✅ Test result {} updated", resultId);
                
                return saved;
            } else {
                logger.warn("Test result {} not found", resultId);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error updating test result {}: {}", resultId, e.getMessage());
            throw e;
        }
    }

    // ===== DELETE TEST RESULT =====
    
    public void deleteTestResult(Long resultId) {
        try {
            logger.info("Deleting test result {}", resultId);
            testResultRepository.deleteById(resultId);
            logger.info("✅ Test result {} deleted", resultId);
        } catch (Exception e) {
            logger.error("Error deleting test result {}: {}", resultId, e.getMessage());
            throw e;
        }
    }

    // ===== GET PENDING TEST RESULTS =====
    
    public List<TestResult> getPendingTestResults() {
        try {
            return testResultRepository.findByStatus("Pending");
        } catch (Exception e) {
            logger.error("Error fetching pending test results: {}", e.getMessage());
            return List.of();
        }
    }

    // ===== GET COMPLETED TEST RESULTS =====
    
    public List<TestResult> getCompletedTestResults() {
        try {
            return testResultRepository.findByStatus("Completed");
        } catch (Exception e) {
            logger.error("Error fetching completed test results: {}", e.getMessage());
            return List.of();
        }
    }

    // ===== GET TEST RESULTS BY STATUS =====
    
    public List<TestResult> getTestResultsByStatus(String status) {
        try {
            return testResultRepository.findByStatus(status);
        } catch (Exception e) {
            logger.error("Error fetching test results by status {}: {}", status, e.getMessage());
            return List.of();
        }
    }
}