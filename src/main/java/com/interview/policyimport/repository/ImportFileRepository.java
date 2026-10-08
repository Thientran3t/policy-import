package com.interview.policyimport.repository;

import com.interview.policyimport.entity.ImportFile;
import com.interview.policyimport.model.ImportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImportFileRepository extends JpaRepository<ImportFile, Long> {

    Optional<ImportFile> findByPartnerCodeAndFileHash(
            String partnerCode,
            String fileHash
    );
    List<ImportFile> findByStatus(ImportStatus status);
}