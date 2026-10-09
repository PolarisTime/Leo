#!/usr/bin/env bash
# health-guard.sh 自测：覆盖告警触发、退出码语义与极端/异常输入
#
#   bash health-guard.test.sh
#
# 设计要点：不依赖真实生产依赖是否可用——用伪造的 psql / env 文件注入故障，
# 断言"该报警时必须报警、不该报警时不得误报"。最后一个用例会针对真实生产日志
# 验证时间窗（不误报历史事件），该用例在生产不可达时会标记 SKIP 而非 FAIL。
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
GUARD="$SCRIPT_DIR/health-guard.sh"
PROD_ENV="/instance/steelx/shared/steelx.env"
PROD_LOG="/instance/steelx/logs/backend.log"

PASS=0; FAIL=0; SKIP=0
WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT

ok()   { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$1"; }
skip() { SKIP=$((SKIP+1)); printf '  \033[33mSKIP\033[0m %s\n' "$1"; }

# run_case <期望退出码> <用例名> [env 赋值 ... ] -- <额外参数 ...>
run_case() {
  local expect="$1" name="$2"; shift 2
  local -a envs=() args=()
  while [[ $# -gt 0 && "$1" != "--" ]]; do envs+=("$1"); shift; done
  [[ "${1:-}" == "--" ]] && shift
  args=("$@")

  local out rc
  set +e
  out="$(env "${envs[@]}" "$GUARD" "${args[@]}" 2>&1)"
  rc=$?
  set -e

  if [[ "$rc" != "$expect" ]]; then
    bad "$name（期望退出码 $expect，实际 $rc）"
    printf '%s\n' "$out" | sed 's/^/       | /'
    return 1
  fi
  ok "$name（退出码 $rc）"
  LAST_OUT="$out"
}

# ---------------------------------------------------------------- 前置
[[ -x "$GUARD" ]] || { echo "找不到可执行守卫: $GUARD" >&2; exit 1; }
if [[ -r "$PROD_ENV" ]]; then
  ENV_FILE="$PROD_ENV"
else
  # 无生产配置时构造最小可用配置，保证关键分支仍被覆盖
  printf 'SPRING_DATASOURCE_HOST=127.0.0.1\nSPRING_DATASOURCE_PORT=5432\nSPRING_DATASOURCE_DB=leo\nSPRING_DATASOURCE_USERNAME=leo\n' > "$WORK/min.env"
  ENV_FILE="$WORK/min.env"
  echo "提示: 未找到 $PROD_ENV，改用最小配置（部分用例将 SKIP）"
fi
BASE=(--no-redis-write-probe "--env-file=$ENV_FILE")

echo "== 1. 参数与用法 =="
run_case 3 "未知参数应返回 3"            -- --bogus
run_case 0 "帮助应返回 0"                -- --help
printf 'LEO_GUARD_DISK_WARN_PCT=abc\n' > "$WORK/bad-num.env"
run_case 3 "非数字阈值应返回 3"          -- "--env-file=$WORK/bad-num.env" --no-redis-write-probe

echo "== 2. 磁盘水位分支 =="
run_case 1 "阈值调低应触发 WARN" \
  "LEO_GUARD_DISK_WARN_PCT=1" "LEO_GUARD_DISK_CRIT_PCT=99" -- "${BASE[@]}"
grep -q 'disk/' <<<"${LAST_OUT:-}" && ok "WARN 输出包含 disk 检查项" || bad "WARN 输出缺少 disk 检查项"
run_case 2 "阈值调至 0 应触发 CRIT" \
  "LEO_GUARD_DISK_WARN_PCT=0" "LEO_GUARD_DISK_CRIT_PCT=0" -- "${BASE[@]}"

echo "== 3. PostgreSQL 分支（伪造 psql 注入）=="
mkdir -p "$WORK/bin"
cat > "$WORK/bin/psql" <<'EOF'
#!/usr/bin/env bash
printf 'true|1\n'
EOF
chmod +x "$WORK/bin/psql"
run_case 2 "pg_is_in_recovery=true 应 CRIT" "PATH=$WORK/bin:$PATH" -- "${BASE[@]}"
grep -q '恢复' <<<"${LAST_OUT:-}" && ok "识别为崩溃恢复/只读模式" || bad "未识别恢复模式"

cat > "$WORK/bin/psql" <<'EOF'
#!/usr/bin/env bash
printf 'psql: error: FATAL:  the database system is in recovery mode\n' >&2
exit 2
EOF
run_case 2 "psql 报 recovery mode 应 CRIT" "PATH=$WORK/bin:$PATH" -- "${BASE[@]}"

cat > "$WORK/bin/psql" <<'EOF'
#!/usr/bin/env bash
printf 'false|1\n'
EOF
run_case 0 "psql 返回 false|1 应 OK" "PATH=$WORK/bin:$PATH" -- "${BASE[@]}"

cat > "$WORK/bin/psql" <<'EOF'
#!/usr/bin/env bash
printf '0|1\n'
EOF
run_case 2 "无法识别的 psql 输出应保守判 CRIT" "PATH=$WORK/bin:$PATH" -- "${BASE[@]}"

echo "== 4. 日志特征与时间窗 =="
NOW="$(date -u +%Y-%m-%dT%H:%M:%S)"; OLD="$(date -u -d '-3 hours' +%Y-%m-%dT%H:%M:%S)"
{
  printf '{"@timestamp":"%s.1Z","message":"SQL Error: 0, SQLState: 53100"}\n' "$NOW"
  printf '{"@timestamp":"%s.2Z","message":"SQL Error: 0, SQLState: 57P03"}\n' "$NOW"
  printf '{"@timestamp":"%s.3Z","message":"SQL Error: 0, SQLState: 53100"}\n' "$OLD"
  printf 'plain header line without timestamp\n'
  printf '{"@timestamp":"%s.4Z","message":"Redis MISCONF not able to persist on disk"}\n' "$OLD"
} > "$WORK/app.log"
run_case 2 "窗口内 ERROR 应 CRIT（窗口外忽略）" "LEO_GUARD_LOG_FILE=$WORK/app.log" -- "${BASE[@]}"
grep -qE '出现 2 条' <<<"${LAST_OUT:-}" \
  && ok "窗口内命中数=2（正确忽略 3 小时前与无时间戳行）" \
  || { bad "窗口内命中数不符"; printf '%s\n' "${LAST_OUT:-}" | sed 's/^/       | /'; }

printf '{"@timestamp":"%s.5Z","message":"一切正常"}\n' "$NOW" > "$WORK/clean.log"
run_case 0 "无错误日志应 OK" "LEO_GUARD_LOG_FILE=$WORK/clean.log" -- "${BASE[@]}"

printf 'not json at all\n' > "$WORK/junk.log"
run_case 0 "非 JSON 日志不应误报" "LEO_GUARD_LOG_FILE=$WORK/junk.log" -- "${BASE[@]}"

echo "== 5. 日志不可读（应 WARN 而非崩溃）=="
run_case 1 "日志不可读应 WARN" "LEO_GUARD_LOG_FILE=$WORK/nope.log" -- "--env-file=$ENV_FILE" --no-redis-write-probe

echo "== 6. HTTP 端点分支 =="
run_case 2 "端点不可达应 CRIT" "LEO_GUARD_HEALTH_URL=http://127.0.0.1:1/health" -- "${BASE[@]}"

echo "== 7. Redis 分支 =="
if [[ -r "$PROD_ENV" ]] && command -v redis-cli >/dev/null 2>&1; then
  sed 's/^SPRING_DATA_REDIS_PASSWORD=.*/SPRING_DATA_REDIS_PASSWORD=definitely-wrong/' "$PROD_ENV" > "$WORK/wrong-redis.env"
  run_case 2 "Redis 认证失败应 CRIT" -- "--env-file=$WORK/wrong-redis.env" --no-redis-write-probe
else
  skip "Redis 用例（无生产配置或缺少 redis-cli）"
fi

echo "== 8. 真实生产日志的时间窗复核 =="
if [[ -r "$PROD_LOG" ]]; then
  run_case 0 "900s 窗口不误报历史事故" "LEO_GUARD_LOG_FILE=$PROD_LOG" -- "${BASE[@]}"
  run_case 2 "24h 窗口应命中事故特征" \
    "LEO_GUARD_LOG_FILE=$PROD_LOG" "LEO_GUARD_LOG_WINDOW_SECONDS=86400" -- "${BASE[@]}"
else
  skip "生产日志用例（$PROD_LOG 不可读）"
fi

echo
echo "结果: PASS=$PASS FAIL=$FAIL SKIP=$SKIP"
(( FAIL == 0 )) || exit 1
