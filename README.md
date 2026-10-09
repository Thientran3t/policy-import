# Partner Policy Enrollment Import

A Java application that imports partner enrollment CSV files through REST or SFTP and creates insurance policies.

## Tech stack

- Java 21
- Spring Boot 3.x (Spring Web, Spring Data JPA, Spring Integration SFTP, Actuator)
- PostgreSQL
- Flyway
- Apache Commons CSV
- Micrometer
- Docker and Docker Compose
- JUnit 5 and Testcontainers (integration testing)

## Prerequisites

- JDK 21
- Docker with Docker Compose
- Maven or the Maven wrapper included in the project
- An SFTP client (`sftp` command or a GUI client)

## 1. Start PostgreSQL

PostgreSQL and SFTP are defined in the same `docker-compose.yml`. Start both services together:

```bash
docker compose up -d
```

Create/configure the application database and set the Spring datasource URL, username, and password in your local configuration. Flyway applies the database migrations on application startup.

## 2. Local SFTP server

For local testing, the example SFTP connection uses `localhost:2222` with username `partner` and password `partner123`. Check the port and credentials in your actual Compose configuration before connecting.

## 3. Configure Spring Boot

Configure the PostgreSQL datasource and partner CSV mappings in `application.yml` or your local profile. For local SFTP polling, use the property names defined in your `SftpProperties` class. Example:

```yaml
policy-import:
  sftp:
    enabled: true
    partners:
      ACME:
        host: localhost
        port: 2222
        username: partner
        password: ${SFTP_PASSWORD:partner123}
        remote-directory: /upload/ACME
        strict-host-key-checking: false
      TELCO:
        host: localhost
        port: 2222
        username: partner
        password: ${SFTP_PASSWORD:partner123}
        remote-directory: /upload/TELCO
        strict-host-key-checking: false
```

Merge this with the existing `policy-import` section; do not duplicate the root YAML key. Confirm the actual configuration structure matches your code. If the application runs in the same Docker Compose network, use `sftp:22` instead of `localhost:2222`. Disable host-key verification **only in local testing**.

## 4. Run the application

```bash
./mvnw spring-boot:run
```

If the project does not include a Maven wrapper, use `mvn spring-boot:run`. The API is expected at `http://localhost:8080` with the default port.

## 5. Test HTTP import

```bash
curl -X POST "http://localhost:8080/api/imports/ACME" \
  -F "file=@src/test/resources/files/acme-policy.csv"

# Replace 1 with the returned import ID
curl "http://localhost:8080/api/imports/1"
curl "http://localhost:8080/api/imports/1/errors?page=0&size=50"
```

## 6. Test SFTP import

Connect to the local SFTP server:

```bash
sftp -P 2222 partner@localhost
```

Enter password `partner123`, then run:

```text
cd upload
mkdir ACME
mkdir TELCO
cd ACME
put src/test/resources/files/acme-policy.csv
ls
bye
```

If the `put` command cannot find the local CSV, use `lpwd` and `lcd` inside the SFTP client or supply an absolute local path. Skip `mkdir` if the folders already exist. Wait for the application's configured SFTP poll, check the logs, and query the import status endpoint. The incoming file should remain available until processing completes so an interrupted staging attempt can be retried.

## 7. Stop local services

```bash
docker compose down
```

Avoid `docker compose down -v` if you want to preserve PostgreSQL data.
