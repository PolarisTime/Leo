#!/usr/bin/env bash

set -euo pipefail

ARCHIVE=""
SHA256_FILE=""
RELEASE_ROOT="/opt/leo"
FRONTEND_ROOT="/var/www/leo"
BACKEND_SERVICE="leo-backend"
HEALTHCHECK_URL="http://127.0.0.1:11211/api/auth/ping"
KEEP_RELEASES=5
START_COMMAND=""
STOP_COMMAND=""

usage() {
  cat <<'EOF'
用法:
  sudo bash install-production-release.sh \
    --archive /tmp/leo-production-release.tar.gz \
    [--sha256-file /tmp/leo-production-release.tar.gz.sha256] \
    [--release-root /opt/leo] \
    [--frontend-root /var/www/leo] \
    [--backend-service leo-backend] \
    [--healthcheck-url http://127.0.0.1:11211/api/auth/ping] \
    [--keep-releases 5] \
    [--start-command <command>] \
    [--stop-command <command>]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --archive) ARCHIVE="$2"; shift 2 ;;
    --sha256-file) SHA256_FILE="$2"; shift 2 ;;
    --release-root) RELEASE_ROOT="$2"; shift 2 ;;
    --frontend-root) FRONTEND_ROOT="$2"; shift 2 ;;
    --backend-service) BACKEND_SERVICE="$2"; shift 2 ;;
    --healthcheck-url) HEALTHCHECK_URL="$2"; shift 2 ;;
    --keep-releases) KEEP_RELEASES="$2"; shift 2 ;;
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
require_command sha256sum
require_command tar

if [[ -z "$START_COMMAND" ]]; then
  require_command systemctl
fi

if [[ -z "$ARCHIVE" || ! -f "$ARCHIVE" ]]; then
  echo "发布包不存在: ${ARCHIVE:-<empty>}" >&2
  exit 1
fi
if [[ -n "$SHA256_FILE" && ! -f "$SHA256_FILE" ]]; then
  echo "校验和文件不存在: $SHA256_FILE" >&2
  exit 1
fi

if [[ ! "$KEEP_RELEASES" =~ ^[0-9]+$ || "$KEEP_RELEASES" -lt 2 ]]; then
  echo "--keep-releases 必须是 >= 2 的整数" >&2
  exit 1
fi

timestamp="$(date +%Y%m%d%H%M%S)"
archive_name="$(basename "$ARCHIVE")"
release_id="${timestamp}-${archive_name%.tar.gz}"
releases_dir="$RELEASE_ROOT/releases"
current_link="$RELEASE_ROOT/current"
previous_link="$RELEASE_ROOT/previous"
shared_dir="$RELEASE_ROOT/shared"
release_dir="$releases_dir/$release_id"
frontend_releases_dir="$FRONTEND_ROOT/releases"
frontend_current_link="$FRONTEND_ROOT/current"
frontend_release_dir="$frontend_releases_dir/$release_id"

mkdir -p "$RELEASE_ROOT" "$releases_dir" "$shared_dir" "$FRONTEND_ROOT" "$frontend_releases_dir"
lock_file="$RELEASE_ROOT/deploy.lock"
exec 9>"$lock_file"
if ! flock -n 9; then
  echo "已有生产发布正在执行，拒绝并发发布" >&2
  exit 1
fi

if [[ -n "$SHA256_FILE" ]]; then
  archive_dir="$(dirname "$ARCHIVE")"
  sha_name="$(basename "$SHA256_FILE")"
  echo "校验发布包 SHA256 ..."
  (cd "$archive_dir" && sha256sum -c "$sha_name")
fi

old_backend_target=""
old_frontend_target=""
if [[ -L "$current_link" ]]; then
  old_backend_target="$(readlink -f "$current_link")"
fi
if [[ -L "$frontend_current_link" ]]; then
  old_frontend_target="$(readlink -f "$frontend_current_link")"
fi

if [[ -e "$current_link" && ! -L "$current_link" ]]; then
  echo "$current_link 已存在但不是软链，拒绝发布" >&2
  exit 1
fi
if [[ -e "$frontend_current_link" && ! -L "$frontend_current_link" ]]; then
  echo "$frontend_current_link 已存在但不是软链，拒绝发布" >&2
  exit 1
fi

rollback() {
  local reason="$1"
  echo "发布失败，开始回滚: $reason" >&2
  if [[ -n "$old_backend_target" && -d "$old_backend_target" ]]; then
    ln -sfn "$old_backend_target" "$current_link"
    start_backend || true
  fi
  if [[ -n "$old_frontend_target" && -d "$old_frontend_target" ]]; then
    ln -sfn "$old_frontend_target" "$frontend_current_link"
  fi
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

run_hook() {
  local hook_name="$1"
  local hook_path="$shared_dir/$hook_name"
  if [[ -x "$hook_path" ]]; then
    "$hook_path" "$release_dir" "$release_id"
  fi
}

prune_old_releases() {
  local base_dir="$1"
  local current_target="$2"
  local previous_target="$3"
  [[ -d "$base_dir" ]] || return 0

  mapfile -t release_dirs < <(find "$base_dir" -mindepth 1 -maxdepth 1 -type d -printf '%T@ %p\n' | sort -rn | awk '{print $2}')
  local index=0
  for dir in "${release_dirs[@]}"; do
    index=$((index + 1))
    if (( index <= KEEP_RELEASES )); then
      continue
    fi
    if [[ "$dir" == "$current_target" || "$dir" == "$previous_target" ]]; then
      continue
    fi
    rm -rf -- "$dir"
  done
}

echo "准备发布: $release_id"
mkdir -p "$release_dir" "$frontend_release_dir"
tar -xzf "$ARCHIVE" -C "$release_dir"

if [[ ! -f "$release_dir/backend/leo.jar" ]]; then
  echo "发布包缺少 backend/leo.jar" >&2
  exit 1
fi
if [[ ! -f "$release_dir/frontend/index.html" ]]; then
  echo "发布包缺少 frontend/index.html" >&2
  exit 1
fi

cp -a "$release_dir/frontend/." "$frontend_release_dir/"
run_hook "pre-deploy.sh"

if [[ -n "$old_backend_target" ]]; then
  ln -sfn "$old_backend_target" "$previous_link"
fi

ln -sfn "$release_dir" "$current_link"
if [[ -z "$START_COMMAND" ]]; then
  systemctl daemon-reload
else
  stop_backend
fi
start_backend

if ! healthcheck 120; then
  rollback "后端健康检查未通过: $HEALTHCHECK_URL"
  exit 1
fi

ln -sfn "$frontend_release_dir" "$frontend_current_link"
run_hook "post-deploy.sh"

prune_old_releases "$releases_dir" "$(readlink -f "$current_link")" "$(readlink -f "$previous_link" 2>/dev/null || true)"
prune_old_releases "$frontend_releases_dir" "$(readlink -f "$frontend_current_link")" "$old_frontend_target"

rm -f -- "$ARCHIVE" "$SHA256_FILE"
rmdir "$(dirname "$ARCHIVE")" 2>/dev/null || true

echo "发布完成: $release_id"
