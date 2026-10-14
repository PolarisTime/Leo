#!/usr/bin/env bash
#
# Flyway 迁移历史断言门禁（CI 与生产部署流程共用同一份实现）
#
# 为什么需要它：`flyway:validate` 回答的是“库中记录与本地脚本是否对齐”，
# 它不是“这条迁移到底跑没跑”的探针；判断某版本是否已执行必须看
# flyway_schema_history 的 success / checksum。本脚本在 migrate/validate 之后
# 追加可失败的 SQL 断言：
#   1) success = false 的行数必须为 0；
#   2) 目标版本必须恰有 1 行，且该行 success = true、checksum 非空；
#   3) max(已成功版本的版本号) 必须等于 verify-flyway-target.sh 的输出；
#   4) 目标版本必须等于代码库最新迁移版本（防止把 target 调低后“看起来绿”）。
# 任一条不满足即以非零退出码失败，使流水线变红。
#
# 明确不做的事：
#   - 不执行 Flyway 的 repair goal / 子命令（严禁进入流水线，见 assert-no-migration-repair.sh 守卫）；
#   - 不自行计算 blob/md5/CRC 去比对 checksum：checksum 的算法属 Flyway 实现细节，
#     本脚本只断言“checksum 非空”，校验和一致性交给 flyway:validate；
#   - 不宣称具备 drift 检测能力（那是 Flyway Enterprise 功能）。
#
# 用法：
#   PGHOST=127.0.0.1 PGPORT=5432 PGDATABASE=leo PGUSER=leo PGPASSWORD=... \
#     bash scripts/deploy/assert-flyway-history.sh --target 186 [--report flyway-history-gate.txt]
#
# 连接参数沿用 libpq 环境变量，与同一次 flyway:migrate / flyway:validate 使用同一套凭据。

set -euo pipefail

REPO_DIR="."
TARGET=""
HISTORY_SOURCE="flyway_schema_history"
REPORT_FILE=""
DEFAULT_HISTORY_SOURCE="flyway_schema_history"

usage() {
  cat <<'EOF'
用法:
  PGHOST=... PGPORT=... PGDATABASE=... PGUSER=... PGPASSWORD=... \
    bash scripts/deploy/assert-flyway-history.sh --target <版本号> [选项]

选项:
  --target <版本号>      必须已应用的目标迁移版本（CI 由 verify-flyway-target.sh 解析，
                         生产部署由部署变量给出）
  --repo-dir <目录>      后端仓库根目录，默认当前目录
  --report <文件>        把查询证据写入该文件（同时仍打印到 stdout），供 CI artifact 留档
  --history-source <SQL> 历史来源 FROM 片段，仅供脚本自检注入合成历史；默认 flyway_schema_history
  -h, --help             显示本帮助
EOF
}

fail() {
  printf 'FLYWAY_HISTORY_GATE_FAIL: %s\n' "$*" >&2
}

while (($# > 0)); do
  case "$1" in
    --target)
      [[ $# -ge 2 ]] || { fail "--target 缺少取值"; exit 2; }
      TARGET="$2"
      shift 2
      ;;
    --repo-dir)
      [[ $# -ge 2 ]] || { fail "--repo-dir 缺少取值"; exit 2; }
      REPO_DIR="$2"
      shift 2
      ;;
    --report)
      [[ $# -ge 2 ]] || { fail "--report 缺少取值"; exit 2; }
      REPORT_FILE="$2"
      shift 2
      ;;
    --history-source)
      [[ $# -ge 2 ]] || { fail "--history-source 缺少取值"; exit 2; }
      HISTORY_SOURCE="$2"
      shift 2
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      fail "未知参数: $1"
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$TARGET" ]]; then
  fail "必须通过 --target 显式给出目标版本"
  exit 2
fi

if [[ ! "$TARGET" =~ ^[0-9]+$ ]]; then
  fail "目标版本必须是十进制整数: ${TARGET}"
  exit 2
fi

# 历史来源只允许“表标识符”或“自检用 (…) AS alias(列定义)”；CI/生产固定使用默认表名。
if [[ ! "$HISTORY_SOURCE" =~ ^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)?$ ]] &&
  [[ ! "$HISTORY_SOURCE" =~ ^\(.*\)[[:space:]]+AS[[:space:]]+[A-Za-z_][A-Za-z0-9_]*(\([A-Za-z_][A-Za-z0-9_,[:space:]]*\))?$ ]]; then
  fail "非法的 --history-source: ${HISTORY_SOURCE}"
  exit 2
fi

if ! command -v psql >/dev/null 2>&1; then
  fail "未找到 psql 客户端，无法读取 flyway_schema_history"
  exit 2
fi

missing_pg_vars=()
for pg_var in PGDATABASE PGUSER; do
  if [[ -z "${!pg_var:-}" ]]; then
    missing_pg_vars+=("$pg_var")
  fi
done
if ((${#missing_pg_vars[@]} > 0)); then
  fail "缺少 libpq 连接变量: ${missing_pg_vars[*]}"
  exit 2
fi

TARGET_SCRIPT="$REPO_DIR/scripts/deploy/verify-flyway-target.sh"
if [[ ! -f "$TARGET_SCRIPT" ]]; then
  fail "找不到 $TARGET_SCRIPT，无法解析代码库最新迁移版本"
  exit 2
fi

if ! code_max="$(bash "$TARGET_SCRIPT" "$REPO_DIR")"; then
  fail "verify-flyway-target.sh 执行失败，无法解析代码库最新迁移版本"
  exit 2
fi

if [[ "$REPORT_FILE" != "" ]]; then
  report_dir="$(dirname "$REPORT_FILE")"
  if [[ "$report_dir" != "" && "$report_dir" != "." ]]; then
    mkdir -p "$report_dir"
  fi
  {
    echo "# Flyway 迁移历史断言证据（CI/生产部署门禁）"
    echo "# 采集时间(UTC): $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "# 目标版本: $TARGET"
    echo "# 代码库最新迁移版本(verify-flyway-target.sh): $code_max"
    echo "# 历史来源: $HISTORY_SOURCE"
    echo "# 连接: host=${PGHOST:-localhost} port=${PGPORT:-5432} database=$PGDATABASE user=$PGUSER"
  } >"$REPORT_FILE"
fi

SQL=$(cat <<SQL
SELECT 'gate_failed_rows|' || count(*)::text
FROM $HISTORY_SOURCE WHERE success = false;
SELECT 'gate_target_rows|' || count(*)::text
FROM $HISTORY_SOURCE WHERE version = '$TARGET';
SELECT 'gate_target_success_rows|' || count(*)::text
FROM $HISTORY_SOURCE WHERE version = '$TARGET' AND success = true;
SELECT 'gate_target_checksum_rows|' || count(*)::text
FROM $HISTORY_SOURCE
WHERE version = '$TARGET' AND success = true
  AND checksum IS NOT NULL AND btrim(checksum::text) <> '';
SELECT 'gate_max_success_version|' || coalesce(max(version::numeric)::text, '0')
FROM $HISTORY_SOURCE WHERE success = true AND version ~ '^[0-9]+\$';
SELECT 'gate_detail|' || coalesce(string_agg(
    'version=' || version || ' type=' || type || ' success=' || success::text ||
    ' checksum=' || coalesce(checksum::text, '<null>') ||
    ' installed_on=' || installed_on::text, ' ;; '), '<no-row>')
FROM $HISTORY_SOURCE WHERE version = '$TARGET';
SELECT 'history|' || installed_rank::text || '|' || coalesce(version, '') || '|' ||
       coalesce(description, '') || '|' || type || '|' || success::text || '|' ||
       coalesce(checksum::text, '<null>') || '|' || installed_on::text
FROM $HISTORY_SOURCE ORDER BY installed_rank;
SQL
)

set +e
# 必须用 stdin 逐条执行：psql 的 -c 传多语句时只打印最后一条结果，会静默吞掉其余断言证据。
sql_output="$(printf '%s\n' "$SQL" | psql -X -q -A -t -F '|' -v ON_ERROR_STOP=1 2>&1)"
psql_status=$?
set -e

if [[ "$REPORT_FILE" != "" ]]; then
  printf '%s\n' "$sql_output" >>"$REPORT_FILE"
fi
printf 'FLYWAY_HISTORY_GATE_INFO: 查询 %s（host=%s database=%s）\n' \
  "$HISTORY_SOURCE" "${PGHOST:-localhost}" "$PGDATABASE"

if ((psql_status != 0)); then
  fail "flyway_schema_history 查询失败: $(printf '%s\n' "$sql_output" | tail -n 3)"
  exit 2
fi

field() {
  # 纯 bash 解析，避免 printf|awk 在 awk 提前退出时触发 SIGPIPE（pipefail 下返回 141，
  # 会把「断言不通过」或「全部通过」误报成「脚本崩溃」）。未命中时输出空串并返回 0。
  local key="$1" line
  while IFS= read -r line; do
    if [[ "$line" == *"|"* && "${line%%|*}" == "$key" ]]; then
      printf '%s' "${line#*|}"
      return 0
    fi
  done <<<"$sql_output"
  return 0
}

failures=()
check_eq() {
  local key="$1" expected="$2" desc="$3" actual
  actual="$(field "$key")"
  if [[ "$actual" == "$expected" ]]; then
    printf 'FLYWAY_HISTORY_GATE_PASS: %s（%s=%s）\n' "$desc" "$key" "$actual"
  else
    failures+=("$desc：期望 $expected，实际 ${actual:-<无输出>}")
    printf 'FLYWAY_HISTORY_GATE_ERROR: %s（%s=%s，期望 %s）\n' "$desc" "$key" "${actual:-<无输出>}" "$expected"
  fi
}

if [[ "$TARGET" == "$code_max" ]]; then
  printf 'FLYWAY_HISTORY_GATE_PASS: 目标版本与代码库最新迁移版本一致（target=%s）\n' "$TARGET"
else
  failures+=("目标版本与代码库最新迁移版本不一致：target=$TARGET，代码库最新=$code_max；禁止把 target 调低后发布")
  printf 'FLYWAY_HISTORY_GATE_ERROR: 目标版本与代码库最新迁移版本不一致（target=%s，代码库最新=%s）\n' \
    "$TARGET" "$code_max"
fi

check_eq gate_failed_rows 0 "flyway_schema_history 中 success=false 的行数必须为 0"
check_eq gate_target_rows 1 "目标版本必须恰有 1 行记录"
check_eq gate_target_success_rows 1 "目标版本该行 success 必须为 true"
check_eq gate_target_checksum_rows 1 "目标版本该行 checksum 必须非空"
check_eq gate_max_success_version "$code_max" "max(已成功版本) 必须等于 verify-flyway-target.sh 输出"

printf 'FLYWAY_HISTORY_GATE_EVIDENCE: target=%s %s\n' "$TARGET" "$(field gate_detail)"

if ((${#failures[@]} > 0)); then
  printf 'FLYWAY_HISTORY_GATE_FAIL: %d 条断言未通过\n' "${#failures[@]}" >&2
  for failure in "${failures[@]}"; do
    printf '  - %s\n' "$failure" >&2
  done
  exit 1
fi

printf 'FLYWAY_HISTORY_GATE_OK: target=%s code_max=%s failed_rows=0\n' "$TARGET" "$code_max"
