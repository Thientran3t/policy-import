
package com.interview.policyimport.service;

import com.interview.policyimport.config.ImportWorkerProperties;
import com.interview.policyimport.repository.ImportRecoveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportRecoveryService {

    private final ImportRecoveryRepository recoveryRepository;
    private final ImportWorkerProperties properties;
    private final MeterRegistry meterRegistry;

    public void recover() {
        long start = System.nanoTime();

        log.debug("Starting recovery of expired import rows");

        try {
            // Mark rows that have exhausted retry attempts.
            int failed = recoveryRepository.failExhaustedRows(
                    properties.maxAttempts()
            );

            // Return retryable rows to PENDING.
            int recovered = recoveryRepository.recoverExpiredRows(
                    properties.maxAttempts()
            );

            // Record Micrometer counters.
            if (recovered > 0) {
                meterRegistry.counter(
                        "policy.import.rows.recovered"
                ).increment(recovered);
            }

            if (failed > 0) {
                meterRegistry.counter(
                        "policy.import.rows.retry.exhausted"
                ).increment(failed);
            }

            long durationMs =
                    (System.nanoTime() - start) / 1_000_000;

            if (recovered > 0 || failed > 0) {
                log.info(
                        "Import recovery completed: recovered={}, failed={}, durationMs={}",
                        recovered,
                        failed,
                        durationMs
                );
            } else {
                log.debug(
                        "No expired import rows found: durationMs={}",
                        durationMs
                );
            }

        } catch (Exception e) {
            log.error("Import recovery failed", e);
            throw e;
        }
    }
}
