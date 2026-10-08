package com.interview.policyimport.service;

import com.interview.policyimport.model.ImportStatus;
import com.interview.policyimport.repository.ImportFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportResumeService {

    private final ImportFileRepository importFileRepository;
    private final ParallelImportProcessor parallelImportProcessor;
    private final ImportFinalizationService finalizationService;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public void resumeUnfinishedImports() {
        if (!running.compareAndSet(false, true)) {
            log.debug("Import recovery already running; skipping");
            return;
        }

        try {
            var imports = importFileRepository.findByStatus(
                    ImportStatus.PROCESSING
            );

            for (var file : imports) {
                try {
                    Long fileId = file.getId();

                    log.info("Resuming import: fileId={}", fileId);

                    parallelImportProcessor.process(fileId);
                    finalizationService.finalizeImport(fileId);

                } catch (Exception e) {
                    log.error(
                            "Failed to resume import: fileId={}",
                            file.getId(),
                            e
                    );
                }
            }
        } finally {
            running.set(false);
        }
    }
}