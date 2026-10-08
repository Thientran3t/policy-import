# Partner Policy Enrollment Import

A Java 21 / Spring Boot 3 proof of concept for importing partner enrollment files into insurance policies. The system normalizes partner-specific CSV formats, validates each row independently, stages import results in PostgreSQL, prevents duplicate policies, and exposes import reports.

## Project status

**Day 1 — Complete (end-to-end tested):** core import pipeline, configurable CSV mapping for ACME and TELCO, row validation, durable staging, file-level idempotency, policy deduplication, and REST reporting.

**Day 2 — Planned:** concurrent batch workers, PostgreSQL `FOR UPDATE SKIP LOCKED`, worker leases and recovery, conflict-safe policy creation, and performance tuning against the 100,000-row / 30-minute target.

**Day 3 — Planned:** observability and alerting, automated integration/performance tests, documentation, and benchmark results.

> Scope note: Day 1 accepts multipart HTTP uploads. Scheduled SFTP retrieval is part of the intended architecture but is not yet implemented. The 30-minute SLA is a target, not a verified benchmark.

## Technology stack

- Java 21
- Spring Boot 3.x (Spring Web, Spring Data JPA, Validation as applicable)
- PostgreSQL
- Flyway database migrations
- Apache Commons CSV
- Maven or Gradle (use the build wrapper included in the repository)

## Architecture

```text
Partner CSV → HTTP upload → temporary file → SHA-256
    → (partner code, file hash) idempotency check
    → import_file → partner configuration → generic CSV parser
    → ParsedRow → CanonicalEnrollment → validation
    → import_row staging (batches) → policy creation / duplicate detection
    → import summary and paginated row errors
```

A malformed row is recorded as `FAILED` without stopping valid rows. The staging table also provides the durable foundation for Day 2 restart/retry support.

## Domain and database

| Table | Purpose | Important unique key |
| --- | --- | --- |
| `partner` | Registered, active partner identities | `code` |
| `import_file` | File hash, import status and counts | `(partner_code, file_hash)` |
| `import_row` | Staged canonical values, status and row errors | `(file_id, row_number)` |
| `policy` | Successfully created policies | `(partner_code, imei)` |

File-level idempotency uses **partner code + SHA-256 file hash**. Business deduplication uses **partner code + IMEI**. Database unique constraints provide the final integrity safeguard; concurrency-safe conflict handling is a Day 2 improvement.

## Partner-specific CSV mapping

Partner identity is supplied by the upload path (or eventually the SFTP connection), not by the CSV row. File formats are defined under `policy-import.partners` in application configuration.

| Partner | Delimiter | IMEI column | Plan column | Start date | End date | Premium | Currency |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ACME | `,` | `IMEI` | `Plan` | `Effective Date` | `Expiry Date` | `Premium` | `Currency` |
| TELCO | `\|` | `device_imei` | `product_code` | `start_date` | `end_date` | `amount` | `ccy` |

Both map to `CanonicalEnrollment(imei, planCode, effectiveDate, expiryDate, premium, currency)` through the same `CsvPartnerFileParser`. New CSV partners generally require configuration rather than a new parser class, provided their formats are supported by the generic parser.

## Source layout

```text
src/main/java/com/interview/policyimport/
├── config/       # PartnerProperties
├── controller/   # ImportController
├── dto/          # API response records
├── entity/       # Partner, ImportFile, ImportRow, Policy
├── exception/
├── model/        # CanonicalEnrollment, ParsedRow, statuses, validation
├── repository/   # Spring Data repositories
└── service/      # Parsing, hashing, validation, import, policy creation

src/main/resources/db/migration/
├── V1__init_schema.sql
└── V2__seed_partners.sql

src/test/resources/files/
├── acme-policy.csv
└── telco-policy.csv
```

## Getting started

### Prerequisites

- JDK 21
- PostgreSQL running locally or in Docker
- Database credentials configured for the application's Spring datasource

1. Create a PostgreSQL database for the application.
2. Configure the datasource URL, username and password in local configuration or environment variables. **Do not commit credentials.**
3. Verify the `policy-import.partners` configuration includes ACME and TELCO.
4. Start the application using the wrapper included in your repository:

   ```bash
   ./mvnw spring-boot:run
   # or, for a Gradle project:
   ./gradlew bootRun
   ```

5. Flyway should create the schema and seed the ACME/TELCO partner records on startup.

> The exact datasource environment-variable names and commands depend on your project's existing configuration and build system.

## REST API

### Import a CSV

```bash
curl -X POST \
  -F "file=@src/test/resources/files/acme-policy.csv" \
  http://localhost:8080/api/imports/ACME
```

For the second partner:

```bash
curl -X POST \
  -F "file=@src/test/resources/files/telco-policy.csv" \
  http://localhost:8080/api/imports/TELCO
```

### Get import summary

```bash
curl http://localhost:8080/api/imports/1
```

The summary includes the import ID, partner, filename, status, total rows, success/failure/duplicate counts and timestamps.

### Get row-level errors

```bash
curl 'http://localhost:8080/api/imports/1/errors?page=0&size=100'
```

The response includes paginated failed rows with row numbers, error codes and messages.

## Day 1 verification

The following scenarios were exercised in end-to-end testing:

- Import ACME comma-separated CSV.
- Import TELCO pipe-separated CSV using the same parser abstraction.
- Continue processing valid rows after row-level parsing/validation failures.
- Persist successful policies and failed-row details.
- Re-upload an identical file without creating another import.
- Reject duplicate `(partner_code, imei)` policies from different files.
- Retrieve import summary and row error reports.

Sample files include valid rows and intentionally invalid IMEIs, missing plans, reversed dates and malformed dates.

## Limitations and next steps

- SFTP polling/downloading is not yet implemented.
- Day 1 uses bounded staging batches but not parallel processing.
- Durable staging exists, but automated restart recovery and expired worker leases are not yet implemented.
- Concurrent duplicate handling will be hardened with database conflict-safe inserts.
- The 100,000-row / 30-minute SLA needs a measured benchmark.
- Production monitoring, alerts and operational dashboards are planned for Day 3.

## License

No license has been selected yet. Add a `LICENSE` file if you intend to publish the project with reuse permissions.
