package com.interview.policyimport.dto;

import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.model.ImportStatus;

import java.time.LocalDateTime;

public record ImportResponse(
        Long id,
        String partnerCode,
        String fileName,
        ImportStatus status,
        int totalRows,
        int successCount,
        int failedCount,
        int duplicateCount,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {

    public static ImportResponse from(ImportFile importFile) {
        return new ImportResponse(
                importFile.getId(),
                importFile.getPartnerCode(),
                importFile.getFileName(),
                importFile.getStatus(),
                importFile.getTotalRows(),
                importFile.getSuccessCount(),
                importFile.getFailedCount(),
                importFile.getDuplicateCount(),
                importFile.getCreatedAt(),
                importFile.getCompletedAt()
        );
    }
}