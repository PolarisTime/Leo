#!/usr/bin/env bash
# Leo ERP 生产健康守卫（只读为主）
#
# 背景：2026-10-09 生产事故中，根文件系统被写满约 1 小时，PostgreSQL 因无法写盘
# 进入崩溃恢复循环，而现有的 /api/v2.0/health 只探测"数据库连接/Redis PING"：
#   - 磁盘写满期间 SELECT 1（读）仍可能成功，健康检查不会翻转；
#   - 磁盘水位、写盘失败(SQLState 53100)、恢复中(57P03)、Redis 拒写(MISCONF)
#     没有任何采集与告警。
# 本脚本补齐上述盲区，供 systemd timer 每分钟执行；只做读取类操作，
# 唯一的写操作是 Redis 探针键（带 TTL、立即删除，可用 --no-redis-write-probe 关闭）。
#
# 用法:
#   health-guard.sh [--env-file <path>] [--json] [--quiet] [--no-redis-write-probe]
#
# 退出码: 0=OK  1=WARN  2=CRIT  3=用法/配置错误
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}")"

# ---------------------------------------------------------------- 默认配置
# 阈值与监控对象均可用环境变量覆盖，便于在其他环境复用。
LEO_GUARD_MOUNTS="${LEO_GUARD_MOUNTS:-/:/home}"
LEO_GUARD_DISK_WARN_PCT="${LEO_GUARD_DISK_WARN_PCT:-80}"
LEO_GUARD_DISK_CRIT_PCT="${LEO_GUARD_DISK_CRIT_PCT:-90}"
LEO_GUARD_INODE_WARN_PCT="${LEO_GUARD_INODE_WARN_PCT:-80}"
LEO_GUARD_INODE_CRIT_PCT="${LEO_GUARD_INODE_CRIT_PCT:-90}"
LEO_GUARD_HEALTH_URL="${LEO_GUARD_HEALTH_URL:-http://127.0.0.1:57217/api/v2.0/health}"
LEO_GUARD_PUBLIC_HEALTH_URL="${LEO_GUARD_PUBLIC_HEALTH_URL:-}"
LEO_GUARD_LOG_FILE="${LEO_GUARD_LOG_FILE:-/instance/steelx/logs/backend.log}"
LEO_GUARD_LOG_WINDOW_SECONDS="${LEO_GUARD_LOG_WINDOW_SECONDS:-900}"
LEO_GUARD_ENV_FILE="${LEO_GUARD_ENV_FILE:-/instance/steelx/shared/steelx.env}"
LEO_GUARD_TIMEOUT_SECONDS="${LEO_GUARD_TIMEOUT_SECONDS:-5}"
LEO_GUARD_REDIS_WRITE_PROBE="${LEO_GUARD_REDIS_WRITE_PROBE:-true}"
LEO_GUARD_ALERT_WEBHOOK="${LEO_GUARD_ALERT_WEBHOOK:-}"
LEO_GUARD_HOST_LABEL="${LEO_GUARD_HOST_LABEL:-$(hostname -s 2>/dev/null || echo unknown)}"

OUTPUT_JSON=false
QUIET=false
declare -a RESULTS=()
declare -a PROBLEMS=()
WORST=0   # 0=OK 1=WARN 2=CRIT

usage() {
  cat <<'USAGE'
Leo ERP 生产健康守卫（只读为主）

用法:
  health-guard.sh [--env-file <path>] [--json] [--quiet] [--no-redis-write-probe]

选项:
  --env-file <path>       依赖凭据文件（支持 --env-file=<path> 形式）
  --json                  输出机器可读 JSON
  --quiet                 仅输出 RESULT 汇总行
  --no-redis-write-probe  跳过 Redis 写入探针（完全不写 Redis）
  -h, --help              显示本帮助

退出码: 0=OK  1=WARN  2=CRIT  3=用法/配置错误
USAGE
}

# ---------------------------------------------------------------- 工具函数
log() { $QUIET || printf '%s\n' "$*"; }

json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"; s="${s//\"/\\\"}"
  s="${s//$'\n'/\\n}"; s="${s//$'\t'/\\t}"; s="${s//$'\r'/\\r}"
  printf '%s' "$s"
}

# 记录一条检查结果。level: OK|WARN|CRIT
record() {
  local level="$1" name="$2" detail="$3"
  RESULTS+=("$level|$name|$detail")
  case "$level" in
    OK)   [[ $WORST -lt 0 ]] && WORST=0 ;;
    WARN) [[ $WORST -lt 1 ]] && WORST=1; PROBLEMS+=("$name: $detail") ;;
    CRIT) WORST=2; PROBLEMS+=("$name: $detail") ;;
  esac
  log "[$level] $name - $detail"
}

require_cmd_or_skip() {
  local cmd="$1" name="$2"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    record WARN "$name" "缺少命令 $cmd，跳过检查"
    return 1
  fi
  return 0
}

# ---------------------------------------------------------------- 参数解析
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file) LEO_GUARD_ENV_FILE="${2:?--env-file 需要路径}"; shift 2 ;;
    --env-file=*) LEO_GUARD_ENV_FILE="${1#*=}"; [[ -n "$LEO_GUARD_ENV_FILE" ]] || { echo "--env-file 需要路径" >&2; exit 3; }; shift ;;
    --json) OUTPUT_JSON=true; shift ;;
    --quiet) QUIET=true; shift ;;
    --no-redis-write-probe) LEO_GUARD_REDIS_WRITE_PROBE=false; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage >&2; exit 3 ;;
  esac
done

# 依赖凭据文件（生产为 600，未提供时相关检查降级为 WARN）
if [[ -r "$LEO_GUARD_ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$LEO_GUARD_ENV_FILE"
  set +a
fi

# 数值配置校验必须放在加载 env 文件之后：env 文件中的值同样需要被校验，
# 否则非法值会一路走到算术比较处( (( x >= VAR )) 会把值当变量名解析)，
# 在 set -u 下直接终止脚本并给出难以理解的报错，而不是清晰的配置错误。
for numeric in LEO_GUARD_DISK_WARN_PCT LEO_GUARD_DISK_CRIT_PCT \
               LEO_GUARD_INODE_WARN_PCT LEO_GUARD_INODE_CRIT_PCT \
               LEO_GUARD_LOG_WINDOW_SECONDS LEO_GUARD_TIMEOUT_SECONDS; do
  if ! [[ "${!numeric}" =~ ^[0-9]+$ ]]; then
    echo "配置错误: $numeric 必须是非负整数（当前=${!numeric}，来源=命令行或 $LEO_GUARD_ENV_FILE）" >&2
    exit 3
  fi
done

# 校验通过后固化为本地数值，后续算术比较不再依赖外部可变输入
readonly DISK_WARN=$LEO_GUARD_DISK_WARN_PCT DISK_CRIT=$LEO_GUARD_DISK_CRIT_PCT
readonly INODE_WARN=$LEO_GUARD_INODE_WARN_PCT INODE_CRIT=$LEO_GUARD_INODE_CRIT_PCT

# ---------------------------------------------------------------- 检查：磁盘
check_disk() {
  local mount="$1"
  require_cmd_or_skip df "disk$mount" || return 0

  local line used_pct inode_pct
  line="$(df -P "$mount" 2>/dev/null | awk 'NR==2{print $5; exit}')" || true
  if [[ -z "$line" ]]; then
    record CRIT "disk$mount" "无法读取 $mount 使用率"
    return 0
  fi
  used_pct="${line%\%}"

  inode_pct="$(df -Pi "$mount" 2>/dev/null | awk 'NR==2{print $5; exit}')" || true
  inode_pct="${inode_pct%\%}"

  local detail="${mount} 空间 ${used_pct}%"
  [[ -n "$inode_pct" ]] && detail="$detail, inode ${inode_pct}%"

  if (( used_pct >= DISK_CRIT )); then
    record CRIT "disk$mount" "$detail（阈值 ${DISK_CRIT}%）"
  elif (( used_pct >= DISK_WARN )); then
    record WARN "disk$mount" "$detail（阈值 ${DISK_WARN}%）"
  else
    record OK "disk$mount" "$detail"
  fi

  if [[ -n "$inode_pct" ]]; then
    if (( inode_pct >= INODE_CRIT )); then
      record CRIT "inode$mount" "$mount inode ${inode_pct}%"
    elif (( inode_pct >= INODE_WARN )); then
      record WARN "inode$mount" "$mount inode ${inode_pct}%"
    else
      record OK "inode$mount" "$mount inode ${inode_pct}%"
    fi
  fi
}

# ---------------------------------------------------------------- 检查：PostgreSQL
check_postgres() {
  if ! command -v psql >/dev/null 2>&1; then
    record WARN "postgres" "缺少 psql，跳过检查"
    return 0
  fi
  if [[ -z "${SPRING_DATASOURCE_DB:-}" || -z "${SPRING_DATASOURCE_USERNAME:-}" ]]; then
    record WARN "postgres" "未提供数据库配置（LEO_GUARD_ENV_FILE=${LEO_GUARD_ENV_FILE}）"
    return 0
  fi

  local out
  out="$(PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-}" timeout "$LEO_GUARD_TIMEOUT_SECONDS" \
    psql -h "${SPRING_DATASOURCE_HOST:-localhost}" -p "${SPRING_DATASOURCE_PORT:-5432}" \
         -U "$SPRING_DATASOURCE_USERNAME" -d "$SPRING_DATASOURCE_DB" \
         -Atqc "select pg_is_in_recovery()::text || '|' || (select 1)::text" 2>&1)" || true

  if [[ "$out" == "false|1" ]]; then
    record OK "postgres" "${SPRING_DATASOURCE_DB} 可连接且未处于恢复模式"
  elif [[ "$out" == true* ]]; then
    record CRIT "postgres" "数据库处于崩溃恢复/只读模式（pg_is_in_recovery=true）"
  else
    record CRIT "postgres" "数据库不可用: $(printf '%s' "$out" | head -c 200)"
  fi
}

# ---------------------------------------------------------------- 检查：Redis
check_redis() {
  if ! command -v redis-cli >/dev/null 2>&1; then
    record WARN "redis" "缺少 redis-cli，跳过检查"
    return 0
  fi
  local host="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
  local port="${SPRING_DATA_REDIS_PORT:-6379}"
  local db="${SPRING_DATA_REDIS_DATABASE:-0}"
  local pass="${SPRING_DATA_REDIS_PASSWORD:-}"
  local -a args=(-h "$host" -p "$port" -n "$db" --no-auth-warning)
  [[ -n "$pass" ]] && args+=(-a "$pass")

  local ping
  ping="$(timeout "$LEO_GUARD_TIMEOUT_SECONDS" redis-cli "${args[@]}" ping 2>&1)" || true
  if [[ "$ping" != *PONG* ]]; then
    record CRIT "redis" "PING 失败: $(printf '%s' "$ping" | head -c 120)"
    return 0
  fi

  if [[ "$LEO_GUARD_REDIS_WRITE_PROBE" != "true" ]]; then
    record OK "redis" "$host:$port/db$db PING 正常（未做写入探针）"
    return 0
  fi

  # 写入探针：TTL 5s + 立即删除，用于发现 stop-writes-on-bgsave-error(MISCONF) / 只读
  local key="leo:guard:probe:$$"
  local set_out
  set_out="$(timeout "$LEO_GUARD_TIMEOUT_SECONDS" redis-cli "${args[@]}" set "$key" "$(date +%s)" EX 5 2>&1)" || true
  timeout "$LEO_GUARD_TIMEOUT_SECONDS" redis-cli "${args[@]}" del "$key" >/dev/null 2>&1 || true

  if [[ "$set_out" == "OK" ]]; then
    record OK "redis" "$host:$port/db$db PING 与写入探针均正常"
  else
    record CRIT "redis" "写入被拒绝（疑似 RDB 持久化失败/MISCONF）: $(printf '%s' "$set_out" | head -c 160)"
  fi
}

# ---------------------------------------------------------------- 检查：HTTP 端点
check_http() {
  local url="$1" name="$2"
  [[ -n "$url" ]] || return 0
  if ! command -v curl >/dev/null 2>&1; then
    record WARN "$name" "缺少 curl，跳过检查"
    return 0
  fi

  local body http_code
  body="$(mktemp)" || { record WARN "$name" "无法创建临时文件"; return 0; }
  http_code="$(timeout "$LEO_GUARD_TIMEOUT_SECONDS" curl -sS -o "$body" \
      -w '%{http_code}' "$url" 2>/dev/null)" || http_code="000"
  local payload
  payload="$(head -c 300 "$body" 2>/dev/null || true)"
  rm -f -- "$body"

  if [[ "$http_code" == "200" && "$payload" == *'"UP"'* ]]; then
    record OK "$name" "HTTP 200 UP"
  elif [[ "$http_code" == "000" ]]; then
    record CRIT "$name" "无法连接 $url"
  else
    record CRIT "$name" "HTTP $http_code 响应异常: $(printf '%s' "$payload" | tr -d '\n' | head -c 200)"
  fi
}

# ---------------------------------------------------------------- 检查：日志特征
check_logs() {
  local file="$1"
  if [[ ! -r "$file" ]]; then
    record WARN "applog" "日志不可读，跳过: $file"
    return 0
  fi
  require_cmd_or_skip awk "applog" || return 0

  # 只回看最近 N 秒（应用日志时间戳为 ISO8601 UTC），避免把历史故障当当前故障。
  # 实现要点：ISO8601 定长格式在字典序上等价于时间序，故直接做字符串比较即可，
  # 比 mktime()(gawk 扩展) 更可移植；此前按"精确到秒的前缀匹配"会导致几乎永不命中。
  local since
  since="$(date -u -d "@$(( $(date +%s) - LEO_GUARD_LOG_WINDOW_SECONDS ))" +%Y-%m-%dT%H:%M:%S 2>/dev/null)" || since=""
  local hits error_pat='SQLState: 53100|SQLState: 57P03|MISCONF|No space left on device|database system is in recovery mode'
  if [[ -n "$since" ]]; then
    hits="$(tail -n 20000 "$file" 2>/dev/null \
      | awk -v since="$since" -v pat="$error_pat" '
          BEGIN { FS = "\"@timestamp\":\"" }
          NF < 2 { next }
          substr($2, 1, 19) < since { next }
          $0 ~ pat { count++ }
          END { print count + 0 }')" || hits=0
  else
    # 时间戳格式不可解析时退化为不限时间窗（偏保守：宁可多报）
    hits="$(tail -n 20000 "$file" 2>/dev/null | grep -acE "$error_pat")" || hits=0
  fi

  if (( hits > 0 )); then
    record CRIT "applog" "最近 ${LEO_GUARD_LOG_WINDOW_SECONDS}s 出现 $hits 条存储/数据库致命错误(53100/57P03/MISCONF/No space)"
  else
    record OK "applog" "最近 ${LEO_GUARD_LOG_WINDOW_SECONDS}s 无存储/数据库致命错误"
  fi

  # systemd journal：文件系统写满的最早信号
  if command -v journalctl >/dev/null 2>&1; then
    local jhits
    jhits="$(timeout "$LEO_GUARD_TIMEOUT_SECONDS" journalctl --since "-${LEO_GUARD_LOG_WINDOW_SECONDS} seconds" --no-pager 2>/dev/null \
      | grep -ac 'No space left on device')" || jhits=0
    if (( jhits > 0 )); then
      record CRIT "journal" "最近 ${LEO_GUARD_LOG_WINDOW_SECONDS}s journal 有 $jhits 条 No space left on device"
    else
      record OK "journal" "journal 无 ENOSPC 记录"
    fi
  fi
}

# ---------------------------------------------------------------- 告警外发
send_alert() {
  [[ -n "$LEO_GUARD_ALERT_WEBHOOK" ]] || return 0
  command -v curl >/dev/null 2>&1 || return 0
  local level_word="WARN"
  (( WORST >= 2 )) && level_word="CRIT"
  local joined=""
  if (( ${#PROBLEMS[@]} > 0 )); then
    joined="$(printf '%s; ' "${PROBLEMS[@]}")"
  fi
  local payload
  payload="$(printf '{"host":"%s","level":"%s","script":"%s","problems":"%s"}' \
      "$(json_escape "$LEO_GUARD_HOST_LABEL")" "$level_word" "$(json_escape "$SCRIPT_NAME")" \
      "$(json_escape "$joined")")"
  timeout "$LEO_GUARD_TIMEOUT_SECONDS" curl -sS -X POST -H 'Content-Type: application/json' \
    --data "$payload" "$LEO_GUARD_ALERT_WEBHOOK" >/dev/null 2>&1 \
    || echo "告警外发失败: $LEO_GUARD_ALERT_WEBHOOK" >&2
}

# ---------------------------------------------------------------- 主流程
IFS=':' read -r -a guard_mounts <<< "$LEO_GUARD_MOUNTS"
for mount in "${guard_mounts[@]}"; do
  [[ -n "$mount" ]] && check_disk "$mount"
done
check_postgres
check_redis
check_http "$LEO_GUARD_HEALTH_URL" "health-api"
check_http "$LEO_GUARD_PUBLIC_HEALTH_URL" "health-public"
check_logs "$LEO_GUARD_LOG_FILE"

case "$WORST" in
  0) level="OK" ;;
  1) level="WARN" ;;
  *) level="CRIT" ;;
esac

if $OUTPUT_JSON; then
  printf '{"host":"%s","result":"%s","checks":[' \
    "$(json_escape "$LEO_GUARD_HOST_LABEL")" "$level"
  first=true
  for row in "${RESULTS[@]}"; do
    IFS='|' read -r rlevel rname rdetail <<< "$row"
    $first || printf ','
    first=false
    printf '{"level":"%s","check":"%s","detail":"%s"}' \
      "$(json_escape "$rlevel")" "$(json_escape "$rname")" "$(json_escape "$rdetail")"
  done
  printf ']}\n'
else
  log "RESULT=$level host=$LEO_GUARD_HOST_LABEL problems=${#PROBLEMS[@]}"
fi

if (( WORST > 0 )); then
  send_alert
fi

exit "$WORST"
