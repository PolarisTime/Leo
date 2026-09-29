#!/usr/bin/env bash
# 泄漏检测器的双向验证（合成数据，不依赖后端、Redis 与真实压测数据，可随时离线运行）。
#
# 为什么必须验证：本套件的 soak 结论建立在 `collect-metrics.sh trend` 的堆内存判定上。
# 早期版本只用**均值**的最小二乘斜率，而 heap_used 在持续压力下是锯齿波
# （实测 2 小时：峰谷幅度 419 MB、86 次 GC），均值斜率会被 GC 相位主导，正负都会翻：
# 同一份稳态负载在 71.8 分钟窗口算出 -65.13 MB/h、在 119.9 分钟窗口算出 +18.92 MB/h，
# 后者会被判成「疑似内存泄漏」——**这是一次真实的误报**，差点写进报告。
#
# 因此判定改为「分桶最小值（≈GC 后存活水位）的斜率」+ 显著性检验（|t| < 2 视为噪声）。
# 本测试用合成数据双向验证：
#   正例（必须报泄漏）：注入 +60 / +20 MB/h 的存活水位增长
#   负例（必须不报）：水位恒定；水位恒定但带 ±25 / ±35 MB 抖动（真实数据的抖动量级）
#   边界：缓慢增长落入「轻微上升」档；观测窗口不足 15 分钟必须拒绝判定
#
# 用法： bash leo/perf/test/leak-detector-selftest.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
METRICS="$REPO_ROOT/leo/perf/collect-metrics.sh"
[[ -f "$METRICS" ]] || { echo "找不到 $METRICS" >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

cat > "$WORK/gen.py" <<'PY'
import csv, random, sys

def series(n, interval, floor_mb, amp_mb, period_s, ramp_mb_per_h, noise_mb, seed):
    """生成锯齿状堆序列：存活水位（floor）按 ramp 线性增长，每 period_s 被 GC 拉回水位。"""
    rnd = random.Random(seed)
    out = []
    for i in range(n):
        t = i * interval
        phase = (t % period_s) / period_s
        floor = floor_mb + ramp_mb_per_h * (t / 3600.0) + rnd.gauss(0, noise_mb)
        out.append(floor if phase > 0.97 else floor + amp_mb * phase)
    return out

COLS = ['ts','hikari_active','hikari_idle','hikari_pending','hikari_max','hikari_timeout_total',
        'heap_used_bytes','jvm_threads_live','sched_active','sched_queued','tomcat_busy','tomcat_current',
        'tomcat_conns','process_cpu','system_cpu','cache_hits','cache_misses','cache_puts',
        'http_count','http_sum_seconds','http_max_seconds']

def write(path, vals, interval):
    with open(path, 'w', newline='') as f:
        w = csv.writer(f); w.writerow(COLS)
        for i, v in enumerate(vals):
            s = int(i * interval)
            w.writerow([f"{9+s//3600:02d}:{(s//60)%60:02d}:{s%60:02d}"] + [0]*5 + [int(v*1048576)] + [0]*14)

CASES = {
    #           n   间隔 水位   锯齿幅度 周期  增长     抖动  种子
    'leak60':   (240, 30, 250, 400, 84, 60,  0,  1),
    'leak20':   (240, 30, 250, 400, 84, 20,  5,  3),
    'leak5':    (240, 30, 250, 400, 84,  5,  0,  2),
    'flat':     (240, 30, 250, 400, 84,  0,  0,  1),
    'flat25':   (240, 30, 250, 400, 84,  0, 25,  5),
    'flat35':   (240, 30, 250, 400, 84,  0, 35, 11),
    'short':    (20,  30, 250, 400, 84,  0,  0,  1),
}
if __name__ == '__main__':
    kind, path = sys.argv[1], sys.argv[2]
    n, interval, floor_mb, amp, period, ramp, noise, seed = CASES[kind]
    write(path, series(n, interval, floor_mb, amp, period, ramp, noise, seed), interval)
PY

pass=0; failed=0
chk() { # chk <用例> <必须包含的文本> <说明>
  local kind="$1" want="$2" desc="$3" out verdict
  out="$(bash "$METRICS" trend "$WORK/$kind.csv" 2>&1)"
  verdict="$(printf '%s\n' "$out" | grep -E '堆内存增长判定|不足以判定泄漏' | head -1)"
  if [[ "$verdict" == *"$want"* ]]; then
    printf '  ✅ %-9s %s\n     判定: %s\n' "$kind" "$desc" "$verdict"
    pass=$((pass+1))
  else
    printf '  ❌ %-9s 期望包含「%s」，实际判定: %s\n' "$kind" "$want" "$verdict"
    failed=$((failed+1))
  fi
  # 存活水位斜率行里若出现两个相反结论，必须同时给出「均值斜率仅供参考」的提示
  if printf '%s\n' "$out" | grep -q '堆内存存活水位斜率'; then
    if printf '%s\n' "$out" | grep -q '受 GC 相位影响'; then
      printf '     ✅ 同时给出均值斜率仅供参考的说明\n'
    else
      printf '     ❌ 缺少「均值斜率受 GC 相位影响」的说明\n'; failed=$((failed+1))
    fi
  fi
}

for kind in leak60 leak20 leak5 flat flat25 flat35 short; do
  python3 "$WORK/gen.py" "$kind" "$WORK/$kind.csv" || { echo "生成 $kind 失败" >&2; exit 1; }
done

echo "正例（必须报泄漏）:"
chk leak60 "疑似内存泄漏" "+60 MB/小时 的存活水位增长"
chk leak20 "疑似内存泄漏" "+20 MB/小时 的存活水位增长"
echo "边界（落入复核档，不得报「未见泄漏」）:"
chk leak5  "轻微上升"     "+5 MB/小时 的缓慢增长"
echo "负例（必须报未见泄漏）:"
chk flat   "未见泄漏迹象" "水位恒定"
chk flat25 "未见泄漏迹象" "水位恒定 + ±25MB 抖动（旧均值斜率逻辑会在此误报）"
chk flat35 "未见泄漏迹象" "水位恒定 + ±35MB 抖动（与真实 soak 数据同量级）"
echo "窗口保护:"
chk short  "不足以判定泄漏" "10 分钟窗口必须拒绝判定"

echo "结果: pass=$pass fail=$failed"
[[ $failed -eq 0 ]]
