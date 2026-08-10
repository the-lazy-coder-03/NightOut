# Nightout Deployment

GitHub Actions builds the Spring Boot jar with Maven on pushes to `master` or `main`, then deploys it to the EC2 host at `13.41.33.158` when the `EC2_SSH_KEY` repository secret is configured.

Required GitHub repository secret:

- `EC2_SSH_KEY`: the private SSH key that can log in as `ubuntu` on the EC2 host. Do not commit the key file.

Server paths:

- Application jar: `/opt/nightout/nightout.jar`
- systemd service: `/etc/systemd/system/nightout.service`
- Runtime environment: `/etc/nightout/nightout.env`

Useful server commands:

```sh
sudo systemctl status nightout
sudo journalctl -u nightout -f
sudo systemctl restart nightout
```

The app reads runtime settings from `/etc/nightout/nightout.env` in production. Keep passwords and tokens out of git.

Required production values:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver`
- `SPRING_JPA_DATABASE_PLATFORM=org.hibernate.dialect.PostgreSQLDialect`
- `NIGHTOUT_SCHEMA_VERSION`
- `NIGHTOUT_SCHEMA_RESET_ALLOWED=false`
- `NIGHTOUT_BASE_URL`
- `NIGHTOUT_STORAGE_PROVIDER=9drive`
- `NIGHTOUT_9DRIVE_BASE_URL`
- `NIGHTOUT_9DRIVE_API_KEY`
- `NIGHTOUT_9DRIVE_EMAIL`
- `NIGHTOUT_9DRIVE_PASSWORD`

The 9Drive API key is used for `POST /api/v1/uploads`. The email/password are used server-side for streaming `/files/{id}/download` and permanent cleanup through the 9Drive file endpoints.

If `NIGHTOUT_SCHEMA_VERSION` changes and `NIGHTOUT_SCHEMA_RESET_ALLOWED=true`, app startup drops and rebuilds the configured Flyway schema. Leave reset allowed as `false` unless you intentionally want to erase the current database tables.

For local runs from the project root, Spring imports `.env` automatically. Keep `.env` uncommitted; use `.env.example` as the shape of the file.
