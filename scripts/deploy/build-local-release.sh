#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
WORKSPACE_DIR="$(cd "$LEO_DIR/.." && pwd)"
ARIES_DIR="$WORKSPACE_DIR/aries"
OUTPUT_DIR="$LEO_DIR/target/local-release"
RELEASE_NAME="leo-local-release"
VITE_API_BASE_URL="/api"
LEO_VERSION="1.1.0"
ARIES_VERSION="1.1.0"
SKIP_TESTS=false

usage() {
  cat <<'EOF'
用法:
  bash leo/scripts/deploy/build-local-release.sh [选项]

选项:
  --output-dir <dir>       发布包输出目录，默认 leo/target/local-release
  --release-name <name>    发布包名称，默认 leo-local-release
  --api-base-url <path>    前端 API Base，默认 /api
  --skip-tests             跳过测试，仅构建产物
  -h, --help               查看帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --release-name) RELEASE_NAME="$2"; shift 2 ;;
    --api-base-url) VITE_API_BASE_URL="$2"; shift 2 ;;
    --skip-tests) SKIP_TESTS=true; shift ;;
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

require_command mvn
require_command pnpm
require_command sha256sum

if [[ ! -d "$ARIES_DIR" ]]; then
  echo "未找到前端仓库: $ARIES_DIR" >&2
  exit 1
fi

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/input/backend" "$OUTPUT_DIR/input/frontend" "$OUTPUT_DIR/release/backend" "$OUTPUT_DIR/release/frontend" "$OUTPUT_DIR/release/deploy"

echo "[build] Leo backend"
if [[ "$SKIP_TESTS" == "true" ]]; then
  (cd "$LEO_DIR" && mvn -B -ntp -DskipTests package)
else
  (cd "$LEO_DIR" && mvn -B -ntp package)
fi

backend_jar="$(find "$LEO_DIR/target" -maxdepth 1 -type f -name 'leo-*.jar' ! -name '*sources*' | sort | tail -1)"
if [[ -z "$backend_jar" ]]; then
  echo "未找到后端 JAR" >&2
  exit 1
fi
cp "$backend_jar" "$OUTPUT_DIR/release/backend/leo.jar"

echo "[build] Aries frontend"
if [[ ! -d "$ARIES_DIR/node_modules" ]]; then
  (cd "$ARIES_DIR" && pnpm install --frozen-lockfile)
fi
if [[ "$SKIP_TESTS" == "true" ]]; then
  (cd "$ARIES_DIR" && VITE_API_BASE_URL="$VITE_API_BASE_URL" pnpm build-only --mode production)
else
  (cd "$ARIES_DIR" && pnpm lint && pnpm test:unit && pnpm typecheck && VITE_API_BASE_URL="$VITE_API_BASE_URL" pnpm build-only --mode production)
fi
cp -a "$ARIES_DIR/dist/." "$OUTPUT_DIR/release/frontend/"
cp "$LEO_DIR/scripts/deploy/install-production-release.sh" "$OUTPUT_DIR/release/deploy/"

leo_ref="$(git -C "$LEO_DIR" rev-parse --short=12 HEAD 2>/dev/null || echo unknown)"
aries_ref="$(git -C "$ARIES_DIR" rev-parse --short=12 HEAD 2>/dev/null || echo unknown)"
cat > "$OUTPUT_DIR/release/manifest.json" <<JSON
{
  "releaseId": "$RELEASE_NAME",
  "target": "local-steelx",
  "leoVersion": "$LEO_VERSION",
  "ariesVersion": "$ARIES_VERSION",
  "leoRef": "$leo_ref",
  "ariesRef": "$aries_ref"
}
JSON

archive="$OUTPUT_DIR/$RELEASE_NAME.tar.gz"
tar -C "$OUTPUT_DIR/release" -czf "$archive" .
sha256sum "$archive" > "$archive.sha256"

echo "发布包: $archive"
echo "校验和: $archive.sha256"
