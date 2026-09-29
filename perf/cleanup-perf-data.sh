#!/usr/bin/env bash
# 清理压测遗留数据：按 customerName 前缀检索并逐个删除。
#
# 压测脚本创建的客户 customerName 统一以 PERF-LOAD- 开头（含批次号），
# 本脚本默认先「预演计数」，加 --yes 才真正执行删除。
#
# 用法：
#   source tmp/perf/creds.env
#   bash leo/perf/cleanup-perf-data.sh                 # 只统计，不删除
#   bash leo/perf/cleanup-perf-data.sh --yes            # 删除全部 PERF-LOAD-* 客户
#   bash leo/perf/cleanup-perf-data.sh --yes PERF-LOAD-run20260101-120000   # 只删指定批次
set -euo pipefail

PREFIX="${2:-PERF-LOAD-}"
CONFIRM="${1:-}"
BASE_URL="${LEO_PERF_BASE_URL:-http://127.0.0.1:11211/api}"

[[ -n "${LEO_PERF_LOGIN_NAME:-}" && -n "${LEO_PERF_PASSWORD:-}" ]] \
  || { echo "缺少 LEO_PERF_LOGIN_NAME / LEO_PERF_PASSWORD" >&2; exit 1; }

TOKEN="$(curl -s --max-time 10 -X POST "$BASE_URL/v2.0/auth/login" \
  -H 'Content-Type: application/json' \
  -H "X-Idempotency-Key: cleanup-$RANDOM-$RANDOM" \
  -d "{\"loginName\":\"$LEO_PERF_LOGIN_NAME\",\"password\":\"$LEO_PERF_PASSWORD\"}" \
  | jq -r '.accessToken // empty')"
[[ -n "$TOKEN" ]] || { echo "登录失败，无法清理" >&2; exit 1; }

deleted=0
page=0
while :; do
  body="$(curl -s --max-time 20 -H "Authorization: Bearer $TOKEN" \
    --get --data-urlencode "keyword=$PREFIX" --data-urlencode "page=$page" --data-urlencode "size=200" \
    "$BASE_URL/v2.0/customers")"
  ids="$(printf '%s' "$body" | jq -r '(.content // [])[].id // empty' 2>/dev/null || true)"
  [[ -z "$ids" ]] && break

  while IFS= read -r id; do
    [[ -z "$id" ]] && continue
    if [[ "$CONFIRM" == "--yes" ]]; then
      code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X DELETE \
        "$BASE_URL/v2.0/customers/$id" \
        -H "Authorization: Bearer $TOKEN" \
        -H "X-Idempotency-Key: cleanup-$id-$RANDOM")"
      [[ "$code" == "204" ]] && deleted=$((deleted + 1)) || echo "删除失败 id=$id status=$code" >&2
    else
      deleted=$((deleted + 1))
    fi
  done <<< "$ids"
  page=$((page + 1))
  [[ $page -gt 100 ]] && break
done

if [[ "$CONFIRM" == "--yes" ]]; then
  echo "已删除 $deleted 条压测客户数据（前缀 $PREFIX）"
else
  echo "匹配到 $deleted 条压测客户数据（前缀 $PREFIX）；加 --yes 才会真正删除"
fi
