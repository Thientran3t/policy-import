package com.interview.policyimport.service;

import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.model.ImportRowStatus;
import com.interview.policyimport.repository.ImportFileRepository;
import com.interview.policyimport.repository.ImportRowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportFinalizationService {

    private final ImportFileRepository importFileRepository;
    private final ImportRowRepository importRowRepository;

    @Transactional
    public boolean finalizeImport(Long fileId) {
        ImportFile file = importFileRepository.findById(fileId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Import file not found: " + fileId
                        )
                );

        long pending = importRowRepository.countByFileIdAndStatus(
                fileId, ImportRowStatus.PENDING
        );

        long processing = importRowRepository.countByFileIdAndStatus(
                fileId, ImportRowStatus.PROCESSING
        );

        if (pending > 0 || processing > 0) {
            log.debug(
                    "Import not ready for finalization: fileId={}, pending={}, processing={}",
                    fileId, pending, processing
            );
            return false;
        }

        long success = importRowRepository.countByFileIdAndStatus(
                fileId, ImportRowStatus.SUCCESS
        );

        long failed = importRowRepository.countByFileIdAndStatus(
                fileId, ImportRowStatus.FAILED
        );

        long duplicate = importRowRepository.countByFileIdAndStatus(
                fileId, ImportRowStatus.DUPLICATE
        );

        long total = success + failed + duplicate;

        file.complete(
                Math.toIntExact(total),
                Math.toIntExact(success),
                Math.toIntExact(failed),
                Math.toIntExact(duplicate)
        );

        importFileRepository.save(file);

        log.info(
                "Import finalized: fileId={}, total={}, success={}, failed={}, duplicate={}",
                fileId, total, success, failed, duplicate
        );

        return true;
    }
}