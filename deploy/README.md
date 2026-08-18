# CrowdCam Deployment

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
- `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_LOGTO_ISSUER_URI=https://auth.crowdcam.co.za/oidc`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_LOGTO_CLIENT_ID`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_LOGTO_CLIENT_SECRET`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_LOGTO_SCOPE=openid,profile,email,roles`
- `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_LOGTO_REDIRECT_URI={baseUrl}/login/oauth2/code/{registrationId}`
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://auth.crowdcam.co.za/oidc`
- `NIGHTOUT_MOBILE_API_AUDIENCE=https://crowdcam.co.za/api`
- `SERVER_PORT=8090`
- `NIGHTOUT_IMAGE_OPTIMIZATION_ENABLED=true`
- `NIGHTOUT_IMAGE_OPTIMIZATION_MAX_DIMENSION=1080`
- `NIGHTOUT_IMAGE_OPTIMIZATION_JPEG_QUALITY=0.82`
- `NIGHTOUT_IMAGE_OPTIMIZATION_DELETE_ORIGINAL=true`
- `NIGHTOUT_IMAGE_OPTIMIZATION_BATCH_SIZE=10`
- `NIGHTOUT_STORAGE_PROVIDER=s3`
- `NIGHTOUT_S3_ENDPOINT=http://127.0.0.1:8080`
- `NIGHTOUT_S3_BUCKET=nightout`
- `NIGHTOUT_S3_REGION=us-east-1`
- `NIGHTOUT_S3_ACCESS_KEY`
- `NIGHTOUT_S3_SECRET_KEY`
- `NIGHTOUT_S3_PATH_STYLE=true`
- `RCLONE_REMOTE=drive_primary:` or `RCLONE_REMOTE=nightout_union:`

CrowdCam talks to `rclone serve s3` with the AWS SDK for Java. Rclone should listen on `127.0.0.1:8080` only, require `--auth-key`, and use Google Drive as the backend. Nginx proxies public traffic to CrowdCam on `127.0.0.1:8090`; it must not expose rclone publicly. See `deploy/rclone-s3.service.example` for a systemd unit shape.

New uploads store S3 object keys in `photos.storage_file_id`. Existing rows from older storage providers are not migrated automatically.

If `NIGHTOUT_SCHEMA_VERSION` changes and `NIGHTOUT_SCHEMA_RESET_ALLOWED=true`, app startup drops and rebuilds the configured Flyway schema. Leave reset allowed as `false` unless you intentionally want to erase the current database tables.

For local runs from the project root, Spring imports `.env` automatically. Keep `.env` uncommitted; use `.env.example` as the shape of the file.

## Logto

CrowdCam uses Logto for every authenticated browser flow. Local email/password login is intentionally disabled.

Create one Logto Traditional Web app named `CrowdCam Web` with:

- Redirect URI: `https://crowdcam.co.za/login/oauth2/code/logto`
- Local redirect URI: `http://localhost:8090/login/oauth2/code/logto`
- Post sign-out redirect URI: `https://crowdcam.co.za/`
- Local post sign-out redirect URI: `http://localhost:8090/`

Create Logto roles named exactly `super_admin`, `club_owner`, and `user`. Existing CrowdCam admins and club owners keep their app-side assignments by signing in with the same email address already stored in `app_users`; CrowdCam links that row to the Logto subject on first login.

See `deploy/rclone-s3.md` for rclone remote examples, optional union storage, and the `rclone serve s3` command.
