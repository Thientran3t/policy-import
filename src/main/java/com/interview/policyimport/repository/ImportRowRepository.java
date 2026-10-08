package com.interview.policyimport.repository;

import com.interview.policyimport.entity.ImportRow;
import com.interview.policyimport.model.ImportRowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRowRepository
        extends JpaRepository<ImportRow, Long> {

    Page<ImportRow> findByFileIdAndStatus(
            Long fileId,
            ImportRowStatus status,
            Pageable pageable
    );

    long countByFileId(Long fileId);

    long countByFileIdAndStatus(
            Long fileId,
            ImportRowStatus status
    );
}