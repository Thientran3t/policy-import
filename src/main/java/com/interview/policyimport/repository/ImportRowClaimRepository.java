package com.interview.policyimport.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
            int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be positive"
            );
        }

        String sql = """
                WITH claimed AS (
                      SELECT id
                      FROM import_row
                      WHERE file_id = :fileId
                        AND status = 'PENDING'
                      ORDER BY id
                      LIMIT :batchSize
                      FOR UPDATE SKIP LOCKED
                  )
                  UPDATE import_row r
                  SET status = 'PROCESSING',
                      attempt_count = attempt_count + 1
                  FROM claimed
                  WHERE r.id = claimed.id
                  RETURNING r.id;
                """;

        var params = new MapSqlParameterSource()
                .addValue("fileId", fileId)
                .addValue("batchSize", batchSize);

        return jdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> rs.getLong("id")
        );
    }
}