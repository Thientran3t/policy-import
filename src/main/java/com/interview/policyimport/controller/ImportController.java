package com.interview.policyimport.controller;

import com.interview.policyimport.dto.ImportResponse;
import com.interview.policyimport.dto.ImportRowErrorResponse;
import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.entity.ImportRow;
import com.interview.policyimport.repository.ImportFileRepository;
import com.interview.policyimport.repository.ImportRowRepository;
import com.interview.policyimport.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final ImportFileRepository importFileRepository;
    private final ImportRowRepository importRowRepository;

    /**
     * Upload and process a partner enrollment file.
     */
    @PostMapping(
            value = "/{partnerCode}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ImportResponse> uploadFile(
            @PathVariable String partnerCode,
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Uploaded file must not be empty"
            );
        }

        String fileName = file.getOriginalFilename();

        log.info(
                "Received import upload: partnerCode={}, fileName={}, sizeBytes={}",
                partnerCode,
                fileName,
                file.getSize()
        );

        // Store the uploaded file temporarily.
        Path tempFile = Files.createTempFile(
                "policy-import-",
                ".tmp"
        );

        try {
            file.transferTo(tempFile);

            ImportFile importFile = importService.importFile(
                    partnerCode,
                    fileName,
                    tempFile
            );

            log.info(
                    "Import request handled: fileId={}, status={}",
                    importFile.getId(),
                    importFile.getStatus()
            );

            return ResponseEntity.ok(
                    ImportResponse.from(importFile)
            );

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Retrieve import status and summary.
     */
    @GetMapping("/{fileId}")
    public ResponseEntity<ImportResponse> getImport(
            @PathVariable Long fileId
    ) {
        ImportFile importFile = importFileRepository
                .findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Import file not found: " + fileId
                ));

        return ResponseEntity.ok(
                ImportResponse.from(importFile)
        );
    }

    /**
     * Retrieve row-level validation and processing errors.
     */
    @GetMapping("/{fileId}/errors")
    public ResponseEntity<Page<ImportRowErrorResponse>> getErrors(
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        if (page < 0 || size < 1 || size > 500) {
            throw new IllegalArgumentException(
                    "Invalid pagination parameters"
            );
        }

        if (!importFileRepository.existsById(fileId)) {
            throw new IllegalArgumentException(
                    "Import file not found: " + fileId
            );
        }

        Page<ImportRow> errors =
                importRowRepository.findByFileIdAndStatus(
                        fileId,
                        com.interview.policyimport.model.ImportRowStatus.FAILED,
                        PageRequest.of(page, size)
                );

        return ResponseEntity.ok(
                errors.map(ImportRowErrorResponse::from)
        );
    }
}