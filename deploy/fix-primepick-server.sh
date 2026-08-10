#!/usr/bin/env bash
set -u

backup_dir="/root/nginx-backup-$(date +%Y%m%d%H%M%S)"

echo "Backing up current Nginx site config to ${backup_dir}"
sudo mkdir -p "${backup_dir}"
sudo cp -a /etc/nginx/sites-available "${backup_dir}/"
sudo cp -a /etc/nginx/sites-enabled "${backup_dir}/"

if [ ! -f /etc/letsencrypt/live/primepick.co.za/fullchain.pem ]; then
  echo "Creating Let's Encrypt certificate for primepick.co.za and www.primepick.co.za"
  sudo certbot certonly --nginx --non-interactive --agree-tos --register-unsafely-without-email \
    -d primepick.co.za -d www.primepick.co.za
fi

if [ ! -f /etc/letsencrypt/live/drive.primepick.co.za/fullchain.pem ]; then
  echo "Missing /etc/letsencrypt/live/drive.primepick.co.za/fullchain.pem"
  echo "Create that certificate first or adjust /etc/nginx/sites-available/primepick.conf."
  exit 1
fi

cat >/tmp/primepick.conf <<'NGINX'
server {
    listen 80;
    listen [::]:80;
    server_name primepick.co.za www.primepick.co.za drive.primepick.co.za;

    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name primepick.co.za www.primepick.co.za;

    ssl_certificate /etc/letsencrypt/live/primepick.co.za/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/primepick.co.za/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    client_max_body_size 50m;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port 443;
        proxy_redirect off;
    }
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name drive.primepick.co.za;

    ssl_certificate /etc/letsencrypt/live/drive.primepick.co.za/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/drive.primepick.co.za/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    client_max_body_size 5g;

    location ~ ^/(api|public|auth|api-keys|provider-configs|connected-accounts|storage|uploads|files|folders|invites|audit-logs|system)(/|$) {
        proxy_pass http://127.0.0.1:4000;
        proxy_http_version 1.1;
        proxy_request_buffering off;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port 443;
    }

    location / {
        proxy_pass http://127.0.0.1:5173;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_set_header X-Forwarded-Port 443;
    }
}
NGINX

sudo install -o root -g root -m 0644 /tmp/primepick.conf /etc/nginx/sites-available/primepick.conf
sudo rm -f /etc/nginx/sites-enabled/default /etc/nginx/sites-enabled/nightout /etc/nginx/sites-enabled/9drive
sudo ln -sf /etc/nginx/sites-available/primepick.conf /etc/nginx/sites-enabled/primepick.conf

if [ -f /etc/nightout/nightout.env ]; then
  set_env() {
    key="$1"
    value="$2"
    if sudo grep -q "^${key}=" /etc/nightout/nightout.env; then
      sudo sed -i "s|^${key}=.*|${key}=${value}|" /etc/nightout/nightout.env
    else
      printf '%s=%s\n' "${key}" "${value}" | sudo tee -a /etc/nightout/nightout.env >/dev/null
    fi
  }

  set_env SERVER_PORT 8080
  set_env NIGHTOUT_BASE_URL https://primepick.co.za
  set_env NIGHTOUT_9DRIVE_BASE_URL https://drive.primepick.co.za
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
sudo systemctl status nightout --no-pager -l | sed -n '1,80p'

echo
echo "Recent NightOut logs:"
sudo journalctl -u nightout -n 120 --no-pager -o cat

echo
echo "Local port check:"
sudo ss -lntp | grep -E ':(80|443|4000|5173|8080)\b' || true

echo
echo "External checks to run from your laptop:"
echo "curl -I https://primepick.co.za"
echo "curl -I https://drive.primepick.co.za"
