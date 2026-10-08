package com.interview.policyimport.service;

import com.interview.policyimport.config.ImportWorkerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ParallelImportProcessor {

    private final ThreadPoolTaskExecutor executor;
    private final ImportRowClaimService claimService;
    private final ImportBatchProcessor batchProcessor;
    private final ImportWorkerProperties properties;

    public ParallelImportProcessor(
            @Qualifier("importExecutor")
            ThreadPoolTaskExecutor executor,
            ImportRowClaimService claimService,
            ImportBatchProcessor batchProcessor,
            ImportWorkerProperties properties
    ) {
        this.executor = executor;
        this.claimService = claimService;
        this.batchProcessor = batchProcessor;
        this.properties = properties;
    }

    public void process(Long fileId) {
        long start = System.nanoTime();

        log.info("Parallel processing started: fileId={}", fileId);

        List<CompletableFuture<Void>> futures =
                new ArrayList<>();

        for (int i = 0; i < properties.concurrency(); i++) {
            String workerId = UUID.randomUUID().toString();

            futures.add(
                    CompletableFuture.runAsync(
                            () -> runWorker(fileId, workerId),
                            executor
                    )
            );
        }

        CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        ).join();

        log.info(
                "Parallel processing finished: fileId={}, durationMs={}",
                fileId,
                (System.nanoTime() - start) / 1_000_000
        );

    }

    private void runWorker(
            Long fileId,
            String workerId
    ) {
        while (true) {
            List<Long> rowIds =
                    claimService.claimNextBatch(fileId, workerId);

            if (rowIds.isEmpty()) {
                return;
            }

            batchProcessor.processBatch(
                    fileId,
                    workerId,
                    rowIds
            );
        }
    }
}