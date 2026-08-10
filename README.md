# NightOut

NightOut is a Spring Boot nightlife photo-sharing MVP. Public visitors choose a club, choose a night, view the gallery, and upload photos without creating an account. Administrators and club owners use authenticated dashboards to create clubs/events, moderate photos, and generate permanent club QR codes.

## Stack

- Java 25
- Spring Boot 4
- Spring MVC
- Spring Data JPA
- Spring Security
- PostgreSQL
- Flyway
- Thymeleaf
- HTML, CSS, and JavaScript
- Maven
- 9Drive storage gateway for Google Drive/S3-backed file storage

## Storage Model

PostgreSQL is the source of truth for clubs, events, users, and photo metadata. Photo binaries are never stored in PostgreSQL.

This app stores uploaded image bytes through a `StorageService` abstraction. The production provider is `9drive`, which calls the 9Drive gateway:

- Upload: `POST /api/v1/uploads` with a bearer API key.
- Download: server-side authenticated `GET /files/{id}/download`.
- Cleanup/delete: server-side authenticated 9Drive file delete and permanent delete endpoints.

For local development and automated tests, `NIGHTOUT_STORAGE_PROVIDER=local` stores files on disk under `NIGHTOUT_LOCAL_STORAGE_PATH`.

## Configuration

Copy `.env.example` to `.env` for local development and fill in real values:

```sh
cp .env.example .env
```

Required PostgreSQL values:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/nightout
SPRING_DATASOURCE_USERNAME=nightout
SPRING_DATASOURCE_PASSWORD=change-me
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
```

Required 9Drive values for production storage:

```properties
NIGHTOUT_STORAGE_PROVIDER=9drive
NIGHTOUT_9DRIVE_BASE_URL=https://your-9drive.example.com
NIGHTOUT_9DRIVE_API_KEY=9d_live_...
NIGHTOUT_9DRIVE_EMAIL=storage-account@example.com
NIGHTOUT_9DRIVE_PASSWORD=change-me
NIGHTOUT_9DRIVE_FOLDER_ID=
```

Use `NIGHTOUT_BASE_URL` for generated QR codes, for example:

```properties
NIGHTOUT_BASE_URL=https://localmarketeca.duckdns.org
```

## Database

Create the PostgreSQL database and user:

```sql
CREATE DATABASE nightout;
CREATE USER nightout WITH PASSWORD 'change-me';
GRANT ALL PRIVILEGES ON DATABASE nightout TO nightout;
\c nightout
GRANT ALL ON SCHEMA public TO nightout;
ALTER SCHEMA public OWNER TO nightout;
```

Flyway creates the application tables at startup from `src/main/resources/db/migration`.

The app also supports an explicit destructive schema reset. It records `NIGHTOUT_SCHEMA_VERSION` in the database. If you change that value and set `NIGHTOUT_SCHEMA_RESET_ALLOWED=true`, startup drops the configured Flyway schema and rebuilds it from the SQL migrations:

```properties
NIGHTOUT_SCHEMA_VERSION=2
NIGHTOUT_SCHEMA_RESET_ALLOWED=true
SPRING_FLYWAY_SCHEMAS=public
```

After the reset succeeds, keep the new version and set `NIGHTOUT_SCHEMA_RESET_ALLOWED=false`.

## Run Locally

```sh
mvn spring-boot:run
```

With demo seeding enabled, the app creates fictional clubs and events, plus:

- Admin: `admin@nightout.local` / `admin12345`
- Club owner: `owner@nightout.local` / `owner12345`

Set `NIGHTOUT_SEED_DEMO=false` outside local demo environments.

## Tests

```sh
mvn test
```

Tests use H2 in PostgreSQL mode, Flyway migrations, local storage, and mocked storage for cleanup resilience.

## Seven-Day Lifecycle

Events are created by admins or club owners. Uploads and galleries are available from the event date through `event date + NIGHTOUT_RETENTION_DAYS`.

The backend rejects uploads to:

- Future events
- Expired events
- Cancelled events
- Events that do not belong to the selected club
- Unsupported or oversized files

`CleanupService` runs hourly. It finds photos whose event date is outside the retention window, deletes each file from storage, then deletes that photo record. If one storage deletion fails, the other photos still continue and the failed photo remains for a future retry.

## QR Codes

Each club has a permanent QR code that points to `/clubs/{slug}`. The QR code does not grant permission or represent physical presence. It simply takes guests to the club page where they can choose the relevant night.

## Deployment

GitHub Actions builds with Maven and deploys the jar to EC2 when `EC2_SSH_KEY` is configured. Production runtime variables live in `/etc/nightout/nightout.env`; see `deploy/nightout.env.example`.

The production app expects PostgreSQL and a reachable 9Drive deployment. Do not commit secrets, API keys, OAuth credentials, or real database passwords.
