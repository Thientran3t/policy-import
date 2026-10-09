
package com.interview.policyimport.service;

import com.interview.policyimport.config.SftpProperties;
import com.interview.policyimport.config.SftpProperties.PartnerSftpConfig;
import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.model.ImportStatus;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.sftp.client.SftpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.integration.file.remote.session.Session;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.integration.sftp.session.SftpRemoteFileTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "policy-import.sftp",
        name = "enabled",
        havingValue = "true"
)
public class SftpPollingService {

    private final SftpProperties properties;
    private final ImportService importService;
    private final MeterRegistry meterRegistry;

    private final AtomicBoolean polling = new AtomicBoolean(false);

    @Scheduled(
            fixedDelayString =
                    "${policy-import.sftp.polling-interval-ms:60000}"
    )
    public void poll() {
        log.info("Polling started");
        if (!polling.compareAndSet(false, true)) {
            log.debug("SFTP polling already running");
            return;
        }

        long start = System.nanoTime();

        try {
            if (properties.partners() == null
                    || properties.partners().isEmpty()) {
                log.warn("No SFTP partners configured");
                return;
            }

            for (Map.Entry<String, PartnerSftpConfig> entry
                    : properties.partners().entrySet()) {

                String partnerCode = entry.getKey();
                PartnerSftpConfig config = entry.getValue();

                try {
                    pollPartner(partnerCode, config);
                } catch (Exception e) {
                    meterRegistry.counter(
                            "policy.import.sftp.poll.failures",
                            "partner", partnerCode
                    ).increment();
                    log.error(
                            "SFTP polling failed for partner={}",
                            partnerCode,
                            e
                    );
                }
            }

        } finally {
            polling.set(false);

            log.info(
                    "SFTP polling cycle finished: durationMs={}",
                    elapsedMs(start)
            );
        }
    }

    private void pollPartner(
            String partnerCode,
            PartnerSftpConfig config
    ) {
        long start = System.nanoTime();

        DefaultSftpSessionFactory sessionFactory =
                createSessionFactory(config);

        SftpRemoteFileTemplate template =
                new SftpRemoteFileTemplate(sessionFactory);

        template.afterPropertiesSet();

        String remoteDirectory = config.remoteDirectory();
        String processedDirectory = remoteDirectory + "/processed";

        log.info(
                "SFTP partner polling started: partner={}",
                partnerCode
        );

        template.execute(session -> {

            if (!session.exists(processedDirectory)) {
                session.mkdir(processedDirectory);
            }

            for (SftpClient.DirEntry file
                    : session.list(remoteDirectory)) {

                String fileName = file.getFilename();

                if (!fileName.toLowerCase().endsWith(".csv")) {
                    continue;
                }

                processFile(
                        session,
                        partnerCode,
                        remoteDirectory,
                        processedDirectory,
                        fileName
                );
            }

            return null;
        });

        log.info(
                "SFTP partner polling finished: partner={}, durationMs={}",
                partnerCode,
                elapsedMs(start)
        );
    }

    private DefaultSftpSessionFactory createSessionFactory(
            PartnerSftpConfig config
    ) {
        DefaultSftpSessionFactory factory =
                new DefaultSftpSessionFactory(true);

        factory.setHost(config.host());
        factory.setPort(config.port());
        factory.setUser(config.username());
        factory.setPassword(config.password());

        factory.setAllowUnknownKeys(
                !config.strictHostKeyChecking()
        );

        return factory;
    }

    private void processFile(
            Session<SftpClient.DirEntry> session,
            String partnerCode,
            String remoteDirectory,
            String processedDirectory,
            String fileName
    ) {
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile(
                    "sftp-policy-import-",
                    ".csv"
            );

            String remotePath = remoteDirectory + "/" + fileName;

            log.info(
                    "Downloading SFTP file: partner={}, file={}",
                    partnerCode,
                    fileName
            );

            try (OutputStream output =
                         Files.newOutputStream(tempFile)) {
                session.read(remotePath, output);
            }

            ImportFile result = importService.importFile(
                    partnerCode,
                    fileName,
                    tempFile
            );

            if (result.getStatus() == ImportStatus.COMPLETED) {
                String processedPath =
                        processedDirectory + "/" + fileName;

                session.rename(remotePath, processedPath);

                log.info(
                        "SFTP file completed: partner={}, fileId={}, file={}",
                        partnerCode,
                        result.getId(),
                        fileName
                );
            } else {
                log.warn(
                        "SFTP import incomplete: partner={}, fileId={}, status={}",
                        partnerCode,
                        result.getId(),
                        result.getStatus()
                );
            }

        } catch (Exception e) {
            log.error(
                    "SFTP file processing failed: partner={}, file={}",
                    partnerCode,
                    fileName,
                    e
            );

        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn(
                            "Temporary file cleanup failed: {}",
                            tempFile,
                            e
                    );
                }
            }
        }
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
