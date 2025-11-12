package com.medicalapp.medical_app_backend.service;

import com.medicalapp.medical_app_backend.dto.*;
import com.medicalapp.medical_app_backend.entity.*;
import com.medicalapp.medical_app_backend.repository.*;

import com.medicalapp.medical_app_backend.entity.TestResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private AppointmentRepository appointmentRepository;
    
    @Autowired
    private ResultRepository resultRepository;
    
    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MedicalResultRepository medicalResultRepository;

    @Autowired
    private AutoNotificationService autoNotificationService;

    /**
     * Get dashboard statistics for admin view
     */
    public Map<String, Object> getDashboardStats(UserDetails userDetails) {
        try {
            logger.info("Loading dashboard stats for admin: {}", userDetails.getUsername());
            
            Map<String, Object> stats = new HashMap<>();
            
            // Get total patients count
            long totalPatients = userRepository.count();
            
            // Get today's appointments
            LocalDate today = LocalDate.now();
            long todayAppointments = appointmentRepository.countByScheduledDate(today);
            
            // Get pending tests (scheduled status)
            long pendingTests = appointmentRepository.countByStatus(Appointment.Status.SCHEDULED);
            
            // Get completed reports this month
            LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
            long completedReports = resultRepository.countByCreatedAtAfter(monthStart);
            
            // Calculate growth percentages (mock data for demo)
            stats.put("totalPatients", totalPatients > 0 ? totalPatients : 1247);
            stats.put("todayAppointments", todayAppointments > 0 ? todayAppointments : 23);
            stats.put("pendingTests", pendingTests > 0 ? pendingTests : 8);
            stats.put("completedReports", completedReports > 0 ? completedReports : 45);
            
            // Additional metrics
            stats.put("patientsGrowth", "+12%");
            stats.put("appointmentsToday", "5 completed, 18 pending");
            stats.put("urgentTests", 2);
            stats.put("reportsGrowth", "+18%");
            
            logger.info("Dashboard stats loaded: {} patients, {} appointments today", 
                       stats.get("totalPatients"), stats.get("todayAppointments"));
            
            return stats;
            
        } catch (Exception e) {
            logger.error("Error loading dashboard stats: {}", e.getMessage());
            throw new RuntimeException("Error loading dashboard statistics", e);
        }
    }

    /**
     * Get all patients with pagination and search
     */
    public Map<String, Object> getAllPatients(UserDetails userDetails, int page, int size, String search) {
        try {
            logger.info("Loading patients for admin - Page: {}, Size: {}, Search: '{}'", page, size, search);
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<User> patientsPage;
            
            if (search != null && !search.trim().isEmpty()) {
                // Search by name or email
                patientsPage = userRepository.findBySearchTerm(search.trim(), pageable);
            } else {
                patientsPage = userRepository.findAll(pageable);
            }
            
            List<AdminPatientDto> patientDtos = patientsPage.getContent().stream()
                .map(this::convertToAdminPatientDto)
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("patients", patientDtos);
            result.put("totalCount", patientsPage.getTotalElements());
            result.put("totalPages", patientsPage.getTotalPages());
            
            logger.info("Loaded {} patients out of {} total", patientDtos.size(), patientsPage.getTotalElements());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error loading patients: {}", e.getMessage());
            throw new RuntimeException("Error loading patients data", e);
        }
    }

    /**
     * Get detailed patient information including medical history
     */
    public Map<String, Object> getPatientDetails(Long patientId, UserDetails userDetails) {
        try {
            logger.info("Loading patient details for ID: {}", patientId);
            
            User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + patientId));
            
            // Get patient appointments
            List<Appointment> appointments = appointmentRepository.findByUserIdOrderByScheduledDateDesc(patientId);
            
            // Get patient test results
            List<Result> testResults = resultRepository.findByUserIdOrderByCreatedAtDesc(patientId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("patient", convertToAdminPatientDto(patient));
            result.put("medicalHistory", getMedicalHistoryForPatient(patient));
            result.put("appointments", appointments.stream().map((Appointment a) -> convertToAdminAppointmentDto(a)).collect(Collectors.toList()));
            result.put("testResults", testResults.stream().map((Result r) -> convertToAdminTestResultDto(r)).collect(Collectors.toList()));
            
            logger.info("Patient details loaded for: {} {}", patient.getFirstName(), patient.getLastName());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error loading patient details: {}", e.getMessage());
            throw new RuntimeException("Error loading patient details", e);
        }
    }

    /**
     * Get all appointments with filtering
     */
    public Map<String, Object> getAllAppointments(UserDetails userDetails, String date, String status, int page, int size) {
        try {
            logger.info("Loading appointments - Date: {}, Status: {}, Page: {}", date, status, page);
            Pageable pageable = PageRequest.of(page, size, Sort.by("appointmentDate").descending());
            Page<Appointment> appointmentsPage;
            if (date != null && status != null && !status.equals("all")) {
                LocalDate filterDate = LocalDate.parse(date);
               Appointment.Status statusEnum = Appointment.Status.valueOf(status.toUpperCase());
appointmentsPage = appointmentRepository.findByScheduledDateAndStatus(filterDate, statusEnum, pageable);
            } else if (date != null) {
                LocalDate filterDate = LocalDate.parse(date);
                appointmentsPage = appointmentRepository.findByScheduledDate(filterDate, pageable);
            } else if (status != null && !status.equals("all")) {
                Appointment.Status statusEnum = Appointment.Status.valueOf(status.toUpperCase());
                appointmentsPage = appointmentRepository.findByStatus(statusEnum, pageable);
            } else {
                appointmentsPage = appointmentRepository.findAll(pageable);
            }
            
            List<AdminAppointmentDto> appointmentDtos = appointmentsPage.getContent().stream()
                .map(this::convertToAdminAppointmentDto)
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("appointments", appointmentDtos);
            result.put("totalCount", appointmentsPage.getTotalElements());
            result.put("totalPages", appointmentsPage.getTotalPages());
            
            logger.info("Loaded {} appointments", appointmentDtos.size());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error loading appointments: {}", e.getMessage());
            throw new RuntimeException("Error loading appointments", e);
        }
    }

    /**
     * Book a new test appointment
     */
    public Map<String, Object> bookTest(BookTestRequest request, UserDetails userDetails) {
        try {
            logger.info("Booking test for patient ID: {}, Test: {}", request.getPatientId(), request.getTestType());
            
            // Validate patient exists
            User patient = userRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + request.getPatientId()));
            
            // Check for scheduling conflicts
            boolean hasConflict = appointmentRepository.existsByScheduledDateAndScheduledTime(
                request.getScheduledDate(), request.getScheduledTime());
            
            if (hasConflict) {
                throw new RuntimeException("Time slot already booked. Please choose a different time.");
            }
            
            // Create new appointment
            Appointment appointment = new Appointment();
            appointment.setUser(patient);
            appointment.setTestType(request.getTestType());
            appointment.setScheduledDate(request.getScheduledDate());
            appointment.setScheduledTime(request.getScheduledTime());
            appointment.setStatus("scheduled");
            appointment.setPriority(request.getPriority());
            appointment.setNotes(request.getNotes());
            appointment.setCreatedAt(LocalDateTime.now());
            appointment.setUpdatedAt(LocalDateTime.now());
            
            Appointment savedAppointment = appointmentRepository.save(appointment);
            
            // Send notification to patient
            try {
                String message = String.format("Your %s has been scheduled for %s at %s", 
                    request.getTestType(), 
                    request.getScheduledDate(), 
                    request.getScheduledTime());
                notificationService.sendEmail(patient.getEmail(), "Test Appointment Scheduled", message);
            } catch (Exception notificationError) {
                logger.warn("Failed to send appointment notification: {}", notificationError.getMessage());
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("appointmentId", savedAppointment.getId());
            result.put("appointment", convertToAdminAppointmentDto(savedAppointment));
            result.put("message", "Test appointment booked successfully");
            
            logger.info("Test appointment booked successfully. Appointment ID: {}", savedAppointment.getId());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error booking test: {}", e.getMessage());
            throw new RuntimeException("Error booking test: " + e.getMessage(), e);
        }
    }

    /**
     * Get all test results with filtering
     */
    public Map<String, Object> getAllTestResults(UserDetails userDetails, String status, String testType, int page, int size) {
        try {
            logger.info("Loading test results - Status: {}, Type: {}, Page: {}", status, testType, page);
            
            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
            Page<Result> resultsPage;
            
            if (status != null && !status.equals("all") && testType != null && !testType.equals("all")) {
                resultsPage = resultRepository.findByStatusAndTestType(status, testType, pageable);
            } else if (status != null && !status.equals("all")) {
                resultsPage = resultRepository.findByStatus(status, pageable);
            } else if (testType != null && !testType.equals("all")) {
                resultsPage = resultRepository.findByTestType(testType, pageable);
            } else {
                resultsPage = resultRepository.findAll(pageable);
            }
            
            List<AdminTestResultDto> resultDtos = resultsPage.getContent().stream()
                .map(this::convertToAdminTestResultDto)
                .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("results", resultDtos);
            result.put("totalCount", resultsPage.getTotalElements());
            result.put("totalPages", resultsPage.getTotalPages());
            
            logger.info("Loaded {} test results", resultDtos.size());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error loading test results: {}", e.getMessage());
            throw new RuntimeException("Error loading test results", e);
        }
    }

    /**
     * Add a new test result
     */
    public Map<String, Object> addTestResult(AddTestResultRequest request, UserDetails userDetails) {
        try {
            logger.info("Adding test result for patient ID: {}, Test: {}", request.getPatientId(), request.getTestType());
            
            // Validate patient exists
            User patient = userRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + request.getPatientId()));
            
            // Create new test result
            Result testResult = new Result();
            testResult.setUser(patient);
            testResult.setTestType(request.getTestType());
            testResult.setResult(request.getResult());
            testResult.setStatus(request.getStatus());
            testResult.setNotes(request.getNotes());
            testResult.setDoctorName(request.getDoctorName());
            testResult.setTestDate(request.getTestDate());
            testResult.setCreatedAt(LocalDateTime.now());
            testResult.setUpdatedAt(LocalDateTime.now());
            
            Result savedResult = resultRepository.save(testResult);
            
            // Send notification to patient if result is abnormal
            try {
                if ("abnormal".equals(request.getStatus())) {
                    String message = String.format("Your %s results are ready. Please contact your doctor for follow-up.", 
                        request.getTestType());
                    notificationService.sendEmail(patient.getEmail(), "Test Results Available", message);
                }
            } catch (Exception notificationError) {
                logger.warn("Failed to send result notification: {}", notificationError.getMessage());
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("resultId", savedResult.getId());
            result.put("result", convertToAdminTestResultDto(savedResult));
            result.put("message", "Test result added successfully");
            
            logger.info("Test result added successfully. Result ID: {}", savedResult.getId());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error adding test result: {}", e.getMessage());
            throw new RuntimeException("Error adding test result: " + e.getMessage(), e);
        }
    }

    /**
 * Upload test result with file attachment
 */
public Map<String, Object> uploadResultWithFile(
        Long patientId, 
        String testType, 
        String result, 
        String notes,
        String category, 
        String status, 
        String doctorName, 
        String testDate,
        Long appointmentId, 
        boolean markCompleted, 
        MultipartFile file,
        UserDetails adminDetails) {
    
    Map<String, Object> response = new HashMap<>();
    
    try {
        logger.info("=== FILE UPLOAD DEBUG ===");
        logger.info("File parameter received: {}", file != null ? file.getOriginalFilename() : "NULL");
        
        // Get patient
        User patient = userRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + patientId));
        
        logger.info("Found patient: {} {}", patient.getFirstName(), patient.getLastName());
        
        // Create MedicalResult (not Result)
        MedicalResult medicalResult = new MedicalResult();
        medicalResult.setPatient(patient);
        medicalResult.setTestName(testType);
        medicalResult.setTestType(testType);
        medicalResult.setResult(result);
        medicalResult.setNotes(notes);
        medicalResult.setCategory(category != null ? category : "General");
        medicalResult.setStatus(parseStatus(status));
        medicalResult.setTestDate(parseTestDate(testDate));
        medicalResult.setVisible(true);
        medicalResult.setDownloadable(true);
        medicalResult.setCreatedAt(LocalDateTime.now());
        medicalResult.setUpdatedAt(LocalDateTime.now());
        
        // Handle file if present
        // Handle file if present
// Handle file if present
if (file != null && !file.isEmpty()) {
    try {
        logger.info("Processing file: {}, size: {}", file.getOriginalFilename(), file.getSize());
        
        // Validate file size (10MB limit)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new RuntimeException("File size exceeds 10MB limit");
        }
        
        // Create upload directory
        String uploadDir = "uploads/results";
        Path uploadPath = Paths.get(uploadDir);
        
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            logger.info("Created upload directory: {}", uploadPath.toAbsolutePath());
        }
        
        // Generate unique filename
        String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        
        // Save file to disk
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        
        logger.info("File saved to disk: {}", filePath.toAbsolutePath());
        
        // Save metadata to database
        medicalResult.setFileName(file.getOriginalFilename());
        medicalResult.setFilePath(fileName); // Store relative path
        medicalResult.setFileType(file.getContentType());
        medicalResult.setMimeType(file.getContentType());
        medicalResult.setFileSize(file.getSize());
        
    } catch (IOException e) {
        logger.error("Failed to save file: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to save file: " + e.getMessage(), e);
    }
} else {
    logger.info("No file attached to this result");
}
        // Save result
        MedicalResult savedResult = medicalResultRepository.save(medicalResult);
        
        logger.info("Result saved with ID: {} for patient: {}", savedResult.getId(), patientId);
        
        response.put("success", true);
        response.put("message", "Test result uploaded successfully");
        response.put("resultId", savedResult.getId());

        try {
    // Convert MedicalResult to TestResult for notification compatibility
    TestResult testResultForNotification = new TestResult();
    testResultForNotification.setId(savedResult.getId());
    testResultForNotification.setUser(patient);
    testResultForNotification.setTestType(testType);
    testResultForNotification.setTestName(testType);
    testResultForNotification.setResult(result);
    testResultForNotification.setStatus(status);
    
    // Send auto-notification
    autoNotificationService.onTestResultUploaded(testResultForNotification);
    logger.info("✅ Auto-notification triggered for result upload");
} catch (Exception e) {
    logger.error("❌ Failed to send result notification: {}", e.getMessage());
}

response.put("success", true);
response.put("message", "Test result uploaded successfully");
response.put("resultId", savedResult.getId());

        
    } catch (Exception e) {
        logger.error("Error uploading result with file", e);
        response.put("success", false);
        response.put("message", "Error uploading result: " + e.getMessage());
    }
    
    return response;
}

    /**
     * Update appointment status
     */
    public void updateAppointmentStatus(Long appointmentId, String status, UserDetails userDetails) {
        try {
            logger.info("Updating appointment {} to status: {}", appointmentId, status);
            
            Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found with ID: " + appointmentId));
            
            appointment.setStatus(status);
            appointment.setUpdatedAt(LocalDateTime.now());
            
            appointmentRepository.save(appointment);
            
            // Send notification to patient
            try {
                String message = String.format("Your appointment for %s on %s has been %s", 
                    appointment.getTestType(), 
                    appointment.getScheduledDate(), 
                    status);
                notificationService.sendEmail(appointment.getUser().getEmail(), "Appointment Status Update", message);
            } catch (Exception notificationError) {
                logger.warn("Failed to send status update notification: {}", notificationError.getMessage());
            }
            
            logger.info("Appointment status updated successfully");
            
        } catch (Exception e) {
            logger.error("Error updating appointment status: {}", e.getMessage());
            throw new RuntimeException("Error updating appointment status", e);
        }
    }

    /**
     * Schedule a new appointment
     */
    public Map<String, Object> scheduleAppointment(ScheduleAppointmentRequest request, UserDetails userDetails) {
        try {
            logger.info("Scheduling appointment for patient ID: {}, Type: {}", request.getPatientId(), request.getAppointmentType());
            
            // Validate patient exists
            User patient = userRepository.findById(request.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found with ID: " + request.getPatientId()));
            
            // Create new appointment
            Appointment appointment = new Appointment();
            appointment.setUser(patient);
            appointment.setAppointmentType(request.getAppointmentType());
            appointment.setScheduledDate(request.getScheduledDate());
            appointment.setScheduledTime(request.getScheduledTime());
            appointment.setStatus("scheduled");
            appointment.setNotes(request.getNotes());
            appointment.setDoctorName(request.getDoctorName());
            appointment.setDepartment(request.getDepartment());
            appointment.setCreatedAt(LocalDateTime.now());
            appointment.setUpdatedAt(LocalDateTime.now());
            
            Appointment savedAppointment = appointmentRepository.save(appointment);
            
            Map<String, Object> result = new HashMap<>();
            result.put("appointmentId", savedAppointment.getId());
            result.put("appointment", convertToAdminAppointmentDto(savedAppointment));
            
            logger.info("Appointment scheduled successfully. ID: {}", savedAppointment.getId());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error scheduling appointment: {}", e.getMessage());
            throw new RuntimeException("Error scheduling appointment: " + e.getMessage(), e);
        }
    }

    /**
     * Generate reports and analytics
     */
    public Map<String, Object> generateReports(UserDetails userDetails, String startDate, String endDate, String reportType) {
        try {
            logger.info("Generating reports - Type: {}, Date range: {} to {}", reportType, startDate, endDate);
            
            Map<String, Object> reports = new HashMap<>();
            
            LocalDateTime start = startDate != null ? LocalDate.parse(startDate).atStartOfDay() : 
                                 LocalDate.now().minusMonths(1).atStartOfDay();
            LocalDateTime end = endDate != null ? LocalDate.parse(endDate).atTime(23, 59, 59) : 
                               LocalDateTime.now();
            
            if (reportType == null || reportType.equals("all") || reportType.equals("appointments")) {
                // Appointment reports
                long totalAppointments = appointmentRepository.countByCreatedAtBetween(start, end);
                long completedAppointments = appointmentRepository.countByStatusAndCreatedAtBetween(Appointment.Status.COMPLETED, start, end);
                long cancelledAppointments = appointmentRepository.countByStatusAndCreatedAtBetween(Appointment.Status.CANCELLED, start, end);

                
                Map<String, Object> appointmentReport = new HashMap<>();
                appointmentReport.put("total", totalAppointments);
                appointmentReport.put("completed", completedAppointments);
                appointmentReport.put("cancelled", cancelledAppointments);
                appointmentReport.put("completionRate", totalAppointments > 0 ? (completedAppointments * 100.0 / totalAppointments) : 0);
                
                reports.put("appointments", appointmentReport);
            }
            
            if (reportType == null || reportType.equals("all") || reportType.equals("results")) {
                // Test results reports
                long totalResults = resultRepository.countByCreatedAtBetween(start, end);
                long normalResults = resultRepository.countByStatusAndCreatedAtBetween("normal", start, end);
                long abnormalResults = resultRepository.countByStatusAndCreatedAtBetween("abnormal", start, end);
                
                Map<String, Object> resultsReport = new HashMap<>();
                resultsReport.put("total", totalResults);
                resultsReport.put("normal", normalResults);
                resultsReport.put("abnormal", abnormalResults);
                resultsReport.put("abnormalRate", totalResults > 0 ? (abnormalResults * 100.0 / totalResults) : 0);
                
                reports.put("testResults", resultsReport);
            }
            
            if (reportType == null || reportType.equals("all") || reportType.equals("patients")) {
                // Patient reports
                long newPatients = userRepository.countByCreatedAtBetween(start, end);
                long totalPatients = userRepository.count();
                
                Map<String, Object> patientReport = new HashMap<>();
                patientReport.put("total", totalPatients);
                patientReport.put("newPatients", newPatients);
                patientReport.put("growthRate", totalPatients > 0 ? (newPatients * 100.0 / totalPatients) : 0);
                
                reports.put("patients", patientReport);
            }
            
            logger.info("Reports generated successfully");
            
            return reports;
            
        } catch (Exception e) {
            logger.error("Error generating reports: {}", e.getMessage());
            throw new RuntimeException("Error generating reports", e);
        }
    }

    /**
     * Global search across patients, appointments, and results
     */
    public Map<String, Object> globalSearch(String query, String type, UserDetails userDetails) {
        try {
            logger.info("Performing global search - Query: '{}', Type: {}", query, type);
            
            Map<String, Object> searchResults = new HashMap<>();
            
            if (type.equals("all") || type.equals("patients")) {
               List<User> patients = userRepository.findBySearchTerm(query);

                searchResults.put("patients", patients.stream().map(this::convertToAdminPatientDto).collect(Collectors.toList()));
            }
            
            if (type.equals("all") || type.equals("appointments")) {
                List<Appointment> appointments = appointmentRepository.findByReasonContainingOrNotesContaining(query, query);
                searchResults.put("appointments", appointments.stream().map(this::convertToAdminAppointmentDto).collect(Collectors.toList()));
            }
            
            if (type.equals("all") || type.equals("results")) {
                List<Result> results = resultRepository.findByTestTypeContainingIgnoreCaseOrResultContainingIgnoreCaseOrNotesContainingIgnoreCase(
                    query, query, query);
                searchResults.put("results", results.stream().map(this::convertToAdminTestResultDto).collect(Collectors.toList()));
            }
            
            logger.info("Global search completed");
            
            return searchResults;
            
        } catch (Exception e) {
            logger.error("Error performing global search: {}", e.getMessage());
            throw new RuntimeException("Error performing search", e);
        }
    }

    // Helper methods for DTO conversion

    private AdminPatientDto convertToAdminPatientDto(User user) {
        AdminPatientDto dto = new AdminPatientDto();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setDateOfBirth(user.getDob());
        dto.setAddress(user.getAddress());
        dto.setBloodType(user.getBloodGroup());
        dto.setGender(user.getGender());
        dto.setHeight(user.getHeight());
        dto.setWeight(user.getWeight());
        dto.setGenotype(user.getGenotype());
    
        dto.setMedicalHistory(user.getMedicalHistory());
        dto.setCurrentMedications(user.getCurrentMedications());
        dto.setAllergies(user.getAllergies());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setStatus("active"); // Default status
        
        
        // Set additional computed fields
        dto.setTotalAppointments((int) appointmentRepository.countByUserId(user.getId()));
        dto.setTotalTestResults((int) resultRepository.countByUserId(user.getId()));
        
        return dto;
    }

    private AdminAppointmentDto convertToAdminAppointmentDto(Appointment appointment) {
        AdminAppointmentDto dto = new AdminAppointmentDto();
        dto.setId(appointment.getId());
        dto.setPatientId(appointment.getUser().getId());
        dto.setPatientName(appointment.getUser().getFirstName() + " " + appointment.getUser().getLastName());
        dto.setPatientEmail(appointment.getUser().getEmail());
        dto.setTestType(appointment.getTestType());
        dto.setAppointmentType(appointment.getAppointmentType());
        dto.setScheduledDate(appointment.getScheduledDate());
        dto.setScheduledTime(appointment.getScheduledTime());
        dto.setStatus(appointment.getStatus().name().toLowerCase());
        dto.setPriority(appointment.getPriority());
        dto.setNotes(appointment.getNotes());
        dto.setDoctorName(appointment.getDoctorName());
        dto.setDepartment(appointment.getDepartment());
        dto.setCreatedAt(appointment.getCreatedAt());
        dto.setUpdatedAt(appointment.getUpdatedAt());
        return dto;
    }

    private AdminTestResultDto convertToAdminTestResultDto(Result result) {
        AdminTestResultDto dto = new AdminTestResultDto();
        dto.setId(result.getId());
        dto.setPatientId(result.getUser().getId());
        dto.setPatientName(result.getUser().getFirstName() + " " + result.getUser().getLastName());
        dto.setPatientEmail(result.getUser().getEmail());
        dto.setTestType(result.getTestType());
        dto.setResult(result.getResult());
        dto.setStatus(result.getStatus());
        dto.setNotes(result.getNotes());
        dto.setDoctorName(result.getDoctorName());
        dto.setTestDate(result.getTestDate());
        dto.setCreatedAt(result.getCreatedAt());
        dto.setUpdatedAt(result.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> getMedicalHistoryForPatient(User patient) {
        Map<String, Object> medicalHistory = new HashMap<>();
        
        // Basic medical information
        medicalHistory.put("bloodType", patient.getBloodGroup());
        medicalHistory.put("allergies", patient.getAllergies());
        medicalHistory.put("currentMedications", patient.getCurrentMedications());
        medicalHistory.put("medicalHistory", patient.getMedicalHistory());
        
        // Get recent vital signs and measurements (if available)
        // This would come from additional entities in a full system
        medicalHistory.put("height", patient.getHeight());
        medicalHistory.put("weight", patient.getWeight());
        medicalHistory.put("lastCheckup", patient.getLastVisit());
        
        // Emergency contact information
        medicalHistory.put("emergencyContact", patient.getEmergencyContact());
        medicalHistory.put("emergencyPhone", patient.getEmergencyPhone());
        
        return medicalHistory;
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
}