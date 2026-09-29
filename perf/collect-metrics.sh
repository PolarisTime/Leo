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

# 前后半段对比：长跑中的泄漏表现为「后半段均值显著高于前半段」，而非绝对值大小。
# 同时用最小二乘给出堆内存的增长斜率（MB/小时），用于量化泄漏速率。
cmd_trend() {
  local csv="${1:?缺少 CSV 文件}"
  [[ -f "$csv" ]] || fail "文件不存在: $csv"
  awk -F, '
    NR==1 { for (i=1;i<=NF;i++) { h[i]=$i; idx[$i]=i } ; next }
    { n++; row[n]=$0; ts[n]=$1 }
    END {
      if (n < 6) { printf "数据点仅 %d 个（<6），不足以做前后半段对比\n", n; exit }
      half = int(n/2)
      for (r=1; r<=n; r++) {
        split(row[r], a, ",")
        for (i=2; i<=NF; i++) {
          v = a[i]+0
          if (r <= half) s1[i] += v; else s2[i] += v
        }
      }
      cnt1 = half; cnt2 = n - half
      printf "数据点: %d（前半段 %d / 后半段 %d）\n\n", n, cnt1, cnt2
      printf "%-24s %14s %14s %12s\n", "指标", "前半段均值", "后半段均值", "变化"
      for (i=2; i<=NF; i++) {
        m1 = s1[i]/cnt1; m2 = s2[i]/cnt2
        d = (m1 != 0) ? (m2-m1)/m1*100 : 0
        printf "%-24s %14.2f %14.2f %11.1f%%\n", h[i], m1, m2, d
      }
      # 堆内存最小二乘斜率（MB/小时）
      hi = idx["heap_used_bytes"]
      for (r=1; r<=n; r++) { split(row[r], a, ","); x=r; y=a[hi]+0; sx+=x; sy+=y; sxx+=x*x; sxy+=x*y }
      den = n*sxx - sx*sx
      slope = (den != 0) ? (n*sxy - sx*sy)/den : 0
      # 由首末时间戳推算采样间隔
      split(ts[1], t1, ":"); split(ts[n], t2, ":")
      sec1 = t1[1]*3600 + t1[2]*60 + t1[3]
      sec2 = t2[1]*3600 + t2[2]*60 + t2[3]
      if (sec2 < sec1) sec2 += 86400          # 跨零点
      total = sec2 - sec1
      interval = (n > 1 && total > 0) ? total/(n-1) : 1
      perHour = slope * 3600 / interval / 1048576
      printf "\n样本跨度: %.1f 分钟，指标 %d 个\n", total/60, n
      # 最小观测窗口保护：JVM 预热期堆会快速上涨，短窗口算出的斜率纯属噪声，
      # 若就此报「疑似泄漏」会造成误报（实测 2.6 分钟窗口会算出 +7000 MB/h 的假斜率）。
      if (total/60 < 15) {
        printf "堆内存增长斜率: %+.2f MB/小时（观测窗口不足 15 分钟，受 JVM 预热影响，不足以判定泄漏）\n", perHour
        printf "→ 请延长观测窗口后重跑 trend\n"
        exit
      }
      printf "堆内存增长斜率: %+.2f MB/小时", perHour
      if (perHour > 8) printf "  ⚠ 持续增长明显，疑似内存泄漏\n"
      else if (perHour > 2) printf "  ⚠ 轻微上升，建议结合 GC 指标复核\n"
      else printf "  ✅ 基本平稳，未见泄漏迹象\n"
      # 连接池与错误累积
      ho = last_leak = 0
      printf "连接池获取超时累计: %.0f\n", a[idx["hikari_timeout_total"]]+0
      printf "样本跨度: %.1f 分钟\n", total/60
    }' "$csv"
}

case "${1:-}" in
  watch)   shift; cmd_watch "$@" ;;
  summary) shift; cmd_summary "$@" ;;
  trend)   shift; cmd_trend "$@" ;;
  *) fail "用法: collect-metrics.sh watch <csv> <秒数> [间隔] | summary <csv> | trend <csv>" ;;
esac
