package com.interview.policyimport.entity;

import com.interview.policyimport.model.ImportStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "import_file",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_import_file_partner_hash",
                        columnNames = {"partner_code", "file_hash"}
                )
        }
)
public class ImportFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_code", nullable = false, length = 50)
    private String partnerCode;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    protected ImportFile() {
    }

    public ImportFile(
            String partnerCode,
            String fileName,
            String fileHash
    ) {
        this.partnerCode = partnerCode;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.status = ImportStatus.RECEIVED;
        this.createdAt = LocalDateTime.now();
    }

    public void markProcessing() {
        this.status = ImportStatus.PROCESSING;
    }

    public void complete(
            int totalRows,
            int successCount,
            int failedCount,
            int duplicateCount
    ) {
        this.totalRows = totalRows;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.duplicateCount = duplicateCount;
        this.status = ImportStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = ImportStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }
}