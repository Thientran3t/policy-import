package com.interview.policyimport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImportRecoveryStartup implements ApplicationRunner {

    private final ImportRecoveryService recoveryService;
    private final ImportResumeService resumeService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting import recovery after application startup");

        recoveryService.recover();
        resumeService.resumeUnfinishedImports();
    }
}