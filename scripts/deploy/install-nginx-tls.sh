#!/usr/bin/env bash
# 운영 EC2에서 Nginx reverse proxy와 Let's Encrypt TLS 인증서를 구성한다.
# 인증서 private key는 certbot이 서버 로컬에만 저장하며, 스크립트는 secret 값을 출력하지 않는다.
set -euo pipefail

DOMAIN="${DOMAIN:-portal.observstarter.cloud}"
EMAIL="${LETSENCRYPT_EMAIL:-}"
NGINX_CONF_SOURCE="${NGINX_CONF_SOURCE:-deploy/nginx/observation.conf}"
NGINX_CONF_TARGET="${NGINX_CONF_TARGET:-/etc/nginx/conf.d/observation.conf}"
ACME_ROOT="${ACME_ROOT:-/var/www/certbot}"

if [[ ! -f "$NGINX_CONF_SOURCE" ]]; then
  echo "Nginx config source not found: $NGINX_CONF_SOURCE" >&2
  exit 1
fi

dnf install -y nginx certbot
install -d -o nginx -g nginx -m 0755 "$ACME_ROOT"

cat > "$NGINX_CONF_TARGET" <<BOOTSTRAP
server {
    listen 80;
    server_name ${DOMAIN};

    location /.well-known/acme-challenge/ {
        root ${ACME_ROOT};
    }

    location / {
        return 200 "observation cert bootstrap\\n";
        add_header Content-Type text/plain;
    }
}
BOOTSTRAP

nginx -t
systemctl enable --now nginx
systemctl reload nginx

certbot_args=(
  certonly
  --webroot
  --webroot-path "$ACME_ROOT"
  --domain "$DOMAIN"
  --agree-tos
  --non-interactive
  --keep-until-expiring
)

if [[ -n "$EMAIL" ]]; then
  certbot_args+=(--email "$EMAIL" --no-eff-email)
else
  certbot_args+=(--register-unsafely-without-email)
fi

certbot "${certbot_args[@]}"

install -o root -g root -m 0644 "$NGINX_CONF_SOURCE" "$NGINX_CONF_TARGET"
nginx -t
systemctl reload nginx
if systemctl list-unit-files certbot-renew.timer --no-legend 2>/dev/null | grep -q '^certbot-renew\.timer'; then
  systemctl enable --now certbot-renew.timer
elif systemctl list-unit-files certbot.timer --no-legend 2>/dev/null | grep -q '^certbot\.timer'; then
  systemctl enable --now certbot.timer
fi

curl -fsS -o /dev/null http://127.0.0.1:8080/internal/health/ready
curl -fsS -o /dev/null "https://${DOMAIN}/internal/health/ready" \
  --resolve "${DOMAIN}:443:127.0.0.1"
