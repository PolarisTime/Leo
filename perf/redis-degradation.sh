#!/usr/bin/env bash
# Redis 故障降级验证：确认 Redis 不可用时系统的真实行为。
#
# 为什么要单独验证：Redis 在本项目里不只是「缓存」。实测已确证它已被当作硬依赖——
# Redis 不可用时 `GET /health` 返回 503 DEGRADED、`POST /auth/login` 返回
# 503「幂等服务暂不可用」。但「Redis 不可用对**已登录请求**的影响」至今无有效证据：
#
#   v1 脚本的设计是「先起正常后端取 token → 停掉 → 换成坏 Redis 端口重启 → 探测」。
#   实测中这段等待耗掉了约 11 分钟，**超过了 600s 的 access token 有效期**，
#   观察到的 401 其实来自 ExpiredJwtException，与 Redis 无关。
#   报告初稿据此断言「Redis 不可用时已登录用户全部 401」，该结论已撤回。
#
# v2 设计（本文件）用两道独立的对照把「token 失效」「密钥不一致」这两个混淆因素排除：
#   A. 双实例并行：11211 上的**正常实例由外部提供，本脚本绝不触碰**；本脚本只在
#      11212 上另起一个「故障实例」（只把 Redis 端口指向不存在的端口）。
#      因此探测时刻不需要任何重启等待，token 剩余寿命由 assert_token_valid 保证。
#   B. 同 token 活性对照：探测故障实例**之前**，立刻用同一 token 再打一次正常实例；
#      正常实例必须仍是 200，否则直接中止——若同一 token 在正常实例上已失效，
#      故障实例上的 401 就不能归因于 Redis。
#   C. 坏签名判别探针：故障实例上用「篡改过的 token」再打一次，用于区分
#      「签名/密钥不一致导致的 401」与「Redis 故障导致的 5xx」。
#
# 隔离方式：只把新起的 leo 故障实例指向一个不存在的 Redis 端口，
# 绝不触碰共享 Redis 实例（同一实例上还有其它业务库在用），也不触碰 11211 上的正常实例。
#
# 前置：11211 必须已有一个**正常 Redis 配置**的后端在服务（health 200）；11212 必须空闲。
# 用法：bash leo/perf/redis-degradation.sh --yes
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LEO_DIR="$REPO_ROOT/leo"
OUT_DIR="$REPO_ROOT/tmp/perf"
NORMAL_URL="${LEO_PERF_BASE_URL:-http://127.0.0.1:11211/api}"
FAULT_PORT="${FAULT_PORT:-11212}"
FAULT_URL="http://127.0.0.1:${FAULT_PORT}/api"
NORMAL_PORT=11211
BAD_REDIS_PORT="${BAD_REDIS_PORT:-16380}"
LOG="$REPO_ROOT/tmp/backend-run/backend-redisdown.log"
RESULTS="$OUT_DIR/redis-degradation-results.txt"
PROBE_TIMEOUT="${PROBE_TIMEOUT:-25}"

log()  { printf '[redis-degradation] %s\n' "$*"; }
fail() { printf '[redis-degradation][ERROR] %s\n' "$*" >&2; exit 1; }

[[ "${1:-}" == "--yes" ]] || fail "该脚本会另起一个故障后端实例，请显式确认：bash $0 --yes"
mkdir -p "$OUT_DIR" "$(dirname "$LOG")"

if [[ -n "${LEO_PERF_ENV_FILE:-}" && -f "$LEO_PERF_ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$LEO_PERF_ENV_FILE"
fi
[[ -n "${LEO_PERF_LOGIN_NAME:-}" && -n "${LEO_PERF_PASSWORD:-}" ]] || fail "缺少压测账号凭据"

port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null && return 0 || return 1; }
http_code() { curl -s -m "$PROBE_TIMEOUT" -o /dev/null -w '%{http_code}' "$@" 2>/dev/null; }

# 解析 JWT 的剩余有效期（秒）。无法解析时输出空串。
#
# 为什么必须校验：本脚本的结论完全依赖「故障实例上的失败是 Redis 造成的」这一因果。
# 若基线 token 在探测前过期，观察到的 401 其实来自 token 过期，
# 结论会被误读成「Redis 不可用导致已登录用户被登出」——报告初稿正是这样得出过一个
# 随后被撤回的结论。宁可让脚本失败，也不要产出误导性证据。
token_remaining_seconds() {
  local b64 payload exp
  b64="$(printf '%s' "$1" | cut -d. -f2)"
  b64="${b64//-/+}"; b64="${b64//_//}"
  case $(( ${#b64} % 4 )) in
    2) b64="${b64}==" ;;
    3) b64="${b64}=" ;;
  esac
  payload="$(printf '%s' "$b64" | base64 -d 2>/dev/null)" || return 0
  exp="$(printf '%s' "$payload" | jq -r '.exp // empty' 2>/dev/null)"
  [[ -n "$exp" ]] || return 0
  echo $(( exp - $(date +%s) ))
}

assert_token_valid() {
  local label="$1" remaining
  remaining="$(token_remaining_seconds "$TOKEN")"
  [[ -n "$remaining" ]] || fail "${label}: 无法解析 token 有效期，拒绝在凭据状态不明时继续"
  if (( remaining < 120 )); then
    fail "${label}: 基线 token 仅剩 ${remaining}s。继续探测会把「token 过期」误判成「Redis 故障」，结论不可用。"
  fi
  log "${label}: token 剩余 ${remaining}s，足以支撑探测（对照探针会再次确认）"
}

PROBE_BODY="$OUT_DIR/.rd-body"
probe() { # probe <label> <url> [extra curl args...]
  local label="$1" url="$2"; shift 2
  local code out
  code="$(curl -s -m "$PROBE_TIMEOUT" -o "$PROBE_BODY" -w '%{http_code}' "$@" "$url" 2>/dev/null)"
  out="$(head -c 160 "$PROBE_BODY" 2>/dev/null | tr -d '\n')"
  printf '%-40s -> HTTP %-4s %s\n' "$label" "$code" "$out"
}

FAULT_PID=""
stop_fault_instance() {
  if [[ -n "$FAULT_PID" ]] && kill -0 "$FAULT_PID" 2>/dev/null; then
    log "关闭故障实例 (pid=$FAULT_PID)"
    kill "$FAULT_PID" 2>/dev/null
    wait "$FAULT_PID" 2>/dev/null
  fi
  FAULT_PID=""
}
trap stop_fault_instance EXIT

# ---------- 0. 前置检查 ----------
log "步骤 0/4：前置检查（11211 正常实例必须在线，$FAULT_PORT 必须空闲）"
port_in_use "$FAULT_PORT" && fail "$FAULT_PORT 已被占用，请换 FAULT_PORT"
normal_code="$(http_code "$NORMAL_URL/v2.0/health")"
[[ "$normal_code" == "200" ]] || fail "11211 上没有正常后端（/health = $normal_code）。请先以正常配置启动后端；本脚本不会替你启动它。"
log "✅ 11211 正常实例在线（/health = 200）；$FAULT_PORT 空闲"

# ---------- 1. 起故障实例（只想坏 Redis 端口，其余配置与正常实例完全一致）----------
log "步骤 1/4：在 $FAULT_PORT 启动故障实例，Redis 指向不存在的端口 $BAD_REDIS_PORT"
(
  cd "$LEO_DIR" || exit 1
  # 先加载与正常实例相同的 dev 环境（JWT 密钥、数据源库名等），再只覆盖两个端口。
  # 否则「故障实例与正常实例 JWT 密钥不一致」会伪装成「Redis 故障导致 401」。
  # shellcheck disable=SC1091
  source scripts/env/dev.sh >/dev/null 2>&1 || true
  export SERVER_PORT="$FAULT_PORT"
  export SPRING_DATA_REDIS_PORT="$BAD_REDIS_PORT"
  export LEO_MANAGEMENT_ENDPOINTS=health,prometheus,loggers
  exec bash scripts/maven.sh -q -Dmaven.test.skip=true spring-boot:run \
    -Dspring-boot.run.profiles=dev \
    -Dspring-boot.run.jvmArguments="-XX:TieredStopAtLevel=1 -XX:+UseG1GC"
) > "$LOG" 2>&1 &
FAULT_PID=$!

fault_ready=""
for _ in $(seq 1 96); do   # 最长 8 分钟等待启动（不消耗 token：token 在启动完成后才登录获取）
  code="$(http_code "$FAULT_URL/v2.0/health")"
  if [[ "$code" == "503" || "$code" == "200" ]]; then fault_ready="$code"; break; fi
  kill -0 "$FAULT_PID" 2>/dev/null || fail "故障实例进程已退出，见 $LOG"
  sleep 5
done
[[ -n "$fault_ready" ]] || fail "故障实例未在预期时间内就绪，见 $LOG"
log "故障实例已就绪：$FAULT_URL/v2.0/health -> $fault_ready"
[[ "$fault_ready" == "503" ]] || log "⚠ 预期 503 DEGRADED，实际 $fault_ready —— 说明 Redis 故障未被感知，结论需谨慎解读"

# 日志自检：确认故障实例确实启动成功、且数据源正常。
# 两者 JWT 密钥同源是「同 token 可跨实例使用」的前提：正常实例经 scripts/backend/start-dev.sh
# 加载 scripts/env/dev.sh，故障实例也显式 source 同一脚本，二者因此从同一份工作区
# .env.local 读到同一个 LEO_JWT_SECRET。数据源能连上 PostgreSQL 也印证工作区配置已加载
# （若配置缺失，实例根本起不来，健康检查会一直是 000）。
grep -q 'Started LeoApplication' "$LOG" || fail "故障实例日志中未见 Started LeoApplication，见 $LOG"
grep -qE 'Failed to configure a DataSource|Access denied|password authentication failed' "$LOG" \
  && fail "故障实例数据源初始化异常，配置未与正常实例同源，见 $LOG"
log "✅ 故障实例日志确认：启动成功且数据源正常（配置与正常实例同源）"

# ---------- 2. 基线（正常实例）----------
log "步骤 2/4：在正常实例登录并采集基线"
TOKEN=""
for i in 1 2 3 4 5; do
  TOKEN="$(curl -s -m 15 -X POST "$NORMAL_URL/v2.0/auth/login" -H 'Content-Type: application/json' \
    -H "X-Idempotency-Key: rd-base-$RANDOM-$i" \
    -d "{\"loginName\":\"$LEO_PERF_LOGIN_NAME\",\"password\":\"$LEO_PERF_PASSWORD\"}" | jq -r '.accessToken // empty')"
  [[ -n "$TOKEN" ]] && break; sleep 1
done
[[ -n "$TOKEN" ]] || fail "基线登录失败"
assert_token_valid "基线采集前"

CORRUPTED="${TOKEN%????}AAAA"   # 篡改签名段：用于区分「坏签名 401」与「Redis 故障」

{
  echo "=== Redis 可用（正常实例 11211）$(date '+%F %T') ==="
  probe "GET /health"                        "$NORMAL_URL/v2.0/health"
  probe "GET /version"                       "$NORMAL_URL/v2.0/version"
  probe "GET /account (有效 token)"           "$NORMAL_URL/v2.0/account" -H "Authorization: Bearer $TOKEN"
  probe "GET /sales-orders (有效 token)"      "$NORMAL_URL/v2.0/sales-orders?page=0&size=5" -H "Authorization: Bearer $TOKEN"
  probe "GET /account (坏签名 token)"         "$NORMAL_URL/v2.0/account" -H "Authorization: Bearer $CORRUPTED"
  probe "POST /auth/login"                   "$NORMAL_URL/v2.0/auth/login" -X POST -H 'Content-Type: application/json' \
        -H "X-Idempotency-Key: rd-base-login-$RANDOM" \
        -d "{\"loginName\":\"$LEO_PERF_LOGIN_NAME\",\"password\":\"$LEO_PERF_PASSWORD\"}"
} | tee "$RESULTS"

# ---------- 3. 同 token 活性对照 + 故障探测 ----------
log "步骤 3/4：同 token 活性对照（正常实例必须仍为 200）后立即探测故障实例"
assert_token_valid "故障探测前"
control_account="$(http_code -H "Authorization: Bearer $TOKEN" "$NORMAL_URL/v2.0/account")"
control_orders="$(http_code -H "Authorization: Bearer $TOKEN" "$NORMAL_URL/v2.0/sales-orders?page=0&size=5")"
log "活性对照：同一 token 打正常实例 /account=$control_account /sales-orders=$control_orders"
if [[ "$control_account" != "200" || "$control_orders" != "200" ]]; then
  fail "活性对照失败（/account=$control_account /sales-orders=$control_orders）：同一 token 在正常实例上已失效，故障实例上的 401 不可归因于 Redis。已中止以避免产出误导性结论。"
fi

{
  echo
  echo "=== Redis 不可用（故障实例 $FAULT_PORT，Redis 端口 $BAD_REDIS_PORT）$(date '+%F %T') ==="
  echo "--- 以下 4 条为有效 token，且同一 token 刚刚在 11211 上确认 200 ---"
  probe "GET /health"                        "$FAULT_URL/v2.0/health"
  probe "GET /version"                       "$FAULT_URL/v2.0/version"
  probe "GET /account (有效 token)"           "$FAULT_URL/v2.0/account" -H "Authorization: Bearer $TOKEN"
  probe "GET /sales-orders (有效 token)"      "$FAULT_URL/v2.0/sales-orders?page=0&size=5" -H "Authorization: Bearer $TOKEN"
  echo "--- 判别探针：坏签名 token（用于区分 401 与 5xx） ---"
  probe "GET /account (坏签名 token)"         "$FAULT_URL/v2.0/account" -H "Authorization: Bearer $CORRUPTED"
  probe "POST /auth/login"                   "$FAULT_URL/v2.0/auth/login" -X POST -H 'Content-Type: application/json' \
        -H "X-Idempotency-Key: rd-down-login-$RANDOM" \
        -d "{\"loginName\":\"$LEO_PERF_LOGIN_NAME\",\"password\":\"$LEO_PERF_PASSWORD\"}"
  echo
  echo "--- 故障实例日志中的异常类型计数 ---"
  printf 'RedisConnectionFailureException: %s\n' "$(grep -c 'RedisConnectionFailureException' "$LOG" 2>/dev/null || true)"
  printf 'ExpiredJwtException:             %s\n' "$(grep -c 'ExpiredJwtException' "$LOG" 2>/dev/null || true)"
  printf 'JwtException:                    %s\n' "$(grep -c 'JwtException' "$LOG" 2>/dev/null || true)"
} | tee -a "$RESULTS"

# ---------- 4. 收尾 ----------
log "步骤 4/4：关闭故障实例（11211 正常实例全程未受影响）"
stop_fault_instance
log "正常实例仍在线：$NORMAL_URL/v2.0/health -> $(http_code "$NORMAL_URL/v2.0/health")"
echo
echo "结果已写入: $RESULTS"
