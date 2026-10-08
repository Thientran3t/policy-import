package com.interview.policyimport.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Repository
public class ImportRowClaimRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ImportRowClaimRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<Long> claimRows(
            Long fileId,
            String workerId,
            int batchSize,
            Duration leaseDuration
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be positive"
            );
        }

        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "leaseDuration must be positive"
            );
        }

        String sql = """
                WITH candidates AS (
                    SELECT id
                    FROM import_row
                    WHERE file_id = :fileId
                      AND status = 'PENDING'
                    ORDER BY id
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                )
                UPDATE import_row AS r
                SET status = 'PROCESSING',
                    worker_id = :workerId,
                    lease_until = CURRENT_TIMESTAMP
                        + (:leaseSeconds * INTERVAL '1 second'),
                    attempt_count = attempt_count + 1
                FROM candidates
                WHERE r.id = candidates.id
                RETURNING r.id
                """;

        var params = new MapSqlParameterSource()
                .addValue("fileId", fileId)
                .addValue("workerId", workerId)
                .addValue("batchSize", batchSize)
                .addValue("leaseSeconds", leaseDuration.toSeconds());

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getLong("id")
        );
    }
}