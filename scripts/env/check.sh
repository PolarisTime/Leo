#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNTIME_ENV="${1:-${LEO_RUNTIME_ENV:-dev}}"

case "$RUNTIME_ENV" in
  dev|prod) ;;
  *) echo "未知环境: $RUNTIME_ENV，应为 dev 或 prod。" >&2; exit 1 ;;
esac

# shellcheck disable=SC1090
source "$SCRIPT_DIR/$RUNTIME_ENV.sh"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
PASS=0; WARN=0; FAIL=0

check() { echo -n "  $1 ... "; }
ok()   { echo -e "${GREEN}[OK]${NC} ${2:-$1}"; PASS=$((PASS+1)); }
warn() { echo -e "${YELLOW}[WARN]${NC} ${2:-$1}"; WARN=$((WARN+1)); }
fail() { echo -e "${RED}[FAIL]${NC} ${2:-$1}"; FAIL=$((FAIL+1)); }

echo "=== Leo ERP 环境检查 ($RUNTIME_ENV) ==="
echo ""

check "Java 版本"
JAVA_VERSION_OUTPUT="$(java -version 2>&1)"
JAVA_VER=$(awk 'NR == 1 { if (match($0, /[0-9]+/)) print substr($0, RSTART, RLENGTH); exit }' <<< "$JAVA_VERSION_OUTPUT")
JAVA_VER="${JAVA_VER:-0}"
if [[ "$JAVA_VER" -ge 21 ]]; then
  ok "Java $JAVA_VER"
else
  fail "需要 Java 21+，当前为 $JAVA_VER"
fi

check "可用内存"
MEM_MB=$(free -m | awk '/^Mem:/{print $7}')
if [[ "$MEM_MB" -ge 512 ]]; then
  ok "${MEM_MB}MB 可用"
else
  warn "仅 ${MEM_MB}MB，建议 >= 512MB"
fi

check "磁盘空间"
DISK_GB=$(df -BG . | tail -1 | awk '{print $4}' | tr -d 'G')
if [[ "$DISK_GB" -ge 10 ]]; then
  ok "${DISK_GB}GB 可用"
else
  fail "仅 ${DISK_GB}GB，需要 >= 10GB"
fi

check "端口 $SERVER_PORT"
if ! ss -ltnpH "( sport = :$SERVER_PORT )" 2>/dev/null | grep -q .; then
  ok "未占用"
else
  warn "已被占用，启动可能失败"
fi

check "PostgreSQL 登录"
if command -v psql >/dev/null 2>&1; then
  if PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql \
    -h "$SPRING_DATASOURCE_HOST" \
    -p "$SPRING_DATASOURCE_PORT" \
    -U "$SPRING_DATASOURCE_USERNAME" \
    -d "$SPRING_DATASOURCE_DB" \
    -v ON_ERROR_STOP=1 \
    -Atc "SELECT 1" >/dev/null 2>&1; then
    ok "$SPRING_DATASOURCE_USERNAME@$SPRING_DATASOURCE_HOST:$SPRING_DATASOURCE_PORT/$SPRING_DATASOURCE_DB 可登录"
  else
    fail "$SPRING_DATASOURCE_USERNAME@$SPRING_DATASOURCE_HOST:$SPRING_DATASOURCE_PORT/$SPRING_DATASOURCE_DB 不可登录"
  fi
elif timeout 5 bash -c "echo >/dev/tcp/$SPRING_DATASOURCE_HOST/$SPRING_DATASOURCE_PORT" 2>/dev/null; then
  ok "$SPRING_DATASOURCE_HOST:$SPRING_DATASOURCE_PORT 可达"
else
  fail "$SPRING_DATASOURCE_HOST:$SPRING_DATASOURCE_PORT 不可达"
fi

check "Redis 连接"
if command -v redis-cli >/dev/null 2>&1; then
  redis_args=(-h "$SPRING_DATA_REDIS_HOST" -p "$SPRING_DATA_REDIS_PORT")
  if [[ -n "$SPRING_DATA_REDIS_PASSWORD" ]]; then
    redis_args+=(-a "$SPRING_DATA_REDIS_PASSWORD")
  fi
  redis_check_output="$(redis-cli "${redis_args[@]}" ping 2>&1 || true)"
  if [[ "$redis_check_output" == *"PONG"* ]]; then
    ok "$SPRING_DATA_REDIS_HOST:$SPRING_DATA_REDIS_PORT 可用"
  else
    fail "$SPRING_DATA_REDIS_HOST:$SPRING_DATA_REDIS_PORT 不可用"
  fi
elif timeout 3 bash -c "echo >/dev/tcp/$SPRING_DATA_REDIS_HOST/$SPRING_DATA_REDIS_PORT" 2>/dev/null; then
  ok "$SPRING_DATA_REDIS_HOST:$SPRING_DATA_REDIS_PORT 可达"
else
  fail "$SPRING_DATA_REDIS_HOST:$SPRING_DATA_REDIS_PORT 不可达"
fi

check "JWT 密钥"
if [[ -n "$LEO_JWT_SECRET" ]]; then
  if [[ ${#LEO_JWT_SECRET} -ge 32 ]]; then
    ok "已配置 (${#LEO_JWT_SECRET}字符)"
  else
    warn "长度 ${#LEO_JWT_SECRET}，建议 >= 32"
  fi
elif [[ "$RUNTIME_ENV" == "prod" ]]; then
  fail "LEO_JWT_SECRET 未设置"
else
  warn "未设置，将优先尝试使用数据库托管密钥"
fi

check "Spring Profile"
if [[ "$SPRING_PROFILES_ACTIVE" == "$RUNTIME_ENV" ]]; then
  ok "$SPRING_PROFILES_ACTIVE"
else
  warn "当前为 $SPRING_PROFILES_ACTIVE，预期为 $RUNTIME_ENV"
fi

echo ""
echo -e "=== 结果: ${GREEN}${PASS}通过${NC} / ${YELLOW}${WARN}警告${NC} / ${RED}${FAIL}失败${NC} ==="
if [[ $FAIL -gt 0 ]]; then
  echo "请修复以上失败项后重试"
  exit 1
fi
echo "环境检查通过，可以启动"
