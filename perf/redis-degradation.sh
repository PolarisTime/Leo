#!/usr/bin/env bash
# Redis 故障降级验证：确认 Redis 不可用时系统的真实行为。
#
# 为什么要单独验证：Redis 在本项目里不只是「缓存」。实测发现它已被认证链路
# 当作硬依赖使用——Redis 不可用时所有已登录用户会被静默登出（401），
# 且因为幂等过滤器同样依赖 Redis，用户连重新登录都做不到（503）。
#
# 隔离方式：只把 **leo 后端** 指向一个不存在的 Redis 端口，
# 绝不触碰共享 Redis 实例（同一实例上还有其它业务库在用）。
#
# 前置：11211 端口必须空闲（本脚本会自行启动/停止后端并负责恢复）。
#       启动方式绕过 scripts/backend/start-dev.sh 的 Redis 预检——
#       该预检会在 Redis 不可用时直接拒绝启动，从而无法做降级验证。
#
# 用法： bash leo/perf/redis-degradation.sh --yes
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LEO_DIR="$REPO_ROOT/leo"
OUT_DIR="$REPO_ROOT/tmp/perf"
BASE_URL="${LEO_PERF_BASE_URL:-http://127.0.0.1:11211/api}"
BAD_REDIS_PORT="${BAD_REDIS_PORT:-16380}"
BACKEND_PORT=11211
LOG="$REPO_ROOT/tmp/backend-run/backend-redisdown.log"
RESULTS="$OUT_DIR/redis-degradation-results.txt"

log()  { printf '[redis-degradation] %s\n' "$*"; }
fail() { printf '[redis-degradation][ERROR] %s\n' "$*" >&2; exit 1; }

[[ "${1:-}" == "--yes" ]] || fail "该脚本会重启本地后端，请显式确认：bash $0 --yes"
mkdir -p "$OUT_DIR" "$(dirname "$LOG")"

if [[ -n "${LEO_PERF_ENV_FILE:-}" && -f "$LEO_PERF_ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$LEO_PERF_ENV_FILE"
fi
[[ -n "${LEO_PERF_LOGIN_NAME:-}" && -n "${LEO_PERF_PASSWORD:-}" ]] || fail "缺少压测账号凭据"

port_in_use() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null && return 0 || return 1
}

probe() { # probe <label> <url> [extra curl args...]
  local label="$1" url="$2"; shift 2
  local body code
  body="$(curl -s -m 20 -o "$OUT_DIR/.rd-body" -w '%{http_code}' "$@" "$url" 2>/dev/null)"
  code="$body"
  printf '%-34s -> HTTP %s   %s\n' "$label" "$code" "$(head -c 120 "$OUT_DIR/.rd-body" 2>/dev/null | tr -d '\n')"
}

start_backend() { # start_backend <redis_port>
  local redis_port="$1"
  ( cd "$LEO_DIR" && SPRING_DATA_REDIS_PORT="$redis_port" \
      LEO_MANAGEMENT_ENDPOINTS=health,prometheus,loggers \
      bash scripts/maven.sh -q -Dmaven.test.skip=true spring-boot:run \
        -Dspring-boot.run.profiles=dev \
        -Dspring-boot.run.jvmArguments="-XX:TieredStopAtLevel=1 -XX:+UseG1GC" ) > "$LOG" 2>&1 &
  BACKEND_PID=$!
}

stop_backend() {
  if [[ -n "${BACKEND_PID:-}" ]]; then
    kill "$BACKEND_PID" 2>/dev/null
    wait "$BACKEND_PID" 2>/dev/null
    BACKEND_PID=""
  fi
}

restore() {
  log "恢复：以正常 Redis 配置重启后端"
  stop_backend
  sleep 3
  start_backend "${SPRING_DATA_REDIS_PORT_REAL:-16379}"
  for _ in $(seq 1 60); do
    [[ "$(curl -s -m 4 -o /dev/null -w '%{http_code}' "$BASE_URL/v2.0/health")" == "200" ]] && { log "✅ 后端已恢复正常"; return 0; }
    sleep 5
  done
  log "⚠ 后端未在预期时间内恢复，请手工检查 $LOG"
}
trap restore EXIT

port_in_use "$BACKEND_PORT" && fail "$BACKEND_PORT 已被占用；请先停止现有后端再运行本脚本"
port_in_use "$BAD_REDIS_PORT" && fail "$BAD_REDIS_PORT 已被占用，请换 BAD_REDIS_PORT"

# ---------- 0. 正常状态基线（需要真实 Redis，先临时起一次正常后端）----------
log "步骤 0/3：以正常配置启动后端并采集基线"
start_backend "${SPRING_DATA_REDIS_PORT_REAL:-16379}"
for _ in $(seq 1 72); do
  [[ "$(curl -s -m 4 -o /dev/null -w '%{http_code}' "$BASE_URL/v2.0/health")" == "200" ]] && break
  sleep 5
done
TOKEN=""
for i in 1 2 3 4 5; do
  TOKEN="$(curl -s -m 15 -X POST "$BASE_URL/v2.0/auth/login" -H 'Content-Type: application/json' \
    -H "X-Idempotency-Key: rd-base-$RANDOM-$i" \
    -d "{\"loginName\":\"$LEO_PERF_LOGIN_NAME\",\"password\":\"$LEO_PERF_PASSWORD\"}" | jq -r '.accessToken // empty')"
  [[ -n "$TOKEN" ]] && break; sleep 1
done
[[ -n "$TOKEN" ]] || fail "基线登录失败"

{
  echo "=== Redis 可用（基线）$(date '+%F %T') ==="
  probe "GET /health"                "$BASE_URL/v2.0/health"
  probe "GET /version"               "$BASE_URL/v2.0/version"
  probe "GET /account (带 token)"     "$BASE_URL/v2.0/account" -H "Authorization: Bearer $TOKEN"
  probe "POST /auth/login"           "$BASE_URL/v2.0/auth/login" -X POST -H 'Content-Type: application/json' \
        -H "X-Idempotency-Key: rd-base-login-$RANDOM" \
        -d "{\"loginName\":\"$LEO_PERF_LOGIN_NAME\",\"password\":\"$LEO_PERF_PASSWORD\"}"
} | tee "$RESULTS"

# ---------- 1. 注入故障：指向不存在的 Redis 端口 ----------
log "步骤 1/3：停止后端，改指向不存在的 Redis 端口 $BAD_REDIS_PORT"
stop_backend
sleep 3
start_backend "$BAD_REDIS_PORT"
for _ in $(seq 1 72); do
  code="$(curl -s -m 4 -o /dev/null -w '%{http_code}' "$BASE_URL/v2.0/health")"
  [[ "$code" == "200" || "$code" == "503" ]] && break
  sleep 5
done

{
  echo
  echo "=== Redis 不可用（端口 $BAD_REDIS_PORT）$(date '+%F %T') ==="
  probe "GET /health"                 "$BASE_URL/v2.0/health"
  probe "GET /version"                "$BASE_URL/v2.0/version"
  probe "GET /account (带 token)"      "$BASE_URL/v2.0/account" -H "Authorization: Bearer $TOKEN"
  probe "GET /sales-orders (带 token)" "$BASE_URL/v2.0/sales-orders?page=0&size=5" -H "Authorization: Bearer $TOKEN"
  probe "POST /auth/login"            "$BASE_URL/v2.0/auth/login" -X POST -H 'Content-Type: application/json' \
        -H "X-Idempotency-Key: rd-down-login-$RANDOM" \
        -d "{\"loginName\":\"$LEO_PERF_LOGIN_NAME\",\"password\":\"$LEO_PERF_PASSWORD\"}"
} | tee -a "$RESULTS"

# ---------- 2. 恢复 ----------
log "步骤 2/3：恢复（由 EXIT trap 负责）"
echo
echo "结果已写入: $RESULTS"
