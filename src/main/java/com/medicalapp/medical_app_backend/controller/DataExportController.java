// DataExportController.java
package com.medicalapp.medical_app_backend.controller;

import com.medicalapp.medical_app_backend.service.DataExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/export") // Fixed: Match frontend expectation
@CrossOrigin(origins = "*")
public class DataExportController {

    @Autowired
    private DataExportService dataExportService;

    /**
     * Get available data types for export
     * GET /api/export/available-data
     */
    @GetMapping("/available-data") // Fixed: Match frontend expectation
    public ResponseEntity<Map<String, Object>> getAvailableDataTypes(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = dataExportService.getAvailableDataTypes(userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching data types: " + e.getMessage()));
        }
    }

    /**
     * Process export request
     * POST /api/export/create
     */
    @PostMapping("/create") // Fixed: Match frontend expectation
    public ResponseEntity<Map<String, Object>> createExport(
            @RequestBody Map<String, Object> exportRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = dataExportService.createExport(exportRequest, userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Export processing failed: " + e.getMessage()));
        }
    }

    /**
     * Get export history
     * GET /api/export/history
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getExportHistory(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = dataExportService.getExportHistory(userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching export history: " + e.getMessage()));
        }
    }

    /**
     * Download export file
     * GET /api/export/download/{id}
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Map<String, Object>> downloadExport(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = dataExportService.downloadExport(id, userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Download failed: " + e.getMessage()));
        }
    }

    /**
     * Get export formats available
     * GET /api/export/formats
     */
    @GetMapping("/formats")
    public ResponseEntity<Map<String, Object>> getExportFormats(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> formats = Map.of(
                "success", true,
                "formats", Map.of(
                    "JSON", Map.of("name", "JSON Format", "description", "Machine readable, developer friendly", 
                                  "icon", "code-slash", "extension", ".json", "compatibility", "Universal"),
                    "XML", Map.of("name", "XML Format", "description", "Structured format, healthcare standard",
                                 "icon", "code", "extension", ".xml", "compatibility", "Healthcare systems"),
                    "CSV", Map.of("name", "CSV Format", "description", "Spreadsheet compatible, limited structure",
                                 "icon", "grid", "extension", ".csv", "compatibility", "Excel, Sheets"),
                    "FHIR", Map.of("name", "FHIR Standard", "description", "Healthcare interoperability standard",
                                  "icon", "medical", "extension", ".json", "compatibility", "Medical systems"),
                    "PDF", Map.of("name", "PDF Report", "description", "Human readable, printable format",
                                 "icon", "document-text", "extension", ".pdf", "compatibility", "Universal viewing")
                )
            );
            return ResponseEntity.ok(formats);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching formats: " + e.getMessage()));
        }
    }

    /**
     * Cancel export request
     * DELETE /api/export/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> cancelExport(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = dataExportService.cancelExport(id, userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error canceling export: " + e.getMessage()));
        }
    }

    /**
     * Get export statistics
     * GET /api/export/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getExportStats(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = dataExportService.getExportStats(userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching stats: " + e.getMessage()));
        }
    }

    /**
     * Check export status
     * GET /api/export/{id}/status
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getExportStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            Map<String, Object> response = dataExportService.getExportStatus(id, userDetails);
            if ((Boolean) response.get("success")) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Error fetching export status: " + e.getMessage()));
        }
    }

    /**
     * Cleanup expired exports (admin endpoint)
     * POST /api/export/cleanup
     */
    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupExpiredExports(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            // In a real implementation, you'd check if user has admin role
            Map<String, Object> response = dataExportService.cleanupExpiredExports();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Cleanup failed: " + e.getMessage()));
        }
    }
}