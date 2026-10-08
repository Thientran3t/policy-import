
package com.interview.policyimport.service;

import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.model.ImportStatus;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
class ImportServiceIT {

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
    private ImportService importService;

    @Autowired
    private ImportFileRepository importFileRepository;

    @Autowired
    private ImportRowRepository importRowRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @Test
    void shouldImportValidCsv() throws IOException {
        String imei = uniqueImei();

        Path file = createCsv(
                "valid.csv",
                csvRow(imei)
        );

        ImportFile result = importService.importFile(
                "ACME", "valid.csv", file
        );

        assertEquals(ImportStatus.COMPLETED, result.getStatus());
        assertEquals(1, countRows(result.getId(), "SUCCESS"));
        assertEquals(1, countPolicies("ACME", imei));

        log.info("Valid CSV integration test passed");
    }

    @Test
    void shouldContinueProcessingWhenSomeRowsAreInvalid()
            throws IOException {

        String validImei = uniqueImei();

        Path file = createCsv(
                "mixed.csv",
                csvRow(validImei),
                "INVALID,PLAN_A,2026-10-01,2027-09-30,100.00,USD"
        );

        ImportFile result = importService.importFile(
                "ACME", "mixed.csv", file
        );

        assertEquals(ImportStatus.COMPLETED, result.getStatus());
        assertEquals(1, countRows(result.getId(), "SUCCESS"));
        assertEquals(1, countRows(result.getId(), "FAILED"));
        assertEquals(1, countPolicies("ACME", validImei));
    }

    @Test
    void shouldNotProcessSameFileTwice() throws IOException {
        String imei = uniqueImei();

        Path file = createCsv(
                "idempotent.csv",
                csvRow(imei)
        );

        ImportFile first = importService.importFile(
                "ACME", "idempotent.csv", file
        );

        ImportFile second = importService.importFile(
                "ACME", "idempotent.csv", file
        );

        assertEquals(first.getId(), second.getId());
        assertEquals(1, countPolicies("ACME", imei));
        assertEquals(1, countRows(first.getId(), "SUCCESS"));
    }

    @Test
    void shouldDetectDuplicatePolicyAcrossFiles()
            throws IOException {

        String imei = uniqueImei();

        Path firstFile = createCsv(
                "first.csv",
                csvRow(imei)
        );

        Path secondFile = createCsv(
                "second.csv",
                csvRow(imei),
                csvRow(uniqueImei())
        );

        ImportFile first = importService.importFile(
                "ACME", "first.csv", firstFile
        );

        ImportFile second = importService.importFile(
                "ACME", "second.csv", secondFile
        );

        assertNotEquals(first.getId(), second.getId());

        assertEquals(1, countRows(second.getId(), "DUPLICATE"));
        assertEquals(1, countRows(second.getId(), "SUCCESS"));
        assertEquals(1, countPolicies("ACME", imei));
    }

    @Test
    void shouldAllowSameImeiForDifferentPartners()
            throws IOException {

        String imei = uniqueImei();

        Path acmeFile = createCsv(
                "acme.csv",
                csvRow(imei)
        );

        Path telcoFile = tempDir.resolve("telco.csv");

        Files.writeString(telcoFile, """
                device_imei|product_code|start_date|end_date|amount|ccy
                %s|PLAN_A|2026-10-01|2027-09-30|100.00|USD
                """.formatted(imei));

        importService.importFile("ACME", "acme.csv", acmeFile);
        importService.importFile("TELCO", "telco.csv", telcoFile);

        assertEquals(1, countPolicies("ACME", imei));
        assertEquals(1, countPolicies("TELCO", imei));
    }

    private Path createCsv(
            String fileName,
            String... rows
    ) throws IOException {

        Path file = tempDir.resolve(fileName);

        String content = """
                IMEI,Plan,Effective Date,Expiry Date,Premium,Currency
                """ + String.join("\n", rows) + "\n";

        Files.writeString(file, content);
        return file;
    }

    private String csvRow(String imei) {
        return "%s,PLAN_A,2026-10-01,2027-09-30,100.00,USD"
                .formatted(imei);
    }

    private String uniqueImei() {
        long value = Math.floorMod(
                UUID.randomUUID().getMostSignificantBits(),
                1_000_000_000_000L
        );

        return "35" + String.format("%013d", value);
    }

    private int countRows(Long fileId, String status) {
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

    private int countPolicies(String partnerCode, String imei) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM policy
                WHERE partner_code = ? AND imei = ?
                """,
                Integer.class,
                partnerCode,
                imei
        );

        return count;
    }
}
