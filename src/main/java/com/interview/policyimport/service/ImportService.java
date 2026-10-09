
package com.interview.policyimport.service;

import com.interview.policyimport.config.PartnerProperties;
import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.entity.ImportRow;
import com.interview.policyimport.model.*;
import com.interview.policyimport.repository.ImportFileRepository;
import com.interview.policyimport.repository.ImportRowRepository;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private static final int STAGING_BATCH_SIZE = 500;

    private final Set<Long> activeStaging =
            ConcurrentHashMap.newKeySet();

    private final PartnerRepository partnerRepository;
    private final PartnerProperties partnerProperties;
    private final ImportFileRepository importFileRepository;
    private final ImportRowRepository importRowRepository;
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

        validatePartner(partnerCode);

        PartnerDefinition definition =
                partnerProperties.partners().get(partnerCode);

        String fileHash = fileHashService.sha256(filePath);

        ImportFile importFile = importFileRepository
                .findByPartnerCodeAndFileHash(partnerCode, fileHash)
                .orElseGet(() -> createImportFile(
                        partnerCode,
                        fileName,
                        fileHash
                ));

        if (importFile.getStatus() != ImportStatus.STAGING) {
            log.info(
                    "Existing import: fileId={}, status={}",
                    importFile.getId(),
                    importFile.getStatus()
            );
            return importFile;
        }

        return continueImport(importFile, filePath, definition);
    }

    private void validatePartner(String partnerCode) {

        var partner = partnerRepository.findByCode(partnerCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown partner: " + partnerCode
                ));

        if (!partner.isActive()) {
            throw new IllegalArgumentException(
                    "Inactive partner: " + partnerCode
            );
        }

        if (!partnerProperties.partners().containsKey(partnerCode)) {
            throw new IllegalArgumentException(
                    "Missing file mapping: " + partnerCode
            );
        }
    }

    private ImportFile createImportFile(
            String partnerCode,
            String fileName,
            String fileHash
    ) {

        ImportFile file = new ImportFile(
                partnerCode,
                fileName,
                fileHash
        );

        file.markStaging();

        file = importFileRepository.saveAndFlush(file);

        log.info(
                "Import created: fileId={}, partner={}",
                file.getId(),
                partnerCode
        );

        return file;
    }

    private ImportFile continueImport(
            ImportFile importFile,
            Path filePath,
            PartnerDefinition definition
    ) throws IOException {

        Long fileId = importFile.getId();
        String partnerCode = importFile.getPartnerCode();

        if (!activeStaging.add(fileId)) {
            log.info(
                    "Staging already active: fileId={}",
                    fileId
            );
            return importFile;
        }

        long importStart = System.nanoTime();

        try {
            int deleted = importRowRepository
                    .deleteAllByImportFileId(fileId);

            if (deleted > 0) {
                log.info(
                        "Removed partial staging rows: fileId={}, deleted={}",
                        fileId,
                        deleted
                );
            }

            long stagingStart = System.nanoTime();

            int stagedRows = stageRows(
                    importFile,
                    filePath,
                    definition
            );

            log.info(
                    "Staging completed: fileId={}, rows={}, durationMs={}",
                    fileId,
                    stagedRows,
                    elapsedMs(stagingStart)
            );

            // Full staging is complete.
            importFile.markProcessing();
            importFileRepository.saveAndFlush(importFile);

        } catch (Exception ex) {
            log.error(
                    "Staging failed: fileId={}. "
                            + "Import remains STAGING for SFTP retry.",
                    fileId,
                    ex
            );

            if (ex instanceof IOException ioException) {
                throw ioException;
            }

            throw new IllegalStateException(
                    "Staging failed: fileId=" + fileId,
                    ex
            );

        } finally {
            activeStaging.remove(fileId);
        }

        try {
            long processingStart = System.nanoTime();

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
                        "Import has outstanding rows: fileId={}. "
                                + "Recovery will continue.",
                        fileId
                );
            }

        } catch (Exception ex) {
            log.error(
                    "Processing interrupted: fileId={}. "
                            + "Import remains PROCESSING for recovery.",
                    fileId,
                    ex
            );

            throw new IllegalStateException(
                    "Processing failed: fileId=" + fileId,
                    ex
            );

        } finally {
            recordDuration(importStart, partnerCode);
        }

        return importFileRepository.findById(fileId)
                .orElseThrow();
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

        try (
                InputStream inputStream =
                        Files.newInputStream(filePath);
                Stream<ParsedRow> rows =
                        parser.parse(inputStream, definition)
        ) {

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
                }
            }

            if (!batch.isEmpty()) {
                importRowPersistenceService.saveBatch(batch);
            }
        }

        return totalRows;
    }

    private ImportRow buildImportRow(
            ImportFile importFile,
            ParsedRow parsedRow
    ) {

        CanonicalEnrollment enrollment =
                parsedRow.enrollment();

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

            String message = validation.errors()
                    .stream()
                    .map(ValidationError::message)
                    .collect(Collectors.joining("; "));

            row.markFailed("VALIDATION_ERROR", message);
        }

        return row;
    }

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
