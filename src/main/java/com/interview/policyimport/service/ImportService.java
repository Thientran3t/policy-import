package com.interview.policyimport.service;

import com.interview.policyimport.config.PartnerProperties;
import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.entity.ImportRow;
import com.interview.policyimport.entity.Partner;
import com.interview.policyimport.model.CanonicalEnrollment;
import com.interview.policyimport.model.ImportRowStatus;
import com.interview.policyimport.model.PartnerDefinition;
import com.interview.policyimport.model.ParsedRow;
import com.interview.policyimport.model.ValidationError;
import com.interview.policyimport.model.ValidationResult;
import com.interview.policyimport.repository.ImportFileRepository;
import com.interview.policyimport.repository.ImportRowRepository;
import com.interview.policyimport.repository.PartnerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImportService {

    private static final int STAGING_BATCH_SIZE = 500;
    private static final int PROCESSING_BATCH_SIZE = 500;

    private final PartnerProperties partnerProperties;
    private final ImportFileRepository importFileRepository;
    private final ImportRowRepository importRowRepository;
    private final PartnerFileParserFactory parserFactory;
    private final EnrollmentValidator validator;
    private final FileHashService fileHashService;
    private final PolicyService policyService;
    private final PartnerRepository partnerRepository;
    private final ImportRowPersistenceService importRowPersistenceService;

    public ImportService(
            PartnerProperties partnerProperties,
            PartnerRepository partnerRepository,
            ImportFileRepository importFileRepository,
            ImportRowRepository importRowRepository,
            PartnerFileParserFactory parserFactory,
            EnrollmentValidator validator,
            FileHashService fileHashService,
            PolicyService policyService,
            ImportRowPersistenceService importRowPersistenceService
    ) {
        this.partnerProperties = partnerProperties;
        this.partnerRepository = partnerRepository;
        this.importFileRepository = importFileRepository;
        this.importRowRepository = importRowRepository;
        this.parserFactory = parserFactory;
        this.validator = validator;
        this.fileHashService = fileHashService;
        this.policyService = policyService;
        this.importRowPersistenceService = importRowPersistenceService;
    }

    public Page<ImportRow> getFailedRows(
            Long importId,
            Pageable pageable
    ) {
        if (!importFileRepository.existsById(importId)) {
            throw new IllegalArgumentException(
                    "Import not found: " + importId
            );
        }

        return importRowRepository
                .findByFileIdAndStatus(
                        importId,
                        ImportRowStatus.FAILED,
                        pageable
                );
    }

    public ImportFile getImport(Long importId) {
        return importFileRepository.findById(importId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Import not found: " + importId
                        )
                );
    }

    public ImportFile importFile(
            String partnerCode,
            String fileName,
            Path filePath
    ) throws IOException {

        Partner partner = partnerRepository
                .findByCode(partnerCode)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unknown partner: " + partnerCode
                        )
                );

        if (!partner.isActive()) {
            throw new IllegalArgumentException(
                    "Partner is inactive: " + partnerCode
            );
        }

        PartnerDefinition definition =
                getPartnerDefinition(partnerCode);

        String fileHash =
                fileHashService.sha256(filePath);

        // File-level idempotency
        var existing =
                importFileRepository
                        .findByPartnerCodeAndFileHash(
                                partnerCode,
                                fileHash
                        );

        if (existing.isPresent()) {
            return existing.get();
        }

        ImportFile importFile =
                new ImportFile(
                        partnerCode,
                        fileName,
                        fileHash
                );

        importFile.markProcessing();

        importFile =
                importFileRepository.save(importFile);

        try {
            // Phase 1: parse + validate + persist staging rows
            stageRows(
                    importFile,
                    filePath,
                    definition
            );

            // Phase 2: process staged rows
            processRows(importFile);

            completeImport(importFile);

            return importFile;

        } catch (Exception e) {
            importFile.fail();
            importFileRepository.save(importFile);

            throw e;
        }
    }

    private PartnerDefinition getPartnerDefinition(
            String partnerCode
    ) {
        PartnerDefinition definition =
                partnerProperties.partners()
                        .get(partnerCode);

        if (definition == null) {
            throw new IllegalArgumentException(
                    "Unknown partner: " + partnerCode
            );
        }

        return definition;
    }

    private void stageRows(
            ImportFile importFile,
            Path filePath,
            PartnerDefinition definition
    ) throws IOException {

        PartnerFileParser parser =
                parserFactory.getParser(definition.format());

        List<ImportRow> batch =
                new ArrayList<>(STAGING_BATCH_SIZE);

        try (InputStream inputStream =
                     Files.newInputStream(filePath)) {

            parser.parse(inputStream, definition)
                    .forEach(parsedRow -> {

                        ImportRow row =
                                toImportRow(
                                        importFile,
                                        parsedRow
                                );

                        batch.add(row);

                        if (batch.size() >= STAGING_BATCH_SIZE) {
                            saveBatch(batch);
                            batch.clear();
                        }
                    });
        }

        if (!batch.isEmpty()) {
            saveBatch(batch);
        }
    }

    private ImportRow toImportRow(
            ImportFile importFile,
            ParsedRow parsedRow
    ) {
        CanonicalEnrollment enrollment =
                parsedRow.enrollment();

        ImportRow row = new ImportRow(
                importFile,
                parsedRow.rowNumber(),
                enrollment != null
                        ? enrollment.imei()
                        : null,
                enrollment != null
                        ? enrollment.planCode()
                        : null,
                enrollment != null
                        ? enrollment.effectiveDate()
                        : null,
                enrollment != null
                        ? enrollment.expiryDate()
                        : null,
                enrollment != null
                        ? enrollment.premium()
                        : null,
                enrollment != null
                        ? enrollment.currency()
                        : null
        );

        // Parsing failure
        if (parsedRow.hasError()) {
            row.markFailed(
                    parsedRow.errorCode(),
                    parsedRow.errorMessage()
            );

            return row;
        }

        // Business validation
        ValidationResult validationResult =
                validator.validate(enrollment);

        if (!validationResult.isValid()) {
            markValidationFailure(
                    row,
                    validationResult
            );
        }

        return row;
    }

    private void markValidationFailure(
            ImportRow row,
            ValidationResult result
    ) {
        String errorCode =
                result.errors()
                        .stream()
                        .map(ValidationError::code)
                        .reduce((a, b) -> a + "," + b)
                        .orElse("VALIDATION_ERROR");

        String errorMessage =
                result.errors()
                        .stream()
                        .map(ValidationError::message)
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Validation failed");

        row.markFailed(
                errorCode,
                errorMessage
        );
    }

    private void saveBatch(List<ImportRow> batch) {
        importRowPersistenceService.saveBatch(batch);
    }

    private void processRows(
            ImportFile importFile
    ) {
        int pageNumber = 0;

        while (true) {

            Page<ImportRow> page =
                    importRowRepository.findByFileIdAndStatus(
                            importFile.getId(),
                            ImportRowStatus.PENDING,
                            PageRequest.of(
                                    pageNumber,
                                    PROCESSING_BATCH_SIZE
                            )
                    );

            if (page.isEmpty()) {
                break;
            }

            for (ImportRow row : page.getContent()) {
                processRow(importFile, row);
            }

            pageNumber++;
        }
    }

    private void processRow(
            ImportFile importFile,
            ImportRow row
    ) {
        CanonicalEnrollment enrollment =
                new CanonicalEnrollment(
                        row.getImei(),
                        row.getPlanCode(),
                        row.getEffectiveDate(),
                        row.getExpiryDate(),
                        row.getPremium(),
                        row.getCurrency()
                );

        boolean created =
                policyService.createPolicy(
                        importFile.getPartnerCode(),
                        enrollment
                );

        if (created) {
            row.markSuccess();
        } else {
            row.markDuplicate(
                    "Policy already exists for partner and IMEI"
            );
        }

        importRowPersistenceService.save(row);
    }

    private void completeImport(
            ImportFile importFile
    ) {
        int totalRows =
                (int) importRowRepository
                        .countByFileId(
                                importFile.getId()
                        );

        int successCount =
                (int) importRowRepository
                        .countByFileIdAndStatus(
                                importFile.getId(),
                                ImportRowStatus.SUCCESS
                        );

        int failedCount =
                (int) importRowRepository
                        .countByFileIdAndStatus(
                                importFile.getId(),
                                ImportRowStatus.FAILED
                        );

        int duplicateCount =
                (int) importRowRepository
                        .countByFileIdAndStatus(
                                importFile.getId(),
                                ImportRowStatus.DUPLICATE
                        );

        importFile.complete(
                totalRows,
                successCount,
                failedCount,
                duplicateCount
        );

        importFileRepository.save(importFile);
    }
}