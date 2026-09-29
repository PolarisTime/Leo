#!/usr/bin/env bash
# 采集器的登录次数自检（用本地桩服务，不依赖真实后端与 Redis）。
#
# 为什么必须验证：`collect-metrics.sh watch` 的 token 复用逻辑**曾经失效**——
# `fetch_prometheus` 被 `body="$(fetch_prometheus)"` 以命令替换调用，函数里的
# `TOKEN=...` 只存在于子 shell，父 shell 永远拿不到，于是每采样一次登录一次。
# 服务端同账号会话上限硬编码为 3，多余登录会吊销并拉黑既有会话，
# 结果是一次 07-heavy 压测的 346,686 个请求里 99.3% 变成 401（业务日志里无异常），
# 极易被误读成「接口故障」。因此「只登录一次」必须有可执行的证据，不能只看注释。
#
# 三个用例：
#   A 未提供共享 token：整个采集过程只允许登录 1 次
#   B 提供共享 token：一次都不允许登录
#   C 每第 3 次取指标都失败：必须重新登录（次数 ≥2）并打印会话吊销告警
#
# 用法： bash leo/perf/test/collector-login-selftest.sh
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
COLLECTOR="$REPO_ROOT/leo/perf/collect-metrics.sh"
[[ -f "$COLLECTOR" ]] || { echo "找不到 $COLLECTOR" >&2; exit 1; }

PORT_BASE="${STUB_PORT_BASE:-18899}"   # 每个用例用独立端口，避免上一个桩进程未完全退出导致绑定失败
PORT_A=$PORT_BASE
PORT_B=$(( PORT_BASE + 1 ))
PORT_C=$(( PORT_BASE + 2 ))
WORK="$(mktemp -d)"
SERVER_PID=""
cleanup() { [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null; rm -rf "$WORK"; }
trap cleanup EXIT

cat > "$WORK/stub.py" <<'PY'
"""桩服务：/api/v2.0/auth/login 计数登录，/api/actuator/prometheus 按需失败。"""
import json, os, sys, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FAIL_EVERY = int(sys.argv[1])       # 每第 N 次取指标返回 500；0 表示从不失败
COUNT_FILE = sys.argv[2]

lock = threading.Lock()
state = {"logins": 0, "prom": 0}

METRICS = "\n".join([
    "# HELP jvm_memory_used_bytes heap",
    "jvm_memory_used_bytes{area=\"heap\",id=\"G1 Eden Space\"} 1.0e8",
    "jvm_memory_used_bytes{area=\"heap\",id=\"G1 Old Gen\"} 2.0e8",
    "hikaricp_connections_active 3.0",
    "hikaricp_connections_idle 17.0",
    "hikaricp_connections_pending 0.0",
    "hikaricp_connections_max 20.0",
    "hikaricp_connections_timeout_total 452.0",
    "jvm_threads_live_threads 70.0",
    "executor_active_threads{name=\"taskScheduler\"} 0.0",
    "executor_queued_tasks{name=\"taskScheduler\"} 8.0",
    "tomcat_threads_busy_threads 0.0",
    "tomcat_threads_current_threads 0.0",
    "tomcat_connections_current_connections 0.0",
    "process_cpu_usage 0.01",
    "system_cpu_usage 0.02",
    "cache_gets_total{cache=\"options\",result=\"hit\"} 1.0",
    "cache_gets_total{cache=\"options\",result=\"miss\"} 1.0",
    "cache_puts_total{cache=\"options\"} 1.0",
    "http_server_requests_seconds_count 100.0",
    "http_server_requests_seconds_sum 10.0",
    "http_server_requests_seconds_max 0.5",
    "",
])

class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _send(self, code, body, ctype="application/json"):
        data = body.encode()
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if self.path.endswith("/auth/login"):
            with lock:
                state["logins"] += 1
                n = state["logins"]
                open(COUNT_FILE, "w").write(json.dumps(state))
            self._send(200, json.dumps({"accessToken": f"stub-token-{n}"}))
        else:
            self._send(404, "{}")

    def do_GET(self):
        if self.path.endswith("/health"):
            self._send(200, json.dumps({"status": "UP"}))
        elif "/actuator/prometheus" in self.path:
            with lock:
                state["prom"] += 1
                n = state["prom"]
                fail = FAIL_EVERY > 0 and n % FAIL_EVERY == 0
                open(COUNT_FILE, "w").write(json.dumps(state))
            if fail:
                self._send(500, "{}")
            else:
                self._send(200, METRICS, "text/plain")
        else:
            self._send(404, "{}")

ThreadingHTTPServer(("127.0.0.1", int(sys.argv[3])), H).serve_forever()
PY

start_stub() { # start_stub <fail_every> <port>
  printf '{"logins": 0, "prom": 0}' > "$WORK/count.json"
  python3 "$WORK/stub.py" "$1" "$WORK/count.json" "$2" &
  SERVER_PID=$!
  for _ in $(seq 1 50); do
    # 必须校验 200（且进程存活），否则桩没起来时测试会「静默通过」成别的原因
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
      echo "桩服务启动即退出，用例无法进行" >&2
      exit 1
    fi
    if [[ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 1 "http://127.0.0.1:$2/api/v2.0/health")" == "200" ]]; then
      return 0
    fi
    sleep 0.2
  done
  echo "桩服务未就绪（端口 $2）" >&2
  exit 1
}
stop_stub() { [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null; wait "$SERVER_PID" 2>/dev/null; SERVER_PID=""; sleep 0.5; }

logins_seen() { jq -r '.logins' "$WORK/count.json" 2>/dev/null || echo 0; }
rows_seen()   { [[ -f "$1" ]] && echo $(( $(wc -l < "$1") - 1 )) || echo 0; }

pass=0; failed=0
report() { # report <期望> <实际> <说明>
  if [[ "$1" == "$2" ]]; then
    printf '  ✅ %s（%s）\n' "$3" "$2"; pass=$((pass+1))
  else
    printf '  ❌ %s：期望 %s，实际 %s\n' "$3" "$1" "$2"; failed=$((failed+1))
  fi
}

# ---- 用例 A：未提供 token，指标从不失败 -> 只允许 1 次登录 ----
echo "用例 A（无共享 token、取指标从不失败）:"
start_stub 0 "$PORT_A"
env -u LEO_PERF_TOKEN LEO_PERF_BASE_URL="http://127.0.0.1:$PORT_A/api" \
  LEO_PERF_LOGIN_NAME=stub LEO_PERF_PASSWORD=stub \
  bash "$COLLECTOR" watch "$WORK/a.csv" 6 2 > "$WORK/a.log" 2>&1
stop_stub
report 1 "$(logins_seen)" "整个采集过程只登录 1 次"
if [[ "$(rows_seen "$WORK/a.csv")" -ge 2 ]]; then
  printf '  ✅ 采集到 %s 个数据点\n' "$(rows_seen "$WORK/a.csv")"; pass=$((pass+1))
else
  printf '  ❌ 数据点过少: %s\n' "$(rows_seen "$WORK/a.csv")"; failed=$((failed+1))
fi

# ---- 用例 B：提供共享 token -> 一次都不允许登录 ----
echo "用例 B（提供 LEO_PERF_TOKEN）:"
start_stub 0 "$PORT_B"
LEO_PERF_TOKEN=stub-shared-token LEO_PERF_BASE_URL="http://127.0.0.1:$PORT_B/api" \
  LEO_PERF_LOGIN_NAME=stub LEO_PERF_PASSWORD=stub \
  bash "$COLLECTOR" watch "$WORK/b.csv" 6 2 > "$WORK/b.log" 2>&1
stop_stub
report 0 "$(logins_seen)" "复用共享 token，未发生任何登录"
if [[ "$(rows_seen "$WORK/b.csv")" -ge 2 ]]; then
  printf '  ✅ 采集到 %s 个数据点\n' "$(rows_seen "$WORK/b.csv")"; pass=$((pass+1))
else
  printf '  ❌ 数据点过少: %s\n' "$(rows_seen "$WORK/b.csv")"; failed=$((failed+1))
fi

# ---- 用例 C：取指标周期性失败 -> 必须重登，且必须打印会话吊销告警 ----
echo "用例 C（每第 3 次取指标失败、提供共享 token）:"
start_stub 3 "$PORT_C"
# 窗口必须足够长：失败每 3 次才出现一次，2s 间隔下需要 ≥6 个采样点
# 才会出现第 2 次「失败→重登」，否则用例本身测不到告警分支。
LEO_PERF_TOKEN=stub-shared-token LEO_PERF_BASE_URL="http://127.0.0.1:$PORT_C/api" \
  LEO_PERF_LOGIN_NAME=stub LEO_PERF_PASSWORD=stub \
  bash "$COLLECTOR" watch "$WORK/c.csv" 12 2 > "$WORK/c.log" 2>&1
stop_stub
logins="$(logins_seen)"
if [[ "$logins" -ge 2 ]]; then
  printf '  ✅ 失败后确实重新登录（%s 次）\n' "$logins"; pass=$((pass+1))
else
  printf '  ❌ 失败未触发重登，登录次数=%s\n' "$logins"; failed=$((failed+1))
fi
if grep -q '采集期间登录了' "$WORK/c.log"; then
  printf '  ✅ 打印了会话吊销告警\n'; pass=$((pass+1))
else
  printf '  ❌ 未打印会话吊销告警（这正是不该发生的静默状态）\n'; failed=$((failed+1))
fi
if [[ "$(rows_seen "$WORK/c.csv")" -ge 4 ]]; then
  printf '  ✅ 失败样本被跳过、其余样本正常落盘（%s 个数据点）\n' "$(rows_seen "$WORK/c.csv")"; pass=$((pass+1))
else
  printf '  ❌ 采样基本没有产出: %s\n' "$(rows_seen "$WORK/c.csv")"; failed=$((failed+1))
fi

echo "结果: pass=$pass fail=$failed"
[[ $failed -eq 0 ]]
