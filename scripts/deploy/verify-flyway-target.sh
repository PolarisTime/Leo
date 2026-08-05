#!/usr/bin/env bash

set -euo pipefail

LEO_DIR="${1:-.}"
FLYWAY_TARGET="${2:-}"
MIGRATION_DIR="$LEO_DIR/src/main/resources/db/migration"

if [[ ! -d "$MIGRATION_DIR" ]]; then
  echo "Flyway migration directory not found: $MIGRATION_DIR" >&2
  exit 1
fi

latest_version=0
migration_count=0
while IFS= read -r -d '' migration; do
  filename="${migration##*/}"
  if [[ ! "$filename" =~ ^V([1-9][0-9]*)__.+\.sql$ ]]; then
    echo "Invalid versioned Flyway migration name: $filename" >&2
    exit 1
  fi
  version="${BASH_REMATCH[1]}"
  ((migration_count += 1))
  if (( 10#$version > latest_version )); then
    latest_version=$((10#$version))
  fi
done < <(find "$MIGRATION_DIR" -maxdepth 1 -type f -name 'V*.sql' -print0)

if (( migration_count == 0 )); then
  echo "No versioned Flyway migrations found in $MIGRATION_DIR" >&2
  exit 1
fi

# Resolve 模式：未指定 target 时输出代码库最新 migration 版本，供部署流程自动使用，
# 避免每次新增 migration 都要手动同步 PROD_FLYWAY_TARGET 与生产 SPRING_FLYWAY_TARGET。
if [[ -z "$FLYWAY_TARGET" ]]; then
  echo "$latest_version"
  exit 0
fi

if [[ ! "$FLYWAY_TARGET" =~ ^[1-9][0-9]*$ ]]; then
  echo "Flyway target must be a positive integer: ${FLYWAY_TARGET:-<empty>}" >&2
  exit 1
fi

if (( FLYWAY_TARGET != latest_version )); then
  echo "Production Flyway target $FLYWAY_TARGET does not match packaged migration version $latest_version." >&2
  echo "Refusing to deploy application code against an older or newer schema target." >&2
  exit 1
fi

echo "Verified production Flyway target: $FLYWAY_TARGET ($migration_count migrations)"
