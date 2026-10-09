
package com.interview.policyimport.service;

import com.interview.policyimport.config.ImportWorkerProperties;
import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.model.ImportStatus;
import com.interview.policyimport.repository.ImportFileRepository;
import com.interview.policyimport.repository.ImportRecoveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "policy-import.recovery.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ImportRecoveryService implements ApplicationRunner {

    private final ImportRecoveryRepository recoveryRepository;
    private final ImportFileRepository importFileRepository;
    private final ParallelImportProcessor parallelImportProcessor;
    private final ImportFinalizationService finalizationService;
    private final ImportWorkerProperties workerProperties;
    private final MeterRegistry meterRegistry;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running startup import recovery");
        recover();
    }

    @Scheduled(
            fixedDelayString =
                    "${policy-import.recovery-interval-ms:60000}"
    )
    public void scheduledRecovery() {
        recover();
    }

    public void recover() {

        if (!running.compareAndSet(false, true)) {
            log.debug("Recovery already running; skipping");
            return;
        }

        try {
            int maxAttempts = workerProperties.maxAttempts();

            int exhausted =
                    recoveryRepository.failExhaustedRows(maxAttempts);

            int recovered =
                    recoveryRepository.recoverExpiredRows(maxAttempts);

            if (recovered > 0) {
                meterRegistry.counter(
                        "policy.import.rows.recovered"
                ).increment(recovered);
            }

            if (exhausted > 0) {
                meterRegistry.counter(
                        "policy.import.rows.retry.exhausted"
                ).increment(exhausted);
            }

            if (recovered > 0 || exhausted > 0) {
                log.info(
                        "Worker claims recovered: retried={}, exhausted={}",
                        recovered,
                        exhausted
                );
            }

            resumeUnfinishedImports();

        } catch (Exception ex) {
            log.error("Import recovery failed", ex);

        } finally {
            running.set(false);
        }
    }

    private void resumeUnfinishedImports() {

        List<ImportFile> unfinished =
                importFileRepository.findByStatus(
                        ImportStatus.PROCESSING
                );

        for (ImportFile file : unfinished) {

            Long fileId = file.getId();

            try {
                log.info(
                        "Resuming unfinished import: fileId={}",
                        fileId
                );

                parallelImportProcessor.process(fileId);

                boolean completed =
                        finalizationService.finalizeImport(fileId);

                if (completed) {
                    log.info(
                            "Recovered import completed: fileId={}",
                            fileId
                    );
                } else {
                    log.debug(
                            "Import still has unfinished rows: fileId={}",
                            fileId
                    );
                }

            } catch (Exception ex) {
                log.error(
                        "Failed to resume import: fileId={}",
                        fileId,
                        ex
                );
            }
        }
    }
}
