#!/usr/bin/env bash
# Soak 分段运行器。
#
# 为什么要分段：access token 有效期 600s 且**不可配置**
# （application.yml 的 access-expiration-ms 是硬编码值，没有环境变量占位），
# 而 soak 需要 2 小时。单次 k6 运行无法跨越 token 有效期：
# 一旦 token 过期，全部 VU 会转去重登，撞上会话数上限（默认 3）后互相吊销，
# 进而形成登录风暴并打满数据库连接池。
#
# 因此改为「每段独立登录一次」：段长必须明显小于 token 有效期（默认 8m < 540s 边界），
# 段与段之间串行，全流程只有当前段持有唯一会话。
#
# 用法：
#   bash leo/perf/soak-runner.sh [总分钟数] [每段分钟数]
#   bash leo/perf/soak-runner.sh 120 8      # 默认：2 小时，15 段
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OUT_DIR="$REPO_ROOT/tmp/perf"
TOTAL_MIN="${1:-120}"
SEG_MIN="${2:-8}"

log()  { printf '[soak] %s\n' "$*"; }
fail() { printf '[soak][ERROR] %s\n' "$*" >&2; exit 1; }

[[ -n "${LEO_PERF_ENV_FILE:-}" && -f "$LEO_PERF_ENV_FILE" ]] && source "$LEO_PERF_ENV_FILE"
[[ -n "${LEO_PERF_LOGIN_NAME:-}" && -n "${LEO_PERF_PASSWORD:-}" ]] || fail "缺少压测账号凭据"

BASE_URL="${LEO_PERF_BASE_URL:-http://127.0.0.1:11211/api}"
K6_BIN="${K6_BIN:-$(find "$REPO_ROOT/tmp" -maxdepth 3 -type f -name k6 -perm -u+x 2>/dev/null | head -1)}"
[[ -n "$K6_BIN" ]] || fail "未找到 k6，可用 K6_BIN 指定"

# 段长必须留出安全边界：600s 有效期 - 60s 边界 = 540s = 9m
(( SEG_MIN * 60 <= 540 )) || fail "每段 ${SEG_MIN}m 超出 token 安全边界(9m)，请调小"

SEGMENTS=$(( (TOTAL_MIN + SEG_MIN - 1) / SEG_MIN ))
mkdir -p "$OUT_DIR"
COMBINED="$OUT_DIR/metrics-soak-all.csv"
: > "$COMBINED"
log "计划：共 ${TOTAL_MIN} 分钟，分 ${SEGMENTS} 段，每段 ${SEG_MIN} 分钟"

ERR_TOTAL=0
for ((i = 1; i <= SEGMENTS; i++)); do
  log "---- 第 ${i}/${SEGMENTS} 段 ----"
  TOKEN=""
  for attempt in 1 2 3 4 5; do
    TOKEN="$(curl -s -m 15 -X POST "$BASE_URL/v2.0/auth/login" \
      -H 'Content-Type: application/json' \
      -H "X-Idempotency-Key: soakseg-$i-$attempt-$RANDOM" \
      -d "{\"loginName\":\"$LEO_PERF_LOGIN_NAME\",\"password\":\"$LEO_PERF_PASSWORD\"}" \
      | jq -r '.accessToken // empty')"
    [[ -n "$TOKEN" ]] && break
    sleep 2
  done
  [[ -n "$TOKEN" ]] || fail "第 ${i} 段登录失败，已中止（避免在无有效凭据下继续）"
  export LEO_PERF_TOKEN="$TOKEN"

  CMPARAMS="$OUT_DIR/metrics-soak-seg-$i.csv"
  LEO_PERF_ENV_FILE="${LEO_PERF_ENV_FILE:-}" bash "$SCRIPT_DIR/collect-metrics.sh" \
    watch "$CMPARAMS" "$(( SEG_MIN * 60 + 20 ))" 30 > "$OUT_DIR/soak-seg-$i-collector.log" 2>&1 &
  CPID=$!

  LEO_PERF_SOAK_DURATION="${SEG_MIN}m" "$K6_BIN" run \
    --summary-export "$OUT_DIR/soak-seg-$i.json" "$SCRIPT_DIR/k6/09-soak.js" \
    > "$OUT_DIR/soak-seg-$i.txt" 2>&1
  RC=$?
  kill "$CPID" 2>/dev/null; wait "$CPID" 2>/dev/null

  ERRS=$(grep -cE "Request Failed" "$OUT_DIR/soak-seg-$i.txt" 2>/dev/null || echo 0)
  ITERS=$(grep -oE "[0-9]+ complete and 0 interrupted" "$OUT_DIR/soak-seg-$i.txt" | tail -1 | awk '{print $1}')
  log "第 ${i} 段结束 rc=${RC} 错误=${ERRS} 迭代=${ITERS:-未知}"
  ERR_TOTAL=$(( ERR_TOTAL + ERRS ))

  if [[ -s "$CMPARAMS" ]]; then
    if [[ ! -s "$COMBINED" ]]; then cat "$CMPARAMS" >> "$COMBINED"; else tail -n +2 "$CMPARAMS" >> "$COMBINED"; fi
  fi
  [[ $RC -ne 0 ]] && log "⚠ 第 ${i} 段 k6 退出码 ${RC}（阈值未达标或发生错误，详见 soak-seg-$i.txt）"
done

log "全部段完成，累计错误=${ERR_TOTAL}"
log "合并后的服务端指标: $COMBINED"
log "用以下命令做泄漏趋势判定：bash $SCRIPT_DIR/collect-metrics.sh trend $COMBINED"
