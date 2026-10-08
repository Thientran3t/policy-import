package com.interview.policyimport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "policy-import.recovery.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ImportRecoveryScheduler {

    private final ImportRecoveryService recoveryService;
    private final ImportResumeService resumeService;

    @Scheduled(
            fixedDelayString =
                    "${policy-import.recovery-interval-ms:60000}"
    )
    public void recoverAndResume() {
        log.debug("Starting scheduled recovery cycle");

        recoveryService.recover();
        resumeService.resumeUnfinishedImports();
    }
}