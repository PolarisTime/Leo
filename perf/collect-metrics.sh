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

LOGIN_COUNT=0

# 登录次数硬上限（不变量）：首次登录 + 至多 1 次容错重登 = 2 次。
# 超过这个数就不再登录（继续登录只会新增会话、顶掉正在压测的会话）。
LOGIN_HARD_CAP=2

# 只登录一次并复用 token。
#
# 为什么必须如此：服务端同账号会话上限硬编码为 3（SessionManagementService），
# 第 4 次登录会吊销并拉黑最旧会话。若采集器每次采样都登录，就会把**正在被压测的
# 那个会话**逐步顶掉，表现为被测用例大面积 401。
#
# 这个坑真实发生过：`fetch_prometheus` 被 `body="$(fetch_prometheus)"` 以**命令替换**
# 调用，函数内部的 `TOKEN=...` 赋值只存在于子 shell，永远传不回父 shell，
# 于是 TOKEN 一直为空、每采样一次登录一次。实测一次 07-heavy：采集器每 ~6 秒登录一次，
# 346,686 个请求里 99.3% 变成 401，而业务日志里看不到任何异常，极易被误读成接口故障。
# 现改为「写文件 + 计数」，并把登录次数升级为**硬不变量**（语义与 run.sh 一致）：
#   * 登录 0 次（复用 LEO_PERF_TOKEN）或 1 次（首次取指标时登录）→ 合法；
#   * 取指标失败后的容错重登**允许发生**（保证数据 salvage），但同样计入 LOGIN_COUNT；
#   * LOGIN_COUNT 达到 LOGIN_HARD_CAP 后拒绝再登录，失败采样只跳过；
#   * watch 结束时显式打印 LOGIN_COUNT，若 > 1 次 → 判定不变量破坏，脚本非零退出。
# 即：容错路径保留，但「一轮采集多于 1 次登录」不再是告警，而是失败——
# 采集期间的会话顶掉会让同时进行的压测失败率失真，绝不能静默通过。
ensure_token() {
  [[ -n "$TOKEN" ]] && return 0
  if (( LOGIN_COUNT >= LOGIN_HARD_CAP )); then
    log "⚠ 登录已达硬上限（$LOGIN_COUNT 次），不再重登，本次取指标跳过"
    return 1
  fi
  TOKEN="$(login_once)"
  LOGIN_COUNT=$(( LOGIN_COUNT + 1 ))
  [[ -n "$TOKEN" ]] || fail "登录失败，无法读取 /actuator/prometheus"
  return 0
}

# 采集一次指标写入文件 $1。
# ⚠ 必须直接调用，不要在 `$( )` 里调用本函数，否则 TOKEN/LOGIN_COUNT 的赋值会丢在子 shell。
fetch_prometheus_to() {
  local out="$1" code
  ensure_token || return 1
  code="$(curl -s --max-time 20 -o "$out" -w '%{http_code}' \
    -H "Authorization: Bearer $TOKEN" "$BASE_URL/actuator/prometheus")"
  if [[ "$code" != "200" ]]; then
    log "⚠ /actuator/prometheus 返回 $code，重新登录一次后重试（重登计入登录不变量）"
    TOKEN=""
    if ! ensure_token; then
      return 1
    fi
    code="$(curl -s --max-time 20 -o "$out" -w '%{http_code}' \
      -H "Authorization: Bearer $TOKEN" "$BASE_URL/actuator/prometheus")"
  fi
  [[ "$code" == "200" ]] || { log "⚠ 本轮采样失败（HTTP $code），跳过"; return 1; }
  return 0
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
  local tmp; tmp="$(mktemp)"
  while (( $(date +%s) < end )); do
    local body
    if ! fetch_prometheus_to "$tmp"; then
      sleep "$interval"
      continue
    fi
    body="$(cat "$tmp")"
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
  rm -f "$tmp"
  log "采样结束，共 $(( $(wc -l < "$out") - 1 )) 个数据点，采集期间登录 $LOGIN_COUNT 次"
  # ---- 登录次数不变量（硬门禁，与 run.sh 语义一致）----
  # 结束时显式打印的 LOGIN_COUNT 已包含「首次登录」与「取指标失败后的容错重登」。
  # 只要 > 1 次就判定不变量破坏并以非零退出：服务端同账号会话上限硬编码为 3，
  # 多余登录会静默吊销并拉黑最旧会话；若同时有压测在跑，其 401 就是本采集器造成的，
  # 此时采集数据本身也可能因会话抖动而失真，绝不能只告警后「静默通过」。
  if (( LOGIN_COUNT > 1 )); then
    log "✗✗ 登录不变量被破坏：采集期间登录了 $LOGIN_COUNT 次（硬上限 1 次）"
    log "    服务端同账号会话上限硬编码为 3，多余登录会静默吊销并拉黑最旧会话，"
    log "    正在压测的会话会被顶掉（表现为大面积 401，业务日志无异常）。"
    log "    请通过 LEO_PERF_TOKEN 复用共享会话；本采集器以退出码 1 失败。"
    return 1
  fi
  return 0
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
      # ---- 锯齿诊断 + 存活水位斜率 ----
      # heap_used 在持续压力下是锯齿波：每轮 GC 把堆拉回低水位，再逐步涨回去。
      # 此时**均值**的最小二乘斜率会被 GC 相位主导，正负都可能翻——同一份稳态负载
      # 在 71.8 分钟窗口算出 -65.13 MB/h、在 119.9 分钟窗口算出 +18.92 MB/h，
      # 两者互相矛盾，都不能当作泄漏证据。
      # 泄漏的真实特征不是均值升高，而是 **GC 后的存活水位单调抬高**（对象回收不掉）。
      # 因此判定改用「分桶最小值」的斜率：桶内 GC 次数足够时，最小值≈存活水位。
      # 并且必须做显著性检验：真实数据的水位本身就有 ±35 MB 的抖动，
      # 只看斜率会给出一条毫无意义的「+7 MB/h」并误报为「轻微上升」。
      buckets = (n >= 24) ? 12 : 0
      bsize = (buckets > 0) ? int(n/buckets) : n
      if (bsize < 2) bsize = 2
      for (r=1; r<=n; r++) {
        split(row[r], a, ","); v=a[hi]+0
        if (v > gmax) gmax=v
        if (r==1 || v < gmin) gmin=v
        if (r > 1 && pv - v > 10*1048576) {
          gcCount++; gcReclaimed += (pv - v); gcb[int((r-2)/bsize)]++
        }
        pv = v
      }
      printf "锯齿诊断: 峰谷幅度 %.1f MB，GC 回收(单次 >10MB) %d 次，共回收 %.0f MB\n", \
        (gmax-gmin)/1048576, gcCount, gcReclaimed/1048576
      if (buckets == 0) {
        printf "堆内存存活水位斜率: 样本仅 %d 个，不足以做分桶水位分析，只能参考均值斜率\n", n
      } else {
        nb = 0
        for (r=1; r<=n; r++) {
          split(row[r], a, ","); v=a[hi]+0
          b = int((r-1)/bsize)
          if (!(b in bmin) || v < bmin[b]) bmin[b]=v
          if (b+1 > nb) nb = b+1
        }
        for (k=0; k<nb; k++) { x=k+1; y=bmin[k]/1048576; sx2+=x; sy2+=y; sxx2+=x*x; sxy2+=x*y
          if (!(k in gcb)) emptyGC++ }
        den2 = nb*sxx2 - sx2*sx2
        slope2 = (den2 != 0) ? (nb*sxy2 - sx2*sy2)/den2 : 0
        mx2 = sx2/nb; my2 = sy2/nb
        inter2 = my2 - slope2*mx2
        for (k=0; k<nb; k++) { x=k+1; e=(bmin[k]/1048576)-(inter2+slope2*x); sse2 += e*e }
        residVar = (nb > 2) ? sse2/(nb-2) : 0   # 注意不能叫 s2：上面 s1/s2 已是数组
        se = (sxx2 > sx2*sx2/nb) ? sqrt(residVar/(sxx2 - sx2*sx2/nb)) : 0
        perHour2 = slope2 * 3600 / (bsize*interval)   # 每桶跨 bsize*interval 秒
        seHour = se * 3600 / (bsize*interval)
        tStat = (seHour > 0) ? (perHour2/seHour) : 0
        printf "堆内存存活水位斜率(GC 后最小值, %d 桶): %+.2f ± %.2f MB/小时（t=%.2f）\n", \
          nb, perHour2, seHour, tStat
        printf "  首桶水位 %.1f MB → 末桶水位 %.1f MB\n", bmin[0]/1048576, bmin[nb-1]/1048576
        if (emptyGC > 0) printf "  注意: %d 个桶内未观察到 >10MB 的回收，其最小值可能不是真实存活水位\n", emptyGC
        printf "  （对比）均值斜率: %+.2f MB/小时（受 GC 相位影响，仅供参考）\n", perHour
        perHour = perHour2
        # 显著性门槛：|t| < 2 时把斜率判为噪声。合成数据双向验证：
        # 注入 +60 MB/h → t=56、注入 +20 MB/h → t=14.7（均判为泄漏）；
        # 无泄漏 → t=0；带 ±25MB 水位噪声的无泄漏 → t=0.13（均判为未见泄漏）。
        if (tStat < 2 && tStat > -2) noiseOnly = 1
      }

      if (noiseOnly) {
        printf "堆内存增长判定: %+.2f MB/小时，但 t=%.2f < 2（漂移在噪声范围内）  ✅ 未见泄漏迹象\n", \
          perHour, tStat
      } else if (perHour > 8) {
        printf "堆内存增长判定: %+.2f MB/小时，t=%.2f  ⚠ 持续增长明显，疑似内存泄漏\n", perHour, tStat
      } else if (perHour > 2) {
        printf "堆内存增长判定: %+.2f MB/小时，t=%.2f  ⚠ 轻微上升，建议结合 GC 指标复核\n", perHour, tStat
      } else {
        printf "堆内存增长判定: %+.2f MB/小时，t=%.2f  ✅ 基本平稳，未见泄漏迹象\n", perHour, tStat
      }
      # 连接池与错误累积
      # 计数器必须看区间增量：hikaricp_connections_timeout_total 是自进程启动的累计值，
      # 直接打印绝对值会把「本窗口开始前就存在」的历史残留误读成本次窗口的问题
      # （实测一次：窗口内各区段增量均为 0，但累计值 452 来自此前测试，极易误判）。
      split(row[1], rowFirst, ",")
      split(row[n], rowLast, ",")
      toi = idx["hikari_timeout_total"]
      printf "连接池获取超时（本窗口增量）: %.0f\n", rowLast[toi]-rowFirst[toi]
      printf "  窗口起始时已存在的累计值: %.0f（不属本窗口）\n", rowFirst[toi]
      printf "样本跨度: %.1f 分钟\n", total/60
    }' "$csv"
}

case "${1:-}" in
  watch)   shift; cmd_watch "$@" ;;
  summary) shift; cmd_summary "$@" ;;
  trend)   shift; cmd_trend "$@" ;;
  *) fail "用法: collect-metrics.sh watch <csv> <秒数> [间隔] | summary <csv> | trend <csv>" ;;
esac
