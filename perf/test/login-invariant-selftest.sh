#!/usr/bin/env bash
# 登录次数不变量的自检（本地桩服务 + 现场提取函数，不依赖后端与 Redis）。
#
# 不变量定义：一轮压测（run.sh 的一次进程）内，真实登录（POST /v2.0/auth/login
# 且成功换回 access token）次数必须 <= 1；采集器 collect-metrics.sh 以同语义
# 独立计数，其自身登录次数 > 1 同样必须失败退出。
#
# 为什么是硬门禁而不是告警：服务端同账号会话上限硬编码为 3，第 4 次登录会
# 静默吊销并拉黑最旧会话——压测用例、指标采集器、辅助调用各自登录时互相顶掉，
# 表现为被测用例大面积 401 而业务日志毫无异常（实测 07-heavy：346,686 请求
# 99.3% 变 401）。只告警不失败的版本会让「跑完了」被误读成「跑对了」。
#
# 与 collector-login-selftest.sh 的分工：那个测试验证「采集器只登录一次」的
# 行为本身（桩侧计数）；本测试验证**不变量的判定与失败路径**——run.sh 侧的
# resolve_shared_token / assert_login_invariant、超限必须非零退出并说清后果、
# STAGE=login 豁免，以及采集器计数语义（0/1 次通过、重登超限失败）。
#
# 本测试从 run.sh 里**现场提取**函数定义，而不是复制一份，
# 避免出现「测的代码和跑的代码不是同一份」。
#
# 用例：
#   A run.sh 复用外部 LEO_PERF_TOKEN -> 登录 0 次 -> 通过
#   B run.sh 首次获取共享 token -> 登录 1 次 -> 通过（桩侧计数 = 1）
#   C run.sh 二次登录企图 -> 被防御门禁拒绝、非零退出，桩侧不得发生第 2 次登录
#   D 登录计数 = 2 -> assert_login_invariant 非零退出并打印后果（上限 3/静默吊销）
#   E STAGE=login（06-login 专项）-> 豁免判定，退出码 0，但仍显式打印登录次数
#   F 采集器计数语义：0 次/1 次登录 -> 通过；容错重登达到 2 次 -> 非零退出
#     并在结束时显式打印总次数、拒绝继续重登
#
# 用法： bash leo/perf/test/login-invariant-selftest.sh
set -uo pipefail

# 被测文件相对本脚本自身定位：仓库目录名在不同检出形态下不同（主仓库 leo、
# worktree d/b6、GitHub Actions 的 Leo），不能写死 ../../..+leo/。
PERF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_SH="$PERF_DIR/run.sh"
COLLECTOR="$PERF_DIR/collect-metrics.sh"
[[ -f "$RUN_SH" ]] || { echo "找不到 $RUN_SH" >&2; exit 1; }
[[ -f "$COLLECTOR" ]] || { echo "找不到 $COLLECTOR" >&2; exit 1; }

WORK="$(mktemp -d)"
SERVER_PID=""
cleanup() { [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null; rm -rf "$WORK"; }
trap cleanup EXIT

# ---- 现场提取 run.sh 的登录不变量函数 ----------------------------------------
FUNCS="$WORK/funcs.sh"
sed -n \
  -e '/^log()/,/^fail()/p' \
  -e '/^PERF_LOGIN_COUNT=0$/p' \
  -e '/^record_perf_login()/,/^}/p' \
  -e '/^assert_login_invariant()/,/^}/p' \
  -e '/^resolve_shared_token()/,/^}/p' \
  "$RUN_SH" > "$FUNCS"
for need in '^log()' '^fail()' '^PERF_LOGIN_COUNT=0' '^record_perf_login()' \
            '^assert_login_invariant()' '^resolve_shared_token()'; do
  grep -q "$need" "$FUNCS" || { echo "未能从 $RUN_SH 提取 $need，测试无效" >&2; exit 1; }
done
# shellcheck disable=SC1090
source "$FUNCS"

# ---- 桩服务：登录计数 + 按需失败的 /actuator/prometheus -----------------------
PORT_BASE="${STUB_PORT_BASE:-18910}"   # 每个用例独立端口，避免上一桩进程未退出导致绑定失败
COUNTER=0
next_port() { COUNTER=$(( COUNTER + 1 )); printf '%s' "$(( PORT_BASE + COUNTER ))"; }

cat > "$WORK/stub.py" <<'PY'
"""桩服务：/api/v2.0/auth/login 计数登录，/api/actuator/prometheus 按需失败。"""
import json, sys, threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

FAIL_EVERY = int(sys.argv[1])   # 每第 N 次取指标返回 500；0 表示从不失败
COUNT_FILE = sys.argv[2]

lock = threading.Lock()
state = {"logins": 0, "prom": 0}

METRICS = "\n".join([
    '# HELP jvm_memory_used_bytes heap',
    'jvm_memory_used_bytes{area="heap",id="G1 Old Gen"} 2.0e8',
    'hikaricp_connections_active 3.0',
    'hikaricp_connections_max 20.0',
    'http_server_requests_seconds_count 100.0',
    '',
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
report_contains() { # report_contains <文件> <模式> <说明>
  if grep -q "$2" "$1"; then
    printf '  ✅ %s\n' "$3"; pass=$((pass+1))
  else
    printf '  ❌ %s（未找到模式: %s）\n' "$3" "$2"; failed=$((failed+1))
  fi
}

# ---- 用例 A：复用外部 token -> 0 次登录 -> 通过 ------------------------------
echo "用例 A（run.sh 复用 LEO_PERF_TOKEN）:"
(
  export LEO_PERF_TOKEN=extern-shared-token
  export LEO_PERF_BASE_URL="http://127.0.0.1:1/api"   # 走早返回分支，不会真的访问
  PERF_LOGIN_COUNT=0
  resolve_shared_token
  assert_login_invariant
) > "$WORK/a.log" 2>&1
report 0 "$?" "登录 0 次 → 判定通过（退出码 0）"
report_contains "$WORK/a.log" "本轮真实登录 0 次" "结束时显式打印登录次数 0"

# ---- 用例 B：首次登录 -> 1 次 -> 通过 ----------------------------------------
echo "用例 B（run.sh 首次获取共享 token）:"
PORT_B="$(next_port)"
start_stub 0 "$PORT_B"
(
  unset LEO_PERF_TOKEN
  export LEO_PERF_BASE_URL="http://127.0.0.1:$PORT_B/api"
  export LEO_PERF_LOGIN_NAME=stub LEO_PERF_PASSWORD=stub
  PERF_LOGIN_COUNT=0
  resolve_shared_token
  assert_login_invariant
) > "$WORK/b.log" 2>&1
b_rc=$?
stop_stub
report 0 "$b_rc" "登录 1 次 → 判定通过（退出码 0）"
report 1 "$(logins_seen)" "桩侧只发生 1 次真实登录"
report_contains "$WORK/b.log" "本轮真实登录 1 次" "结束时显式打印登录次数 1"

# ---- 用例 C：二次登录企图 -> 被防御门禁拒绝 ----------------------------------
echo "用例 C（run.sh 二次登录企图）:"
PORT_C="$(next_port)"
start_stub 0 "$PORT_C"
(
  unset LEO_PERF_TOKEN
  export LEO_PERF_BASE_URL="http://127.0.0.1:$PORT_C/api"
  export LEO_PERF_LOGIN_NAME=stub LEO_PERF_PASSWORD=stub
  PERF_LOGIN_COUNT=0
  resolve_shared_token        # 第 1 次真实登录成功
  unset LEO_PERF_TOKEN        # 模拟 token 丢失后再次解析
  resolve_shared_token        # 必须被拒绝，不得发生第 2 次登录
) > "$WORK/c.log" 2>&1
c_rc=$?
stop_stub
report 1 "$c_rc" "二次登录企图 → 非零退出"
report 1 "$(logins_seen)" "桩侧仍然只有 1 次登录（第 2 次被拒绝）"
report_contains "$WORK/c.log" "拒绝再次登录" "报错指明拒绝再次登录"
report_contains "$WORK/c.log" "静默吊销" "报错指出静默吊销最旧会话的后果"

# ---- 用例 D：计数 = 2 -> 不变量判定失败 --------------------------------------
echo "用例 D（登录计数达到 2 次）:"
(
  PERF_LOGIN_COUNT=0
  record_perf_login
  record_perf_login
  assert_login_invariant
) > "$WORK/d.log" 2>&1
d_rc=$?
report 1 "$d_rc" "登录 2 次 → 非零退出"
report_contains "$WORK/d.log" "登录次数不变量被破坏" "报错明确指出不变量被破坏"
report_contains "$WORK/d.log" "上限硬编码为 3" "报错指出服务端会话上限 3"
report_contains "$WORK/d.log" "静默吊销" "报错指出静默吊销最旧会话的后果"
report_contains "$WORK/d.log" "真实登录 2 次" "报错给出实际登录次数"

# ---- 用例 E：STAGE=login 豁免 ------------------------------------------------
echo "用例 E（STAGE=login 登录专项豁免）:"
(
  STAGE=login
  PERF_LOGIN_COUNT=0
  record_perf_login
  record_perf_login
  assert_login_invariant
) > "$WORK/e.log" 2>&1
e_rc=$?
report 0 "$e_rc" "STAGE=login → 豁免判定，退出码 0"
report_contains "$WORK/e.log" "豁免登录次数不变量" "打印豁免说明"
report_contains "$WORK/e.log" "真实登录 2 次" "豁免时仍显式打印登录次数"

# ---- 用例 F：采集器路径的计数语义 --------------------------------------------
echo "用例 F1（采集器：复用共享 token、取指标从不失败）:"
PORT_F1="$(next_port)"
start_stub 0 "$PORT_F1"
LEO_PERF_TOKEN=stub-shared-token LEO_PERF_BASE_URL="http://127.0.0.1:$PORT_F1/api" \
  LEO_PERF_LOGIN_NAME=stub LEO_PERF_PASSWORD=stub \
  bash "$COLLECTOR" watch "$WORK/f1.csv" 6 2 > "$WORK/f1.log" 2>&1
f1_rc=$?
stop_stub
report 0 "$(logins_seen)" "共享 token → 采集器 0 次登录"
report 0 "$f1_rc" "0 次 → 退出码 0"
report_contains "$WORK/f1.log" "采集期间登录 0 次" "结束时显式打印登录总次数"

echo "用例 F2（采集器：无共享 token、取指标从不失败）:"
PORT_F2="$(next_port)"
start_stub 0 "$PORT_F2"
env -u LEO_PERF_TOKEN LEO_PERF_BASE_URL="http://127.0.0.1:$PORT_F2/api" \
  LEO_PERF_LOGIN_NAME=stub LEO_PERF_PASSWORD=stub \
  bash "$COLLECTOR" watch "$WORK/f2.csv" 6 2 > "$WORK/f2.log" 2>&1
f2_rc=$?
stop_stub
report 1 "$(logins_seen)" "首次取指标登录 1 次"
report 0 "$f2_rc" "1 次 → 退出码 0"
report_contains "$WORK/f2.log" "采集期间登录 1 次" "结束时显式打印登录总次数"

echo "用例 F3（采集器：共享 token + 取指标周期性失败 → 容错重登超限）:"
PORT_F3="$(next_port)"
# FAIL_EVERY=2：第 1 次失败触发容错重登（计数 1），第 2 次失败重登（计数 2），
# 此后达硬上限拒绝再登录；watch 结束时计数 2 > 1 → 不变量破坏 → 非零退出。
start_stub 2 "$PORT_F3"
LEO_PERF_TOKEN=stub-shared-token LEO_PERF_BASE_URL="http://127.0.0.1:$PORT_F3/api" \
  LEO_PERF_LOGIN_NAME=stub LEO_PERF_PASSWORD=stub \
  bash "$COLLECTOR" watch "$WORK/f3.csv" 8 2 > "$WORK/f3.log" 2>&1
f3_rc=$?
stop_stub
report 2 "$(logins_seen)" "容错重登后登录总次数封顶为 2"
if [[ "$f3_rc" -ne 0 ]]; then
  printf '  ✅ 登录 2 次 → 采集器非零退出（rc=%s）\n' "$f3_rc"; pass=$((pass+1))
else
  printf '  ❌ 登录 2 次但采集器退出码 0：不变量只是告警，等于没有门禁\n'; failed=$((failed+1))
fi
report_contains "$WORK/f3.log" "采集期间登录 2 次" "结束时显式打印登录总次数"
report_contains "$WORK/f3.log" "采集期间登录了 2 次" "打印不变量破坏详情"
report_contains "$WORK/f3.log" "不再重登" "达到硬上限后拒绝继续重登（防止会话被继续顶掉）"

echo "结果: pass=$pass fail=$failed"
[[ $failed -eq 0 ]]
