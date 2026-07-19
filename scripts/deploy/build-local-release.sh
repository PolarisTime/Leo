#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUTPUT_DIR="$LEO_DIR/target/local-release"
RELEASE_NAME="leo-local-release"
SKIP_TESTS=false

usage() {
  cat <<'EOF'
用法:
  bash leo/scripts/deploy/build-local-release.sh [选项]

选项:
  --output-dir <dir>       发布包输出目录，默认 leo/target/local-release
  --release-name <name>    发布包名称，默认 leo-local-release
  --skip-tests             跳过测试，仅构建产物
  -h, --help               查看帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
    --release-name) RELEASE_NAME="$2"; shift 2 ;;
    --skip-tests) SKIP_TESTS=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ ! "$RELEASE_NAME" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "--release-name 只能包含字母、数字、点、下划线和连字符" >&2
  exit 1
fi

output_parent="$(dirname "$OUTPUT_DIR")"
output_name="$(basename "$OUTPUT_DIR")"
mkdir -p "$output_parent"
output_parent="$(cd "$output_parent" && pwd -P)"
OUTPUT_DIR="$output_parent/$output_name"
if [[ "$OUTPUT_DIR" == "/" || "$OUTPUT_DIR" == "$LEO_DIR" ]]; then
  echo "拒绝清理危险输出目录: $OUTPUT_DIR" >&2
  exit 1
fi

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "缺少命令: $command_name" >&2
    exit 1
  fi
}

require_command mvn
require_command sha256sum
require_command tar

read_maven_version() {
  local version
  version="$(cd "$LEO_DIR" && mvn -q -DforceStdout help:evaluate -Dexpression=project.version)"
  if [[ -z "$version" ]]; then
    echo "无法读取 Maven 项目版本" >&2
    exit 1
  fi
  printf '%s\n' "$version"
}

rm -rf -- "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR/release/deploy"

echo "[build] Leo backend"
if [[ "$SKIP_TESTS" == "true" ]]; then
  (cd "$LEO_DIR" && mvn -B -ntp -DskipTests package)
else
  (cd "$LEO_DIR" && mvn -B -ntp package)
fi

executable_jar="$(find "$LEO_DIR/target" -maxdepth 1 -type f -name 'leo-*.jar' ! -name '*sources*' | sort | tail -1)"
if [[ -z "$executable_jar" ]]; then
  echo "未找到 Spring Boot 可执行 JAR" >&2
  exit 1
fi
application_jar="$executable_jar.original"
if [[ ! -f "$application_jar" ]]; then
  echo "未找到 Maven 原始应用 JAR: $application_jar" >&2
  exit 1
fi

prepared_dir="$OUTPUT_DIR/prepared"
bash "$SCRIPT_DIR/prepare-backend-release.sh" \
  --executable-jar "$executable_jar" \
  --application-jar "$application_jar" \
  --output-dir "$prepared_dir"

dependency_bundle_id="$(<"$prepared_dir/dependency/dependency-bundle.id")"
application_sha256="$(awk '{print $1}' "$prepared_dir/application/application.sha256")"

cp "$prepared_dir/application/leo.jar" "$OUTPUT_DIR/release/leo.jar"
cp "$prepared_dir/dependency/dependency-bundle.id" "$OUTPUT_DIR/release/dependency-bundle.id"

cp "$LEO_DIR/scripts/deploy/install-production-release.sh" "$OUTPUT_DIR/release/deploy/"
cp "$LEO_DIR/scripts/deploy/steelx-process.sh" "$OUTPUT_DIR/release/deploy/"

leo_version="$(read_maven_version)"
leo_ref="$(git -C "$LEO_DIR" rev-parse --short=12 HEAD 2>/dev/null || echo unknown)"
cat > "$OUTPUT_DIR/release/manifest.json" <<JSON
{
  "releaseId": "$RELEASE_NAME",
  "target": "local-steelx",
  "leoVersion": "$leo_version",
  "leoRef": "$leo_ref",
  "applicationSha256": "$application_sha256",
  "dependencyBundleId": "$dependency_bundle_id"
}
JSON

archive="$OUTPUT_DIR/$RELEASE_NAME.tar.gz"
tar -C "$OUTPUT_DIR/release" -czf "$archive" .
(cd "$OUTPUT_DIR" && sha256sum "$(basename "$archive")") > "$archive.sha256"

dependency_archive="$OUTPUT_DIR/$RELEASE_NAME-dependencies-$dependency_bundle_id.tar.gz"
tar -C "$prepared_dir/dependency" -czf "$dependency_archive" .
(cd "$OUTPUT_DIR" && sha256sum "$(basename "$dependency_archive")") > "$dependency_archive.sha256"
printf '%s\n' "$dependency_bundle_id" > "$OUTPUT_DIR/dependency-bundle.id"

rm -rf -- "$prepared_dir"

echo "应用发布包: $archive"
echo "应用校验和: $archive.sha256"
echo "依赖 bundle: $dependency_archive"
echo "依赖校验和: $dependency_archive.sha256"
echo "依赖 bundle ID: $dependency_bundle_id"
