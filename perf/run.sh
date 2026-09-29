#!/usr/bin/env bash
# Leo ERP dev 环境压测调度脚本。
#
# 凭据通过环境变量注入，不落盘、不入库：
#   LEO_PERF_LOGIN_NAME / LEO_PERF_PASSWORD   必填
#   LEO_PERF_BASE_URL                         默认 http://127.0.0.1:11211/api
# 或者用 LEO_PERF_ENV_FILE 指向一个 shell 文件（例如只在本机保留的凭据文件）。
#
# 用法：
#   bash leo/perf/run.sh smoke|baseline|read|write|spike|heavy|race|soak|metrics|all
#   bash leo/perf/run.sh metrics 300 5     # 单独采集服务端指标（秒数 间隔）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
K6_DIR="$SCRIPT_DIR/k6"
RESULT_DIR="$SCRIPT_DIR/results"
STAGE="${1:-all}"

log()  { printf '[perf] %s\n' "$*"; }
fail() { printf '[perf][ERROR] %s\n' "$*" >&2; exit 1; }

# ---- 凭据加载 ----------------------------------------------------------------
if [[ -n "${LEO_PERF_ENV_FILE:-}" ]]; then
  [[ -f "$LEO_PERF_ENV_FILE" ]] || fail "LEO_PERF_ENV_FILE 不存在: $LEO_PERF_ENV_FILE"
  # shellcheck disable=SC1090
  source "$LEO_PERF_ENV_FILE"
fi

[[ -n "${LEO_PERF_LOGIN_NAME:-}" ]] || fail "缺少 LEO_PERF_LOGIN_NAME"
[[ -n "${LEO_PERF_PASSWORD:-}" ]] || fail "缺少 LEO_PERF_PASSWORD"
export LEO_PERF_BASE_URL="${LEO_PERF_BASE_URL:-http://127.0.0.1:11211/api}"

# ---- k6 定位 ----------------------------------------------------------------
resolve_k6() {
  if [[ -n "${K6_BIN:-}" ]]; then printf '%s' "$K6_BIN"; return; fi
  if command -v k6 >/dev/null 2>&1; then command -v k6; return; fi
  local candidate
  candidate="$(find "$REPO_ROOT/tmp" -maxdepth 3 -type f -name k6 -perm -u+x 2>/dev/null | head -1)"
  [[ -n "$candidate" ]] && { printf '%s' "$candidate"; return; }
  fail "未找到 k6，可通过 K6_BIN 指定，或下载到 tmp/ 下"
}
K6="$(resolve_k6)"
log "k6: $K6 ($("$K6" version))"

mkdir -p "$RESULT_DIR"
RUN_ID="$(date +%Y%m%d-%H%M%S)"
export LEO_PERF_RUN_ID="${LEO_PERF_RUN_ID:-$RUN_ID}"
log "压测批次 RUN_ID=$LEO_PERF_RUN_ID（写测数据标记前缀 PERF-LOAD-$LEO_PERF_RUN_ID）"

# ---- 共享 token：全流程只登录一次 --------------------------------------------
# 服务端对同一账号有会话数上限（SessionManagementService.DEFAULT_MAX_REFRESH_TOKENS = 3），
# 第 4 次登录会吊销并拉黑最旧会话，使其 access token 立即 401。
# 因此压测用例、指标采集器、辅助调用必须共用同一个会话，绝不能各自登录。
resolve_shared_token() {
  if [[ -n "${LEO_PERF_TOKEN:-}" ]]; then
    log "复用外部提供的 LEO_PERF_TOKEN"
    export LEO_PERF_TOKEN
    return 0
  fi
  local attempt token
  for attempt in 1 2 3 4 5 6; do
    token="$(curl -s --max-time 15 -X POST "${LEO_PERF_BASE_URL}/v2.0/auth/login" \
      -H 'Content-Type: application/json' \
      -H "X-Idempotency-Key: shared-$RANDOM-$RANDOM" \
      -d "{\"loginName\":\"$LEO_PERF_LOGIN_NAME\",\"password\":\"$LEO_PERF_PASSWORD\"}" \
      | jq -r '.accessToken // empty')"
    if [[ -n "$token" ]]; then
      export LEO_PERF_TOKEN="$token"
      log "已获取共享 token（本次压测全程只登录一次）"
      return 0
    fi
    sleep 1
  done
  fail "无法获取共享 token：请确认该账号无其他客户端并发登录"
}

# ---- 探测服务可用性 ----------------------------------------------------------
probe() {
  local url="$1" label="$2" code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$url" || true)"
  [[ "$code" == "200" ]] || fail "$label 不可用（$url -> ${code:-无响应}）"
  log "$label 正常"
}
probe "${LEO_PERF_BASE_URL}/v2.0/health" "后端健康检查"
resolve_shared_token

# ---- 自动解析结算主体 ID（写接口必需） --------------------------------------
resolve_company_id() {
  [[ -n "${LEO_PERF_COMPANY_ID:-}" ]] && return 0
  local body id
  resolve_shared_token
  body="$(curl -s --max-time 10 -H "Authorization: Bearer $LEO_PERF_TOKEN" \
    "${LEO_PERF_BASE_URL}/v2.0/company-settings?page=0&size=5")"
  id="$(printf '%s' "$body" | jq -r 'if type=="array" then .[0].id else (.content // [])[0].id end // empty')"
  [[ -n "$id" && "$id" != "null" ]] || fail "未能解析结算主体 ID，请显式设置 LEO_PERF_COMPANY_ID"
  export LEO_PERF_COMPANY_ID="$id"
  log "自动解析结算主体 ID: $id"
}

FAILED=0

# 失败率读数：把 http_req_failed 打出来，避免「通过/失败」只有一个字，看不出量级。
# 教训：07-heavy 曾以 98.05% 失败跑完（346,686 请求里 339,955 失败），
# 但当时脚本既无阈值也不是零退出码，run.sh 照样打印「07-heavy 通过」。
# 现在 k6 脚本自带 thresholds，且这里对读数与退出码双重把关。
report_failure_rate() {
  local name="$1" json="$RESULT_DIR/$name.json" rate
  [[ -f "$json" ]] || { printf '?'; return; }
  rate="$(jq -r '.metrics.http_req_failed.value // empty' "$json" 2>/dev/null)"
  if [[ -n "$rate" ]]; then
    awk -v r="$rate" 'BEGIN { printf "%.3f%%", r*100 }'
  else
    printf '?'
  fi
}

run_k6() {
  local name="$1"; shift
  log "===== 开始 $name ====="
  local rc=0
  set +e
  "$K6" run "$@" --summary-export "$RESULT_DIR/$name.json" 2>&1 | tee "$RESULT_DIR/$name.log"
  rc=${PIPESTATUS[0]}
  set -e
  local rate; rate="$(report_failure_rate "$name")"
  # 兜底阈值可覆盖：race 阶段以 409 冲突为被测现象（k6 把非 2xx/3xx 计为 failed），
  # 其「失败率」本就很高，真正的异常由脚本内的 race_unexpected_status 阈值把关。
  local max_rate="${STAGE_MAX_FAILURE_RATE:-1}"
  if [[ $rc -ne 0 ]]; then
    log "$name 失败：k6 退出码=$rc，http_req_failed=$rate（详见 $RESULT_DIR/$name.log）"
    FAILED=1
  elif [[ "$rate" != "?" ]] && awk -v r="${rate%\%}" -v m="$max_rate" 'BEGIN { exit !(r > m) }'; then
    # k6 阈值若是漏配，这里仍然兜住：失败率超过本阶段上限一律视为失败。
    log "$name 失败：k6 返回 0，但 http_req_failed=$rate 超过 ${max_rate}% 兜底阈值"
    FAILED=1
  else
    log "$name 通过（http_req_failed=$rate）"
  fi
  return 0
}

run_smoke()  { resolve_company_id; run_k6 "01-smoke" "$K6_DIR/01-smoke.js"; }
run_heavy()  { resolve_company_id; run_k6 "07-heavy" "$K6_DIR/07-heavy.js"; }
run_race()   { resolve_company_id; STAGE_MAX_FAILURE_RATE=90 run_k6 "08-concurrency" "$K6_DIR/08-concurrency.js"; }
run_soak()   { resolve_company_id; run_k6 "09-soak" "$K6_DIR/09-soak.js"; }
run_metrics() {
  local seconds="${1:-300}" interval="${2:-5}"
  LEO_PERF_ENV_FILE="${LEO_PERF_ENV_FILE:-}" \
    bash "$SCRIPT_DIR/collect-metrics.sh" watch "$RESULT_DIR/metrics.csv" "$seconds" "$interval"
}
run_baseline() { run_k6 "02-baseline" "$K6_DIR/02-baseline.js"; }
run_read()   { run_k6 "03-read-mixed" --out "csv=$RESULT_DIR/03-read-samples.csv" "$K6_DIR/03-read-mixed.js"; }
run_write()  { resolve_company_id; run_k6 "04-write-mixed" "$K6_DIR/04-write-mixed.js"; }
run_spike()  { run_k6 "05-spike"    --out "csv=$RESULT_DIR/05-spike-samples.csv" "$K6_DIR/05-spike.js"; }

case "$STAGE" in
  smoke)       run_smoke ;;
  baseline)    run_baseline ;;
  read)        run_read ;;
  write)       run_write ;;
  spike)       run_spike ;;
  heavy)       run_heavy ;;
  race)        run_race ;;
  soak)        run_soak ;;
  metrics)     shift; run_metrics "$@" ;;
  all)
    run_smoke
    run_baseline
    run_read
    resolve_company_id
    run_write
    run_heavy
    run_race
    log "常规阶段完成（soak 需单独运行：run.sh soak，其耗时以小时计）"
    log "结果目录: $RESULT_DIR"
    ;;
  *) fail "未知阶段: $STAGE（可选 smoke|baseline|read|write|spike|heavy|race|soak|metrics|all）" ;;
esac

# 阶段失败必须以非零退出码结束：否则 CI/调用方会把「跑完了」当成「跑对了」。
if [[ $FAILED -ne 0 ]]; then
  log "存在失败阶段，退出码 1"
  exit 1
fi
