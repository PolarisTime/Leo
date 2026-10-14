#!/usr/bin/env bash
#
# assert-flyway-history.sh 负例自检：证明这道 SQL 断言门禁在“错误实现/错误数据”下会变红。
#
# 做法：用 `(VALUES …) AS h(...)` 合成一份 flyway_schema_history（纯 SELECT 字面量，
# 不建表、不写库、不碰真实历史表），逐个场景喂给门禁脚本，检查退出码与失败原因：
#   - 正例必须 exit 0；
#   - 负例必须非零退出，且失败原因命中预期断言（避免“因为连不上库而恰好变红”的假红）。
#
# 依赖：一个可连接的 PostgreSQL（开发库或 CI postgres service）与 libpq 连接变量
# PGDATABASE / PGUSER（PGHOST / PGPORT / PGPASSWORD 按需）。
#
# 用法：
#   PGHOST=... PGDATABASE=leo PGUSER=leo PGPASSWORD=... \
#     bash scripts/deploy/test/assert-flyway-history-selftest.sh

set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
ASSERT_SCRIPT="$REPO_DIR/scripts/deploy/assert-flyway-history.sh"

if [[ ! -f "$ASSERT_SCRIPT" ]]; then
  printf 'SELFTEST_FAIL: 找不到 %s\n' "$ASSERT_SCRIPT" >&2
  exit 2
fi

if [[ -z "${PGDATABASE:-}" || -z "${PGUSER:-}" ]]; then
  printf 'SELFTEST_FAIL: 需要 libpq 连接变量 PGDATABASE 与 PGUSER\n' >&2
  exit 2
fi

if ! command -v psql >/dev/null 2>&1 || ! psql -X -q -A -t -c 'SELECT 1' >/dev/null 2>&1; then
  printf 'SELFTEST_FAIL: 无法连接 PostgreSQL，合成历史自检无法执行\n' >&2
  exit 2
fi

code_max="$(bash "$REPO_DIR/scripts/deploy/verify-flyway-target.sh" "$REPO_DIR")"
prev_version=$((code_max - 1))

baseline_row="(1, '1', 'baseline', 'SQL', true, -1521133953::bigint, timestamp '2026-01-01 00:00:00')"

row() {
  # row <rank> <version> <success:true|false> <checksum SQL 表达式>
  printf "(%s, '%s', 'synthetic', 'SQL', %s, %s, timestamp '2026-01-02 00:00:00')" "$1" "$2" "$3" "$4"
}

source_of() {
  printf '(VALUES %s) AS h(installed_rank, version, description, type, success, checksum, installed_on)' "$1"
}

total=0
failed=0

run_case() {
  local name="$1" expect="$2" target="$3" rows="$4" marker="$5"
  local source output status reason="" ok=1
  source="$(source_of "$rows")"
  set +e
  output="$(bash "$ASSERT_SCRIPT" --repo-dir "$REPO_DIR" --target "$target" --history-source "$source" 2>&1)"
  status=$?
  set -e
  total=$((total + 1))
  if [[ "$expect" == "ok" ]]; then
    if ((status != 0)); then
      ok=0
      reason="期望 exit=0，实际 exit=$status"
    fi
  else
    if ((status == 0)); then
      ok=0
      reason="期望非零退出，实际 exit=0（假绿）"
    fi
  fi
  if ((ok == 1)) && [[ -n "$marker" ]] && ! grep -qF -- "$marker" <<<"$output"; then
    ok=0
    reason="退出码符合预期（exit=$status），但未命中预期失败断言: $marker"
  fi
  if ((ok == 1)); then
    printf 'FLYWAY_GATE_SELFTEST_PASS: %s（exit=%d）\n' "$name" "$status"
  else
    failed=$((failed + 1))
    printf 'FLYWAY_GATE_SELFTEST_FAIL: %s（%s）\n' "$name" "$reason" >&2
    printf '%s\n' "$output" | tail -n 5 >&2
  fi
}

# 1) 正例：成功版本齐全、目标版本恰 1 行且 checksum 非空 → 必须放行
run_case "正例：历史完整（target=$code_max）" ok "$code_max" \
  "$baseline_row, $(row 2 "$code_max" true 1928227925::bigint)" \
  "FLYWAY_HISTORY_GATE_OK"

# 2) 负例：存在 success=false 的行 → 必须红（失败行放在非目标版本，避免与其他断言混淆）
run_case "负例：存在 success=false 的行" fail "$code_max" \
  "$baseline_row, $(row 2 "$code_max" true 1928227925::bigint), $(row 3 178 false -123::bigint)" \
  "success=false 的行数必须为 0"

# 3) 负例：目标版本该行 checksum 为空 → 必须红
run_case "负例：目标版本 checksum 为空" fail "$code_max" \
  "$baseline_row, $(row 2 "$code_max" true NULL::bigint)" \
  "checksum 必须非空"

# 4) 负例：目标版本该行 success=false → 必须红
run_case "负例：目标版本该行 success=false" fail "$code_max" \
  "$baseline_row, $(row 2 "$code_max" false 1928227925::bigint)" \
  "success 必须为 true"

# 5) 负例：目标版本在历史中缺失 → 必须红
run_case "负例：目标版本行缺失" fail "$code_max" \
  "$baseline_row" \
  "目标版本必须恰有 1 行记录"

# 6) 负例：目标版本被调低（代码库最新为 $code_max，却指定 $prev_version）→ 必须红
run_case "负例：目标版本调低到 $prev_version" fail "$prev_version" \
  "$baseline_row, $(row 2 "$prev_version" true 1187971182::bigint)" \
  "目标版本与代码库最新迁移版本不一致"

# 7) 负例：目标版本出现重复行 → 必须红
run_case "负例：目标版本重复 2 行" fail "$code_max" \
  "$baseline_row, $(row 2 "$code_max" true 1928227925::bigint), $(row 3 "$code_max" true 111::bigint)" \
  "目标版本必须恰有 1 行记录"

if ((failed > 0)); then
  printf 'FLYWAY_GATE_SELFTEST_FAIL: %d/%d 个场景未达预期\n' "$failed" "$total" >&2
  exit 1
fi

printf 'FLYWAY_GATE_SELFTEST_OK: %d/%d 个场景符合预期（1 正例 + %d 负例）\n' \
  "$total" "$total" "$((total - 1))"
