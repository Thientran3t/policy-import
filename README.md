# Partner Policy Import — Setup & Run

## Prerequisites

- Java 21
- Maven 3.9+
- Docker Engine or Docker Desktop with Docker Compose v2
- PostgreSQL is provided by the project Docker Compose configuration (or an existing local instance)

## 1. Start PostgreSQL with Docker Compose

The project includes a Docker Compose file at its root. From the project directory, run:

```bash
docker compose up -d
```

Check container status and logs:

```bash
docker compose ps
docker compose logs -f
```

The database container must be running and ready before starting Spring Boot. Check the Compose file for the actual PostgreSQL port, database name, username, and password. The examples below assume it exposes port `5432` on your host and uses database `policy_import` with username/password `postgres`.

Configure the matching datasource settings in `src/main/resources/application.yml` (or use environment variables):

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/policy_import}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

If the Compose file does not create the `policy_import` database, set its `POSTGRES_DB` environment variable accordingly or create the database manually. Merge these settings with your existing YAML rather than duplicating keys.

Flyway migrations create the application tables and seed sample partners (`ACME` and `TELCO`) when Spring Boot starts.

To stop the containers without deleting database data:

```bash
docker compose down
```

**Warning:** `docker compose down -v` also deletes Compose-managed volumes and can erase your local database data.

## 2. Configure partner file mappings

Make sure your existing `policy-import.partners` configuration includes `ACME` and `TELCO`. Example:

```yaml
policy-import:
  partners:
    ACME:
      format: CSV
      delimiter: ","
      has-header: true
      columns:
        imei: IMEI
        plan-code: Plan
        effective-date: Effective Date
        expiry-date: Expiry Date
        premium: Premium
        currency: Currency
    TELCO:
      format: CSV
      delimiter: "|"
      has-header: true
      columns:
        imei: device_imei
        plan-code: product_code
        effective-date: start_date
        expiry-date: end_date
        premium: amount
        currency: ccy
  worker:
    concurrency: 4
    batch-size: 200
    lease-seconds: 300
    max-attempts: 3
  recovery:
    enabled: true
  recovery-interval-ms: 60000
```

Merge this with your existing configuration; do not create a second `policy-import` root section.

## 3. Build and start Spring Boot

From the project root:

```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

By default, the application listens on `http://localhost:8080` unless you configured a different port.

## 4. Prepare a sample CSV

Save this as `sample_acme.csv`:

```csv
IMEI,Plan,Effective Date,Expiry Date,Premium,Currency
351234567890123,PLAN_A,2026-10-01,2027-09-30,100.00,USD
351234567890124,PLAN_A,2026-10-01,2027-09-30,150.00,USD
```

## 5. Upload and check an import

Upload the CSV:

```bash
curl -X POST http://localhost:8080/api/imports/ACME \
  -F "file=@sample_acme.csv"
```

The response includes the import ID and status. Replace `1` below with the returned ID.

Check import status:

```bash
curl http://localhost:8080/api/imports/1
```

Retrieve failed rows (paginated):

```bash
curl "http://localhost:8080/api/imports/1/errors?page=0&size=50"
```

The upload request currently waits for processing to finish. Re-uploading identical file content for the same partner returns the existing import.

## 6. Health and metrics

If Spring Boot Actuator is configured to expose these endpoints:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/metrics/policy.import.duration
```

## 7. Run integration tests

Ensure Docker is running, then run:

```bash
mvn test -Dtest=ImportServiceIT
```

If you have a separate recovery test class:

```bash
mvn test -Dtest=ImportCrashRecoveryIT
```

Testcontainers starts a temporary PostgreSQL container for integration tests.

## 8. Troubleshooting

- **Database connection refused:** run `docker compose ps` and `docker compose logs`, then verify the datasource URL, published port, and credentials match the Compose configuration.
- **Unknown partner:** confirm the partner is seeded in the database and has a matching `policy-import.partners` mapping.
- **File upload rejected:** check the configured multipart file-size limits.
- **Testcontainers cannot connect:** start Docker before running integration tests.
- **Metrics endpoint returns 404:** check the Actuator dependency and endpoint exposure settings; a custom metric appears after it has been recorded.
