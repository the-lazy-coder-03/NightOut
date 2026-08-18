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
- Rclone S3 gateway for Google Drive-backed file storage

## Storage Model

PostgreSQL is the source of truth for clubs, events, users, and photo metadata. Photo binaries are never stored in PostgreSQL.

This app stores uploaded image bytes through a `StorageService` abstraction. The production provider is `s3`, which calls `rclone serve s3` through the AWS SDK for Java:

- Upload: `PutObject`
- Download/view: `GetObject`
- Cleanup/delete: `DeleteObject`
- Checks/listing: `HeadObject` and `ListObjectsV2`

For local development and automated tests, `NIGHTOUT_STORAGE_PROVIDER=local` stores files on disk under `NIGHTOUT_LOCAL_STORAGE_PATH`. New production uploads store the S3 object key in `photos.storage_file_id`.

Existing rows from older storage providers are not migrated automatically. Reupload or manually migrate those records if they need to render through the S3 gateway.

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

Required S3/rclone values for production storage:

```properties
NIGHTOUT_STORAGE_PROVIDER=s3
NIGHTOUT_S3_ENDPOINT=http://127.0.0.1:8080
NIGHTOUT_S3_BUCKET=nightout
NIGHTOUT_S3_REGION=us-east-1
NIGHTOUT_S3_ACCESS_KEY=replace-with-random-access-key
NIGHTOUT_S3_SECRET_KEY=replace-with-random-secret-key
NIGHTOUT_S3_PATH_STYLE=true
RCLONE_REMOTE=drive_primary:
```

Rclone must bind its S3 server to localhost and require authentication. See `deploy/rclone-s3.md` for Google Drive and optional union backend examples.

Use `NIGHTOUT_BASE_URL` for generated QR codes, for example:

```properties
NIGHTOUT_BASE_URL=https://crowdcam.co.za
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

Night dates use `NIGHTOUT_TIME_ZONE` and roll over at noon. The default is `Africa/Johannesburg`, so a Friday night that ends early Saturday morning still appears under Friday until 12:00 PM Saturday.

## Run Locally

```sh
mvn spring-boot:run
```

With demo seeding enabled, the app creates fictional clubs and events, plus:

- Admin: `NIGHTOUT_ADMIN_LOGIN_EMAIL` / `NIGHTOUT_ADMIN_LOGIN_PASSWORD`
- Club owner: `NIGHTOUT_CLUB_LOGIN_EMAIL` / `NIGHTOUT_CLUB_LOGIN_PASSWORD`

Set `NIGHTOUT_SEED_DEMO=false` outside local demo environments.

## Tests

```sh
mvn test
```

Tests use H2 in PostgreSQL mode, Flyway migrations, local storage, and mocked storage for cleanup resilience.

Load tests live in `load-tests/`. Start with the read-only k6 test:

```sh
docker run --rm -e BASE_URL=https://crowdcam.co.za -v "$PWD:/repo" grafana/k6 run /repo/load-tests/k6/public-read.js
```

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

The production app expects PostgreSQL and a reachable localhost rclone S3 gateway. Do not commit secrets, API keys, OAuth credentials, or real database passwords.
