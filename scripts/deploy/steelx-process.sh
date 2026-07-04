#!/usr/bin/env bash

set -euo pipefail

ACTION="${1:-}"
STEELX_ROOT="${STEELX_ROOT:-/instance/steelx}"
ENV_FILE="$STEELX_ROOT/shared/steelx.env"
BACKEND_PID_FILE="$STEELX_ROOT/run/backend.pid"
BACKEND_LOG_FILE="$STEELX_ROOT/logs/backend.log"

usage() {
  echo "用法: STEELX_ROOT=/instance/steelx bash steelx-process.sh {start|stop|status}" >&2
}

if [[ -z "$ACTION" ]]; then
  usage
  exit 1
fi

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

SERVER_PORT="${SERVER_PORT:-57217}"
export SPRING_AI_MCP_SERVER_ENABLED="${SPRING_AI_MCP_SERVER_ENABLED:-false}"
JAR_FILE="$STEELX_ROOT/current/backend/leo.jar"

find_pid_by_port() {
  ss -ltnpH "( sport = :$SERVER_PORT )" 2>/dev/null | sed -n 's/.*pid=\([0-9]\+\).*/\1/p' | head -1
}

start_backend() {
  mkdir -p "$STEELX_ROOT/run" "$STEELX_ROOT/logs"
  if [[ -f "$BACKEND_PID_FILE" ]] && kill -0 "$(cat "$BACKEND_PID_FILE")" 2>/dev/null; then
    echo "steelx 后端已运行 PID=$(cat "$BACKEND_PID_FILE")"
    return 0
  fi
  local port_pid
  port_pid="$(find_pid_by_port)"
  if [[ -n "$port_pid" ]]; then
    echo "后端端口 $SERVER_PORT 已被占用 PID=$port_pid" >&2
    exit 1
  fi
  if [[ ! -f "$JAR_FILE" ]]; then
    echo "JAR 不存在: $JAR_FILE" >&2
    exit 1
  fi
  {
    printf '\n===== %s starting %s =====\n' "$(date -Is)" "$JAR_FILE"
    env -u RUNNER_TRACKING_ID setsid java \
      -Xms512m -Xmx2g \
      -XX:+UseG1GC \
      -XX:MaxGCPauseMillis=200 \
      -XX:+HeapDumpOnOutOfMemoryError \
      -XX:HeapDumpPath="$STEELX_ROOT/shared/heapdump.hprof" \
      -Dserver.port="$SERVER_PORT" \
      -Dspring.profiles.active=prod \
      -jar "$JAR_FILE"
  } >> "$BACKEND_LOG_FILE" 2>&1 &
  echo "$!" > "$BACKEND_PID_FILE"
  echo "steelx 后端启动 PID=$(cat "$BACKEND_PID_FILE")"
}

stop_backend() {
  local pid=""
  if [[ -f "$BACKEND_PID_FILE" ]]; then
    pid="$(cat "$BACKEND_PID_FILE")"
  fi
  if [[ -z "$pid" || ! "$pid" =~ ^[0-9]+$ ]]; then
    pid="$(find_pid_by_port)"
  elif ! kill -0 "$pid" 2>/dev/null; then
    pid="$(find_pid_by_port)"
  fi
  if [[ -z "$pid" ]]; then
    echo "steelx 后端未运行"
    rm -f "$BACKEND_PID_FILE"
    return 0
  fi
  kill "$pid" 2>/dev/null || true
  for _ in $(seq 1 20); do
    if ! kill -0 "$pid" 2>/dev/null; then
      rm -f "$BACKEND_PID_FILE"
      echo "steelx 后端已停止 PID=$pid"
      return 0
    fi
    sleep 1
  done
  kill -9 "$pid" 2>/dev/null || true
  rm -f "$BACKEND_PID_FILE"
  echo "steelx 后端已强制停止 PID=$pid"
}

status_backend() {
  local backend_pid
  backend_pid="$(find_pid_by_port)"
  if [[ -n "$backend_pid" ]]; then
    echo "steelx 后端运行中 :$SERVER_PORT PID=$backend_pid"
  else
    echo "steelx 后端未运行 :$SERVER_PORT"
  fi
}

case "$ACTION" in
  start) start_backend ;;
  stop) stop_backend ;;
  status) status_backend ;;
  *)
    usage
    exit 1
    ;;
esac
