# NightOut Load Tests

These tests use [k6](https://grafana.com/docs/k6/latest/) to generate HTTP traffic against NightOut.

Start with the read-only public test. It hits safe public pages and does not upload files:

```sh
docker run --rm \
  -e BASE_URL=https://primepick.co.za \
  -v "$PWD:/repo" \
  grafana/k6 run /repo/load-tests/k6/public-read.js
```

For a local app:

```sh
docker run --rm \
  -e BASE_URL=http://host.docker.internal:8090 \
  -v "$PWD:/repo" \
  grafana/k6 run /repo/load-tests/k6/public-read.js
```

Use the upload smoke test only against a test event, because it writes image files to storage:

```sh
docker run --rm \
  -e BASE_URL=https://primepick.co.za \
  -e UPLOAD_PATH=/clubs/halo/dates/2026-08-17/upload \
  -v "$PWD:/repo" \
  grafana/k6 run /repo/load-tests/k6/upload-smoke.js
```

Useful options:

```sh
docker run --rm \
  -e BASE_URL=https://primepick.co.za \
  -e STRESS_MAX_VUS=300 \
  -e STRESS_P95_MS=1500 \
  -v "$PWD:/repo" \
  grafana/k6 run /repo/load-tests/k6/public-read.js
```

Watch the server while a test runs:

```sh
ssh -i ~/Downloads/MacBookNight.pem ubuntu@13.41.33.158 'sudo journalctl -u nightout -f'
ssh -i ~/Downloads/MacBookNight.pem ubuntu@13.41.33.158 'sudo tail -f /var/log/nginx/access.log /var/log/nginx/error.log'
```
