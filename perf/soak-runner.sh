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

  # 必须解析结算主体 ID：finance/overview 与 cash-ledger 需要 settlementCompanyId，
  # 缺失时它们会整段返回 400。曾因漏掉这一步导致 8.4% 的请求失败，
  # 而当时只 grep 了传输层错误（Request Failed），完全没有察觉。
  if [[ -z "${LEO_PERF_COMPANY_ID:-}" ]]; then
    LEO_PERF_COMPANY_ID="$(curl -s -m 10 -H "Authorization: Bearer $TOKEN" \
      "$BASE_URL/v2.0/company-settings?page=0&size=1" \
      | jq -r 'if type=="array" then .[0].id else (.content // [])[0].id end // empty')"
    [[ -n "$LEO_PERF_COMPANY_ID" && "$LEO_PERF_COMPANY_ID" != "null" ]] \
      || fail "第 ${i} 段未能解析结算主体 ID，财务/台账接口将整段失败"
    export LEO_PERF_COMPANY_ID
    log "  结算主体 ID = $LEO_PERF_COMPANY_ID"
  fi

  CMPARAMS="$OUT_DIR/metrics-soak-seg-$i.csv"
  LEO_PERF_ENV_FILE="${LEO_PERF_ENV_FILE:-}" bash "$SCRIPT_DIR/collect-metrics.sh" \
    watch "$CMPARAMS" "$(( SEG_MIN * 60 + 20 ))" 30 > "$OUT_DIR/soak-seg-$i-collector.log" 2>&1 &
  CPID=$!

  LEO_PERF_SOAK_DURATION="${SEG_MIN}m" "$K6_BIN" run \
    --summary-export "$OUT_DIR/soak-seg-$i.json" "$SCRIPT_DIR/k6/09-soak.js" \
    > "$OUT_DIR/soak-seg-$i.txt" 2>&1
  RC=$?
  kill "$CPID" 2>/dev/null; wait "$CPID" 2>/dev/null

  # 用 awk 计数：始终输出单个数字且退出码为 0。
  # 不能用 `grep -c ... || echo 0`——无匹配时 grep 返回 1 会触发 echo，
  # 使变量变成两行（"0\n0"），进而让下面的算术展开报错并中断循环。
  ERRS=$(awk '/Request Failed/{n++} END{print n+0}' "$OUT_DIR/soak-seg-$i.txt" 2>/dev/null)
  ITERS=$(awk '/complete and [0-9]+ interrupted/{if (match($0, /[0-9]+ complete/)) {v=substr($0, RSTART, RLENGTH-9)}} END{print v+0}' "$OUT_DIR/soak-seg-$i.txt" 2>/dev/null)
  log "第 ${i} 段结束 rc=${RC} 错误=${ERRS:-0} 迭代=${ITERS:-0}"
  ERR_TOTAL=$(( ERR_TOTAL + ${ERRS:-0} ))

  if [[ -s "$CMPARAMS" ]]; then
    if [[ ! -s "$COMBINED" ]]; then cat "$CMPARAMS" >> "$COMBINED"; else tail -n +2 "$CMPARAMS" >> "$COMBINED"; fi
  fi
  [[ $RC -ne 0 ]] && log "⚠ 第 ${i} 段 k6 退出码 ${RC}（阈值未达标或发生错误，详见 soak-seg-$i.txt）"
done

DONE_SEGS=$(ls "$OUT_DIR"/soak-seg-*.json 2>/dev/null | wc -l)
if (( DONE_SEGS < SEGMENTS )); then
  fail "计划 ${SEGMENTS} 段，实际仅完成 ${DONE_SEGS} 段即退出。这属于静默假成功——必须显式失败而不是宣称完成。请检查各段 soak-seg-*.txt 的结尾，以及上面是否出现过算术/循环异常。"
fi
log "全部 ${DONE_SEGS} 段完成，累计错误=${ERR_TOTAL}"
log "合并后的服务端指标: $COMBINED"
log "用以下命令做泄漏趋势判定：bash $SCRIPT_DIR/collect-metrics.sh trend $COMBINED"

# 结束即聚合：若存在失败段则以其退出码结束，避免「跑完 2 小时却带着大量失败」
# 被当成成功。此前的 8.4% 4xx 正是这样逃过检查的。
AGG_OUT="$(python3 "$SCRIPT_DIR/soak-aggregate.py" "$OUT_DIR" 2>&1)"
echo "$AGG_OUT"
python3 "$SCRIPT_DIR/soak-aggregate.py" "$OUT_DIR" > /dev/null 2>&1 \
  || fail "soak 聚合发现存在失败段，详见上方总览（服务端 5xx 或客户端 4xx 不为 0）"
