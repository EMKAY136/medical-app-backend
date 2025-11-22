package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.dto.ResultDto;
import com.medicalapp.medical_app_backend.entity.Appointment;
import com.medicalapp.medical_app_backend.entity.MedicalResult;
import com.medicalapp.medical_app_backend.entity.Result;
import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.repository.AppointmentRepository;
import com.medicalapp.medical_app_backend.repository.MedicalResultRepository;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ResultService {

    private static final Logger logger = LoggerFactory.getLogger(ResultService.class);

    @Autowired
    private MedicalResultRepository medicalResultRepository;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    @Value("${app.file.upload-dir:uploads/results}")
    private String uploadDir;

    // ========== WEBSOCKET NOTIFICATION ==========

    /**
     * Send WebSocket notification when a result is uploaded
     * Updated to match React Native app notification handler
     */
    private void sendWebSocketNotification(Long patientId, String testType, MedicalResult savedResult) {
        try {
            String userId = String.valueOf(patientId);
            
            logger.info("\n");
            logger.info("===========================================");
            logger.info("📤 ATTEMPTING TO SEND RESULT NOTIFICATION");
            logger.info("===========================================");
            logger.info("Patient ID: {}", patientId);
            logger.info("User ID String: {}", userId);
            logger.info("Test Name: {}", testType);
            logger.info("Result ID: {}", savedResult.getId());
            
            // Create notification payload - Make it match multiple possible patterns
            Map<String, Object> notification = new HashMap<>();
            notification.put("event", "TEST_RESULT_READY");
            notification.put("type", "result");
            notification.put("title", "Test Result Available");
            notification.put("message", "Your " + testType + " results are ready to view");
            notification.put("testName", testType);
            notification.put("resultId", savedResult.getId());
            notification.put("timestamp", LocalDateTime.now().toString());
            notification.put("data", Map.of(
                "testName", testType,
                "resultId", savedResult.getId(),
                "patientId", patientId,
                "status", savedResult.getStatus().name()
            ));
            
            logger.info("Notification Payload:");
            logger.info("  - event: {}", notification.get("event"));
            logger.info("  - type: {}", notification.get("type"));
            logger.info("  - title: {}", notification.get("title"));
            logger.info("  - message: {}", notification.get("message"));
            logger.info("  - testName: {}", notification.get("testName"));
            logger.info("  - resultId: {}", notification.get("resultId"));
            logger.info("Full Payload JSON: {}", notification);
            
            // Check if messagingTemplate is available
            if (messagingTemplate == null) {
                logger.error("❌ CRITICAL: SimpMessagingTemplate is NULL!");
                logger.error("WebSocket notifications CANNOT be sent!");
                return;
            } else {
                logger.info("✓ SimpMessagingTemplate is available");
            }
            
            // Send to topic
            String topicDestination = "/user/" + userId + "/topic/notifications";
            logger.info("Attempting to send to TOPIC: {}", topicDestination);
            try {
                messagingTemplate.convertAndSendToUser(userId, "/topic/notifications", notification);
                logger.info("✓✓✓ SUCCESSFULLY sent to topic: {}", topicDestination);
            } catch (Exception topicError) {
                logger.error("❌ FAILED to send to topic: {}", topicError.getMessage(), topicError);
            }
            
            // Also send to queue (backup channel)
            String queueDestination = "/user/" + userId + "/queue/messages";
            logger.info("Attempting to send to QUEUE: {}", queueDestination);
            try {
                messagingTemplate.convertAndSendToUser(userId, "/queue/messages", notification);
                logger.info("✓✓✓ SUCCESSFULLY sent to queue: {}", queueDestination);
            } catch (Exception queueError) {
                logger.error("❌ FAILED to send to queue: {}", queueError.getMessage(), queueError);
            }
            
            logger.info("===========================================");
            logger.info("📤 NOTIFICATION SENDING COMPLETED");
            logger.info("===========================================\n");
            
        } catch (Exception e) {
            logger.error("\n");
            logger.error("❌❌❌ CRITICAL ERROR IN sendWebSocketNotification ❌❌❌");
            logger.error("Exception Type: {}", e.getClass().getName());
            logger.error("Error Message: {}", e.getMessage());
            logger.error("Stack Trace:", e);
            logger.error("❌❌❌ END CRITICAL ERROR ❌❌❌\n");
        }
    }

    // ========== ADMIN METHODS ==========

    @Transactional
    public Map<String, Object> uploadResultWithFile(
            Long patientId, String testType, String result, String notes,
            String category, String status, String doctorName, String testDate,
            Long appointmentId, boolean markCompleted, MultipartFile file,
            UserDetails adminDetails) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("=== FILE UPLOAD DEBUG ===");
            logger.info("File parameter received: {}", file != null ? file.getOriginalFilename() : "NULL");
            logger.info("File size: {}", file != null ? file.getSize() : 0);
            logger.info("Patient ID: {}", patientId);
            logger.info("Admin {} uploading result with file for patient {}", 
               adminDetails.getUsername(), patientId);

            User admin = getCurrentUser(adminDetails);
            if (admin.getRole() != User.Role.ADMIN) {
                response.put("success", false);
                response.put("message", "Unauthorized: Admin access required");
                return response;
            }
            
            User patient = userRepository.findById(patientId)
                    .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + patientId));
            
            logger.info("Found patient: {} {}", patient.getFirstName(), patient.getLastName());
            
            MedicalResult medicalResult = new MedicalResult();
            medicalResult.setPatient(patient);
            medicalResult.setTestName(testType != null ? testType : "General Test");
            medicalResult.setResult(result != null ? result : "");
            medicalResult.setNotes(notes != null ? notes : "");
            medicalResult.setTestType(testType != null ? testType : "Lab Report");
            medicalResult.setCategory(category != null ? category : "General");
            medicalResult.setNormalRange("N/A");
            medicalResult.setUploadedBy((doctorName != null ? doctorName : "Admin") + 
                                       " (" + admin.getUsername() + ")");
            medicalResult.setDoctorName(doctorName != null ? doctorName : "Admin");
            medicalResult.setStatus(parseStatus(status));
            medicalResult.setTestDate(parseTestDate(testDate));
            medicalResult.setVisible(true);
            medicalResult.setDownloadable(true);
            
            MedicalResult savedResult = medicalResultRepository.save(medicalResult);
            
            if (file != null && !file.isEmpty()) {
                validateFile(file);
                String fileName = saveFile(file, savedResult.getId());
                
                savedResult.setFileName(fileName);
                savedResult.setFilePath(fileName);
                savedResult.setFileType(file.getContentType());
                savedResult.setFileSize(file.getSize());
                
                savedResult = medicalResultRepository.save(savedResult);
                logger.info("File uploaded: {}", fileName);
            }
            
            if (appointmentId != null) {
                handleAppointmentAssociationSimple(savedResult, appointmentId, patientId, markCompleted);
                savedResult = medicalResultRepository.save(savedResult);
            }
            
            logger.info("Result saved with ID: {} for patient: {}", savedResult.getId(), patientId);
            
            // 🔥 SAVE TO DATABASE AND SEND WEBSOCKET NOTIFICATION 🔥
notificationService.createAndSendResultNotification(patientId, savedResult.getId(), testType, savedResult.getStatus().name());
            
            response.put("success", true);
            response.put("message", "Test result uploaded successfully for " + 
                        patient.getFirstName() + " " + patient.getLastName());
            response.put("resultId", savedResult.getId());
            response.put("result", convertToDto(savedResult));
            
        } catch (Exception e) {
            logger.error("Error uploading result with file", e);
            response.put("success", false);
            response.put("message", "Error uploading result: " + e.getMessage());
        }
        
        return response;
    }

    @Transactional
    public Map<String, Object> uploadResultForPatient(Map<String, Object> resultData, UserDetails adminDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.info("Admin {} uploading result for patient", adminDetails.getUsername());
            
            User admin = getCurrentUser(adminDetails);
            if (admin.getRole() != User.Role.ADMIN) {
                response.put("success", false);
                response.put("message", "Unauthorized: Admin access required");
                return response;
            }
            
            Long patientId = extractLongValue(resultData, "patientId");
            if (patientId == null) {
                response.put("success", false);
                response.put("message", "Patient ID is required");
                return response;
            }
            
            User patient = userRepository.findById(patientId)
                    .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + patientId));
            
            logger.info("Found patient: {} {}", patient.getFirstName(), patient.getLastName());
            
            MedicalResult result = createMedicalResultFromMap(resultData, patient, admin);
            handleAppointmentAssociation(result, resultData, patientId);
            
            MedicalResult savedResult = medicalResultRepository.save(result);
            logger.info("Result saved with ID: {} for patient: {}", savedResult.getId(), patientId);
            
            String testType = (String) resultData.get("testType");
            
// 🔥 SAVE TO DATABASE AND SEND WEBSOCKET NOTIFICATION 🔥
notificationService.createAndSendResultNotification(patientId, savedResult.getId(), testType, savedResult.getStatus().name());
            
            response.put("success", true);
            response.put("message", "Test result uploaded successfully for " + 
                        patient.getFirstName() + " " + patient.getLastName());
            response.put("resultId", savedResult.getId());
            response.put("result", convertToDto(savedResult));
            
        } catch (NumberFormatException e) {
            logger.error("Number format error: {}", e.getMessage());
            response.put("success", false);
            response.put("message", "Invalid patient ID or appointment ID format");
        } catch (Exception e) {
            logger.error("Error uploading result", e);
            response.put("success", false);
            response.put("message", "Error uploading result: " + e.getMessage());
        }
        
        return response;
    }

    public Map<String, Object> getAllResultsForAdmin(UserDetails adminDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            User admin = getCurrentUser(adminDetails);
            if (admin.getRole() != User.Role.ADMIN) {
                response.put("success", false);
                response.put("message", "Unauthorized: Admin access required");
                return response;
            }
            
            List<MedicalResult> allResults = medicalResultRepository.findAll();
            
            List<Map<String, Object>> resultsList = allResults.stream()
                    .map(this::mapResultForAdmin)
                    .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("results", resultsList);
            response.put("totalCount", allResults.size());
            
        } catch (Exception e) {
            logger.error("Error fetching all results", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        
        return response;
    }

    public Map<String, Object> getPatientResultsByAdmin(Long patientId, UserDetails adminDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            User admin = getCurrentUser(adminDetails);
            if (admin.getRole() != User.Role.ADMIN) {
                response.put("success", false);
                response.put("message", "Unauthorized: Admin access required");
                return response;
            }
            
            User patient = userRepository.findById(patientId)
                    .orElseThrow(() -> new RuntimeException("Patient not found"));
            
            List<MedicalResult> results = medicalResultRepository.findByPatient(patient);
            
            List<Map<String, Object>> resultsList = results.stream()
                    .map(this::mapResultDetailed)
                    .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("results", resultsList);
            response.put("totalCount", results.size());
            response.put("patientName", patient.getFirstName() + " " + patient.getLastName());
            
        } catch (Exception e) {
            logger.error("Error fetching patient results", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        
        return response;
    }

    public List<ResultDto> getRecentResults(UserDetails userDetails) {
    try {
        User patient = getCurrentUser(userDetails);
        
        // Get results from last 30 days
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<MedicalResult> results = medicalResultRepository.findByPatient(patient);
        
        return results.stream()
                .filter(r -> r.isVisible() && r.isDownloadable())
                .filter(r -> r.getTestDate().isAfter(thirtyDaysAgo))
                .sorted((a, b) -> b.getTestDate().compareTo(a.getTestDate())) // newest first
                .limit(10) // only 10 most recent
                .map(this::convertToDto)
                .collect(Collectors.toList());

    } catch (Exception e) {
        logger.error("Error fetching recent results", e);
        return Collections.emptyList();
    }
}

    public Map<String, Object> getAllResultsByAdmin(UserDetails adminDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            User admin = getCurrentUser(adminDetails);
            if (admin.getRole() != User.Role.ADMIN) {
                response.put("success", false);
                response.put("message", "Unauthorized: Admin access required");
                return response;
            }
            
            List<MedicalResult> allResults = medicalResultRepository.findAll();
            
            List<Map<String, Object>> resultsList = allResults.stream()
                    .map(this::mapResultForAdminDetailed)
                    .collect(Collectors.toList());
            
            response.put("success", true);
            response.put("results", resultsList);
            response.put("totalCount", allResults.size());
            
        } catch (Exception e) {
            logger.error("Error fetching all results by admin", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
        }
        
        return response;
    }

    public Map<String, Object> getOverallResultStats(UserDetails adminDetails) {
        try {
            User admin = getCurrentUser(adminDetails);
            if (admin.getRole() != User.Role.ADMIN) {
                throw new RuntimeException("Unauthorized: Admin access required");
            }
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalResults", medicalResultRepository.count());
            stats.put("totalPatients", countDistinctPatients());
            stats.put("pendingResults", countByStatus(MedicalResult.ResultStatus.PENDING));
            stats.put("completedResults", countByStatus(MedicalResult.ResultStatus.COMPLETED));
            stats.put("criticalResults", countByStatus(MedicalResult.ResultStatus.CRITICAL));
            
            return stats;
            
        } catch (Exception e) {
            logger.error("Error fetching overall stats", e);
            throw new RuntimeException("Error fetching statistics: " + e.getMessage());
        }
    }

    public ResponseEntity<?> downloadResultFile(Long resultId, UserDetails userDetails) {
        try {
            User user = getCurrentUser(userDetails);
            MedicalResult result = medicalResultRepository.findById(resultId)
                    .orElseThrow(() -> new RuntimeException("Result not found"));

            boolean isAdmin = user.getRole() == User.Role.ADMIN;
            boolean isOwner = result.getPatient().getId().equals(user.getId());
            
            if (!isAdmin && !isOwner) {
                return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "message", "Unauthorized access to this file"
                ));
            }

            if (result.getFileName() == null || result.getFileName().isEmpty()) {
                logger.info("Result {} has no file attached, returning JSON data", resultId);
                return ResponseEntity.ok().body(Map.of(
                    "success", true,
                    "hasFile", false,
                    "result", convertToDownloadFormat(result)
                ));
            }

            if (!result.isDownloadable()) {
                return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "message", "This file is not available for download"
                ));
            }

            Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
            Path filePath = uploadPath.resolve(result.getFilePath());
            logger.info("Looking for file at: {}", filePath);

            if (!Files.exists(filePath)) {
                logger.error("File path exists in DB but file not found on disk: {}", result.getFilePath());
                logger.error("Full path checked: {}", filePath.toAbsolutePath());
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "File not found on server"
                ));
            }

            byte[] fileBytes = Files.readAllBytes(filePath);
            String base64Content = java.util.Base64.getEncoder().encodeToString(fileBytes);

            String contentType = result.getFileType();
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            logger.info("Sending file {} as base64 ({} bytes)", result.getFileName(), fileBytes.length);

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of(
                        "success", true,
                        "hasFile", true,
                        "fileName", result.getFileName(),
                        "fileType", contentType,
                        "fileSize", fileBytes.length,
                        "base64Content", base64Content
                    ));

        } catch (Exception e) {
            logger.error("Error downloading file", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error downloading file: " + e.getMessage()
            ));
        }
    }

    // ========== PATIENT METHODS ==========

    @Transactional
    public Map<String, Object> createResult(ResultDto resultDto, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();

        try {
            User patient = getCurrentUser(userDetails);

            MedicalResult result = new MedicalResult();
            result.setPatient(patient);
            result.setTestName(resultDto.getTestName());
            result.setResult(resultDto.getResult());
            result.setNormalRange(resultDto.getNormalRange());
            result.setNotes(resultDto.getNotes());
            result.setCategory(resultDto.getCategory() != null ? resultDto.getCategory() : "General");
            result.setTestType(resultDto.getTestType() != null ? resultDto.getTestType() : "Lab Report");
            result.setStatus(resultDto.getStatus() != null ? resultDto.getStatus() : MedicalResult.ResultStatus.PENDING);
            result.setTestDate(resultDto.getTestDate() != null ? resultDto.getTestDate() : LocalDateTime.now());
            result.setUploadedBy(patient.getFirstName() + " " + patient.getLastName());

            if (resultDto.getAppointmentId() != null) {
                Appointment appointment = appointmentRepository.findById(resultDto.getAppointmentId())
                        .orElseThrow(() -> new RuntimeException("Appointment not found"));
                
                if (!appointment.getPatient().getId().equals(patient.getId())) {
                    response.put("success", false);
                    response.put("message", "Unauthorized: Cannot associate with another patient's appointment");
                    return response;
                }
                result.setAppointment(appointment);
            }

            MedicalResult savedResult = medicalResultRepository.save(result);

            response.put("success", true);
            response.put("message", "Medical result added successfully!");
            response.put("result", convertToDto(savedResult));

        } catch (Exception e) {
            logger.error("Error creating result", e);
            response.put("success", false);
            response.put("message", "Error creating result: " + e.getMessage());
        }

        return response;
    }

    // REPLACE the getUserResults() method in ResultService.java with this:

public List<ResultDto> getUserResults(UserDetails userDetails) {
    try {
        logger.info("========================================");
        logger.info("📋 getUserResults() - START");
        logger.info("👤 User: {}", userDetails != null ? userDetails.getUsername() : "NULL");
        
        // CRITICAL: Check if userDetails is null
        if (userDetails == null) {
            logger.error("❌ UserDetails is NULL - authentication failed!");
            return Collections.emptyList();
        }
        
        // Get the patient User entity
        User patient = getCurrentUser(userDetails);
        logger.info("✅ Found patient: {} (ID: {})", patient.getUsername(), patient.getId());
        
        // Use the existing repository method
        logger.info("🔍 Querying repository with findByPatient...");
        List<MedicalResult> results = medicalResultRepository.findByPatient(patient);
        
        logger.info("✅ Repository returned {} results", results.size());
        
        // Filter for visible and downloadable results
        List<MedicalResult> filteredResults = results.stream()
                .filter(r -> r.isVisible() && r.isDownloadable())
                .collect(Collectors.toList());
        
        logger.info("✅ After filtering: {} visible/downloadable results", filteredResults.size());
        
        // Convert to DTOs
        List<ResultDto> dtoList = filteredResults.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        
        logger.info("✅ Converted to DTOs: {} items", dtoList.size());
        logger.info("✅ getUserResults() - SUCCESS");
        logger.info("========================================");
        
        return dtoList;

    } catch (Exception e) {
        logger.error("❌❌❌ ERROR IN getUserResults() ❌❌❌");
        logger.error("Exception type: {}", e.getClass().getName());
        logger.error("Message: {}", e.getMessage());
        logger.error("Stack trace:", e);
        logger.error("========================================");
        return Collections.emptyList();
    }
}


    public List<ResultDto> searchResultsByTestName(String testName, UserDetails userDetails) {
        try {
            User patient = getCurrentUser(userDetails);
            List<MedicalResult> results = medicalResultRepository
                    .findByPatientAndTestNameContainingIgnoreCase(patient, testName);

            return results.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Error searching results", e);
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getResultById(Long resultId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            User patient = getCurrentUser(userDetails);
            MedicalResult result = medicalResultRepository.findById(resultId)
                    .orElseThrow(() -> new RuntimeException("Result not found"));
            
            if (!result.getPatient().getId().equals(patient.getId())) {
                response.put("success", false);
                response.put("message", "Unauthorized access");
                return response;
            }

            response.put("success", true);
            response.put("result", convertToDownloadFormat(result));

        } catch (Exception e) {
            logger.error("Error fetching result by ID", e);
            response.put("success", false);
            response.put("message", "Error fetching result: " + e.getMessage());
        }

        return response;
    }

    @Transactional
    public Map<String, Object> updateResult(Long resultId, ResultDto resultDto, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            User patient = getCurrentUser(userDetails);
            MedicalResult result = medicalResultRepository.findById(resultId)
                    .orElseThrow(() -> new RuntimeException("Result not found"));
            
            if (!result.getPatient().getId().equals(patient.getId())) {
                response.put("success", false);
                response.put("message", "Unauthorized access");
                return response;
            }

            updateResultFields(result, resultDto);

            MedicalResult savedResult = medicalResultRepository.save(result);
            response.put("success", true);
            response.put("message", "Result updated successfully");
            response.put("result", convertToDto(savedResult));

        } catch (Exception e) {
            logger.error("Error updating result", e);
            response.put("success", false);
            response.put("message", "Error updating result: " + e.getMessage());
        }

        return response;
    }

    @Transactional
    public Map<String, Object> deleteResult(Long resultId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            User patient = getCurrentUser(userDetails);
            MedicalResult result = medicalResultRepository.findById(resultId)
                    .orElseThrow(() -> new RuntimeException("Result not found"));
            
            if (!result.getPatient().getId().equals(patient.getId())) {
                response.put("success", false);
                response.put("message", "Unauthorized access");
                return response;
            }

            result.setVisible(false);
            medicalResultRepository.save(result);

            response.put("success", true);
            response.put("message", "Result deleted successfully");

        } catch (Exception e) {
            logger.error("Error deleting result", e);
            response.put("success", false);
            response.put("message", "Error deleting result: " + e.getMessage());
        }

        return response;
    }

    public Map<String, Object> getUserResultStats(UserDetails userDetails) {
        try {
            User patient = getCurrentUser(userDetails);
            List<MedicalResult> userResults = medicalResultRepository.findByPatient(patient);
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalResults", userResults.size());
            stats.put("positiveResults", countResultsWithKeyword(userResults, "POSITIVE"));
            stats.put("negativeResults", countResultsWithKeyword(userResults, "NEGATIVE"));
            stats.put("pendingResults", countResultsByStatus(userResults, MedicalResult.ResultStatus.PENDING));
            stats.put("completedResults", countResultsByStatus(userResults, MedicalResult.ResultStatus.COMPLETED));
            
            return stats;
        } catch (Exception e) {
            logger.error("Error fetching user stats", e);
            throw new RuntimeException("Error fetching user statistics: " + e.getMessage());
        }
    }

    // ========== HELPER METHODS ==========

    private User getCurrentUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private Long extractLongValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            logger.error("Failed to parse {} as Long: {}", key, value);
            return null;
        }
    }

    private MedicalResult createMedicalResultFromMap(Map<String, Object> data, User patient, User admin) {
        MedicalResult result = new MedicalResult();
        result.setPatient(patient);
        
        String testType = (String) data.get("testType");
        String resultValue = (String) data.get("result");
        String notes = (String) data.getOrDefault("notes", "");
        String status = (String) data.getOrDefault("status", "NORMAL");
        String doctorName = (String) data.getOrDefault("doctorName", "Admin");
        
        result.setTestName(testType != null ? testType : "General Test");
        result.setResult(resultValue != null ? resultValue : "");
        result.setNotes(notes);
        result.setTestType((String) data.getOrDefault("testType", "Lab Report"));
        result.setCategory((String) data.getOrDefault("category", "General"));
        result.setNormalRange("N/A");
        result.setUploadedBy(doctorName + " (" + admin.getUsername() + ")");
        result.setDoctorName(doctorName);
        result.setStatus(parseStatus(status));
        result.setTestDate(parseTestDate((String) data.get("testDate")));
        result.setVisible(true);
        result.setDownloadable(true);
        
        return result;
    }

    @Transactional
    private void handleAppointmentAssociation(MedicalResult result, Map<String, Object> data, Long patientId) {
        if (!data.containsKey("appointmentId") || data.get("appointmentId") == null) {
            return;
        }
        
        try {
            Long appointmentId = extractLongValue(data, "appointmentId");
            if (appointmentId == null) return;
            
            Optional<Appointment> appointmentOpt = appointmentRepository.findById(appointmentId);
            
            if (appointmentOpt.isPresent()) {
                Appointment appointment = appointmentOpt.get();
                
                if (!appointment.getPatient().getId().equals(patientId)) {
                    logger.warn("Appointment {} does not belong to patient {}", appointmentId, patientId);
                    return;
                }
                
                result.setAppointment(appointment);
                
                Boolean markCompleted = (Boolean) data.getOrDefault("markAppointmentCompleted", false);
                if (markCompleted && !"COMPLETED".equals(appointment.getStatus())) {
                    appointment.setStatus("COMPLETED");
                    appointmentRepository.save(appointment);
                    logger.info("Marked appointment {} as completed", appointmentId);
                }
            }
        } catch (Exception e) {
            logger.error("Error handling appointment association", e);
        }
    }

    @Transactional
    private void handleAppointmentAssociationSimple(MedicalResult result, Long appointmentId, 
                                                    Long patientId, boolean markCompleted) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (!appointment.getPatient().getId().equals(patientId)) {
            throw new RuntimeException("Appointment does not belong to this patient");
        }

        result.setAppointment(appointment);

        if (markCompleted && !"COMPLETED".equals(appointment.getStatus())) {
            appointment.setStatus("COMPLETED");
            appointmentRepository.save(appointment);
            logger.info("Marked appointment {} as completed", appointmentId);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size of 10MB");
        }

        String contentType = file.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("File type cannot be determined");
        }

        boolean isValidType = contentType.startsWith("image/") || 
                             contentType.equals("application/pdf") ||
                             contentType.equals("application/msword") ||
                             contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        
        if (!isValidType) {
            throw new IllegalArgumentException("Invalid file type. Only images, PDFs, and Word documents are allowed");
        }
    }

    private String saveFile(MultipartFile file, Long resultId) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            logger.info("Created upload directory: {}", uploadPath.toAbsolutePath());
        }

        String originalFilename = file.getOriginalFilename();
        String safeFileName = System.currentTimeMillis() + "_" + originalFilename;
        Path filePath = uploadPath.resolve(safeFileName);

        Files.write(filePath, file.getBytes());
        logger.info("File saved successfully to: {} ({} bytes)", filePath.toAbsolutePath(), file.getSize());

        return safeFileName;
    }

    private LocalDateTime parseTestDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }

        try {
            return LocalDateTime.parse(dateStr);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(dateStr).atStartOfDay();
            } catch (DateTimeParseException ex) {
                logger.warn("Failed to parse date: {}, using current time", dateStr);
                return LocalDateTime.now();
            }
        }
    }

    private MedicalResult.ResultStatus parseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return MedicalResult.ResultStatus.COMPLETED;
        }

        try {
            return MedicalResult.ResultStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid status: {}, using COMPLETED", status);
            return MedicalResult.ResultStatus.COMPLETED;
        }
    }

    private void updateResultFields(MedicalResult result, ResultDto dto) {
        if (dto.getTestName() != null) result.setTestName(dto.getTestName());
        if (dto.getResult() != null) result.setResult(dto.getResult());
        if (dto.getNormalRange() != null) result.setNormalRange(dto.getNormalRange());
        if (dto.getNotes() != null) result.setNotes(dto.getNotes());
        if (dto.getCategory() != null) result.setCategory(dto.getCategory());
        if (dto.getTestType() != null) result.setTestType(dto.getTestType());
        if (dto.getStatus() != null) result.setStatus(dto.getStatus());
        if (dto.getTestDate() != null) result.setTestDate(dto.getTestDate());
    }

    private Map<String, Object> mapResultForAdmin(MedicalResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", result.getId());
        map.put("patientId", result.getPatient().getId());
        map.put("patientName", result.getPatient().getFirstName() + " " + result.getPatient().getLastName());
        map.put("testName", result.getTestName());
        map.put("result", result.getResult());
        map.put("testDate", result.getTestDate().toString());
        map.put("status", result.getStatus().name());
        map.put("uploadedBy", result.getUploadedBy() != null ? result.getUploadedBy() : "N/A");
        return map;
    }

    private Map<String, Object> mapResultDetailed(MedicalResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", result.getId());
        map.put("testName", result.getTestName());
        map.put("result", result.getResult());
        map.put("category", result.getCategory());
        map.put("status", result.getStatus().name());
        map.put("testDate", result.getTestDate().toString());
        map.put("normalRange", result.getNormalRange());
        map.put("notes", result.getNotes());
        return map;
    }

    private Map<String, Object> mapResultForAdminDetailed(MedicalResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", result.getId());
        map.put("patientId", result.getPatient().getId());
        map.put("patientName", result.getPatient().getFirstName() + " " + result.getPatient().getLastName());
        map.put("testName", result.getTestName());
        map.put("result", result.getResult());
        map.put("category", result.getCategory());
        map.put("status", result.getStatus().name());
        map.put("testDate", result.getTestDate().toString());
        map.put("uploadedBy", result.getUploadedBy() != null ? result.getUploadedBy() : "N/A");
        return map;
    }

    private ResultDto convertToDto(MedicalResult result) {
        ResultDto dto = new ResultDto();
        dto.setId(result.getId());
        dto.setTestName(result.getTestName());
        dto.setResult(result.getResult());
        dto.setNormalRange(result.getNormalRange());
        dto.setNotes(result.getNotes());
        dto.setTestDate(result.getTestDate());
        dto.setCategory(result.getCategory());
        dto.setTestType(result.getTestType());
        dto.setStatus(result.getStatus());
        dto.setAppointmentId(result.getAppointment() != null ? result.getAppointment().getId() : null);
        return dto;
    }

    private Map<String, Object> convertToDownloadFormat(MedicalResult result) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", result.getId().toString());
        dto.put("name", result.getTestName());
        dto.put("date", result.getTestDate().toLocalDate().toString());
        dto.put("category", result.getCategory());
        dto.put("status", result.getStatus().getDisplayName());
        dto.put("size", result.getFileSize() != null ? result.getFileSize() : "N/A");
        dto.put("type", result.getTestType());
        dto.put("testName", result.getTestName());
        dto.put("testDate", result.getTestDate().toString());
        dto.put("testType", result.getTestType());
        return dto;
    }

    private long countDistinctPatients() {
        return medicalResultRepository.findAll().stream()
                .map(MedicalResult::getPatient)
                .distinct()
                .count();
    }

    private long countByStatus(MedicalResult.ResultStatus status) {
        return medicalResultRepository.findAll().stream()
                .filter(r -> r.getStatus() == status)
                .count();
    }

    private long countResultsWithKeyword(List<MedicalResult> results, String keyword) {
        return results.stream()
                .filter(r -> r.getResult() != null && r.getResult().toUpperCase().contains(keyword))
                .count();
    }

    private long countResultsByStatus(List<MedicalResult> results, MedicalResult.ResultStatus status) {
        return results.stream()
                .filter(r -> r.getStatus() == status)
                .count();
    }

    private List<Map<String, Object>> getMonthlyResultsTrend(User patient) {
        List<Map<String, Object>> trend = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 5; i >= 0; i--) {
            LocalDateTime monthStart = now.minusMonths(i).withDayOfMonth(1)
                    .withHour(0).withMinute(0).withSecond(0);
            LocalDateTime monthEnd = monthStart.plusMonths(1).minusSeconds(1);
            
            long count = medicalResultRepository.countByPatientAndTestDateBetween(
                    patient, monthStart, monthEnd);
            
            trend.add(Map.of(
                "month", monthStart.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                "count", count
            ));
        }
        
        return trend;
    }

    private String generateExportFileName(String format, int resultCount) {
        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return String.format("medical_results_%d_items_%s.%s", 
                resultCount, timestamp, format.toLowerCase());
    }

    private String createExportFile(List<MedicalResult> results, String format, String fileName) {
        try {
            Path exportPath = Paths.get(uploadDir, "exports");
            if (!Files.exists(exportPath)) {
                Files.createDirectories(exportPath);
            }

            Path filePath = exportPath.resolve(fileName);

            switch (format.toUpperCase()) {
                case "CSV":
                    createCSVExport(results, filePath);
                    break;
                case "JSON":
                    createJSONExport(results, filePath);
                    break;
                default:
                    logger.warn("Unsupported export format: {}", format);
                    return null;
            }

            return filePath.toString();
        } catch (Exception e) {
            logger.error("Error creating export file", e);
            return null;
        }
    }

    private void createCSVExport(List<MedicalResult> results, Path filePath) throws IOException {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Test Name,Result,Category,Status,Test Date,Notes\n");
        
        for (MedicalResult result : results) {
            csv.append(String.format("\"%d\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                result.getId(),
                escapeCSV(result.getTestName()),
                escapeCSV(result.getResult()),
                escapeCSV(result.getCategory()),
                result.getStatus().getDisplayName(),
                result.getTestDate().toLocalDate().toString(),
                escapeCSV(result.getNotes() != null ? result.getNotes() : "")
            ));
        }
        
        Files.write(filePath, csv.toString().getBytes());
        logger.info("CSV export created: {}", filePath);
    }

    private void createJSONExport(List<MedicalResult> results, Path filePath) throws IOException {
        List<Map<String, Object>> jsonResults = results.stream()
                .map(this::convertToDownloadFormat)
                .collect(Collectors.toList());
        
        String json = "{\n  \"results\": " + jsonResults.toString() + 
                     ",\n  \"exportDate\": \"" + LocalDateTime.now().toString() + 
                     "\",\n  \"totalCount\": " + results.size() + "\n}";
        
        Files.write(filePath, json.getBytes());
        logger.info("JSON export created: {}", filePath);
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
}