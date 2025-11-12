// DataExport.java - Entity
package com.medicalapp.medical_app_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "data_exports")
public class DataExport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "data_types", nullable = false)
    private String dataTypes; // Comma-separated list

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    private ExportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExportStatus status;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "file_size")
    private String fileSize;

    @Column(name = "is_encrypted")
    private boolean encrypted = true;

    @Column(name = "download_count")
    private int downloadCount = 0;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "error_message")
    private String errorMessage;

    // Enums
    public enum ExportFormat {
        JSON("JSON", "application/json"),
        XML("XML", "application/xml"),
        CSV("CSV", "text/csv"),
        FHIR("FHIR", "application/fhir+json"),
        PDF("PDF", "application/pdf");

        private final String displayName;
        private final String mimeType;

        ExportFormat(String displayName, String mimeType) {
            this.displayName = displayName;
            this.mimeType = mimeType;
        }

        public String getDisplayName() { return displayName; }
        public String getMimeType() { return mimeType; }
    }

    public enum ExportStatus {
        PENDING("Pending"),
        PROCESSING("Processing"),
        COMPLETED("Completed"),
        FAILED("Failed"),
        EXPIRED("Expired");

        private final String displayName;

        ExportStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    // Constructors
    public DataExport() {}

    public DataExport(User user, String dataTypes, ExportFormat format) {
        this.user = user;
        this.dataTypes = dataTypes;
        this.format = format;
        this.status = ExportStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusDays(7); // Expire after 7 days
    }

    // Lifecycle callbacks
    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusDays(7);
        }
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getDataTypes() { return dataTypes; }
    public void setDataTypes(String dataTypes) { this.dataTypes = dataTypes; }

    public ExportFormat getFormat() { return format; }
    public void setFormat(ExportFormat format) { this.format = format; }

    public ExportStatus getStatus() { return status; }
    public void setStatus(ExportStatus status) { this.status = status; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getFileSize() { return fileSize; }
    public void setFileSize(String fileSize) { this.fileSize = fileSize; }

    public boolean isEncrypted() { return encrypted; }
    public void setEncrypted(boolean encrypted) { this.encrypted = encrypted; }

    public int getDownloadCount() { return downloadCount; }
    public void setDownloadCount(int downloadCount) { this.downloadCount = downloadCount; }

    public LocalDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(LocalDateTime requestedAt) { this.requestedAt = requestedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getLastDownloadedAt() { return lastDownloadedAt; }
    public void setLastDownloadedAt(LocalDateTime lastDownloadedAt) { this.lastDownloadedAt = lastDownloadedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    // Utility methods
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isReadyForDownload() {
        return status == ExportStatus.COMPLETED && !isExpired();
    }

    public String[] getDataTypesList() {
        return dataTypes != null ? dataTypes.split(",") : new String[0];
    }
}