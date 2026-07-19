#!/usr/bin/env bash

set -Eeuo pipefail

EXECUTABLE_JAR=""
APPLICATION_JAR=""
OUTPUT_DIR=""

usage() {
  cat <<'EOF'
用法:
  bash prepare-backend-release.sh \
    --executable-jar target/leo.jar \
    --application-jar target/leo.jar.original \
    --output-dir target/prepared-release

将 Spring Boot fat JAR 拆分为应用 JAR 与可复用的运行时依赖 bundle。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --executable-jar) EXECUTABLE_JAR="${2:-}"; shift 2 ;;
    --application-jar) APPLICATION_JAR="${2:-}"; shift 2 ;;
    --output-dir) OUTPUT_DIR="${2:-}"; shift 2 ;;
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

require_command jar
require_command sha256sum

if [[ -z "$EXECUTABLE_JAR" || ! -f "$EXECUTABLE_JAR" ]]; then
  echo "Spring Boot 可执行 JAR 不存在: ${EXECUTABLE_JAR:-<empty>}" >&2
  exit 1
fi
if [[ -z "$APPLICATION_JAR" || ! -f "$APPLICATION_JAR" ]]; then
  echo "应用 JAR 不存在: ${APPLICATION_JAR:-<empty>}" >&2
  exit 1
fi
if [[ -z "$OUTPUT_DIR" ]]; then
  echo "--output-dir 不能为空" >&2
  exit 1
fi
if [[ -e "$OUTPUT_DIR" ]]; then
  echo "输出目录已存在，拒绝覆盖: $OUTPUT_DIR" >&2
  exit 1
fi

executable_jar="$(cd "$(dirname "$EXECUTABLE_JAR")" && pwd -P)/$(basename "$EXECUTABLE_JAR")"
application_jar="$(cd "$(dirname "$APPLICATION_JAR")" && pwd -P)/$(basename "$APPLICATION_JAR")"
output_parent="$(dirname "$OUTPUT_DIR")"
mkdir -p "$output_parent"
output_parent="$(cd "$output_parent" && pwd -P)"
output_dir="$output_parent/$(basename "$OUTPUT_DIR")"
staging_dir="$(mktemp -d "$output_parent/.prepare-backend-release.XXXXXX")"

cleanup() {
  if [[ -n "${staging_dir:-}" && -d "$staging_dir" ]]; then
    rm -rf -- "$staging_dir"
  fi
}
trap cleanup EXIT

mkdir -p "$staging_dir/extracted" "$staging_dir/application" "$staging_dir/dependency"
(cd "$staging_dir/extracted" && jar -xf "$executable_jar" BOOT-INF/lib)

extracted_lib="$staging_dir/extracted/BOOT-INF/lib"
dependency_count="$(find "$extracted_lib" -maxdepth 1 -type f -name '*.jar' 2>/dev/null | wc -l)"
if [[ "$dependency_count" -eq 0 ]]; then
  echo "可执行 JAR 中未找到 BOOT-INF/lib 运行时依赖" >&2
  exit 1
fi

cp "$application_jar" "$staging_dir/application/leo.jar"
mv "$extracted_lib" "$staging_dir/dependency/lib"

(
  cd "$staging_dir/application"
  sha256sum leo.jar > application.sha256
)
(
  cd "$staging_dir/dependency"
  while IFS= read -r -d '' dependency_file; do
    sha256sum "$dependency_file"
  done < <(find lib -type f -name '*.jar' -print0 | LC_ALL=C sort -z)
) > "$staging_dir/dependency/dependency-bundle.sha256"

dependency_bundle_id="$(sha256sum "$staging_dir/dependency/dependency-bundle.sha256" | awk '{print $1}')"
printf '%s\n' "$dependency_bundle_id" > "$staging_dir/dependency/dependency-bundle.id"

mv "$staging_dir" "$output_dir"
staging_dir=""
trap - EXIT

printf 'application_dir=%s\n' "$output_dir/application"
printf 'dependency_dir=%s\n' "$output_dir/dependency"
printf 'dependency_bundle_id=%s\n' "$dependency_bundle_id"
printf 'dependency_count=%s\n' "$dependency_count"
