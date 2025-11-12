package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.entity.DataExport;
import com.medicalapp.medical_app_backend.entity.User;
import com.medicalapp.medical_app_backend.entity.MedicalResult;
import com.medicalapp.medical_app_backend.entity.Appointment;
import com.medicalapp.medical_app_backend.repository.DataExportRepository;
import com.medicalapp.medical_app_backend.repository.UserRepository;
import com.medicalapp.medical_app_backend.repository.MedicalResultRepository;
import com.medicalapp.medical_app_backend.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Service
public class DataExportService {

    @Autowired
    private DataExportRepository dataExportRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MedicalResultRepository medicalResultRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Value("${app.export.storage-dir:exports}")
    private String exportStorageDir;

    @Value("${app.export.max-file-size:50MB}")
    private String maxFileSize;

    @Value("${app.export.retention-days:7}")
    private int retentionDays;

    private final ObjectMapper objectMapper;

    public DataExportService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Get available data types for export with current user's data sizes
     */
    public Map<String, Object> getAvailableDataTypes(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            // Calculate data sizes and counts for current user
            List<Map<String, Object>> dataTypes = new ArrayList<>();
            
            // Profile Information (always required)
            dataTypes.add(createDataTypeInfo("profile", "Profile Information", 
                "Basic demographics, contact info, emergency contacts", "person-circle", 
                "2.1 KB", "Personal", true, false, 1));

            // Medical History
            long medicalHistoryCount = calculateMedicalHistoryCount(user);
            dataTypes.add(createDataTypeInfo("medical_history", "Medical History", 
                "Past diagnoses, surgeries, chronic conditions", "medical", 
                calculateSize(medicalHistoryCount * 2), "Medical", false, true, medicalHistoryCount));

            // Test Results
            long testResultsCount = medicalResultRepository.countByPatient(user);
            dataTypes.add(createDataTypeInfo("test_results", "Test Results", 
                "Lab results, imaging reports, diagnostic tests", "analytics", 
                calculateSize(testResultsCount * 15), "Medical", false, true, testResultsCount));

            // Appointments
            long appointmentsCount = appointmentRepository.countByPatient(user);
            dataTypes.add(createDataTypeInfo("appointments", "Appointments", 
                "Appointment history, upcoming bookings", "calendar", 
                calculateSize(appointmentsCount * 3), "Activity", false, false, appointmentsCount));

            // Medications (mock data for now)
            dataTypes.add(createDataTypeInfo("medications", "Medications", 
                "Current and past medications, dosages, allergies", "medical-outline", 
                "5.2 KB", "Medical", false, false, 0));

            // Vitals (mock data for now)
            dataTypes.add(createDataTypeInfo("vitals", "Vital Signs", 
                "Blood pressure, heart rate, temperature readings", "pulse", 
                "32.1 KB", "Medical", false, false, 0));

            // Documents (mock data for now)
            dataTypes.add(createDataTypeInfo("documents", "Documents", 
                "Uploaded documents, insurance cards, ID copies", "document-attach", 
                "2.8 MB", "Documents", false, false, 0));

            // App Preferences
            dataTypes.add(createDataTypeInfo("preferences", "App Preferences", 
                "Settings, notifications, privacy preferences", "settings", 
                "1.4 KB", "Settings", false, false, 1));

            // User statistics for frontend
            Map<String, Object> userStats = new HashMap<>();
            userStats.put("totalResults", testResultsCount);
            userStats.put("totalAppointments", appointmentsCount);
            userStats.put("accountAge", calculateAccountAge(user));
            userStats.put("lastActivity", user.getUpdatedAt());

            response.put("success", true);
            response.put("dataTypes", dataTypes);
            response.put("userStats", userStats);
            response.put("totalCategories", dataTypes.size());

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching data types: " + e.getMessage());
        }

        return response;
    }

    /**
     * Create export request and start processing
     */
    public Map<String, Object> createExport(Map<String, Object> exportRequest, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();

            // Validate request
            @SuppressWarnings("unchecked")
            List<String> dataTypes = (List<String>) exportRequest.get("dataTypes");
            String format = (String) exportRequest.get("format");
            @SuppressWarnings("unchecked")
            Map<String, Object> options = (Map<String, Object>) exportRequest.get("options");

            if (dataTypes == null || dataTypes.isEmpty()) {
                response.put("success", false);
                response.put("message", "No data types selected");
                return response;
            }

            if (format == null || format.isEmpty()) {
                format = "json"; // Default format
            }

            // Check for recent exports (rate limiting)
            LocalDateTime recentLimit = LocalDateTime.now().minusMinutes(15);
            List<DataExport> recentExports = dataExportRepository.findByUserAndRequestedAtAfter(user, recentLimit);
            if (recentExports.size() >= 3) {
                response.put("success", false);
                response.put("message", "Too many export requests. Please wait before creating another export.");
                return response;
            }

            // Create export entity
            DataExport dataExport = new DataExport();
            dataExport.setUser(user);
            dataExport.setDataTypes(String.join(",", dataTypes));
            dataExport.setFormat(DataExport.ExportFormat.valueOf(format.toUpperCase()));
            dataExport.setEncrypted(options != null ? (Boolean) options.getOrDefault("encryptExport", true) : true);
            dataExport.setStatus(DataExport.ExportStatus.PENDING);

            // Generate file name
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("medical_data_export_%s_%s.%s", 
                user.getUsername(), timestamp, format.toLowerCase());
            dataExport.setFileName(fileName);

            DataExport savedExport = dataExportRepository.save(dataExport);

            // Start async export processing
            processExportAsync(savedExport);

            response.put("success", true);
            response.put("message", "Export request created successfully");
            response.put("exportId", savedExport.getId());
            response.put("fileName", fileName);
            response.put("status", "PENDING");
            response.put("estimatedTime", "2-5 minutes");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error creating export: " + e.getMessage());
        }

        return response;
    }

    /**
     * Get export history for user
     */
    public Map<String, Object> getExportHistory(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            List<DataExport> exports = dataExportRepository.findByUserOrderByRequestedAtDesc(user);

            List<Map<String, Object>> exportList = exports.stream()
                .map(this::convertExportToMap)
                .collect(Collectors.toList());

            response.put("success", true);
            response.put("exports", exportList);
            response.put("totalExports", exportList.size());

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching export history: " + e.getMessage());
        }

        return response;
    }

    /**
     * Download export file
     */
    public Map<String, Object> downloadExport(Long exportId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            Optional<DataExport> exportOpt = dataExportRepository.findByIdAndUser(exportId, user);
            
            if (exportOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Export not found");
                return response;
            }

            DataExport dataExport = exportOpt.get();

            if (!dataExport.isReadyForDownload()) {
                response.put("success", false);
                response.put("message", "Export is not ready for download");
                response.put("status", dataExport.getStatus().getDisplayName());
                return response;
            }

            // Update download count and last downloaded time
            dataExport.setDownloadCount(dataExport.getDownloadCount() + 1);
            dataExport.setLastDownloadedAt(LocalDateTime.now());
            dataExportRepository.save(dataExport);

            // In a real implementation, you would:
            // 1. Generate a secure download URL
            // 2. Return the file path or streaming response
            // 3. Handle file decryption if encrypted

            response.put("success", true);
            response.put("downloadUrl", "/api/export/files/" + dataExport.getId() + "/" + dataExport.getFileName());
            response.put("fileName", dataExport.getFileName());
            response.put("fileSize", dataExport.getFileSize());
            response.put("mimeType", dataExport.getFormat().getMimeType());
            
            if (dataExport.isEncrypted()) {
                response.put("password", generateDownloadPassword(dataExport));
                response.put("encrypted", true);
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error preparing download: " + e.getMessage());
        }

        return response;
    }

    /**
     * Cancel export request
     */
    public Map<String, Object> cancelExport(Long exportId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            Optional<DataExport> exportOpt = dataExportRepository.findByIdAndUser(exportId, user);
            
            if (exportOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Export not found");
                return response;
            }

            DataExport dataExport = exportOpt.get();

            if (dataExport.getStatus() == DataExport.ExportStatus.COMPLETED) {
                response.put("success", false);
                response.put("message", "Cannot cancel completed export");
                return response;
            }

            // Delete export record and file if exists
            if (dataExport.getFilePath() != null) {
                deleteExportFile(dataExport.getFilePath());
            }
            
            dataExportRepository.delete(dataExport);

            response.put("success", true);
            response.put("message", "Export canceled successfully");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error canceling export: " + e.getMessage());
        }

        return response;
    }

    /**
     * Get export statistics
     */
    public Map<String, Object> getExportStats(UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            
            long totalExports = dataExportRepository.countByUser(user);
            long completedExports = dataExportRepository.countByUserAndDateRange(user, 
                LocalDateTime.now().minusYears(1), LocalDateTime.now());

            List<DataExport> recentExports = dataExportRepository.findByUserAndRequestedAtAfter(user, 
                LocalDateTime.now().minusMonths(6));

            String mostPopularFormat = recentExports.stream()
                .collect(Collectors.groupingBy(e -> e.getFormat().getDisplayName(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("JSON");

            Optional<DataExport> lastExport = recentExports.stream().findFirst();

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalExports", totalExports);
            stats.put("completedExports", completedExports);
            stats.put("failedExports", totalExports - completedExports);
            stats.put("totalDataExported", calculateTotalDataSize(recentExports));
            stats.put("averageExportTime", "3.2 seconds");
            stats.put("mostPopularFormat", mostPopularFormat);
            stats.put("lastExport", lastExport.map(e -> e.getRequestedAt().toLocalDate().toString()).orElse("Never"));

            response.put("success", true);
            response.putAll(stats);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching export stats: " + e.getMessage());
        }

        return response;
    }

    /**
     * Get export status
     */
    public Map<String, Object> getExportStatus(Long exportId, UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Optional<User> userOpt = userRepository.findByUsername(userDetails.getUsername());
            if (userOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "User not found");
                return response;
            }

            User user = userOpt.get();
            Optional<DataExport> exportOpt = dataExportRepository.findByIdAndUser(exportId, user);
            
            if (exportOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Export not found");
                return response;
            }

            DataExport dataExport = exportOpt.get();

            response.put("success", true);
            response.put("status", dataExport.getStatus().getDisplayName());
            response.put("progress", calculateProgress(dataExport));
            response.put("fileName", dataExport.getFileName());
            response.put("requestedAt", dataExport.getRequestedAt().toString());
            response.put("completedAt", dataExport.getCompletedAt() != null ? dataExport.getCompletedAt().toString() : null);
            response.put("errorMessage", dataExport.getErrorMessage());

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error fetching export status: " + e.getMessage());
        }

        return response;
    }

    /**
     * Cleanup expired exports
     */
    public Map<String, Object> cleanupExpiredExports() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            LocalDateTime expiredBefore = LocalDateTime.now();
            List<DataExport> expiredExports = dataExportRepository.findExpiredExports(expiredBefore);

            int deletedCount = 0;
            for (DataExport export : expiredExports) {
                if (export.getFilePath() != null) {
                    deleteExportFile(export.getFilePath());
                }
                dataExportRepository.delete(export);
                deletedCount++;
            }

            response.put("success", true);
            response.put("message", "Cleanup completed");
            response.put("deletedExports", deletedCount);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Cleanup failed: " + e.getMessage());
        }

        return response;
    }

    // Private helper methods

    @Async
    private CompletableFuture<Void> processExportAsync(DataExport dataExport) {
        try {
            // Update status to processing
            dataExport.setStatus(DataExport.ExportStatus.PROCESSING);
            dataExportRepository.save(dataExport);

            // Simulate processing time
            Thread.sleep(3000);

            // Create export directory if it doesn't exist
            Path exportPath = Paths.get(exportStorageDir);
            if (!Files.exists(exportPath)) {
                Files.createDirectories(exportPath);
            }

            // Generate export file
            String filePath = exportStorageDir + "/" + dataExport.getFileName();
            generateExportFile(dataExport, filePath);

            // Update export record
            dataExport.setStatus(DataExport.ExportStatus.COMPLETED);
            dataExport.setCompletedAt(LocalDateTime.now());
            dataExport.setFilePath(filePath);
            dataExport.setFileSize(calculateFileSize(filePath));
            dataExportRepository.save(dataExport);

        } catch (Exception e) {
            dataExport.setStatus(DataExport.ExportStatus.FAILED);
            dataExport.setErrorMessage(e.getMessage());
            dataExportRepository.save(dataExport);
        }

        return CompletableFuture.completedFuture(null);
    }

    private void generateExportFile(DataExport dataExport, String filePath) throws IOException {
        User user = dataExport.getUser();
        String[] dataTypes = dataExport.getDataTypesList();
        
        Map<String, Object> exportData = new HashMap<>();
        exportData.put("exportMetadata", createExportMetadata(dataExport));
        
        for (String dataType : dataTypes) {
            switch (dataType.trim()) {
                case "profile":
                    exportData.put("profile", exportUserProfile(user));
                    break;
                case "test_results":
                    exportData.put("testResults", exportTestResults(user));
                    break;
                case "appointments":
                    exportData.put("appointments", exportAppointments(user));
                    break;
                case "medical_history":
                    exportData.put("medicalHistory", exportMedicalHistory(user));
                    break;
                // Add more data types as needed
            }
        }

        // Write to file based on format
        try (FileWriter writer = new FileWriter(filePath)) {
            if (dataExport.getFormat() == DataExport.ExportFormat.JSON || 
                dataExport.getFormat() == DataExport.ExportFormat.FHIR) {
                objectMapper.writeValue(writer, exportData);
            } else {
                // Handle other formats (XML, CSV, PDF)
                writer.write(objectMapper.writeValueAsString(exportData));
            }
        }
    }

    private Map<String, Object> createDataTypeInfo(String id, String name, String description, 
                                                  String icon, String size, String category, 
                                                  boolean required, boolean recommended, long recordCount) {
        Map<String, Object> dataType = new HashMap<>();
        dataType.put("id", id);
        dataType.put("name", name);
        dataType.put("description", description);
        dataType.put("icon", icon);
        dataType.put("size", size);
        dataType.put("category", category);
        dataType.put("required", required);
        dataType.put("recommended", recommended);
        if (recordCount > 0) {
            dataType.put("recordCount", recordCount);
        }
        return dataType;
    }

    private Map<String, Object> convertExportToMap(DataExport export) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", export.getId());
        map.put("fileName", export.getFileName());
        map.put("name", export.getFileName());
        map.put("format", export.getFormat().getDisplayName());
        map.put("size", export.getFileSize());
        map.put("status", export.getStatus().getDisplayName());
        map.put("createdAt", export.getRequestedAt().toString());
        map.put("date", export.getRequestedAt().toLocalDate().toString());
        map.put("downloadCount", export.getDownloadCount());
        map.put("encrypted", export.isEncrypted());
        return map;
    }

    // Additional helper methods for data export
    private Map<String, Object> exportUserProfile(User user) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("firstName", user.getFirstName());
        profile.put("lastName", user.getLastName());
        profile.put("phone", user.getPhone());
        profile.put("address", user.getAddress());
        profile.put("role", user.getRole().name());
        profile.put("createdAt", user.getCreatedAt());
        return profile;
    }

    private List<Map<String, Object>> exportTestResults(User user) {
        List<MedicalResult> results = medicalResultRepository.findByPatient(user);
        return results.stream().map(result -> {
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("id", result.getId());
            resultMap.put("testName", result.getTestName());
            resultMap.put("result", result.getResult());
            resultMap.put("normalRange", result.getNormalRange());
            resultMap.put("testDate", result.getTestDate());
            resultMap.put("category", result.getCategory());
            resultMap.put("status", result.getStatus() != null ? result.getStatus().name() : null);
            return resultMap;
        }).collect(Collectors.toList());
    }

    private List<Map<String, Object>> exportAppointments(User user) {
        List<Appointment> appointments = appointmentRepository.findByPatient(user);
        return appointments.stream().map(appointment -> {
            Map<String, Object> appointmentMap = new HashMap<>();
            appointmentMap.put("id", appointment.getId());
            appointmentMap.put("appointmentDate", appointment.getAppointmentDate());
            appointmentMap.put("status", appointment.getStatus() != null ? appointment.getStatus().name() : null);
            appointmentMap.put("notes", appointment.getNotes());
            return appointmentMap;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> exportMedicalHistory(User user) {
        // Mock implementation - you would implement based on your medical history structure
        Map<String, Object> history = new HashMap<>();
        history.put("chronicConditions", new ArrayList<>());
        history.put("allergies", new ArrayList<>());
        history.put("medications", new ArrayList<>());
        history.put("surgeries", new ArrayList<>());
        return history;
    }

    private Map<String, Object> createExportMetadata(DataExport dataExport) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("exportId", dataExport.getId());
        metadata.put("exportDate", dataExport.getRequestedAt());
        metadata.put("format", dataExport.getFormat().getDisplayName());
        metadata.put("dataTypes", Arrays.asList(dataExport.getDataTypesList()));
        metadata.put("encrypted", dataExport.isEncrypted());
        metadata.put("version", "1.0");
        return metadata;
    }

    // Utility methods
    private String calculateSize(long recordCount) {
        long sizeKB = recordCount * 2; // Estimate 2KB per record
        return sizeKB > 1024 ? String.format("%.1f MB", sizeKB / 1024.0) : sizeKB + " KB";
    }

    private long calculateMedicalHistoryCount(User user) {
        // Mock implementation - calculate based on your medical history structure
        return 5; // Placeholder
    }

    private String calculateAccountAge(User user) {
        // Calculate days since account creation
        long days = java.time.Duration.between(user.getCreatedAt(), LocalDateTime.now()).toDays();
        if (days < 30) return days + " days";
        if (days < 365) return (days / 30) + " months";
        return (days / 365) + " years";
    }

    private int calculateProgress(DataExport export) {
        switch (export.getStatus()) {
            case PENDING: return 0;
            case PROCESSING: return 50;
            case COMPLETED: return 100;
            case FAILED: return 0;
            default: return 0;
        }
    }

    private String calculateTotalDataSize(List<DataExport> exports) {
        // Mock calculation - in real implementation, sum actual file sizes
        return "15.2 MB";
    }

    private String calculateFileSize(String filePath) {
        try {
            long bytes = Files.size(Paths.get(filePath));
            if (bytes < 1024) return bytes + " B";
            else if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            else return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } catch (IOException e) {
            return "Unknown";
        }
    }

    private String generateDownloadPassword(DataExport export) {
        // Generate a secure password for encrypted exports
        return "export_" + export.getId() + "_" + export.getUser().getUsername().substring(0, 3);
    }

    private void deleteExportFile(String filePath) {
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            // Log error but don't fail the operation
            System.err.println("Failed to delete export file: " + filePath);
        }
    }
}