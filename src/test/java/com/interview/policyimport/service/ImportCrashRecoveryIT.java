
package com.interview.policyimport.service;

import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.entity.ImportRow;
import com.interview.policyimport.model.ImportRowStatus;
import com.interview.policyimport.repository.ImportFileRepository;
import com.interview.policyimport.repository.ImportRowRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest(properties = {
        "policy-import.recovery.enabled=false",
        "policy-import.worker.concurrency=2",
        "policy-import.worker.batch-size=2",
        "policy-import.worker.lease-seconds=300",
        "policy-import.worker.max-attempts=3"
})
@Testcontainers
class ImportCrashRecoveryIT {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    private ImportFileRepository importFileRepository;

    @Autowired
    private ImportRowRepository importRowRepository;

    @Autowired
    private ImportRowClaimService claimService;

    @Autowired
    private ImportBatchProcessor batchProcessor;

    @Autowired
    private ImportRecoveryService recoveryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @Test
    void shouldRecoverExpiredRowsAndContinueProcessing() {
        ImportFile file = createProcessingImport();

        String imei1 = uniqueImei();
        String imei2 = uniqueImei();
        String imei3 = uniqueImei();

        saveRows(file, imei1, imei2, imei3);

        log.info("Starting crash recovery test: fileId={}", file.getId());

        // Worker A claims and successfully processes two rows.
        List<Long> workerARows = claimService.claimNextBatch(
                file.getId(),
                "worker-A"
        );

        assertEquals(2, workerARows.size());

        batchProcessor.processBatch(
                file.getId(),
                "worker-A",
                workerARows
        );

        // Worker B claims the remaining row but crashes.
        List<Long> workerBRows = claimService.claimNextBatch(
                file.getId(),
                "worker-B"
        );

        assertEquals(1, workerBRows.size());

        // Simulate crash: Worker B never processes its claimed row.
        // Force the lease to expire.
        jdbcTemplate.update(
                """
                UPDATE import_row
                SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                WHERE id = ?
                """,
                workerBRows.get(0)
        );

        // Recovery resets the abandoned row to PENDING.
        recoveryService.recover();

        ImportRow recoveredRow = importRowRepository
                .findById(workerBRows.get(0))
                .orElseThrow();

        assertEquals(
                ImportRowStatus.PENDING,
                recoveredRow.getStatus()
        );

        // Worker C reclaims and processes the recovered row.
        List<Long> workerCRows = claimService.claimNextBatch(
                file.getId(),
                "worker-C"
        );

        assertEquals(workerBRows, workerCRows);

        batchProcessor.processBatch(
                file.getId(),
                "worker-C",
                workerCRows
        );

        assertEquals(
                3,
                countRows(file.getId(), "SUCCESS")
        );

        assertEquals(
                3,
                countPolicies(file.getId())
        );

        log.info(
                "Crash recovery test passed: fileId={}, successfulRows=3",
                file.getId()
        );
    }

    @Test
    void shouldNotRecoverRowsWithActiveLease() {
        ImportFile file = createProcessingImport();
        saveRows(file, uniqueImei());

        List<Long> claimed = claimService.claimNextBatch(
                file.getId(),
                "worker-A"
        );

        assertEquals(1, claimed.size());

        recoveryService.recover();

        ImportRow row = importRowRepository
                .findById(claimed.get(0))
                .orElseThrow();

        assertEquals(
                ImportRowStatus.PROCESSING,
                row.getStatus()
        );

        log.info(
                "Active lease test passed: rowId={}",
                row.getId()
        );
    }

    @Test
    void shouldFailRowsAfterMaximumAttempts() {
        ImportFile file = createProcessingImport();
        saveRows(file, uniqueImei());

        List<Long> claimed = claimService.claimNextBatch(
                file.getId(),
                "worker-A"
        );

        assertEquals(1, claimed.size());

        jdbcTemplate.update(
                """
                UPDATE import_row
                SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 minute',
                    attempt_count = 3
                WHERE id = ?
                """,
                claimed.get(0)
        );

        recoveryService.recover();

        ImportRow row = importRowRepository
                .findById(claimed.get(0))
                .orElseThrow();

        assertEquals(
                ImportRowStatus.FAILED,
                row.getStatus()
        );

        assertEquals(
                "MAX_RETRIES_EXCEEDED",
                row.getErrorCode()
        );

        log.info(
                "Maximum attempts test passed: rowId={}",
                row.getId()
        );
    }

    private ImportFile createProcessingImport() {
        ImportFile file = new ImportFile(
                "ACME",
                "recovery-test.csv",
                UUID.randomUUID().toString()
        );

        file = importFileRepository.saveAndFlush(file);

        file.markStaging();
        file = importFileRepository.saveAndFlush(file);

        file.markProcessing();
        return importFileRepository.saveAndFlush(file);
    }

    private void saveRows(
            ImportFile file,
            String... imeis
    ) {
        int rowNumber = 1;

        for (String imei : imeis) {
            ImportRow row = new ImportRow(
                    file,
                    rowNumber++,
                    imei,
                    "PLAN_A",
                    LocalDate.of(2026, 10, 1),
                    LocalDate.of(2027, 9, 30),
                    new BigDecimal("100.00"),
                    "USD"
            );

            importRowRepository.save(row);
        }

        importRowRepository.flush();
    }

    private String uniqueImei() {
        long value = Math.floorMod(
                UUID.randomUUID().getMostSignificantBits(),
                1_000_000_000_000L
        );

        return "35" + String.format("%013d", value);
    }

    private int countRows(
            Long fileId,
            String status
    ) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM import_row
                WHERE file_id = ? AND status = ?
                """,
                Integer.class,
                fileId,
                status
        );

        return count;
    }

    private int countPolicies(Long fileId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM policy
                WHERE partner_code = (
                    SELECT partner_code
                    FROM import_file
                    WHERE id = ?
                )
                AND imei IN (
                    SELECT imei
                    FROM import_row
                    WHERE file_id = ?
                )
                """,
                Integer.class,
                fileId,
                fileId
        );

        return count;
    }
}
