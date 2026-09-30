#!/usr/bin/env bash
# perf/verify-race-sql.sh —— 并发正确性专项（perf/k6/08-concurrency.js）的 SQL 自动核对。
#
# 背景：2026-09-30 并发专项（见 perf/reports/2026-09-30-gap-closure.md 第二节）的四个场景
# 当时是**人工执行 SQL** 逐条核对的，08-concurrency.js 头注释也写着「最终只创建了一条
# 需由 harness 侧查询核对」。本脚本把同一套口径自动化，并由 perf/run.sh 的 run_race
# 在压测结束后自动调用。
#
# 【硬性约束：只读】附件与压测数据没有回收站——核对脚本一旦误写（哪怕一条 UPDATE/DELETE），
# 没有任何回滚或回收手段。因此本脚本只允许 SELECT，并有三道防线：
#   1) 语句级 assert_readonly_sql：必须以 SELECT 开头、禁止分号（杜绝多语句拼接）、
#      禁止一切写/DDL/事务关键字（INSERT/UPDATE/DELETE/DDL/COPY/CALL/DO/WITH/INTO...）；
#   2) 表级 assert_allowed_tables：FROM/JOIN 后的表名必须落在白名单内
#      （sys_role / sys_role_permission / md_customer / so_sales_order / so_sales_order_item）；
#   3) 连接级：PGOPTIONS='-c default_transaction_read_only=on'，由数据库服务端兜底拒写，
#      外加 psql -X -v ON_ERROR_STOP=1、每条语句单独 -c 提交。
#
# 数据库连接与 perf/cleanup-perf-data.sh 用同一套环境变量约定：
#   SPRING_DATASOURCE_PASSWORD            必填（缺少即整体 SKIP，并打印清晰诊断）
#   SPRING_DATASOURCE_HOST                默认 localhost
#   SPRING_DATASOURCE_PORT                默认 5432
#   SPRING_DATASOURCE_USERNAME            默认 leo
#   SPRING_DATASOURCE_DB                  默认 leo
#   （通常 source scripts/env/dev.sh 即可提供）
#
# 用法：
#   source scripts/env/dev.sh
#   export LEO_PERF_RUN_ID=<与 k6 完全相同的批次号>   # run.sh 已自动 export
#   bash perf/verify-race-sql.sh --snapshot    # 压测【前】抓靶子单据基线（version / 明细行数）
#   bash perf/verify-race-sql.sh               # 压测【后】逐场景核对，输出 PASS/FAIL/SKIP
#   bash perf/verify-race-sql.sh --dry-run     # 只打印将执行的 SQL，不连接数据库
#   bash perf/verify-race-sql.sh --selftest    # 离线自测：用 fixture 验证 PASS/FAIL/SKIP 与退出码
#   bash perf/verify-race-sql.sh --doc-id 123  # 手工指定场景 D 的靶子单据 id（覆盖自动定位）
#
# 四个场景的核对口径（与 08-concurrency.js 实际写入的数据对齐）：
#   A 同一角色权限并发替换：角色 code='PERF-RACE-<RUN_ID>' 恰 1 个，其未删除权限行
#     恰为脚本提交的集合（setup_data.permissions，默认 roles:read），count=1、无重复无残缺；
#   B 同一幂等键并发重放：customer_name='PERF-LOAD-<RUN_ID>-REPLAY' 恰 1 条客户；
#   C 同一客户编码并发创建：customer_name='PERF-LOAD-<RUN_ID>-SAMECODE' 恰 1 条客户；
#   D 同一单据并发整体替换：version 增量 == k6 的 race_doc_ok_200（200 成功写入数）
#     + 1 次 setup 预检（若测试后 remark 已还原原值，再 +1 次还原写入）；明细行数 == 跑前基线；
#     单据状态仍为「草稿」（未被并发写推进）；remark 必须仍是本脚本族的痕迹
#     （race-doc-* / 预检标记 / 已还原的原值），出现第三者即 FAIL。
#     注：08-concurrency.js 当前**没有**还原 remark 的断言（以文件实际行为为准），
#     因此 remark 仍为 race-doc-* 属正常放行并打印提示，不算失败。
#   U（附加）race_unexpected_status：报告第 8 节口径，只允许 200/201/409/422，
#     计数必须为 0（从 k6 summary 读取，不依赖数据库）。
#
# 退出码约定：
#   0  全部 PASS，或 PASS/SKIP 混合，或全部 SKIP
#      （SKIP = 没跑过压测/数据已清理，或缺库环境——打印明确诊断，但不算「核对失败」）
#   1  至少一个场景 FAIL（真正的核对失败，perf/run.sh 据此以非零退出码结束）
#   2  用法错误 / 只读防线被触发 / SQL 执行异常（run.sh 视为脚本异常，同样非零退出）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULT_DIR="$SCRIPT_DIR/results"
SUMMARY_JSON="${LEO_PERF_RACE_RESULT_JSON:-$RESULT_DIR/08-concurrency.json}"
BASELINE_FILE="${LEO_PERF_RACE_BASELINE:-$RESULT_DIR/08-concurrency.baseline}"
K6_LOG="${LEO_PERF_RACE_LOG:-$RESULT_DIR/08-concurrency.log}"

MODE="verify"          # verify | snapshot | dry-run | selftest
SELFTEST_CASE=""       # --selftest-case <pass|fail|skip>（自测用子进程验证退出码）
RUN_ID="${LEO_PERF_RUN_ID:-}"
DOC_ID_OPT=""

# 表名白名单：核对只允许读这五张表，防「白名单外表名」被拼进语句。
ALLOWED_TABLES="sys_role sys_role_permission md_customer so_sales_order so_sales_order_item"

# ---------------------------------------------------------------------------
# 参数解析
# ---------------------------------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --snapshot)      MODE="snapshot"; shift ;;
    --dry-run)       MODE="dry-run"; shift ;;
    --selftest)      MODE="selftest"; shift ;;
    --selftest-case|--run-id|--doc-id)
      if [[ $# -lt 2 ]]; then
        printf '[verify-race][ERROR] 参数 %s 缺少取值\n' "$1" >&2
        exit 2
      fi
      case "$1" in
        --selftest-case) SELFTEST_CASE="$2"; MODE="selftest-case" ;;
        --run-id)        RUN_ID="$2" ;;
        --doc-id)        DOC_ID_OPT="$2" ;;
      esac
      shift 2 ;;
    -h|--help)
      sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0 ;;
    *)
      printf '[verify-race][ERROR] 未知参数: %s（支持 --snapshot/--dry-run/--selftest/--run-id/--doc-id/--help）\n' "$1" >&2
      exit 2 ;;
  esac
done

# RUN_ID 必须与 k6 侧一致，且只能是安全字符集（它会被拼进 SQL 字面量）。
validate_run_id() {
  if [[ -z "$RUN_ID" ]]; then
    printf '[verify-race][ERROR] 缺少 RUN_ID：请 export LEO_PERF_RUN_ID=<与 k6 相同的批次号>，或用 --run-id 指定\n' >&2
    exit 2
  fi
  if ! [[ "$RUN_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
    printf '[verify-race][ERROR] RUN_ID 含非法字符（只允许 [A-Za-z0-9._-]，且首字符为字母数字）: %s\n' "$RUN_ID" >&2
    exit 2
  fi
}

# ---------------------------------------------------------------------------
# 只读防线（详见头部注释）：任一断言失败返回 1，由调用方按退出码 2 终止。
# ---------------------------------------------------------------------------
assert_readonly_sql() {
  local sql="$1" lower
  lower="$(printf '%s' "$sql" | tr '[:upper:]' '[:lower:]')"
  if ! [[ "$lower" =~ ^[[:space:]]*select[[:space:]] ]]; then
    printf '[verify-race][只读防线] 拒绝非 SELECT 开头的语句: %s\n' "$sql" >&2
    return 1
  fi
  if [[ "$sql" == *";"* ]]; then
    printf '[verify-race][只读防线] 拒绝含分号的语句（禁止多语句拼接）: %s\n' "$sql" >&2
    return 1
  fi
  # 写/DDL/事务关键字一律拒绝。关键词后必须是空白或右括号，因此
  # updated_at / deleted_flag / created_by 这类列名不会被误伤。
  if printf '%s' "$lower" | grep -Eq '[[:space:](](insert|update|delete|merge|upsert|drop|alter|create|truncate|rename|grant|revoke|copy|call|do|vacuum|reindex|comment|lock|execute|prepare|deallocate|refresh|set|reset|with|into|merge|values|pg_sleep|pg_read_file)[[:space:])]+'; then
    printf '[verify-race][只读防线] 拒绝含写/DDL 关键字的语句: %s\n' "$sql" >&2
    return 1
  fi
  return 0
}

assert_allowed_tables() {
  local sql="$1" lower tbl
  lower="$(printf '%s' "$sql" | tr '[:upper:]' '[:lower:]')"
  # 抽取 FROM/JOIN 后的表名，逐个比对白名单（子查询内的表同样会被抽到）。
  while read -r tbl; do
    [[ -n "$tbl" ]] || continue
    case " $ALLOWED_TABLES " in
      *" $tbl "*) ;;
      *)
        printf '[verify-race][只读防线] 拒绝白名单外的表名: %s（白名单: %s）\n' "$tbl" "$ALLOWED_TABLES" >&2
        return 1 ;;
    esac
  done < <(printf '%s' "$lower" | grep -Eio '(from|join)[[:space:]]+[a-z_][a-z0-9_]*' | awk '{print $2}' | sort -u)
  return 0
}

# ---------------------------------------------------------------------------
# 数据库连接：与 cleanup-perf-data.sh 同一套环境变量约定。
# 返回 1 = 环境不可用（调用方按 SKIP 语义处理，退出码 0），诊断写 stderr。
# ---------------------------------------------------------------------------
require_db_env() {
  local missing=()
  [[ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]] || missing+=("SPRING_DATASOURCE_PASSWORD")
  if [[ ${#missing[@]} -gt 0 ]]; then
    printf '[verify-race][SKIP] 缺少数据库环境变量: %s（与 cleanup-perf-data.sh 同一套约定，请先 source scripts/env/dev.sh）\n' "${missing[*]}" >&2
    return 1
  fi
  if ! command -v psql >/dev/null 2>&1; then
    printf '[verify-race][SKIP] 未找到 psql，无法连接数据库核对\n' >&2
    return 1
  fi
  return 0
}

# 连接级只读兜底：所有 psql 调用都带 default_transaction_read_only=on，
# 即便语句级防线被绕过，数据库服务端也会拒绝任何写入。
export PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"
if [[ -n "${PGOPTIONS:-}" ]]; then
  export PGOPTIONS="$PGOPTIONS -c default_transaction_read_only=on"
else
  export PGOPTIONS="-c default_transaction_read_only=on"
fi
PSQL_CMD=(psql -X -v ON_ERROR_STOP=1 -q -A -t
  -h "${SPRING_DATASOURCE_HOST:-localhost}"
  -p "${SPRING_DATASOURCE_PORT:-5432}"
  -U "${SPRING_DATASOURCE_USERNAME:-leo}"
  -d "${SPRING_DATASOURCE_DB:-leo}")

# run_sql：只读防线 + 单语句提交。防线被触发返回 2，psql 失败透传其退出码。
run_sql() {
  local sql="$1"
  assert_readonly_sql "$sql" || return 2
  assert_allowed_tables "$sql" || return 2
  "${PSQL_CMD[@]}" -c "$sql"
}

# q：执行并取输出；任何失败（防线 2 / 执行异常）都以退出码 2 终止脚本。
q() {
  local out rc=0
  out="$(run_sql "$1")" || rc=$?
  if [[ $rc -ne 0 ]]; then
    if [[ $rc -ne 2 ]]; then
      printf '[verify-race][ERROR] SQL 执行失败（psql 退出码 %s）: %s\n' "$rc" "$1" >&2
    fi
    exit 2
  fi
  printf '%s' "$out"
}

db_connect_ok() {
  local rc=0
  run_sql "SELECT 1" >/dev/null 2>&1 || rc=$?
  if [[ $rc -ne 0 ]]; then
    printf '[verify-race][SKIP] 无法连接数据库 %s@%s:%s/%s，本轮不执行 SQL 核对\n' \
      "${SPRING_DATASOURCE_USERNAME:-leo}" "${SPRING_DATASOURCE_HOST:-localhost}" \
      "${SPRING_DATASOURCE_PORT:-5432}" "${SPRING_DATASOURCE_DB:-leo}" >&2
    return 1
  fi
  return 0
}

# ---------------------------------------------------------------------------
# 判定与输出
# ---------------------------------------------------------------------------
PASS_N=0
FAIL_N=0
SKIP_N=0
RES_V=""
RES_D=""

report() { # $1=PASS|FAIL|SKIP  $2=场景标签  $3=关键数值/诊断
  printf '[%s] %s：%s\n' "$1" "$2" "$3"
  case "$1" in
    PASS) PASS_N=$((PASS_N + 1)) ;;
    FAIL) FAIL_N=$((FAIL_N + 1)) ;;
    SKIP) SKIP_N=$((SKIP_N + 1)) ;;
  esac
}

finish() {
  printf '[verify-race] 汇总：PASS=%s FAIL=%s SKIP=%s\n' "$PASS_N" "$FAIL_N" "$SKIP_N"
  if [[ $FAIL_N -gt 0 ]]; then
    printf '[verify-race] 结论：核对失败（存在 FAIL 场景），退出码 1\n'
    return 1
  fi
  if [[ $PASS_N -eq 0 ]]; then
    printf '[verify-race] 结论：全部 SKIP——没有可核对的靶子数据或库环境（不是「核对通过」，但按约定退出码 0）\n'
  elif [[ $SKIP_N -gt 0 ]]; then
    printf '[verify-race] 结论：已核对项全部通过（另有 %s 项 SKIP——未核对，既非通过也非失败，见上方诊断）\n' "$SKIP_N"
  else
    printf '[verify-race] 结论：核对通过\n'
  fi
  return 0
}

count_lines() {
  printf '%s' "$1" | grep -c . || true
}

# ---- 纯判定函数（输入 fixture，输出 RES_V/RES_D；--selftest 直接喂字符串调用）----

# judge_role $1=命中角色行数 $2="未删除权限行数|总行数|权限码列表" $3=期望权限集合 $4=角色 code
judge_role() {
  local found="$1" perm_line="$2" expected="$3" code="$4"
  if [[ "$found" == "0" ]]; then
    RES_V="SKIP"
    RES_D="未找到靶子角色 code=$code（该批次未跑过压测，或数据已被清理）"
    return 0
  fi
  if [[ "$found" != "1" ]]; then
    RES_V="FAIL"
    RES_D="靶子角色命中 $found 行（期望恰 1 行，code=$code）"
    return 0
  fi
  local active total codes
  IFS='|' read -r active total codes <<< "$perm_line"
  if [[ "$active" == "1" && "$codes" == "$expected" ]]; then
    RES_V="PASS"
    RES_D="角色=1；权限行 未删除=$active 总数=$total；集合=[$codes] 与脚本提交的 [$expected] 完全一致，无重复无残缺"
  else
    RES_V="FAIL"
    RES_D="权限集合不符：未删除行=$active（期望 1） 总行数=$total 实际=[$codes] 期望=[$expected]（重复或残缺）"
  fi
}

# judge_customer $1=标记 customer_name $2="总行数|未删除行数|customer_code" $3=期望编码（可空=不比对）
judge_customer() {
  local marker="$1" line="$2" expected_code="$3"
  local total active code
  IFS='|' read -r total active code <<< "$line"
  total="${total:-0}"
  if [[ "$total" == "0" ]]; then
    RES_V="SKIP"
    RES_D="未找到靶子客户 customer_name=$marker（计数 0；该批次未跑过压测，或数据已被清理）"
    return 0
  fi
  if [[ "$total" != "1" ]]; then
    RES_V="FAIL"
    RES_D="同一标记命中 $total 条客户（期望恰 1 条）——并发创建/幂等重放产生了重复：customer_name=$marker"
    return 0
  fi
  if [[ -n "$expected_code" && "$code" != "$expected_code" ]]; then
    RES_V="FAIL"
    RES_D="靶子客户编码不符：实际 customer_code=$code 期望=$expected_code（customer_name=$marker，疑似混入其它批次数据）"
    return 0
  fi
  RES_V="PASS"
  RES_D="客户=1（未删除=$active customer_code=$code）customer_name=$marker"
}

# judge_doc $1=靶子状态(0=不存在 1=存在 2=已软删) $2=version $3=status $4=remark $5=明细行数
#           $6=跑前基线 version $7=跑前基线明细行数 $8=race_doc_ok_200 $9=原 remark
judge_doc() {
  local exists="$1" version="$2" status="$3" remark="$4" items="$5"
  local base_ver="$6" base_items="$7" doc_ok="$8" orig="$9"

  if [[ "$exists" == "0" ]]; then
    RES_V="SKIP"
    RES_D="未找到靶子单据（id 不存在或已被删除）——该批次可能未跑过压测"
    return 0
  fi
  if [[ "$exists" == "2" ]]; then
    RES_V="FAIL"
    RES_D="靶子单据已被软删（deleted_flag=true）——压测脚本不会删除单据，属脚本之外的改写"
    return 0
  fi
  local current="当前 version=$version status=$status 明细行=$items remark=\"$remark\""
  if [[ -z "$base_ver" ]]; then
    RES_V="SKIP"
    RES_D="缺少跑前基线（未执行 --snapshot 或快照与 RUN_ID 不匹配），无法核对 version 增量；$current"
    return 0
  fi
  if [[ -z "$doc_ok" ]]; then
    RES_V="SKIP"
    RES_D="缺少 k6 summary 的 race_doc_ok_200（200 成功写入数），无法核对 version 增量；$current"
    return 0
  fi
  # 进入算术比较前的数字防线：version/基线/成功数/明细行数都必须是纯数字，
  # 否则 $(( )) 会因语法错误中断脚本，把「输入损坏」误报成别的退出码。
  if ! [[ "$version" =~ ^[0-9]+$ && "$base_ver" =~ ^[0-9]+$ && "$base_items" =~ ^[0-9]+$ \
       && "$doc_ok" =~ ^[0-9]+$ && "$items" =~ ^[0-9]+$ ]]; then
    RES_V="SKIP"
    RES_D="核对输入含非数字（version/基线/成功数/明细行数需为整数），无法核对；$current"
    return 0
  fi

  # remark 判定（以 08-concurrency.js 的实际行为为准）：
  #   已还原原值 → 放行，并把这次还原写入计入 version 期望（+1）；
  #   仍是 race-doc-<vu>-<iter> / 预检标记 PERF-LOAD-<RUN_ID>-DOC-PROBE → 放行
  #     （当前 k6 脚本没有还原 remark 的断言，见文件头注释）；
  #   其它值 → 被脚本之外改写 → FAIL。
  local restored=0 remark_verdict="ok" remark_note
  if [[ -n "$orig" && "$remark" == "$orig" ]]; then
    restored=1
    remark_note="remark 已还原为原值 \"$orig\"（计入 +1 次还原写入）"
  elif [[ "$remark" =~ ^race-doc-[0-9]+-[0-9]+$ ]]; then
    remark_note="remark=\"$remark\" 仍为并发写标记（08 脚本当前不还原 remark，按脚本实际行为放行；原值=\"$orig\"）"
  elif [[ "$remark" == "PERF-LOAD-$RUN_ID-DOC-PROBE" ]]; then
    remark_note="remark=\"$remark\" 为 setup 预检标记（并发写入全部未成功时的预期状态，放行）"
  else
    remark_verdict="bad"
    remark_note="remark=\"$remark\" 既非 race-doc-*、也非预检标记、也非原值 \"$orig\"，疑似被脚本之外改写"
  fi

  local expected=$((base_ver + 1 + doc_ok + restored))
  local version_ok="yes" items_ok="yes" status_ok="yes"
  [[ "$version" == "$expected" ]] || version_ok="no"
  [[ "$items" == "$base_items" ]] || items_ok="no"
  [[ "$status" == "草稿" ]] || status_ok="no"

  local restore_part=""
  [[ $restored -eq 1 ]] && restore_part=" + 还原1"
  local detail="version $base_ver→$version（基线$base_ver + 预检1 + 200成功$doc_ok$restore_part = 期望$expected）；明细行 $items（基线 $base_items）；状态=$status；$remark_note"

  if [[ "$version_ok" == "yes" && "$items_ok" == "yes" && "$status_ok" == "yes" && "$remark_verdict" == "ok" ]]; then
    RES_V="PASS"
    RES_D="$detail"
  else
    local bad=()
    [[ "$version_ok" == "yes" ]] || bad+=("version 增量不符（实际 $version 期望 $expected）")
    [[ "$items_ok" == "yes" ]] || bad+=("明细行数变化（实际 $items 基线 $base_items，出现重复或残缺）")
    [[ "$status_ok" == "yes" ]] || bad+=("单据状态被推进（实际 \"$status\" 期望 \"草稿\"）")
    [[ "$remark_verdict" == "ok" ]] || bad+=("remark 异常")
    RES_V="FAIL"
    RES_D="${bad[*]} | $detail"
  fi
}

# judge_unexpected $1=k6 summary 中 race_unexpected_status 的计数（空=读不到）
judge_unexpected() {
  local n="$1"
  if [[ -z "$n" ]]; then
    RES_V="SKIP"
    RES_D="读不到 race_unexpected_status（缺 k6 summary 或 jq），无法核对非预期状态码"
    return 0
  fi
  if [[ "$n" == "0" ]]; then
    RES_V="PASS"
    RES_D="race_unexpected_status=0（口径：只允许 200/201/409/422，见报告第 8 节）"
  else
    RES_V="FAIL"
    RES_D="race_unexpected_status=$n ≠ 0——有 401/403/5xx 等非预期状态码混入，本次并发结论不可用"
  fi
}

# ---------------------------------------------------------------------------
# SQL 口径（dry-run 时原样打印）
# ---------------------------------------------------------------------------
SQL_SNAPSHOT="SELECT o.id || '|' || o.version || '|' || (SELECT count(*) FROM so_sales_order_item i WHERE i.order_id = o.id) FROM so_sales_order o WHERE o.status = '草稿' AND o.deleted_flag = false ORDER BY o.id"
sql_role_find()   { printf "SELECT id || '|' || status FROM sys_role WHERE code = 'PERF-RACE-%s' AND deleted_flag = false" "$RUN_ID"; }
sql_role_perms()  { printf "SELECT count(*) FILTER (WHERE NOT deleted_flag) || '|' || count(*) || '|' || coalesce(string_agg(permission_code, ',' ORDER BY permission_code) FILTER (WHERE NOT deleted_flag), '') FROM sys_role_permission WHERE role_id = %s" "$1"; }
sql_customer()    { printf "SELECT count(*) || '|' || count(*) FILTER (WHERE NOT deleted_flag) || '|' || coalesce(min(customer_code), '') FROM md_customer WHERE customer_name = '%s'" "$1"; }
sql_doc()         { printf "SELECT version || '|' || status || '|' || coalesce(remark, '') || '|' || deleted_flag || '|' || (SELECT count(*) FROM so_sales_order_item i WHERE i.order_id = o.id) FROM so_sales_order o WHERE o.id = %s" "$1"; }

print_dry_run() {
  printf '[verify-race][dry-run] RUN_ID=%s（只打印 SQL，不连接数据库）\n\n' "$RUN_ID"
  printf -- '---- 跑前基线（--snapshot，perf/run.sh 在 run_race 里调用）----\n%s\n\n' "$SQL_SNAPSHOT"
  printf -- '---- A 同一角色权限并发替换 ----\n%s\n%s\n\n' \
    "$(sql_role_find)" "$(sql_role_perms '<由上一步查得的 role_id>')"
  printf -- '---- B 同一幂等键并发重放（期望计数=1）----\n%s\n\n' \
    "$(sql_customer "PERF-LOAD-$RUN_ID-REPLAY")"
  printf -- '---- C 同一客户编码并发创建（期望计数=1）----\n%s\n\n' \
    "$(sql_customer "PERF-LOAD-$RUN_ID-SAMECODE")"
  printf -- '---- D 同一单据并发整体替换（doc_id 来自 k6 setup_data/日志/--doc-id）----\n%s\n' \
    "$(sql_doc '<doc_id>')"
  printf '     口径：version == 跑前基线 + 1（预检）+ race_doc_ok_200（+1 若 remark 已还原）；明细行数 == 基线；状态 == 草稿；remark 为脚本族痕迹\n'
  printf '     附：race_unexpected_status 从 %s 读取（不需要 SQL）\n' "$SUMMARY_JSON"
}

# ---------------------------------------------------------------------------
# 跑前快照：记录靶子单据的 version / 明细行数（只读；缺库环境 → SKIP 退出码 0）
# ---------------------------------------------------------------------------
cmd_snapshot() {
  if ! require_db_env || ! db_connect_ok; then
    printf '[verify-race][SKIP] --snapshot 未执行：数据库环境不可用（跑后核对的 D 场景 version 增量项将因此 SKIP）\n'
    exit 0
  fi
  local rows n
  rows="$(q "$SQL_SNAPSHOT")"
  n="$(count_lines "$rows")"
  mkdir -p "$RESULT_DIR"
  {
    printf '# verify-race-sql.sh 跑前基线 %s run_id=%s（列: doc_id|version|明细行数）\n' \
      "$(date '+%F %T')" "${RUN_ID:-unknown}"
    if [[ -n "$rows" ]]; then
      printf '%s\n' "$rows"
    fi
  } > "$BASELINE_FILE"
  printf '[verify-race] --snapshot 完成：%s（可写草稿单 %s 张）\n' "$BASELINE_FILE" "$n"
  if [[ "$n" == "0" ]]; then
    printf '[verify-race] 诊断：没有状态为「草稿」的未删除单据，跑后 D 场景将 SKIP（与 k6 setup 的靶子发现同口径）\n'
  fi
  exit 0
}

# ---------------------------------------------------------------------------
# 跑后核对
# ---------------------------------------------------------------------------
jget() { # $1=jq 表达式；summary 不可用时直接空
  jq -r "$1 // empty" "$SUMMARY_JSON" 2>/dev/null || true
}

cmd_verify() {
  printf '[verify-race] RUN_ID=%s 库=%s@%s:%s/%s\n' "$RUN_ID" \
    "${SPRING_DATASOURCE_USERNAME:-leo}" "${SPRING_DATASOURCE_HOST:-localhost}" \
    "${SPRING_DATASOURCE_PORT:-5432}" "${SPRING_DATASOURCE_DB:-leo}"

  local summary_ok=false
  if [[ -f "$SUMMARY_JSON" ]] && command -v jq >/dev/null 2>&1; then
    summary_ok=true
  fi

  # ---- U：非预期状态码（只读 k6 summary，不需要数据库；口径见报告第 8 节）----
  if [[ "$summary_ok" == true ]]; then
    judge_unexpected "$(jget '.metrics.race_unexpected_status.count')"
  else
    RES_V="SKIP"
    RES_D="缺 k6 summary（$SUMMARY_JSON）或 jq，无法读取 race_unexpected_status"
  fi
  report "$RES_V" "U 非预期状态码" "$RES_D"

  # ---- 数据库环境：缺环境变量/连不上库 → A~D 全部 SKIP（诊断已写 stderr）----
  if ! require_db_env || ! db_connect_ok; then
    report SKIP "A 角色权限并发替换" "缺数据库连接环境，未核对（诊断见上方 [SKIP] 行）"
    report SKIP "B 幂等键并发重放"   "缺数据库连接环境，未核对"
    report SKIP "C 同码并发创建"     "缺数据库连接环境，未核对"
    report SKIP "D 单据并发整体替换" "缺数据库连接环境，未核对"
    if finish; then exit 0; else exit 1; fi
  fi

  # ---- k6 summary / 日志 / 基线里的输入 ----
  local doc_id="" orig_remark="" doc_ok="" expected_perms="roles:read"
  local setup_role_id="" setup_replay_code="" setup_race_code=""
  if [[ "$summary_ok" == true ]]; then
    doc_id="$(jget '.setup_data.draftOrderId')"
    orig_remark="$(jget '.setup_data.draftOriginalRemark')"
    doc_ok="$(jget '.metrics.race_doc_ok_200.count')"
    setup_role_id="$(jget '.setup_data.roleId')"
    setup_replay_code="$(jget '.setup_data.replayCode')"
    setup_race_code="$(jget '.setup_data.raceCode')"
    local perms
    perms="$(jq -r '(.setup_data.permissions // []) | join(",")' "$SUMMARY_JSON" 2>/dev/null || true)"
    [[ -n "$perms" ]] && expected_perms="$perms"
  fi
  if [[ -z "$doc_id" && -f "$K6_LOG" ]]; then
    # 兜底：从 k6 日志解析 setup 打印的靶子行
    doc_id="$(grep -o '同一单据并发写目标: id=[0-9]*' "$K6_LOG" 2>/dev/null | tail -1 | grep -o '[0-9][0-9]*$' || true)"
    if [[ -z "$orig_remark" ]]; then
      orig_remark="$(grep -o '原 remark="[^"]*"' "$K6_LOG" 2>/dev/null | tail -1 | sed 's/^原 remark="//; s/"$//' || true)"
    fi
  fi
  if [[ -n "$DOC_ID_OPT" ]]; then
    doc_id="$DOC_ID_OPT"
  elif [[ -n "${LEO_PERF_RACE_DOC_ID:-}" ]]; then
    doc_id="$LEO_PERF_RACE_DOC_ID"
  fi

  # ---- A：同一角色权限并发替换 ----
  local rows found role_id perm_line=""
  rows="$(q "$(sql_role_find)")"
  found="$(count_lines "$rows")"
  if [[ "$found" == "1" ]]; then
    role_id="${rows%%|*}"
    if [[ "$role_id" =~ ^[0-9]+$ ]]; then
      perm_line="$(q "$(sql_role_perms "$role_id")")"
      if [[ -n "$setup_role_id" && "$setup_role_id" != "$role_id" ]]; then
        printf '[verify-race] 提示：k6 setup_data.roleId=%s 与按 code 查到的 %s 不一致（summary 可能来自其它批次，已按 RUN_ID 的角色码为准）\n' \
          "$setup_role_id" "$role_id"
      fi
    else
      found=0
    fi
  fi
  judge_role "$found" "$perm_line" "$expected_perms" "PERF-RACE-$RUN_ID"
  report "$RES_V" "A 角色权限并发替换" "$RES_D"

  # ---- B：同一幂等键并发重放 ----
  local b_marker="PERF-LOAD-$RUN_ID-REPLAY" b_line
  b_line="$(q "$(sql_customer "$b_marker")")"
  judge_customer "$b_marker" "$b_line" "$setup_replay_code"
  report "$RES_V" "B 幂等键并发重放" "$RES_D"

  # ---- C：同一客户编码并发创建 ----
  local c_marker="PERF-LOAD-$RUN_ID-SAMECODE" c_line
  c_line="$(q "$(sql_customer "$c_marker")")"
  judge_customer "$c_marker" "$c_line" "$setup_race_code"
  report "$RES_V" "C 同码并发创建" "$RES_D"

  # ---- D：同一单据并发整体替换 ----
  if [[ -n "$doc_id" && ! "$doc_id" =~ ^[0-9]+$ ]]; then
    printf '[verify-race][ERROR] doc-id 非法（必须是十进制数字）: %s\n' "$doc_id" >&2
    exit 2
  fi
  if [[ -z "$doc_id" ]]; then
    RES_V="SKIP"
    RES_D="无法定位靶子单据（k6 summary/日志均缺 draftOrderId，也未提供 --doc-id）——该批次可能未跑过压测"
    report "$RES_V" "D 单据并发整体替换" "$RES_D"
  else
    # 跑前基线：只认与当前 RUN_ID 匹配的快照，避免拿旧快照误判
    local base_ver="" base_items="" base_run_id="" brow
    if [[ -f "$BASELINE_FILE" ]]; then
      base_run_id="$(grep -o 'run_id=[A-Za-z0-9._-]*' "$BASELINE_FILE" 2>/dev/null | head -1 | cut -d= -f2 || true)"
      brow="$(grep -E "^${doc_id}\|" "$BASELINE_FILE" 2>/dev/null || true)"
      if [[ -n "$brow" ]]; then
        # 基线行格式：doc_id|version|明细行数 —— 第 1 段是 id，第 2/3 段才是要的值
        local _base_doc_id
        IFS='|' read -r _base_doc_id base_ver base_items <<< "$brow"
      fi
    fi
    if [[ -n "$base_ver" && -n "$base_run_id" && "$base_run_id" != "unknown" && "$base_run_id" != "$RUN_ID" ]]; then
      printf '[verify-race] 诊断：基线快照属于旧批次 run_id=%s（当前 %s），忽略之\n' "$base_run_id" "$RUN_ID"
      base_ver=""
      base_items=""
    fi
    local doc_row exists=0 version="" status="" remark="" items=""
    doc_row="$(q "$(sql_doc "$doc_id")")"
    if [[ -n "$doc_row" ]]; then
      IFS='|' read -r version status remark items_flags items <<< "$doc_row"
      # 行内第 4 段是 deleted_flag（t/f），第 5 段是明细行数
      if [[ "$items_flags" == "t" ]]; then
        exists=2
      else
        exists=1
      fi
    fi
    judge_doc "$exists" "$version" "$status" "$remark" "$items" "$base_ver" "$base_items" "$doc_ok" "$orig_remark"
    report "$RES_V" "D 单据并发整体替换" "$RES_D"
  fi

  if finish; then exit 0; else exit 1; fi
}

# ---------------------------------------------------------------------------
# 离线自测：fixture 驱动 PASS/FAIL/SKIP 三分支 + 子进程验证真实退出码。
# 不连数据库、不跑 k6；只测判定函数、只读防线与退出码约定。
# ---------------------------------------------------------------------------
selftest_case() { # --selftest-case：供自测子进程调用，退出码即被测约定
  RUN_ID="${RUN_ID:-TESTRUN}"
  case "$1" in
    pass)
      judge_role 1 "1|1|roles:read" "roles:read" "PERF-RACE-$RUN_ID"
      report "$RES_V" "A 角色权限并发替换" "$RES_D"
      judge_customer "PERF-LOAD-$RUN_ID-REPLAY" "1|1|363685254545801216" "363685254545801216"
      report "$RES_V" "B 幂等键并发重放" "$RES_D"
      judge_doc 1 220 "草稿" "race-doc-3-1" 1 3 1 216 "E2E-B-1789448269548-SO-2"
      report "$RES_V" "D 单据并发整体替换" "$RES_D"
      judge_unexpected 0
      report "$RES_V" "U 非预期状态码" "$RES_D"
      ;;
    fail)
      judge_customer "PERF-LOAD-$RUN_ID-SAMECODE" "2|2|363685254671630336" "363685254671630336"
      report "$RES_V" "C 同码并发创建" "$RES_D"
      judge_doc 1 219 "草稿" "race-doc-3-1" 1 3 1 216 "E2E-B-1789448269548-SO-2"
      report "$RES_V" "D 单据并发整体替换" "$RES_D"
      ;;
    skip)
      judge_role 0 "" "roles:read" "PERF-RACE-$RUN_ID"
      report "$RES_V" "A 角色权限并发替换" "$RES_D"
      judge_customer "PERF-LOAD-$RUN_ID-REPLAY" "0|0|" ""
      report "$RES_V" "B 幂等键并发重放" "$RES_D"
      judge_unexpected ""
      report "$RES_V" "U 非预期状态码" "$RES_D"
      ;;
    *)
      printf '[verify-race][ERROR] 未知 selftest case: %s\n' "$1" >&2
      exit 2 ;;
  esac
  if finish; then exit 0; else exit 1; fi
}

run_selftest() {
  local total=0 fails=0 rc
  expect() { # $1=描述 $2=期望 $3=实际
    total=$((total + 1))
    if [[ "$2" == "$3" ]]; then
      printf '  [ok]   %s\n' "$1"
    else
      printf '  [NG]   %s（期望=%s 实际=%s）\n' "$1" "$2" "$3"
      fails=$((fails + 1))
    fi
  }
  printf '[verify-race] 离线自测开始（不连数据库、不跑 k6）\n'

  # 1) 只读防线：放行合法 SELECT，拒绝写语句/多语句/白名单外表
  rc=0; assert_readonly_sql "SELECT count(*) FROM md_customer" 2>/dev/null || rc=$?
  expect "只读防线放行合法 SELECT" 0 "$rc"
  rc=0; assert_readonly_sql "DELETE FROM md_customer" 2>/dev/null || rc=$?
  expect "只读防线拒绝 DELETE" 1 "$rc"
  rc=0; assert_readonly_sql "UPDATE md_customer SET remark='x'" 2>/dev/null || rc=$?
  expect "只读防线拒绝 UPDATE" 1 "$rc"
  rc=0; assert_readonly_sql "SELECT 1; DROP TABLE md_customer" 2>/dev/null || rc=$?
  expect "只读防线拒绝分号拼接" 1 "$rc"
  rc=0; assert_readonly_sql "WITH x AS (SELECT 1) SELECT * FROM x" 2>/dev/null || rc=$?
  expect "只读防线拒绝 WITH（可包裹写语句）" 1 "$rc"
  rc=0; assert_allowed_tables "SELECT 1 FROM pg_shadow" 2>/dev/null || rc=$?
  expect "表名白名单拒绝 pg_shadow" 1 "$rc"
  rc=0; assert_allowed_tables "SELECT 1 FROM md_customer c JOIN pg_stat_activity a ON true" 2>/dev/null || rc=$?
  expect "表名白名单拒绝 JOIN 白名单外表" 1 "$rc"
  rc=0; assert_allowed_tables "SELECT count(*) FROM so_sales_order_item i WHERE i.order_id IN (SELECT id FROM so_sales_order)" 2>/dev/null || rc=$?
  expect "表名白名单放行子查询内的白名单表" 0 "$rc"

  # 2) 场景判定三分支（fixture 字符串喂给纯判定函数）
  RUN_ID="TESTRUN"
  judge_role 1 "1|1|roles:read" "roles:read" "PERF-RACE-TESTRUN" >/dev/null
  expect "A 正确权限集合 → PASS" PASS "$RES_V"
  judge_role 0 "" "roles:read" "PERF-RACE-TESTRUN" >/dev/null
  expect "A 无靶子角色 → SKIP" SKIP "$RES_V"
  judge_role 1 "2|2|roles:read,roles:read" "roles:read" "PERF-RACE-TESTRUN" >/dev/null
  expect "A 重复权限行 → FAIL" FAIL "$RES_V"
  judge_role 1 "0|1|" "roles:read" "PERF-RACE-TESTRUN" >/dev/null
  expect "A 权限残缺 → FAIL" FAIL "$RES_V"

  judge_customer "PERF-LOAD-TESTRUN-REPLAY" "1|1|C1" "C1" >/dev/null
  expect "B 恰 1 条客户 → PASS" PASS "$RES_V"
  judge_customer "PERF-LOAD-TESTRUN-REPLAY" "0|0|" "C1" >/dev/null
  expect "B 无靶子数据（没跑过压测）→ SKIP" SKIP "$RES_V"
  judge_customer "PERF-LOAD-TESTRUN-REPLAY" "2|2|C1" "C1" >/dev/null
  expect "B 重复创建 2 条 → FAIL" FAIL "$RES_V"
  judge_customer "PERF-LOAD-TESTRUN-SAMECODE" "1|1|C9" "C1" >/dev/null
  expect "C 编码与 setup 不符 → FAIL" FAIL "$RES_V"

  judge_doc 1 220 "草稿" "race-doc-3-1" 1 3 1 216 "E2E-B-1" >/dev/null
  expect "D version=基线+预检+成功数 → PASS" PASS "$RES_V"
  judge_doc 1 221 "草稿" "E2E-B-1" 1 3 1 216 "E2E-B-1" >/dev/null
  expect "D remark 已还原（期望 +1）→ PASS" PASS "$RES_V"
  judge_doc 1 4 "草稿" "PERF-LOAD-TESTRUN-DOC-PROBE" 1 3 1 0 "E2E-B-1" >/dev/null
  expect "D 全部 409（仅预检写入，remark=预检标记）→ PASS" PASS "$RES_V"
  judge_doc 1 219 "草稿" "race-doc-3-1" 1 3 1 216 "E2E-B-1" >/dev/null
  expect "D version 增量不符 → FAIL" FAIL "$RES_V"
  judge_doc 1 220 "已审核" "race-doc-3-1" 1 3 1 216 "E2E-B-1" >/dev/null
  expect "D 状态被推进 → FAIL" FAIL "$RES_V"
  judge_doc 1 220 "草稿" "race-doc-3-1" 2 3 1 216 "E2E-B-1" >/dev/null
  expect "D 明细行数变化 → FAIL" FAIL "$RES_V"
  judge_doc 1 220 "草稿" "别改的备注" 1 3 1 216 "E2E-B-1" >/dev/null
  expect "D remark 被脚本之外改写 → FAIL" FAIL "$RES_V"
  judge_doc 2 220 "草稿" "race-doc-3-1" 1 3 1 216 "E2E-B-1" >/dev/null
  expect "D 靶子被软删 → FAIL" FAIL "$RES_V"
  judge_doc 0 "" "" "" "" "" "" "" "" >/dev/null
  expect "D 找不到靶子 → SKIP" SKIP "$RES_V"
  judge_doc 1 220 "草稿" "race-doc-3-1" 1 "" "" 216 "E2E-B-1" >/dev/null
  expect "D 缺跑前基线 → SKIP（不误报 FAIL）" SKIP "$RES_V"
  judge_doc 1 220 "草稿" "race-doc-3-1" 1 3 1 "" "E2E-B-1" >/dev/null
  expect "D 缺 race_doc_ok_200 → SKIP" SKIP "$RES_V"

  judge_unexpected 0 >/dev/null; expect "U=0 → PASS" PASS "$RES_V"
  judge_unexpected 7 >/dev/null;  expect "U>0 → FAIL" FAIL "$RES_V"
  judge_unexpected "" >/dev/null; expect "U 缺数据 → SKIP" SKIP "$RES_V"

  # 3) 退出码约定（真实子进程跑同一脚本）
  rc=0; bash "$0" --selftest-case pass >/dev/null 2>&1 || rc=$?
  expect "退出码：全 PASS 场景 → 0" 0 "$rc"
  rc=0; bash "$0" --selftest-case fail >/dev/null 2>&1 || rc=$?
  expect "退出码：存在 FAIL 场景 → 1" 1 "$rc"
  rc=0; bash "$0" --selftest-case skip >/dev/null 2>&1 || rc=$?
  expect "退出码：全 SKIP（没跑过压测）→ 0" 0 "$rc"
  rc=0; bash "$0" --dry-run --run-id "TESTRUN" >/dev/null 2>&1 || rc=$?
  expect "退出码：--dry-run → 0" 0 "$rc"
  rc=0; bash "$0" --run-id 'bad;id' >/dev/null 2>&1 || rc=$?
  expect "退出码：非法 RUN_ID → 2" 2 "$rc"
  rc=0; bash "$0" --no-such-flag >/dev/null 2>&1 || rc=$?
  expect "退出码：未知参数 → 2" 2 "$rc"

  printf '[verify-race] 自测结束：%s 项，失败 %s 项\n' "$total" "$fails"
  if [[ $fails -gt 0 ]]; then
    printf '[verify-race] 自测未通过\n'
    exit 1
  fi
  printf '[verify-race] 自测全部通过\n'
  exit 0
}

# ---------------------------------------------------------------------------
# 入口
# ---------------------------------------------------------------------------
case "$MODE" in
  dry-run)
    validate_run_id
    print_dry_run
    exit 0 ;;
  snapshot)
    cmd_snapshot ;;
  selftest)
    run_selftest ;;
  selftest-case)
    selftest_case "$SELFTEST_CASE" ;;
  verify)
    validate_run_id
    cmd_verify ;;
esac
