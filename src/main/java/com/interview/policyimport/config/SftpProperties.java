
package com.interview.policyimport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "policy-import.sftp")
public record SftpProperties(
        boolean enabled,
        long pollingIntervalMs,
        Map<String, PartnerSftpConfig> partners
) {

    public record PartnerSftpConfig(
            String host,
            int port,
            String username,
            String password,
            String remoteDirectory,
            boolean strictHostKeyChecking
    ) {
    }
}
