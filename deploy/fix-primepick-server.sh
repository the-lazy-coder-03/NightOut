#!/usr/bin/env bash
set -euo pipefail

backup_dir="/root/nginx-backup-$(date +%Y%m%d%H%M%S)"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
site_config="${1:-}"

if [ -z "${site_config}" ]; then
  if [ -f "${script_dir}/primepick-nginx.conf" ]; then
    site_config="${script_dir}/primepick-nginx.conf"
  elif [ -f /tmp/primepick-nginx.conf ]; then
    site_config="/tmp/primepick-nginx.conf"
  else
    echo "Could not find primepick-nginx.conf. Pass it as the first argument."
    exit 1
  fi
fi

if [ ! -f "${site_config}" ]; then
  echo "Nginx site config does not exist: ${site_config}"
  exit 1
fi

echo "Backing up current Nginx site config to ${backup_dir}"
sudo mkdir -p "${backup_dir}"
sudo cp -a /etc/nginx/sites-available "${backup_dir}/"
sudo cp -a /etc/nginx/sites-enabled "${backup_dir}/"

if ! command -v certbot >/dev/null 2>&1; then
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y certbot python3-certbot-nginx
fi

if [ ! -f /etc/letsencrypt/live/primepick.co.za/fullchain.pem ]; then
  echo "Creating Let's Encrypt certificate for primepick.co.za and www.primepick.co.za"
  sudo certbot certonly --nginx --non-interactive --agree-tos --register-unsafely-without-email \
    -d primepick.co.za -d www.primepick.co.za
fi

sudo install -o root -g root -m 0644 "${site_config}" /etc/nginx/sites-available/primepick.conf

for site in default nightout nightout.conf 9drive 9drive.conf; do
  enabled="/etc/nginx/sites-enabled/${site}"
  if [ -e "${enabled}" ] || [ -L "${enabled}" ]; then
    sudo mv "${enabled}" "${backup_dir}/disabled-${site}"
  fi
done

sudo ln -sf /etc/nginx/sites-available/primepick.conf /etc/nginx/sites-enabled/primepick.conf

if [ -f /etc/nightout/nightout.env ]; then
  get_env() {
    key="$1"
    sudo sed -n "s/^${key}=//p" /etc/nightout/nightout.env | tail -n 1
  }

  set_env() {
    key="$1"
    value="$2"
    if sudo grep -q "^${key}=" /etc/nightout/nightout.env; then
      sudo sed -i "s|^${key}=.*|${key}=${value}|" /etc/nightout/nightout.env
    else
      printf '%s=%s\n' "${key}" "${value}" | sudo tee -a /etc/nightout/nightout.env >/dev/null
    fi
  }

  ensure_secret() {
    key="$1"
    current_value="$(get_env "${key}")"
    case "${current_value}" in
      ""|change-me|replace-with-random-access-key|replace-with-random-secret-key)
        set_env "${key}" "$(python3 -c 'import secrets; print(secrets.token_urlsafe(32).replace(",", "_"))')"
        ;;
    esac
  }

  set_env_if_missing() {
    key="$1"
    value="$2"
    if [ -z "$(get_env "${key}")" ]; then
      set_env "${key}" "${value}"
    fi
  }

  set_env SERVER_PORT 8090
  set_env NIGHTOUT_BASE_URL https://primepick.co.za
  set_env NIGHTOUT_TIME_ZONE Africa/Johannesburg
  set_env NIGHTOUT_STORAGE_PROVIDER s3
  set_env NIGHTOUT_S3_ENDPOINT http://127.0.0.1:8080
  set_env NIGHTOUT_S3_BUCKET nightout
  set_env NIGHTOUT_S3_REGION us-east-1
  set_env NIGHTOUT_S3_PATH_STYLE true
  set_env_if_missing NIGHTOUT_IMAGE_OPTIMIZATION_ENABLED true
  set_env_if_missing NIGHTOUT_IMAGE_OPTIMIZATION_MAX_DIMENSION 1080
  set_env_if_missing NIGHTOUT_IMAGE_OPTIMIZATION_JPEG_QUALITY 0.82
  set_env_if_missing NIGHTOUT_IMAGE_OPTIMIZATION_DELETE_ORIGINAL true
  set_env_if_missing NIGHTOUT_IMAGE_OPTIMIZATION_BATCH_SIZE 10
  ensure_secret NIGHTOUT_S3_ACCESS_KEY
  ensure_secret NIGHTOUT_S3_SECRET_KEY
  set_env_if_missing RCLONE_REMOTE drive_primary:
fi

echo "Testing and reloading Nginx"
sudo nginx -t
sudo systemctl reload nginx

echo "Restarting NightOut"
if ! sudo systemctl restart nightout; then
  echo "NightOut restart returned a failure."
fi

echo
echo "NightOut status:"
sudo systemctl status nightout --no-pager -l | sed -n '1,80p' || true

echo
echo "Recent NightOut logs:"
sudo journalctl -u nightout -n 120 --no-pager -o cat || true

echo
echo "Local port check:"
sudo ss -lntp | grep -E ':(80|443|8080|8090)\b' || true

if ! command -v curl >/dev/null 2>&1; then
  sudo apt-get update
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y curl
fi

echo
echo "Waiting for NightOut to answer on http://127.0.0.1:8090"
nightout_ready=false
for _ in {1..30}; do
  if curl -fsS --max-time 3 http://127.0.0.1:8090/ >/dev/null; then
    nightout_ready=true
    break
  fi
  sleep 2
done

if [ "${nightout_ready}" != true ]; then
  echo "NightOut did not answer on port 8090."
  sudo systemctl status nightout --no-pager -l | sed -n '1,120p' || true
  sudo journalctl -u nightout -n 160 --no-pager -o cat || true
  exit 1
fi

echo
echo "External checks to run from your laptop:"
echo "curl -I https://primepick.co.za"
