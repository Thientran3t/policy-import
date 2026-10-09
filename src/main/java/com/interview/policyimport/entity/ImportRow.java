package com.interview.policyimport.entity;

import com.interview.policyimport.model.ImportRowStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "import_row",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_import_row_file_row",
                        columnNames = {"file_id", "row_number"}
                )
        }
)
public class ImportRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private ImportFile file;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(length = 50)
    private String imei;

    @Column(name = "plan_code", length = 100)
    private String planCode;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(precision = 19, scale = 4)
    private BigDecimal premium;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportRowStatus status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    protected ImportRow() {
    }

    public ImportRow(
            ImportFile file,
            int rowNumber,
            String imei,
            String planCode,
            LocalDate effectiveDate,
            LocalDate expiryDate,
            BigDecimal premium,
            String currency
    ) {
        this.file = file;
        this.rowNumber = rowNumber;
        this.imei = imei;
        this.planCode = planCode;
        this.effectiveDate = effectiveDate;
        this.expiryDate = expiryDate;
        this.premium = premium;
        this.currency = currency;
        this.status = ImportRowStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }


    public void markSuccess() {
        this.status = ImportRowStatus.SUCCESS;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed(
            String errorCode,
            String errorMessage
    ) {
        this.status = ImportRowStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.processedAt = LocalDateTime.now();
    }

    public void markDuplicate(String message) {
        this.status = ImportRowStatus.DUPLICATE;
        this.errorCode = "DUPLICATE_POLICY";
        this.errorMessage = message;
        this.processedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public ImportFile getFile() {
        return file;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getImei() {
        return imei;
    }

    public String getPlanCode() {
        return planCode;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public BigDecimal getPremium() {
        return premium;
    }

    public String getCurrency() {
        return currency;
    }

    public ImportRowStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }
}