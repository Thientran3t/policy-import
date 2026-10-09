package com.interview.policyimport.service;

import com.interview.policyimport.config.ImportWorkerProperties;
import com.interview.policyimport.repository.ImportRowClaimRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class ImportRowClaimService {

    private final ImportRowClaimRepository claimRepository;
    private final ImportWorkerProperties properties;

    public ImportRowClaimService(
            ImportRowClaimRepository claimRepository,
            ImportWorkerProperties properties
    ) {
        this.claimRepository = claimRepository;
        this.properties = properties;
    }

    public List<Long> claimNextBatch(
            Long fileId
    ) {
        return claimRepository.claimRows(
                fileId,
                properties.batchSize()
        );
    }
}