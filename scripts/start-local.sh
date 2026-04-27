#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_SCRIPT="$ROOT_DIR/scripts/env-local.sh"

if [[ -f "$ENV_SCRIPT" ]]; then
  # 统一从本地环境变量脚本加载开发配置
  # shellcheck disable=SC1090
  source "$ENV_SCRIPT"
fi

DEFAULT_JAVA_HOME="/usr/lib/jvm/java-21-openjdk-21.0.10.0.7-1.el9.x86_64"
DEFAULT_DB_HOST="${SPRING_DATASOURCE_HOST:-localhost}"
DEFAULT_DB_PORT="${SPRING_DATASOURCE_PORT:-5432}"
DEFAULT_DB_NAME="${SPRING_DATASOURCE_DB:-leo}"
DEFAULT_DB_USER="${SPRING_DATASOURCE_USERNAME:-leo}"
DEFAULT_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
DEFAULT_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-16379}"

export JAVA_HOME="${JAVA_HOME:-$DEFAULT_JAVA_HOME}"
export PATH="$JAVA_HOME/bin:$PATH"

optional_secret_envs=(
  LEO_JWT_SECRET
  TOTP_ENCRYPTION_KEY
)

for env_name in "${optional_secret_envs[@]}"; do
  if [[ -z "${!env_name:-}" ]]; then
    echo "[leo] 未设置 ${env_name}，将优先尝试使用数据库托管密钥。" >&2
  fi
done

if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "JAVA_HOME 无效: $JAVA_HOME" >&2
  echo "请设置可用的 Java 21 路径后重试。" >&2
  exit 1
fi

if command -v pg_isready >/dev/null 2>&1; then
  if ! pg_isready -h "$DEFAULT_DB_HOST" -p "$DEFAULT_DB_PORT" -d "$DEFAULT_DB_NAME" >/dev/null 2>&1; then
    echo "PostgreSQL 不可用: $DEFAULT_DB_HOST:$DEFAULT_DB_PORT/$DEFAULT_DB_NAME" >&2
    echo "请先启动数据库服务后重试。" >&2
    exit 1
  fi
fi

if command -v psql >/dev/null 2>&1; then
  if ! PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-}" \
    psql -h "$DEFAULT_DB_HOST" -p "$DEFAULT_DB_PORT" -U "$DEFAULT_DB_USER" -d "$DEFAULT_DB_NAME" -Atqc 'select 1' >/dev/null 2>&1; then
    echo "数据库认证失败: ${DEFAULT_DB_USER}@${DEFAULT_DB_HOST}:${DEFAULT_DB_PORT}/${DEFAULT_DB_NAME}" >&2
    if [[ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then
      echo "当前未提供 SPRING_DATASOURCE_PASSWORD，但目标 PostgreSQL 需要密码认证。" >&2
    fi
    echo "请检查 SPRING_DATASOURCE_PASSWORD 是否正确，以及数据库和账号是否已创建。" >&2
    exit 1
  fi
fi

if command -v redis-cli >/dev/null 2>&1; then
  redis_args=(-h "$DEFAULT_REDIS_HOST" -p "$DEFAULT_REDIS_PORT")
  if [[ -n "${SPRING_DATA_REDIS_PASSWORD:-}" ]]; then
    redis_args+=(-a "${SPRING_DATA_REDIS_PASSWORD}")
  fi

  redis_check_output="$(redis-cli "${redis_args[@]}" ping 2>&1 || true)"
  if [[ "$redis_check_output" != *"PONG"* ]]; then
    echo "Redis 认证或连接失败: ${DEFAULT_REDIS_HOST}:${DEFAULT_REDIS_PORT}" >&2
    if [[ -z "${SPRING_DATA_REDIS_PASSWORD:-}" ]]; then
      echo "当前未提供 SPRING_DATA_REDIS_PASSWORD，但目标 Redis 可能启用了密码认证。" >&2
    fi
    echo "请检查 SPRING_DATA_REDIS_PASSWORD 是否正确，以及 Redis 服务是否已启动。" >&2
    exit 1
  fi
fi

cd "$ROOT_DIR"

echo "[leo] using JAVA_HOME=$JAVA_HOME"
echo "[leo] purge compiled classes"
rm -rf "$ROOT_DIR/target/classes" "$ROOT_DIR/target/test-classes"
echo "[leo] clean compile"
mvn -q -Dmaven.test.skip=true clean compile

echo "[leo] spring-boot:run"
exec mvn -q -Dmaven.test.skip=true spring-boot:run
