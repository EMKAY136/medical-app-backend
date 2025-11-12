package com.medicalapp.medical_app_backend.controller;

import com.medicalapp.medical_app_backend.dto.ResultDto;
import com.medicalapp.medical_app_backend.entity.MedicalResult;
import com.medicalapp.medical_app_backend.repository.MedicalResultRepository;
import com.medicalapp.medical_app_backend.service.ResultService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/results")
public class ResultController {

    private static final Logger logger = LoggerFactory.getLogger(ResultController.class);

    @Autowired
    private ResultService resultService;

    @Autowired
    private MedicalResultRepository medicalResultRepository;

    // ========== PATIENT ENDPOINTS ==========

    @PostMapping
    public ResponseEntity<?> createResult(@Valid @RequestBody ResultDto resultDto,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("Patient {} creating result", userDetails.getUsername());
            Map<String, Object> response = resultService.createResult(resultDto, userDetails);
            
            Boolean success = (Boolean) response.get("success");
            if (success != null && success) {
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Error creating result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error creating result: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getUserResults(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("Fetching results for user: {}", userDetails.getUsername());
            List<ResultDto> results = resultService.getUserResults(userDetails);
            logger.info("Found {} results", results.size());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error fetching results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching results: " + e.getMessage()));
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<?> getRecentResults(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            List<ResultDto> results = resultService.getRecentResults(userDetails);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error fetching recent results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching recent results: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchResults(@RequestParam String testName,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        try {
            List<ResultDto> results = resultService.searchResultsByTestName(testName, userDetails);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            logger.error("Error searching results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error searching results: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getResultById(@PathVariable Long id,
                                          @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = resultService.getResultById(id, userDetails);
            
            Boolean success = (Boolean) response.get("success");
            if (success != null && success) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } catch (Exception e) {
            logger.error("Error fetching result by ID", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching result: " + e.getMessage()));
        }
    }

    /**
     * FIXED: Download result - handles both file and non-file results
     */
    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadResult(@PathVariable Long id,
                                           @AuthenticationPrincipal UserDetails userDetails) {
        try {
            logger.info("Download request for result ID: {} by user: {}", id, userDetails.getUsername());
            
            Optional<MedicalResult> resultOpt = medicalResultRepository.findById(id);
            
            if (resultOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Result not found"));
            }
            
            MedicalResult result = resultOpt.get();
            
            // Check if user owns this result or is admin
            boolean isOwner = result.getPatient().getUsername().equals(userDetails.getUsername());
            boolean isAdmin = userDetails.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
            
            if (!isOwner && !isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Unauthorized"));
            }
            
            // If no file attached, return result data as JSON
            if (result.getFilePath() == null || result.getFilePath().isEmpty()) {
                logger.info("Result {} has no file attached, returning JSON data", id);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "hasFile", false,
                    "result", Map.of(
                        "id", result.getId(),
                        "testName", result.getTestName(),
                        "result", result.getResult(),
                        "testDate", result.getTestDate().toString(),
                        "notes", result.getNotes() != null ? result.getNotes() : "",
                        "status", result.getStatus().name(),
                        "category", result.getCategory() != null ? result.getCategory() : "General",
                        "normalRange", result.getNormalRange() != null ? result.getNormalRange() : "N/A"
                    ),
                    "message", "This result has no attached file. View details above."
                ));
            }
            
            // Build full file path
            String uploadDir = "uploads/results";
            Path filePath = Paths.get(uploadDir, result.getFilePath());
            
            logger.info("Looking for file at: {}", filePath.toAbsolutePath());
            
            if (!Files.exists(filePath)) {
                logger.warn("File not found on disk: {}", filePath.toAbsolutePath());
                // Return JSON with file metadata even if file missing
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "hasFile", true,
                    "fileMissing", true,
                    "result", Map.of(
                        "id", result.getId(),
                        "testName", result.getTestName(),
                        "result", result.getResult(),
                        "testDate", result.getTestDate().toString(),
                        "notes", result.getNotes() != null ? result.getNotes() : "",
                        "status", result.getStatus().name(),
                        "fileName", result.getFileName() != null ? result.getFileName() : "Unknown"
                    ),
                    "message", "File metadata exists but file not found on server"
                ));
            }
            
            // File exists - serve it
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = result.getFileType();
            if (contentType == null) {
                contentType = Files.probeContentType(filePath);
            }
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            
            logger.info("Serving file: {} ({})", result.getFileName(), contentType);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                           "attachment; filename=\"" + result.getFileName() + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            logger.error("Error downloading result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Download failed: " + e.getMessage()));
        }
    }

    // ========== ADMIN ENDPOINTS ==========

    @PostMapping("/admin/upload")
    public ResponseEntity<?> uploadResult(@RequestBody Map<String, Object> resultData,
                                        @AuthenticationPrincipal UserDetails adminDetails) {
        try {
            logger.info("Admin uploading result (no file): {}", resultData);
            Map<String, Object> response = resultService.uploadResultForPatient(resultData, adminDetails);
            
            Boolean success = (Boolean) response.get("success");
            if (success != null && success) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Error uploading result", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error uploading result: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @PostMapping("/admin/upload-with-file")
    public ResponseEntity<?> uploadResultWithFile(
            @RequestParam("patientId") Long patientId,
            @RequestParam("testType") String testType,
            @RequestParam("result") String result,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "doctorName", required = false) String doctorName,
            @RequestParam(value = "testDate", required = false) String testDate,
            @RequestParam(value = "appointmentId", required = false) Long appointmentId,
            @RequestParam(value = "markAppointmentCompleted", required = false, defaultValue = "false") boolean markCompleted,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserDetails adminDetails) {
        
        try {
            logger.info("Admin uploading result with file for patient: {}", patientId);
            
            Map<String, Object> response = resultService.uploadResultWithFile(
                patientId, testType, result, notes, category, status, 
                doctorName, testDate, appointmentId, markCompleted, 
                file, adminDetails
            );
            
            Boolean success = (Boolean) response.get("success");
            if (success != null && success) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Error uploading result with file", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Error uploading result: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/admin/patient/{patientId}")
    public ResponseEntity<?> getPatientResults(@PathVariable Long patientId,
                                               @AuthenticationPrincipal UserDetails adminDetails) {
        try {
            Map<String, Object> response = resultService.getPatientResultsByAdmin(patientId, adminDetails);
            
            Boolean success = (Boolean) response.get("success");
            if (success != null && success) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } catch (Exception e) {
            logger.error("Error fetching patient results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching patient results: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllResults(@AuthenticationPrincipal UserDetails adminDetails) {
        try {
            Map<String, Object> response = resultService.getAllResultsByAdmin(adminDetails);
            
            Boolean success = (Boolean) response.get("success");
            if (success != null && success) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } catch (Exception e) {
            logger.error("Error fetching all results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching all results: " + e.getMessage()));
        }
    }

    // ========== GENERAL ENDPOINTS ==========

    @PutMapping("/{id}")
    public ResponseEntity<?> updateResult(@PathVariable Long id,
                                        @Valid @RequestBody ResultDto resultDto,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = resultService.updateResult(id, resultDto, userDetails);
            
            Boolean success = (Boolean) response.get("success");
            if (success != null && success) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            logger.error("Error updating result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error updating result: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteResult(@PathVariable Long id,
                                        @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = resultService.deleteResult(id, userDetails);
            
            Boolean success = (Boolean) response.get("success");
            if (success != null && success) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }
        } catch (Exception e) {
            logger.error("Error deleting result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error deleting result: " + e.getMessage()));
        }
    }

    // ========== STATISTICS ENDPOINTS ==========

    @GetMapping("/stats")
    public ResponseEntity<?> getUserResultStats(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> stats = resultService.getUserResultStats(userDetails);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error fetching user stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching stats: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<?> getOverallStats(@AuthenticationPrincipal UserDetails adminDetails) {
        try {
            Map<String, Object> stats = resultService.getOverallResultStats(adminDetails);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            logger.error("Error fetching overall stats", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching stats: " + e.getMessage()));
        }
    }
}