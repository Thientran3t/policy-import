ALTER TABLE import_row
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_import_row_claim
    ON import_row (file_id, status, id);