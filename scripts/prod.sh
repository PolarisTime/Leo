#!/usr/bin/env bash
# Leo ERP 生产测试环境管理脚本
# 用法: prod.sh {start|stop|restart|status|logs|check}
set -euo pipefail

RUNTIME_ENV=prod
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
WORKSPACE_DIR="$(cd "$LEO_DIR/.." && pwd)"
ARIES_DIR="$WORKSPACE_DIR/aries"

# shellcheck disable=SC1090
source "$SCRIPT_DIR/env/prod.sh"

BACKEND_PORT="${SERVER_PORT:-11211}"
FRONTEND_PORT="${FRONTEND_PORT:-3100}"
LOG_DIR="$WORKSPACE_DIR/.local/logs/$RUNTIME_ENV"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
BACKEND_PID_FILE="$LOG_DIR/backend.pid"
FRONTEND_PID_FILE="$LOG_DIR/frontend.pid"

mkdir -p "$LOG_DIR"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}[OK]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; }

find_pid() {
  local port="$1"
  ss -ltnpH "( sport = :$port )" 2>/dev/null | sed -n 's/.*pid=\([0-9]\+\).*/\1/p' | head -1
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local log_file="$3"
  local timeout="$4"

  for i in $(seq 1 "$timeout"); do
    sleep 1
    if [[ -f "$log_file" ]] && grep -qiE "BUILD FAILURE|APPLICATION FAILED TO START|Application run failed|Process terminated with exit code|error when starting dev server|failed to load config" "$log_file"; then
      fail "$name 启动失败，日志: $log_file"
      tail -40 "$log_file" 2>/dev/null || true
      return 1
    fi
    if curl -fsS --max-time 2 "$url" > /dev/null 2>&1; then
      ok "$name 就绪 $url"
      return 0
    fi
  done

  fail "$name 启动超时，日志: $log_file"
  tail -40 "$log_file" 2>/dev/null || true
  return 1
}

status_one() {
  local name="$1"
  local port="$2"
  local pid
  pid="$(find_pid "$port")"
  if [[ -n "$pid" ]]; then
    echo "$name :$port  PORT_BUSY  PID=$pid"
  else
    echo "$name :$port  DOWN"
  fi
}

do_status() {
  status_one "后端端口(prod)" "$BACKEND_PORT"
  status_one "前端端口(prod)" "$FRONTEND_PORT"
}

read_managed_pid() {
  local pid_file="$1"
  local pid
  [[ -f "$pid_file" ]] || return 1
  pid="$(<"$pid_file")"
  [[ "$pid" =~ ^[0-9]+$ ]] || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  echo "$pid"
}

stop_process() {
  local name="$1"
  local port="$2"
  local pid_file="$3"
  local pid
  pid="$(read_managed_pid "$pid_file" || true)"
  if [[ -z "$pid" ]]; then
    pid="$(find_pid "$port")"
  fi
  if [[ -z "$pid" ]]; then
    rm -f "$pid_file"
    warn "$name 未运行"
    return 0
  fi

  local pgid
  pgid="$(ps -o pgid= -p "$pid" 2>/dev/null | tr -d ' ')"
  if [[ "$pgid" =~ ^[0-9]+$ ]]; then
    kill -- "-$pgid" 2>/dev/null || true
    for _ in $(seq 1 10); do
      if ! kill -0 -- "-$pgid" 2>/dev/null; then
        rm -f "$pid_file"
        ok "已停止 $name (PGID=$pgid)"
        return 0
      fi
      sleep 1
    done
    kill -9 -- "-$pgid" 2>/dev/null || true
  else
    kill "$pid" 2>/dev/null || true
  fi
  rm -f "$pid_file"
  ok "已强制停止 $name (PID=$pid)"
}

do_stop() {
  stop_process "前端(prod)" "$FRONTEND_PORT" "$FRONTEND_PID_FILE"
  stop_process "后端(prod)" "$BACKEND_PORT" "$BACKEND_PID_FILE"
}

do_check() {
  bash "$SCRIPT_DIR/env/check.sh" prod
}

start_backend() {
  local pid
  pid="$(find_pid "$BACKEND_PORT")"
  if [[ -n "$pid" ]]; then
    fail "后端端口 $BACKEND_PORT 已被占用 (PID=$pid)，请先执行 stop 后再启动 prod"
    return 1
  fi

  echo "[prod] 启动后端 ..."
  setsid bash "$SCRIPT_DIR/backend/start-prod.sh" > "$BACKEND_LOG" 2>&1 < /dev/null &
  echo "$!" > "$BACKEND_PID_FILE"
  if ! wait_for_http "后端(prod)" "http://127.0.0.1:$BACKEND_PORT/api/health" "$BACKEND_LOG" 180; then
    stop_process "后端(prod)" "$BACKEND_PORT" "$BACKEND_PID_FILE"
    return 1
  fi
}

start_frontend() {
  local pid
  pid="$(find_pid "$FRONTEND_PORT")"
  if [[ -n "$pid" ]]; then
    fail "前端端口 $FRONTEND_PORT 已被占用 (PID=$pid)，请先执行 stop 后再启动 prod"
    return 1
  fi

  echo "[prod] 启动前端生产预览 ..."
  setsid bash "$ARIES_DIR/scripts/frontend/start-prod.sh" > "$FRONTEND_LOG" 2>&1 < /dev/null &
  echo "$!" > "$FRONTEND_PID_FILE"
  if ! wait_for_http "前端(prod)" "http://127.0.0.1:$FRONTEND_PORT" "$FRONTEND_LOG" 120; then
    stop_process "前端(prod)" "$FRONTEND_PORT" "$FRONTEND_PID_FILE"
    return 1
  fi
}

do_start() {
  do_check
  start_backend
  start_frontend
  echo ""
  do_status
  echo ""
  ok "生产测试环境已启动: http://localhost:$FRONTEND_PORT"
  echo "日志目录: $LOG_DIR"
}

do_restart() {
  do_stop
  sleep 2
  do_start
}

do_logs() {
  local target="${1:-all}"
  case "$target" in
    be|backend) tail -f "$BACKEND_LOG" ;;
    fe|frontend) tail -f "$FRONTEND_LOG" ;;
    all) tail -f "$BACKEND_LOG" "$FRONTEND_LOG" ;;
    *) echo "用法: prod.sh logs [backend|frontend|all]" >&2; exit 1 ;;
  esac
}

case "${1:-}" in
  start) do_start ;;
  stop) do_stop ;;
  restart) do_restart ;;
  status) do_status ;;
  logs) do_logs "${2:-all}" ;;
  check) do_check ;;
  *)
    echo "用法: prod.sh {start|stop|restart|status|logs|check}"
    echo "  start    启动生产 profile 后端和前端生产预览"
    echo "  stop     停止生产测试前后端"
    echo "  restart  重启生产测试前后端"
    echo "  status   查看生产测试服务状态"
    echo "  logs     查看生产测试日志"
    echo "  check    检查生产环境"
    exit 1
    ;;
esac
