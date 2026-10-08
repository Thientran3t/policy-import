ALTER TABLE import_row
    ADD COLUMN worker_id VARCHAR(100),
    ADD COLUMN lease_until TIMESTAMP,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_import_row_claim
    ON import_row (file_id, status, id);

CREATE INDEX idx_import_row_expired_lease
    ON import_row (lease_until)
    WHERE status = 'PROCESSING';