#!/usr/bin/env bash

set -euo pipefail

ARCHIVE=""
SHA256_FILE=""
DEPENDENCY_ARCHIVE=""
DEPENDENCY_SHA256_FILE=""
DEPENDENCY_BUNDLE_ID=""
RELEASE_ROOT="/opt/leo"
BACKEND_SERVICE="leo-backend"
HEALTHCHECK_URL="http://127.0.0.1:57217/api/health"
KEEP_RELEASES=5
START_COMMAND=""
STOP_COMMAND=""
SHARED_DIR=""

usage() {
  cat <<'EOF'
用法:
  sudo bash install-production-release.sh \
    --archive /tmp/leo-production-release.tar.gz \
    [--sha256-file /tmp/leo-production-release.tar.gz.sha256] \
    [--dependency-bundle-id <sha256>] \
    [--dependency-archive /tmp/leo-dependencies-<sha256>.tar.gz] \
    [--dependency-sha256-file /tmp/leo-dependencies-<sha256>.tar.gz.sha256] \
    [--release-root /opt/leo] \
    [--backend-service leo-backend] \
    [--healthcheck-url http://127.0.0.1:57217/api/health] \
    [--keep-releases 5] \
    [--start-command <command>] \
    [--stop-command <command>] \
    [--shared-dir /opt/leo/shared]
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --archive) ARCHIVE="$2"; shift 2 ;;
    --sha256-file) SHA256_FILE="$2"; shift 2 ;;
    --dependency-bundle-id) DEPENDENCY_BUNDLE_ID="$2"; shift 2 ;;
    --dependency-archive) DEPENDENCY_ARCHIVE="$2"; shift 2 ;;
    --dependency-sha256-file) DEPENDENCY_SHA256_FILE="$2"; shift 2 ;;
    --release-root) RELEASE_ROOT="$2"; shift 2 ;;
    --backend-service) BACKEND_SERVICE="$2"; shift 2 ;;
    --healthcheck-url) HEALTHCHECK_URL="$2"; shift 2 ;;
    --keep-releases) KEEP_RELEASES="$2"; shift 2 ;;
    --start-command) START_COMMAND="$2"; shift 2 ;;
    --stop-command) STOP_COMMAND="$2"; shift 2 ;;
    --shared-dir) SHARED_DIR="$2"; shift 2 ;;
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
require_command install
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
if [[ -n "$DEPENDENCY_ARCHIVE" && ! -f "$DEPENDENCY_ARCHIVE" ]]; then
  echo "依赖 bundle 不存在: $DEPENDENCY_ARCHIVE" >&2
  exit 1
fi
if [[ -n "$DEPENDENCY_SHA256_FILE" && ! -f "$DEPENDENCY_SHA256_FILE" ]]; then
  echo "依赖 bundle 校验和文件不存在: $DEPENDENCY_SHA256_FILE" >&2
  exit 1
fi
if [[ -n "$DEPENDENCY_ARCHIVE" && -z "$DEPENDENCY_BUNDLE_ID" ]]; then
  echo "提供依赖 bundle 时必须同时提供 --dependency-bundle-id" >&2
  exit 1
fi
if [[ -n "$DEPENDENCY_BUNDLE_ID" && ! "$DEPENDENCY_BUNDLE_ID" =~ ^[0-9a-f]{64}$ ]]; then
  echo "--dependency-bundle-id 必须是 64 位小写 SHA-256" >&2
  exit 1
fi

if [[ ! "$KEEP_RELEASES" =~ ^[0-9]+$ || "$KEEP_RELEASES" -lt 2 ]]; then
  echo "--keep-releases 必须是 >= 2 的整数" >&2
  exit 1
fi
if [[ "$RELEASE_ROOT" == "/" ]]; then
  echo "拒绝使用根目录作为 release root" >&2
  exit 1
fi

mkdir -p "$RELEASE_ROOT"
RELEASE_ROOT="$(cd "$RELEASE_ROOT" && pwd -P)"

timestamp="$(date +%Y%m%d%H%M%S)"
archive_name="$(basename "$ARCHIVE")"
release_id="${timestamp}-${archive_name%.tar.gz}"
releases_dir="$RELEASE_ROOT/releases"
dependencies_dir="$RELEASE_ROOT/dependencies"
current_link="$RELEASE_ROOT/current"
previous_link="$RELEASE_ROOT/previous"
shared_dir="${SHARED_DIR:-$RELEASE_ROOT/shared}"
release_dir="$releases_dir/$release_id"

release_has_backend_jar() {
  local candidate_dir="$1"
  local backend_dir="$candidate_dir"
  if [[ ! -f "$backend_dir/leo.jar" && -f "$candidate_dir/backend/leo.jar" ]]; then
    backend_dir="$candidate_dir/backend"
  fi
  if [[ ! -f "$backend_dir/leo.jar" ]]; then
    return 1
  fi
  if [[ -f "$backend_dir/dependency-bundle.id" && ! -d "$backend_dir/lib" ]]; then
    return 1
  fi
  return 0
}

detect_legacy_current_target() {
  local release_root_basename
  release_root_basename="$(basename "$RELEASE_ROOT")"
  if [[ "$release_root_basename" != "backend" ]]; then
    return 0
  fi

  local legacy_current_link
  legacy_current_link="$(dirname "$RELEASE_ROOT")/current"
  if [[ ! -L "$legacy_current_link" ]]; then
    return 0
  fi

  local legacy_target
  if ! legacy_target="$(readlink -f "$legacy_current_link")"; then
    echo "检测到旧后端 current 但无法解析，忽略: $legacy_current_link" >&2
    return 0
  fi
  if release_has_backend_jar "$legacy_target"; then
    old_backend_target="$legacy_target"
    echo "检测到旧后端 current，作为首次迁移 previous: $legacy_current_link -> $legacy_target"
  else
    echo "检测到旧后端 current 但缺少 JAR，忽略: $legacy_current_link -> $legacy_target" >&2
  fi
}

mkdir -p "$RELEASE_ROOT" "$releases_dir" "$dependencies_dir" "$shared_dir"
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
if [[ -L "$current_link" ]]; then
  old_backend_target="$(readlink -f "$current_link")"
fi

if [[ -e "$current_link" && ! -L "$current_link" ]]; then
  echo "$current_link 已存在但不是软链，拒绝发布" >&2
  exit 1
fi

if [[ -z "$old_backend_target" ]]; then
  detect_legacy_current_target
fi

rollback() {
  local reason="$1"
  echo "发布失败，开始回滚: $reason" >&2
  if [[ -n "$old_backend_target" && -d "$old_backend_target" ]]; then
    ln -sfn "$old_backend_target" "$current_link"
    start_backend || true
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

verify_dependency_bundle() {
  local candidate_dir="$1"
  local expected_id="$2"
  local declared_id
  local actual_id

  if [[ ! -d "$candidate_dir/lib" \
    || ! -f "$candidate_dir/dependency-bundle.id" \
    || ! -f "$candidate_dir/dependency-bundle.sha256" ]]; then
    return 1
  fi

  declared_id="$(tr -d '\r\n' < "$candidate_dir/dependency-bundle.id")"
  actual_id="$(sha256sum "$candidate_dir/dependency-bundle.sha256" | awk '{print $1}')"
  if [[ "$declared_id" != "$expected_id" || "$actual_id" != "$expected_id" ]]; then
    return 1
  fi

  if ! awk '
    NF {
      path = $2
      sub(/^\*/, "", path)
      if (path !~ /^lib\/[A-Za-z0-9._+-]+\.jar$/ || path ~ /\.\./) exit 1
      count++
    }
    END { if (count == 0) exit 1 }
  ' "$candidate_dir/dependency-bundle.sha256"; then
    return 1
  fi

  (cd "$candidate_dir" && sha256sum --quiet -c dependency-bundle.sha256)
}

install_dependency_bundle() {
  local bundle_id="$1"
  local bundle_dir="$dependencies_dir/$bundle_id"
  local staging_dir
  local invalid_dir

  if verify_dependency_bundle "$bundle_dir" "$bundle_id"; then
    echo "复用已安装的依赖 bundle: $bundle_id"
    return 0
  fi

  if [[ -z "$DEPENDENCY_ARCHIVE" ]]; then
    echo "依赖 bundle 缺失或损坏，且未提供依赖归档: $bundle_id" >&2
    return 1
  fi

  if [[ -n "$DEPENDENCY_SHA256_FILE" ]]; then
    local expected_archive_sha256
    local actual_archive_sha256
    expected_archive_sha256="$(awk 'NF {print $1; exit}' "$DEPENDENCY_SHA256_FILE")"
    actual_archive_sha256="$(sha256sum "$DEPENDENCY_ARCHIVE" | awk '{print $1}')"
    if [[ ! "$expected_archive_sha256" =~ ^[0-9a-fA-F]{64}$ \
      || "${expected_archive_sha256,,}" != "$actual_archive_sha256" ]]; then
      echo "依赖 bundle 归档 SHA-256 校验失败" >&2
      return 1
    fi
  fi

  staging_dir="$(mktemp -d "$dependencies_dir/.dependency-$bundle_id.XXXXXX")"
  if ! tar -xzf "$DEPENDENCY_ARCHIVE" -C "$staging_dir"; then
    rm -rf -- "$staging_dir"
    return 1
  fi
  if ! verify_dependency_bundle "$staging_dir" "$bundle_id"; then
    echo "依赖 bundle 内容校验失败: $bundle_id" >&2
    rm -rf -- "$staging_dir"
    return 1
  fi

  if [[ -e "$bundle_dir" ]]; then
    invalid_dir="$dependencies_dir/${bundle_id}.invalid-$timestamp-$$"
    mv "$bundle_dir" "$invalid_dir"
    echo "已隔离损坏的依赖 bundle: $invalid_dir" >&2
  fi
  mv "$staging_dir" "$bundle_dir"
  echo "已安装依赖 bundle: $bundle_id"
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
mkdir -p "$release_dir"
tar -xzf "$ARCHIVE" -C "$release_dir"

if [[ ! -f "$release_dir/leo.jar" ]]; then
  echo "发布包缺少 leo.jar" >&2
  exit 1
fi

process_script="$release_dir/deploy/steelx-process.sh"
if [[ ! -f "$process_script" ]]; then
  echo "发布包缺少 deploy/steelx-process.sh" >&2
  exit 1
fi

if [[ -f "$release_dir/dependency-bundle.id" ]]; then
  packaged_dependency_bundle_id="$(tr -d '\r\n' < "$release_dir/dependency-bundle.id")"
  if [[ ! "$packaged_dependency_bundle_id" =~ ^[0-9a-f]{64}$ ]]; then
    echo "发布包中的 dependency-bundle.id 无效" >&2
    exit 1
  fi
  if [[ -n "$DEPENDENCY_BUNDLE_ID" && "$DEPENDENCY_BUNDLE_ID" != "$packaged_dependency_bundle_id" ]]; then
    echo "发布包与参数指定的依赖 bundle ID 不一致" >&2
    exit 1
  fi
  DEPENDENCY_BUNDLE_ID="$packaged_dependency_bundle_id"
  install_dependency_bundle "$DEPENDENCY_BUNDLE_ID"
  if [[ -e "$release_dir/lib" || -L "$release_dir/lib" ]]; then
    echo "发布包不应自带 lib 路径" >&2
    exit 1
  fi
  ln -s "../../dependencies/$DEPENDENCY_BUNDLE_ID/lib" "$release_dir/lib"
elif [[ -n "$DEPENDENCY_BUNDLE_ID" ]]; then
  echo "发布包缺少 dependency-bundle.id，拒绝绑定外部依赖" >&2
  exit 1
fi

run_hook "pre-deploy.sh"

install -m 0755 "$process_script" "$shared_dir/steelx-process.sh"

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

if ! healthcheck 240; then
  rollback "后端健康检查未通过: $HEALTHCHECK_URL"
  exit 1
fi

run_hook "post-deploy.sh"

prune_old_releases "$releases_dir" "$(readlink -f "$current_link")" "$(readlink -f "$previous_link" 2>/dev/null || true)"

cleanup_files=("$ARCHIVE")
if [[ -n "$SHA256_FILE" ]]; then
  cleanup_files+=("$SHA256_FILE")
fi
if [[ -n "$DEPENDENCY_ARCHIVE" ]]; then
  cleanup_files+=("$DEPENDENCY_ARCHIVE")
fi
if [[ -n "$DEPENDENCY_SHA256_FILE" ]]; then
  cleanup_files+=("$DEPENDENCY_SHA256_FILE")
fi
rm -f -- "${cleanup_files[@]}"
rmdir "$(dirname "$ARCHIVE")" 2>/dev/null || true
if [[ -n "$DEPENDENCY_ARCHIVE" ]]; then
  rmdir "$(dirname "$DEPENDENCY_ARCHIVE")" 2>/dev/null || true
fi

echo "发布完成: $release_id"
