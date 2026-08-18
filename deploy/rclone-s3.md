# Rclone S3 Gateway

CrowdCam uses the AWS SDK for Java S3 client, but the S3 endpoint is local `rclone serve s3`. Rclone is responsible for writing objects into Google Drive.

Do not expose this S3 gateway to the public internet. Bind it to `127.0.0.1`, require `--auth-key`, and let only the local Spring Boot process talk to it.

## App Environment

```properties
SERVER_PORT=8090
NIGHTOUT_STORAGE_PROVIDER=s3
NIGHTOUT_S3_ENDPOINT=http://127.0.0.1:8080
NIGHTOUT_S3_BUCKET=nightout
NIGHTOUT_S3_REGION=us-east-1
NIGHTOUT_S3_ACCESS_KEY=replace-with-random-access-key
NIGHTOUT_S3_SECRET_KEY=replace-with-random-secret-key
NIGHTOUT_S3_PATH_STYLE=true
RCLONE_REMOTE=drive_primary:
```

Use real random access and secret values in `/etc/nightout/nightout.env`. Use URL-safe values without commas because rclone separates the access key and secret key with a comma. Use the same values in `RCLONE_AUTH_KEY` when starting rclone from a shell:

```sh
export RCLONE_AUTH_KEY="\"${NIGHTOUT_S3_ACCESS_KEY},${NIGHTOUT_S3_SECRET_KEY}\""
```

## Single Google Drive Remote

Configure a remote interactively:

```sh
rclone config
```

Create the bucket directory under the remote:

```sh
rclone mkdir drive_primary:nightout
```

Run the S3 gateway on localhost only:

```sh
export RCLONE_AUTH_KEY="\"${NIGHTOUT_S3_ACCESS_KEY},${NIGHTOUT_S3_SECRET_KEY}\""
rclone serve s3 drive_primary: --addr 127.0.0.1:8080 --vfs-cache-mode writes
```

For systemd, adapt `deploy/rclone-s3.service.example`, install it as `/etc/systemd/system/rclone-s3.service`, and set `RCLONE_REMOTE=drive_primary:` in `/etc/nightout/nightout.env`.

## Optional Union Remote

Use a union remote when multiple Google Drive accounts should back the same bucket namespace. `create_policy=mfs` writes new objects to the upstream with the most free space.

Example config shape:

```ini
[drive_primary]
type = drive
scope = drive

[drive_secondary]
type = drive
scope = drive

[nightout_union]
type = union
upstreams = drive_primary:CrowdCam drive_secondary:CrowdCam
create_policy = mfs
```

Create the bucket directory and serve the union:

```sh
rclone mkdir nightout_union:nightout
export RCLONE_AUTH_KEY="\"${NIGHTOUT_S3_ACCESS_KEY},${NIGHTOUT_S3_SECRET_KEY}\""
rclone serve s3 nightout_union: --addr 127.0.0.1:8080 --vfs-cache-mode writes
```

For systemd with the union backend, set `RCLONE_REMOTE=nightout_union:`.

## Deployment Checklist

1. Install rclone on the server.
2. Configure Google Drive remotes with `rclone config`.
3. Store the rclone config at `/etc/rclone/rclone.conf`, owned by the `nightout` user with mode `0600`.
4. Put `NIGHTOUT_S3_ACCESS_KEY`, `NIGHTOUT_S3_SECRET_KEY`, and `RCLONE_REMOTE` in `/etc/nightout/nightout.env`.
5. Start `rclone-s3.service` and verify `curl http://127.0.0.1:8080/` does not allow anonymous access.
6. Start CrowdCam with `NIGHTOUT_STORAGE_PROVIDER=s3`.

Do not bind rclone to `0.0.0.0`, do not proxy it through Nginx, and do not run the S3 gateway anonymously.
