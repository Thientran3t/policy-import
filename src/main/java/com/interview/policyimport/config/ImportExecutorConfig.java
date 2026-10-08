package com.interview.policyimport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ImportExecutorConfig {

    @Bean("importExecutor")
    public ThreadPoolTaskExecutor importExecutor(
            ImportWorkerProperties properties
    ) {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(properties.concurrency());
        executor.setMaxPoolSize(properties.concurrency());
        executor.setQueueCapacity(properties.concurrency());
        executor.setThreadNamePrefix("policy-worker-");
        executor.initialize();

        return executor;
    }
}