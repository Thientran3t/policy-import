CREATE TABLE partner (
                         id              BIGSERIAL PRIMARY KEY,
                         code            VARCHAR(50) NOT NULL,
                         name            VARCHAR(255) NOT NULL,
                         active          BOOLEAN NOT NULL DEFAULT TRUE,
                         created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                         CONSTRAINT uk_partner_code UNIQUE (code)
);


CREATE TABLE import_file (
                             id              BIGSERIAL PRIMARY KEY,

                             partner_code    VARCHAR(50) NOT NULL,
                             file_name       VARCHAR(255) NOT NULL,
                             file_hash       VARCHAR(64) NOT NULL,

                             status          VARCHAR(30) NOT NULL,

                             total_rows      INTEGER NOT NULL DEFAULT 0,
                             success_count   INTEGER NOT NULL DEFAULT 0,
                             failed_count    INTEGER NOT NULL DEFAULT 0,
                             duplicate_count INTEGER NOT NULL DEFAULT 0,

                             created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             completed_at    TIMESTAMP NULL,

                             CONSTRAINT uk_import_file_partner_hash
                                 UNIQUE (partner_code, file_hash),

                             CONSTRAINT fk_import_file_partner
                                 FOREIGN KEY (partner_code)
                                     REFERENCES partner(code)
);

CREATE INDEX idx_import_file_partner
    ON import_file(partner_code);

CREATE INDEX idx_import_file_status
    ON import_file(status);


CREATE TABLE import_row (
                            id              BIGSERIAL PRIMARY KEY,

                            file_id         BIGINT NOT NULL,
                            row_number      INTEGER NOT NULL,

                            imei            VARCHAR(50),
                            plan_code       VARCHAR(100),
                            effective_date  DATE,
                            expiry_date     DATE,
                            premium         NUMERIC(19, 4),
                            currency        VARCHAR(3),

                            status          VARCHAR(30) NOT NULL,

                            error_code      VARCHAR(100),
                            error_message   VARCHAR(1000),

                            created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            processed_at    TIMESTAMP NULL,

                            CONSTRAINT uk_import_row_file_row
                                UNIQUE (file_id, row_number),

                            CONSTRAINT fk_import_row_file
                                FOREIGN KEY (file_id)
                                    REFERENCES import_file(id)
                                    ON DELETE CASCADE
);

CREATE INDEX idx_import_row_file_status
    ON import_row(file_id, status);

CREATE INDEX idx_import_row_imei
    ON import_row(imei);


CREATE TABLE policy (
                        id              BIGSERIAL PRIMARY KEY,

                        policy_number   VARCHAR(100) NOT NULL,
                        partner_code    VARCHAR(50) NOT NULL,

                        imei            VARCHAR(50) NOT NULL,
                        plan_code       VARCHAR(100) NOT NULL,

                        effective_date  DATE NOT NULL,
                        expiry_date     DATE NOT NULL,

                        premium         NUMERIC(19, 4) NOT NULL,
                        currency        VARCHAR(3) NOT NULL,

                        status          VARCHAR(30) NOT NULL,

                        created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT uk_policy_number
                            UNIQUE (policy_number),

                        CONSTRAINT uk_policy_partner_imei
                            UNIQUE (partner_code, imei),

                        CONSTRAINT fk_policy_partner
                            FOREIGN KEY (partner_code)
                                REFERENCES partner(code)
);

CREATE INDEX idx_policy_partner
    ON policy(partner_code);

CREATE INDEX idx_policy_imei
    ON policy(imei);