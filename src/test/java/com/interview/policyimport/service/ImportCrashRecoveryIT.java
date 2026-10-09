
package com.interview.policyimport.service;

import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.entity.ImportRow;
import com.interview.policyimport.model.ImportRowStatus;
import com.interview.policyimport.model.ImportStatus;
import com.interview.policyimport.repository.ImportFileRepository;
import com.interview.policyimport.repository.ImportRowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "policy-import.recovery.enabled=true",
        "policy-import.sftp.enabled=false",
        "policy-import.recovery-interval-ms=3600000",
        "policy-import.worker.max-attempts=3"
})
class ImportCrashRecoveryIT {

    @Autowired
    private ImportRecoveryService recoveryService;

    @Autowired
    private ImportFileRepository importFileRepository;

    @Autowired
    private ImportRowRepository importRowRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // Each test uses a unique import file.
        // No global table cleanup required.
    }

    @Test
    void shouldRecoverExpiredClaimAndCompleteImport() {

        ImportFile file = createProcessingImport();
        ImportRow row = createRow(file);

        // Simulate a worker crash after claiming the row.
        updateClaim(
                row.getId(),
                1
        );

        recoveryService.recover();

        ImportRow recovered = importRowRepository
                .findById(row.getId())
                .orElseThrow();

        ImportFile completed = importFileRepository
                .findById(file.getId())
                .orElseThrow();

        assertThat(recovered.getStatus())
                .isEqualTo(ImportRowStatus.SUCCESS);

        assertThat(completed.getStatus())
                .isEqualTo(ImportStatus.COMPLETED);
    }


    @Test
    void shouldFailClaimAfterMaxAttempts() {

        ImportFile file = createProcessingImport();
        ImportRow row = createRow(file);

        // Claim has expired and reached max attempts.
        updateClaim(
                row.getId(),
                3
        );

        recoveryService.recover();

        ImportRow failed = importRowRepository
                .findById(row.getId())
                .orElseThrow();

        assertThat(failed.getStatus())
                .isEqualTo(ImportRowStatus.FAILED);

        assertThat(failed.getErrorCode())
                .isEqualTo("MAX_RETRIES_EXCEEDED");

        ImportFile completed = importFileRepository
                .findById(file.getId())
                .orElseThrow();

        // COMPLETED means all rows have terminal statuses.
        // It does not mean every row succeeded.
        assertThat(completed.getStatus())
                .isEqualTo(ImportStatus.COMPLETED);

        assertThat(completed.getFailedCount())
                .isEqualTo(1);
    }

    private ImportFile createProcessingImport() {

        ImportFile file = new ImportFile(
                "ACME",
                "recovery-" + UUID.randomUUID() + ".csv",
                UUID.randomUUID().toString()
        );

        file.markStaging();
        file.markProcessing();

        return importFileRepository.saveAndFlush(file);
    }

    private ImportRow createRow(ImportFile file) {

        String imei = String.format(
                "35%013d",
                Math.abs(UUID.randomUUID().getMostSignificantBits())
                        % 1_000_000_000_000L
        );

        ImportRow row = new ImportRow(
                file,
                1,
                imei,
                "PLAN_A",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2027, 9, 30),
                new java.math.BigDecimal("100.00"),
                "USD"
        );

        return importRowRepository.saveAndFlush(row);
    }

    private void updateClaim(
            Long rowId,
            int attemptCount
    ) {

        jdbcTemplate.update(
                """
                UPDATE import_row
                SET status = 'PROCESSING',
                    attempt_count = ?
                WHERE id = ?
                """,
                attemptCount,
                rowId
        );
    }
}
