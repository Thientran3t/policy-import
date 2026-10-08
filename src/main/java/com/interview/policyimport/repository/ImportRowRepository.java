package com.interview.policyimport.repository;

import com.interview.policyimport.entity.ImportRow;
import com.interview.policyimport.model.ImportRowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM ImportRow r
        WHERE r.id IN :ids
        ORDER BY r.id
        """)
    List<ImportRow> findAllByIdForUpdate(
            @Param("ids") List<Long> ids
    );

}