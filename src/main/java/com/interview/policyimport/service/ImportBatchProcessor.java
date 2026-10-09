package com.interview.policyimport.service;

import com.interview.policyimport.entity.ImportRow;
import com.interview.policyimport.model.CanonicalEnrollment;
import com.interview.policyimport.model.ImportRowStatus;
import com.interview.policyimport.repository.ImportRowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportBatchProcessor {

    private final ImportRowRepository importRowRepository;
    private final PolicyService policyService;

    @Transactional
    public void processBatch(
            Long fileId,
            List<Long> rowIds
    ) {
        if (rowIds == null || rowIds.isEmpty()) {
            log.debug(
                    "Skipping empty batch: fileId={}",
                    fileId
            );
            return;
        }

        long start = System.nanoTime();

        log.debug(
                "Processing batch: fileId={}, batchSize={}",
                fileId, rowIds.size()
        );

        // Lock rows so recovery or another worker cannot modify
        // their ownership while this transaction is processing.
        List<ImportRow> rows =
                importRowRepository.findAllByIdForUpdate(rowIds);

        if (rows.size() != rowIds.size()) {
            throw new IllegalStateException(
                    "Some claimed rows were not found for fileId=" + fileId
            );
        }

        int successCount = 0;
        int duplicateCount = 0;

        for (ImportRow row : rows) {

            // Validate claim ownership before processing.
            validateOwnership(row, fileId);

            CanonicalEnrollment enrollment = new CanonicalEnrollment(
                    row.getImei(),
                    row.getPlanCode(),
                    row.getEffectiveDate(),
                    row.getExpiryDate(),
                    row.getPremium(),
                    row.getCurrency()
            );

            // Uses PostgreSQL INSERT ... ON CONFLICT DO NOTHING.
            boolean created = policyService.createPolicy(
                    row.getFile().getPartnerCode(),
                    enrollment
            );

            if (created) {
                row.markSuccess();
                successCount++;
            } else {
                row.markDuplicate(
                        "Policy already exists for partner and IMEI"
                );
                duplicateCount++;
            }
        }

        importRowRepository.saveAll(rows);

        long durationMs =
                (System.nanoTime() - start) / 1_000_000;

        log.info(
                "Batch processed: fileId={}, total={}, success={}, duplicate={}, durationMs={}",
                fileId,
                rows.size(),
                successCount,
                duplicateCount,
                durationMs
        );
    }

    private void validateOwnership(
            ImportRow row,
            Long fileId
    ) {
        if (!row.getFile().getId().equals(fileId)) {
            throw new IllegalStateException(
                    "Row belongs to a different import file: rowId="
                            + row.getId()
            );
        }

        if (row.getStatus() != ImportRowStatus.PROCESSING) {

            log.warn(
                    "Invalid or expired row claim: fileId={}, rowId={}, status={}",
                    fileId,
                    row.getId(),
                    row.getStatus()
            );

            throw new IllegalStateException(
                    "Row claim is no longer valid: rowId=" + row.getId()
            );
        }
    }
}