
package com.interview.policyimport.service;

import com.interview.policyimport.config.PartnerProperties;
import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.entity.ImportRow;
import com.interview.policyimport.model.CanonicalEnrollment;
import com.interview.policyimport.model.ParsedRow;
import com.interview.policyimport.model.PartnerDefinition;
import com.interview.policyimport.model.ValidationResult;
import com.interview.policyimport.repository.ImportFileRepository;
import com.interview.policyimport.repository.PartnerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final int STAGING_BATCH_SIZE = 500;

    private final PartnerRepository partnerRepository;
    private final PartnerProperties partnerProperties;
    private final ImportFileRepository importFileRepository;
    private final ImportRowPersistenceService importRowPersistenceService;
    private final PartnerFileParserFactory parserFactory;
    private final EnrollmentValidator enrollmentValidator;
    private final FileHashService fileHashService;
    private final ParallelImportProcessor parallelImportProcessor;
    private final ImportFinalizationService finalizationService;
    private final MeterRegistry meterRegistry;

    public ImportFile importFile(
            String partnerCode,
            String fileName,
            Path filePath
    ) throws IOException {

        log.info(
                "Import requested: partnerCode={}, fileName={}",
                partnerCode,
                fileName
        );

        var partner = partnerRepository.findByCode(partnerCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown partner: " + partnerCode
                ));

        if (!partner.isActive()) {
            throw new IllegalArgumentException(
                    "Inactive partner: " + partnerCode
            );
        }

        PartnerDefinition definition =
                partnerProperties.partners().get(partnerCode);

        if (definition == null) {
            throw new IllegalArgumentException(
                    "Missing file mapping for partner: " + partnerCode
            );
        }

        String fileHash = fileHashService.sha256(filePath);

        var existing = importFileRepository
                .findByPartnerCodeAndFileHash(partnerCode, fileHash);

        if (existing.isPresent()) {
            ImportFile file = existing.get();

            log.info(
                    "Existing import detected: fileId={}, partnerCode={}, status={}",
                    file.getId(),
                    partnerCode,
                    file.getStatus()
            );

            return file;
        }

        ImportFile importFile = new ImportFile(
                partnerCode,
                fileName,
                fileHash
        );

        importFile = importFileRepository.saveAndFlush(importFile);

        importFile.markStaging();
        importFile = importFileRepository.saveAndFlush(importFile);

        Long fileId = importFile.getId();
        long importStart = System.nanoTime();

        log.info(
                "CSV staging started: fileId={}, partnerCode={}",
                fileId,
                partnerCode
        );

        long stagingStart = System.nanoTime();

        try {
            int stagedRows = stageRows(
                    importFile,
                    filePath,
                    definition
            );

            log.info(
                    "CSV staging completed: fileId={}, rows={}, durationMs={}",
                    fileId,
                    stagedRows,
                    elapsedMs(stagingStart)
            );

        } catch (Exception e) {
            log.error(
                    "CSV staging failed: fileId={}, partnerCode={}",
                    fileId,
                    partnerCode,
                    e
            );

            importFile.fail();
            importFileRepository.saveAndFlush(importFile);

            meterRegistry.counter(
                    "policy.import.failed",
                    "partner", partnerCode
            ).increment();

            recordDuration(importStart, partnerCode);

            if (e instanceof IOException ioException) {
                throw ioException;
            }

            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }

            throw new IllegalStateException(
                    "CSV staging failed: fileId=" + fileId,
                    e
            );
        }

        importFile.markProcessing();
        importFileRepository.saveAndFlush(importFile);

        log.info(
                "Import transitioned to PROCESSING: fileId={}",
                fileId
        );

        long processingStart = System.nanoTime();

        try {
            parallelImportProcessor.process(fileId);

            log.info(
                    "Parallel processing finished: fileId={}, durationMs={}",
                    fileId,
                    elapsedMs(processingStart)
            );

            boolean completed =
                    finalizationService.finalizeImport(fileId);

            if (completed) {
                meterRegistry.counter(
                        "policy.import.completed",
                        "partner", partnerCode
                ).increment();

                log.info(
                        "Import completed: fileId={}, totalDurationMs={}",
                        fileId,
                        elapsedMs(importStart)
                );
            } else {
                log.warn(
                        "Import has outstanding rows: fileId={}. Recovery will continue.",
                        fileId
                );
            }

        } catch (Exception e) {
            log.error(
                    "Policy processing interrupted: fileId={}, partnerCode={}. "
                            + "Import remains PROCESSING for recovery.",
                    fileId,
                    partnerCode,
                    e
            );

            // Keep PROCESSING for automatic recovery.
            throw new IllegalStateException(
                    "Policy processing interrupted: fileId=" + fileId,
                    e
            );

        } finally {
            recordDuration(importStart, partnerCode);
        }

        return importFileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalStateException(
                        "Import file not found: " + fileId
                ));
    }

    private int stageRows(
            ImportFile importFile,
            Path filePath,
            PartnerDefinition definition
    ) throws IOException {

        int totalRows = 0;

        List<ImportRow> batch =
                new ArrayList<>(STAGING_BATCH_SIZE);

        PartnerFileParser parser =
                parserFactory.getParser(definition.format());

        try (InputStream inputStream = Files.newInputStream(filePath);
             Stream<ParsedRow> rows =
                     parser.parse(inputStream, definition)) {

            for (ParsedRow parsedRow :
                    (Iterable<ParsedRow>) rows::iterator) {

                ImportRow row = buildImportRow(
                        importFile,
                        parsedRow
                );

                batch.add(row);
                totalRows++;

                if (batch.size() >= STAGING_BATCH_SIZE) {
                    importRowPersistenceService.saveBatch(batch);
                    batch.clear();

                    log.debug(
                            "Staging batch committed: fileId={}, totalRows={}",
                            importFile.getId(),
                            totalRows
                    );
                }
            }

            if (!batch.isEmpty()) {
                importRowPersistenceService.saveBatch(batch);

                log.debug(
                        "Final staging batch committed: fileId={}, batchSize={}",
                        importFile.getId(),
                        batch.size()
                );
            }
        }

        return totalRows;
    }

    /**
     * Converts parsed data into a staging row.
     * Invalid rows are recorded as FAILED.
     */
    private ImportRow buildImportRow(
            ImportFile importFile,
            ParsedRow parsedRow
    ) {
        CanonicalEnrollment enrollment = parsedRow.enrollment();

        ImportRow row = new ImportRow(
                importFile,
                parsedRow.rowNumber(),
                enrollment != null ? enrollment.imei() : null,
                enrollment != null ? enrollment.planCode() : null,
                enrollment != null ? enrollment.effectiveDate() : null,
                enrollment != null ? enrollment.expiryDate() : null,
                enrollment != null ? enrollment.premium() : null,
                enrollment != null ? enrollment.currency() : null
        );

        if (parsedRow.hasError()) {
            row.markFailed(
                    parsedRow.errorCode(),
                    parsedRow.errorMessage()
            );
            return row;
        }

        ValidationResult validation =
                enrollmentValidator.validate(enrollment);

        if (!validation.isValid()) {
            String errorMessage = validation.errors()
                    .stream()
                    .map(error -> error.message())
                    .reduce((first, second) -> first + "; " + second)
                    .orElse("Validation failed");

            row.markFailed(
                    "VALIDATION_ERROR",
                    errorMessage
            );
        }

        return row;
    }

    /**
     * Records import duration in Micrometer.
     */
    private void recordDuration(
            long startNanos,
            String partnerCode
    ) {
        Timer.builder("policy.import.duration")
                .description("Partner import execution duration")
                .tag("partner", partnerCode)
                .register(meterRegistry)
                .record(
                        System.nanoTime() - startNanos,
                        TimeUnit.NANOSECONDS
                );
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
