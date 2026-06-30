#!/usr/bin/env bash

set -euo pipefail

TARGET_RELEASE="previous"
RELEASE_ROOT="/opt/leo"
FRONTEND_ROOT="/var/www/leo"
BACKEND_SERVICE="leo-backend"
HEALTHCHECK_URL="http://127.0.0.1:11211/api/auth/ping"
START_COMMAND=""
STOP_COMMAND=""

usage() {
  cat <<'EOF'
用法:
  sudo bash rollback-production-release.sh \
    [--target-release previous|<release-id>] \
    [--release-root /opt/leo] \
    [--frontend-root /var/www/leo] \
    [--backend-service leo-backend] \
    [--healthcheck-url http://127.0.0.1:11211/api/auth/ping] \
    [--start-command <command>] \
    [--stop-command <command>]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-release) TARGET_RELEASE="$2"; shift 2 ;;
    --release-root) RELEASE_ROOT="$2"; shift 2 ;;
    --frontend-root) FRONTEND_ROOT="$2"; shift 2 ;;
    --backend-service) BACKEND_SERVICE="$2"; shift 2 ;;
    --healthcheck-url) HEALTHCHECK_URL="$2"; shift 2 ;;
    --start-command) START_COMMAND="$2"; shift 2 ;;
    --stop-command) STOP_COMMAND="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 1 ;;
  esac
done

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "缺少命令: $command_name" >&2
    exit 1
  fi
}

require_command curl
require_command flock
if [[ -z "$START_COMMAND" ]]; then
  require_command systemctl
fi

releases_dir="$RELEASE_ROOT/releases"
current_link="$RELEASE_ROOT/current"
previous_link="$RELEASE_ROOT/previous"
shared_dir="$RELEASE_ROOT/shared"
frontend_releases_dir="$FRONTEND_ROOT/releases"
frontend_current_link="$FRONTEND_ROOT/current"

lock_file="$RELEASE_ROOT/deploy.lock"
exec 9>"$lock_file"
if ! flock -n 9; then
  echo "已有生产发布或回滚正在执行，拒绝并发操作" >&2
  exit 1
fi

if [[ ! -L "$current_link" ]]; then
  echo "当前后端软链不存在: $current_link" >&2
  exit 1
fi

current_target="$(readlink -f "$current_link")"
if [[ "$TARGET_RELEASE" == "previous" ]]; then
  if [[ ! -L "$previous_link" ]]; then
    echo "previous 软链不存在，无法回滚" >&2
    exit 1
  fi
  target_backend="$(readlink -f "$previous_link")"
else
  target_backend="$releases_dir/$TARGET_RELEASE"
fi

if [[ ! -d "$target_backend" || ! -f "$target_backend/backend/leo.jar" ]]; then
  echo "目标后端 release 不存在或不完整: $target_backend" >&2
  exit 1
fi

release_id="$(basename "$target_backend")"
target_frontend="$frontend_releases_dir/$release_id"
if [[ ! -d "$target_frontend" || ! -f "$target_frontend/index.html" ]]; then
  echo "目标前端 release 不存在或不完整: $target_frontend" >&2
  exit 1
fi

old_frontend_target=""
if [[ -L "$frontend_current_link" ]]; then
  old_frontend_target="$(readlink -f "$frontend_current_link")"
fi

healthcheck() {
  local timeout_seconds="${1:-90}"
  local start_seconds
  start_seconds="$(date +%s)"
  while true; do
    if curl -fsS "$HEALTHCHECK_URL" >/dev/null; then
      return 0
    fi
    if (( "$(date +%s)" - start_seconds >= timeout_seconds )); then
      return 1
    fi
    sleep 3
  done
}

start_backend() {
  if [[ -n "$START_COMMAND" ]]; then
    bash -lc "$START_COMMAND" 9>&-
  else
    systemctl restart "$BACKEND_SERVICE"
  fi
}

stop_backend() {
  if [[ -n "$STOP_COMMAND" ]]; then
    bash -lc "$STOP_COMMAND" 9>&-
  fi
}

run_hook() {
  local hook_name="$1"
  local hook_path="$shared_dir/$hook_name"
  if [[ -x "$hook_path" ]]; then
    "$hook_path" "$target_backend" "$release_id"
  fi
}

echo "回滚到 release: $release_id"
run_hook "pre-rollback.sh"

ln -sfn "$current_target" "$previous_link"
ln -sfn "$target_backend" "$current_link"
stop_backend
start_backend

if ! healthcheck 120; then
  echo "回滚后健康检查失败，恢复回滚前版本" >&2
  ln -sfn "$current_target" "$current_link"
  start_backend || true
  if [[ -n "$old_frontend_target" ]]; then
    ln -sfn "$old_frontend_target" "$frontend_current_link"
  fi
  exit 1
fi

ln -sfn "$target_frontend" "$frontend_current_link"
run_hook "post-rollback.sh"

echo "回滚完成: $release_id"
