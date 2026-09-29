#!/usr/bin/env bash
# 采集后端 Prometheus 指标，补齐「服务端盲区」。
#
# 背景：压测客户端与服务端同机时，k6 只能看到「客户端视角」的延迟；
# 连接池耗尽、GC、JVM 堆、缓存命中、Tomcat 线程排队都看不到。
#
# 前置：后端需暴露 prometheus 端点，即启动时设置
#   LEO_MANAGEMENT_ENDPOINTS=health,prometheus,loggers
# 若还需要 Tomcat 线程池/连接数指标，另需：
#   SERVER_TOMCAT_MBEANREGISTRY_ENABLED=true
#   （未开启时 tomcat_* 列恒为 0，属预期）
#
# 注意：cache_* 指标只覆盖 Spring Cache region（options/static/project-options）。
# 权限缓存走 RedisJsonCacheSupport 的裸 Redis 写入，不产生 micrometer 缓存指标。
#
# 用法：
#   bash leo/perf/collect-metrics.sh watch <输出.csv> <秒数> [采样间隔秒]
#   bash leo/perf/collect-metrics.sh summary <输出.csv>
set -uo pipefail

BASE_URL="${LEO_PERF_BASE_URL:-http://127.0.0.1:11211/api}"

log() { printf '[metrics] %s\n' "$*"; }
fail() { printf '[metrics][ERROR] %s\n' "$*" >&2; exit 1; }

if [[ -n "${LEO_PERF_ENV_FILE:-}" && -f "$LEO_PERF_ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$LEO_PERF_ENV_FILE"
fi

TOKEN="${LEO_PERF_TOKEN:-}"

login_once() {
  curl -s --max-time 10 -X POST "$BASE_URL/v2.0/auth/login" \
    -H 'Content-Type: application/json' \
    -H "X-Idempotency-Key: metrics-$RANDOM-$RANDOM" \
    -d "{\"loginName\":\"${LEO_PERF_LOGIN_NAME:-}\",\"password\":\"${LEO_PERF_PASSWORD:-}\"}" \
    | jq -r '.accessToken // empty'
}

# 只登录一次并复用 token。
# 每采样一次就登录一次会与被压测用例的 setup 登录竞争同一账号（sys_user 乐观锁 409），
# 一旦把 setup 打成 409，被测用例会退化为「每个 VU 每次迭代重登」的登录风暴，
# 从而把系统压垮并彻底污染测量结果。因此这里绝不能在采样循环里反复登录。
fetch_prometheus() {
  [[ -n "$TOKEN" ]] || TOKEN="$(login_once)"
  [[ -n "$TOKEN" ]] || fail "登录失败，无法读取 /actuator/prometheus"
  local tmp code
  tmp="$(mktemp)"
  code="$(curl -s --max-time 20 -o "$tmp" -w '%{http_code}' \
    -H "Authorization: Bearer $TOKEN" "$BASE_URL/actuator/prometheus")"
  if [[ "$code" != "200" ]]; then
    TOKEN="$(login_once)"
    code="$(curl -s --max-time 20 -o "$tmp" -w '%{http_code}' \
      -H "Authorization: Bearer $TOKEN" "$BASE_URL/actuator/prometheus")"
  fi
  cat "$tmp"
  rm -f "$tmp"
}

# 指标求和（忽略标签）；$1=prometheus文本 $2=指标名 $3=可选标签筛选
sum_metric() {
  printf '%s\n' "$1" | awk -v n="$2" -v sel="${3:-}" '
    {
      if (sel == "") { if ($0 ~ "^"n"([ {]|$)") { v=$NF; if (v ~ /^[0-9.eE+-]+$/) s+=v } }
      else { if (index($0, n"{")==1 && index($0, sel)>0) { v=$NF; if (v ~ /^[0-9.eE+-]+$/) s+=v } }
    }
    END { printf "%.4f", s+0 }'
}

COLUMNS=(
  "hikari_active:hikaricp_connections_active:"
  "hikari_idle:hikaricp_connections_idle:"
  "hikari_pending:hikaricp_connections_pending:"
  "hikari_max:hikaricp_connections_max:"
  "hikari_timeout_total:hikaricp_connections_timeout_total:"
  'heap_used_bytes:jvm_memory_used_bytes:area="heap"'
  "jvm_threads_live:jvm_threads_live_threads:"
  'sched_active:executor_active_threads:name="taskScheduler"'
  'sched_queued:executor_queued_tasks:name="taskScheduler"'
  "tomcat_busy:tomcat_threads_busy_threads:"
  "tomcat_current:tomcat_threads_current_threads:"
  "tomcat_conns:tomcat_connections_current_connections:"
  "process_cpu:process_cpu_usage:"
  "system_cpu:system_cpu_usage:"
  'cache_hits:cache_gets_total:result="hit"'
  'cache_misses:cache_gets_total:result="miss"'
  "cache_puts:cache_puts_total:"
  "http_count:http_server_requests_seconds_count:"
  "http_sum_seconds:http_server_requests_seconds_sum:"
  "http_max_seconds:http_server_requests_seconds_max:"
)

cmd_watch() {
  local out="${1:?缺少输出文件}" seconds="${2:?缺少秒数}" interval="${3:-2}"
  {
    printf 'ts'
    for c in "${COLUMNS[@]}"; do printf ',%s' "${c%%:*}"; done
    printf '\n'
  } > "$out"
  log "开始采样 ${seconds}s，间隔 ${interval}s -> $out"
  local end=$(( $(date +%s) + seconds ))
  while (( $(date +%s) < end )); do
    local body
    body="$(fetch_prometheus)" || { sleep "$interval"; continue; }
    {
      printf '%s' "$(date +%H:%M:%S)"
      for c in "${COLUMNS[@]}"; do
        local metric sel
        metric="$(printf '%s' "$c" | cut -d: -f2)"
        sel="$(printf '%s' "$c" | cut -d: -f3)"
        printf ',%s' "$(sum_metric "$body" "$metric" "$sel")"
      done
      printf '\n'
    } >> "$out"
    sleep "$interval"
  done
  log "采样结束，共 $(( $(wc -l < "$out") - 1 )) 个数据点"
}

cmd_summary() {
  local csv="${1:?缺少 CSV 文件}"
  [[ -f "$csv" ]] || fail "文件不存在: $csv"
  awk -F, '
    NR==1 { for (i=1;i<=NF;i++) { h[i]=$i; idx[$i]=i } ; next }
    {
      n++
      for (i=2;i<=NF;i++) {
        v=$i+0
        if (n==1 || v<min[i]) min[i]=v
        if (n==1 || v>max[i]) max[i]=v
        if (n==1) first[i]=v
        last[i]=v
      }
    }
    END {
      if (n==0) { print "无数据点"; exit }
      printf "采样点: %d\n\n", n
      printf "%-24s %16s %16s %16s\n", "指标", "最小", "最大", "末值-初值"
      for (i=2;i<=NF;i++) printf "%-24s %16.2f %16.2f %16.2f\n", h[i], min[i], max[i], last[i]-first[i]
      hits = last[idx["cache_hits"]] - first[idx["cache_hits"]]
      miss = last[idx["cache_misses"]] - first[idx["cache_misses"]]
      printf "\n"
      if (hits+miss > 0) printf "Spring Cache 命中率(区间增量): %.1f%% (%.0f hit / %.0f miss)\n", hits*100/(hits+miss), hits, miss
      else printf "Spring Cache 区间内无访问（该窗口未触发 options/static region）\n"
      to = last[idx["hikari_timeout_total"]] - first[idx["hikari_timeout_total"]]
      if (to > 0) printf "连接池获取超时(区间增量): %.0f —— 已出现连接池耗尽\n", to
      else printf "连接池获取超时(区间增量): 0 —— 未耗尽\n"
      printf "连接池活跃峰值: %.0f / 上限 %.0f，等待峰值: %.0f\n", max[idx["hikari_active"]], max[idx["hikari_max"]], max[idx["hikari_pending"]]
      printf "JVM 堆峰值: %.1f MB，存活线程峰值: %.0f，Tomcat忙碌峰值: %.0f\n", max[idx["heap_used_bytes"]]/1048576, max[idx["jvm_threads_live"]], max[idx["tomcat_busy"]]
    }' "$csv"
}

case "${1:-}" in
  watch)   shift; cmd_watch "$@" ;;
  summary) shift; cmd_summary "$@" ;;
  *) fail "用法: collect-metrics.sh watch <csv> <秒数> [间隔] | summary <csv>" ;;
esac
