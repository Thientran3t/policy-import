INSERT INTO partner (code, name, active)
VALUES
    ('ACME', 'ACME Insurance Partner', TRUE),
    ('TELCO', 'Telco Insurance Partner', TRUE)
    ON CONFLICT (code) DO NOTHING;