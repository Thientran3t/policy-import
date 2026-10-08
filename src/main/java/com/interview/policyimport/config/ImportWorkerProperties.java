package com.interview.policyimport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "policy-import.worker")
public record ImportWorkerProperties(
        int concurrency,
        int batchSize,
        int leaseSeconds,
        int maxAttempts
) {}