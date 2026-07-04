#!/usr/bin/env bash

set -Eeuo pipefail

RELEASE_ROOT="/instance/steelx"
FRONTEND_ROOT="/instance/steelx/frontend"
BACKEND_PORT="57217"
FRONTEND_PORT="443"
HTTP_REDIRECT_PORT="80"
SERVER_NAME="in1ove.com"
SSL_CERTIFICATE="/instance/ssl/fullchain.crt"
SSL_CERTIFICATE_KEY="/instance/ssl/privkey.key"
NGINX_CONF_DIR="/etc/nginx/conf.d"
NGINX_CONF_NAME="steelx.conf"
NGINX_WORKER_USER="sakura"
NGINX_WORKER_GROUP="sakura"

usage() {
  cat <<'EOF'
用法:
  bash scripts/deploy/install-steelx-nginx-config.sh [选项]

选项:
  --release-root <dir>              SteelX 发布根目录，默认 /instance/steelx
  --frontend-root <dir>             SteelX 前端根目录，默认 /instance/steelx/frontend
  --backend-port <port>             后端端口，默认 57217
  --frontend-port <port>            HTTPS 监听端口，默认 443
  --http-redirect-port <port>       HTTP 跳转端口，默认 80
  --server-name <name>              服务域名，默认 in1ove.com
  --ssl-certificate <path>          TLS 证书链，默认 /instance/ssl/fullchain.crt
  --ssl-certificate-key <path>      TLS 私钥，默认 /instance/ssl/privkey.key
  --nginx-conf-dir <dir>            Nginx conf.d 目录，默认 /etc/nginx/conf.d
  --nginx-conf-name <name>          Nginx 配置文件名，默认 steelx.conf
  --nginx-worker-user <user>        Nginx worker 用户，默认 sakura
  --nginx-worker-group <group>      Nginx worker 用户组，默认 sakura
  -h, --help                        查看帮助
EOF
}

log() {
  printf '[steelx-nginx] %s\n' "$*" >&2
}

fail() {
  printf '[steelx-nginx] ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    fail "缺少命令: $command_name"
  fi
}

run_as_root() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
    return
  fi
  if ! command -v sudo >/dev/null 2>&1; then
    fail "当前用户不是 root，且缺少 sudo，无法安装 Nginx 配置"
  fi
  if [[ -n "${STEELX_SUDO_PASSWORD:-}" ]]; then
    printf '%s\n' "$STEELX_SUDO_PASSWORD" | sudo -S "$@"
    return
  fi
  sudo -n "$@"
}

is_port() {
  [[ "$1" =~ ^[0-9]+$ ]] && (( "$1" >= 1 && "$1" <= 65535 ))
}

require_absolute_path() {
  local value="$1"
  local label="$2"
  [[ "$value" == /* ]] || fail "$label 必须是绝对路径: $value"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-root) RELEASE_ROOT="$2"; shift 2 ;;
    --frontend-root) FRONTEND_ROOT="$2"; shift 2 ;;
    --backend-port) BACKEND_PORT="$2"; shift 2 ;;
    --frontend-port) FRONTEND_PORT="$2"; shift 2 ;;
    --http-redirect-port) HTTP_REDIRECT_PORT="$2"; shift 2 ;;
    --server-name) SERVER_NAME="$2"; shift 2 ;;
    --ssl-certificate) SSL_CERTIFICATE="$2"; shift 2 ;;
    --ssl-certificate-key) SSL_CERTIFICATE_KEY="$2"; shift 2 ;;
    --nginx-conf-dir) NGINX_CONF_DIR="$2"; shift 2 ;;
    --nginx-conf-name) NGINX_CONF_NAME="$2"; shift 2 ;;
    --nginx-worker-user) NGINX_WORKER_USER="$2"; shift 2 ;;
    --nginx-worker-group) NGINX_WORKER_GROUP="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; fail "未知参数: $1" ;;
  esac
done

require_command date
require_command install
require_command mktemp
require_command nginx

is_port "$BACKEND_PORT" || fail "--backend-port 必须是 1-65535 的端口号"
is_port "$FRONTEND_PORT" || fail "--frontend-port 必须是 1-65535 的端口号"
is_port "$HTTP_REDIRECT_PORT" || fail "--http-redirect-port 必须是 1-65535 的端口号"
[[ "$SERVER_NAME" =~ ^[A-Za-z0-9._-]+$ ]] || fail "--server-name 包含非法字符: $SERVER_NAME"
[[ "$NGINX_CONF_NAME" =~ ^[A-Za-z0-9._-]+\.conf$ ]] || fail "--nginx-conf-name 必须是 .conf 文件名"
[[ -n "$NGINX_WORKER_USER" ]] || fail "--nginx-worker-user 不能为空"
[[ -n "$NGINX_WORKER_GROUP" ]] || fail "--nginx-worker-group 不能为空"

require_absolute_path "$RELEASE_ROOT" "--release-root"
require_absolute_path "$FRONTEND_ROOT" "--frontend-root"
require_absolute_path "$SSL_CERTIFICATE" "--ssl-certificate"
require_absolute_path "$SSL_CERTIFICATE_KEY" "--ssl-certificate-key"
require_absolute_path "$NGINX_CONF_DIR" "--nginx-conf-dir"

[[ -f "$SSL_CERTIFICATE" ]] || fail "TLS 证书链不存在: $SSL_CERTIFICATE"
[[ -f "$SSL_CERTIFICATE_KEY" ]] || fail "TLS 私钥不存在: $SSL_CERTIFICATE_KEY"

shared_conf="$RELEASE_ROOT/shared/nginx-steelx.conf"
target_conf="$NGINX_CONF_DIR/$NGINX_CONF_NAME"
client_body_temp_path="$FRONTEND_ROOT/nginx-client-body"
tmp_conf="$(mktemp)"
backup_conf=""

cleanup() {
  rm -f -- "$tmp_conf"
}
trap cleanup EXIT

restore_nginx_config() {
  if [[ -n "$backup_conf" ]]; then
    log "恢复 Nginx 配置备份: $backup_conf"
    run_as_root cp -a "$backup_conf" "$target_conf"
    return
  fi
  log "删除本次新安装的 Nginx 配置: $target_conf"
  run_as_root rm -f -- "$target_conf"
}

run_as_root mkdir -p "$(dirname "$shared_conf")"
run_as_root mkdir -p "$client_body_temp_path"
run_as_root chown "$NGINX_WORKER_USER:$NGINX_WORKER_GROUP" "$client_body_temp_path"
run_as_root chmod 700 "$client_body_temp_path"

cat > "$tmp_conf" <<EOF
server {
    listen $HTTP_REDIRECT_PORT;
    server_name $SERVER_NAME;

    return 301 https://$SERVER_NAME\$request_uri;
}

server {
    listen $FRONTEND_PORT ssl;
    http2 on;
    server_name $SERVER_NAME;

    ssl_certificate $SSL_CERTIFICATE;
    ssl_certificate_key $SSL_CERTIFICATE_KEY;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_session_cache shared:STEELXSSL:10m;
    ssl_session_timeout 10m;

    root $FRONTEND_ROOT/current;
    index index.html;

    client_max_body_size 25m;
    client_body_temp_path $client_body_temp_path 1 2;
    client_body_buffer_size 64k;

    location /assets/ {
        try_files \$uri =404;
        expires 30d;
        add_header Cache-Control "public, max-age=2592000, immutable";
    }

    location /api/ {
        proxy_pass http://127.0.0.1:$BACKEND_PORT/api/;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header X-Forwarded-Port \$server_port;
        proxy_read_timeout 120s;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF

run_as_root install -m 0644 "$tmp_conf" "$shared_conf"

if run_as_root test -f "$target_conf"; then
  backup_conf="$target_conf.bak.$(date +%Y%m%d%H%M%S)"
  log "备份当前 Nginx 配置到 $backup_conf"
  run_as_root cp -a "$target_conf" "$backup_conf"
fi

log "安装 SteelX Nginx 配置: $target_conf"
run_as_root install -m 0644 "$shared_conf" "$target_conf"

if ! run_as_root nginx -t; then
  restore_nginx_config
  run_as_root nginx -t || true
  fail "nginx -t 校验失败，已尝试恢复上一版配置"
fi

if ! run_as_root nginx -s reload; then
  restore_nginx_config
  run_as_root nginx -t && run_as_root nginx -s reload || true
  fail "Nginx reload 失败，已尝试恢复上一版配置"
fi

log "SteelX Nginx 配置已安装并 reload"
