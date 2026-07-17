#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# shellcheck disable=SC1090
source "$LEO_DIR/scripts/env/dev.sh"

export PATH="$JAVA_HOME/bin:$PATH"

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "JAVA_HOME 无效: $JAVA_HOME" >&2
  echo "请设置可用的 Java 21 路径后重试。" >&2
  exit 1
fi

if command -v pg_isready >/dev/null 2>&1; then
  if ! pg_isready -h "$SPRING_DATASOURCE_HOST" -p "$SPRING_DATASOURCE_PORT" -d "$SPRING_DATASOURCE_DB" >/dev/null 2>&1; then
    echo "PostgreSQL 不可用: $SPRING_DATASOURCE_HOST:$SPRING_DATASOURCE_PORT/$SPRING_DATASOURCE_DB" >&2
    exit 1
  fi
fi

if command -v psql >/dev/null 2>&1; then
  if ! PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql \
    -h "$SPRING_DATASOURCE_HOST" \
    -p "$SPRING_DATASOURCE_PORT" \
    -U "$SPRING_DATASOURCE_USERNAME" \
    -d "$SPRING_DATASOURCE_DB" \
    -Atqc 'select 1' >/dev/null 2>&1; then
    echo "数据库认证失败: $SPRING_DATASOURCE_USERNAME@$SPRING_DATASOURCE_HOST:$SPRING_DATASOURCE_PORT/$SPRING_DATASOURCE_DB" >&2
    exit 1
  fi
fi

if command -v redis-cli >/dev/null 2>&1; then
  redis_args=(-h "$SPRING_DATA_REDIS_HOST" -p "$SPRING_DATA_REDIS_PORT")
  if [[ -n "$SPRING_DATA_REDIS_PASSWORD" ]]; then
    redis_args+=(-a "$SPRING_DATA_REDIS_PASSWORD")
  fi
  redis_check_output="$(redis-cli "${redis_args[@]}" ping 2>&1 || true)"
  if [[ "$redis_check_output" != *"PONG"* ]]; then
    echo "Redis 认证或连接失败: $SPRING_DATA_REDIS_HOST:$SPRING_DATA_REDIS_PORT" >&2
    exit 1
  fi
fi

for env_name in LEO_JWT_SECRET; do
  if [[ -z "${!env_name:-}" ]]; then
    echo "[leo] 未设置 ${env_name}，将优先尝试使用数据库托管密钥。" >&2
  fi
done

cd "$LEO_DIR"

echo "[leo:dev] using JAVA_HOME=$JAVA_HOME"
echo "[leo:dev] database=$SPRING_DATASOURCE_USERNAME@$SPRING_DATASOURCE_HOST:$SPRING_DATASOURCE_PORT/$SPRING_DATASOURCE_DB"
echo "[leo:dev] purge compiled classes"
rm -rf "$LEO_DIR/target/classes" "$LEO_DIR/target/test-classes"
echo "[leo:dev] clean compile"
mvn -q -Dmaven.test.skip=true clean compile

echo "[leo:dev] spring-boot:run"
exec mvn -q -Dmaven.test.skip=true spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-XX:TieredStopAtLevel=1 -XX:+UseG1GC"
