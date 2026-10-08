package com.interview.policyimport.controller;

import com.interview.policyimport.dto.ImportResponse;
import com.interview.policyimport.dto.ImportRowErrorResponse;
import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.service.ImportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/{partnerCode}")
    public ResponseEntity<ImportResponse> importFile(
            @PathVariable String partnerCode,
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Import file must not be empty"
            );
        }

        Path tempFile = Files.createTempFile(
                "policy-import-",
                ".tmp"
        );

        try {
            file.transferTo(tempFile);

            ImportFile importFile =
                    importService.importFile(
                            partnerCode,
                            file.getOriginalFilename(),
                            tempFile
                    );

            return ResponseEntity.ok(
                    ImportResponse.from(importFile)
            );

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @GetMapping("/{importId}")
    public ResponseEntity<ImportResponse> getImport(
            @PathVariable Long importId
    ) {
        ImportFile importFile =
                importService.getImport(importId);

        return ResponseEntity.ok(
                ImportResponse.from(importFile)
        );
    }

    @GetMapping("/{importId}/errors")
    public ResponseEntity<Page<ImportRowErrorResponse>> getErrors(
            @PathVariable Long importId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {

        Page<ImportRowErrorResponse> result =
                importService
                        .getFailedRows(
                                importId,
                                PageRequest.of(page, size)
                        )
                        .map(ImportRowErrorResponse::from);

        return ResponseEntity.ok(result);
    }
}