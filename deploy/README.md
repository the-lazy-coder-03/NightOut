# Nightout Deployment

GitHub Actions builds the Spring Boot jar on pushes to `master` or `main`, then deploys it to the EC2 host at `3.8.201.115` when the `EC2_SSH_KEY` repository secret is configured.

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

The starter app currently has JDBC dependencies but no database settings. The default environment file disables JDBC auto-configuration so the service can boot; when a database is added, set the real `SPRING_DATASOURCE_*` values in `/etc/nightout/nightout.env` and remove `SPRING_AUTOCONFIGURE_EXCLUDE`.
