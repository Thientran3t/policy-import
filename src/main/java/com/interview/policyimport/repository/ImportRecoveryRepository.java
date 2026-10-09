package com.interview.policyimport.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ImportRecoveryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ImportRecoveryRepository(
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int recoverExpiredRows(int maxAttempts) {
        String sql = """
                UPDATE import_row
                SET status = 'PENDING'
                WHERE status = 'PROCESSING'
                  AND attempt_count < :maxAttempts
                """;

        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("maxAttempts", maxAttempts)
        );
    }

    @Transactional
    public int failExhaustedRows(int maxAttempts) {
        String sql = """
                UPDATE import_row
                SET status = 'FAILED',
                    error_code = 'MAX_RETRIES_EXCEEDED',
                    error_message = 'Maximum processing attempts exceeded',
                    processed_at = CURRENT_TIMESTAMP
                WHERE status = 'PROCESSING'
                  AND attempt_count >= :maxAttempts
                """;

        return jdbcTemplate.update(
                sql,
                new MapSqlParameterSource()
                        .addValue("maxAttempts", maxAttempts)
        );
    }
}