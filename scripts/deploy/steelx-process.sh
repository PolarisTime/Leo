#!/usr/bin/env bash

set -euo pipefail

ACTION="${1:-}"
STEELX_ROOT="${STEELX_ROOT:-/instance/steelx}"
STEELX_BACKEND_ROOT="${STEELX_BACKEND_ROOT:-$STEELX_ROOT/backend}"
ENV_FILE="$STEELX_ROOT/shared/steelx.env"
BACKEND_PID_FILE="$STEELX_ROOT/run/backend.pid"
BACKEND_LOG_FILE="$STEELX_ROOT/logs/backend.log"

usage() {
  echo "用法: STEELX_ROOT=/instance/steelx bash steelx-process.sh {run|start|stop|status}" >&2
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
if [[ -n "${STEELX_BACKEND_JAR:-}" ]]; then
  JAR_FILE="$STEELX_BACKEND_JAR"
elif [[ -f "$STEELX_BACKEND_ROOT/current/leo.jar" ]]; then
  JAR_FILE="$STEELX_BACKEND_ROOT/current/leo.jar"
elif [[ -f "$STEELX_BACKEND_ROOT/current/backend/leo.jar" ]]; then
  JAR_FILE="$STEELX_BACKEND_ROOT/current/backend/leo.jar"
else
  JAR_FILE="$STEELX_ROOT/current/backend/leo.jar"
fi
BACKEND_DIR="$(dirname "$JAR_FILE")"
LIB_DIR="${STEELX_BACKEND_LIB:-$BACKEND_DIR/lib}"
DEPENDENCY_MARKER="$BACKEND_DIR/dependency-bundle.id"
JAVA_MAIN_CLASS="${STEELX_BACKEND_MAIN_CLASS:-com.leo.erp.LeoApplication}"
JAVA_COMMAND=()
JAVA_LAUNCH_MODE=""

find_pid_by_port() {
  ss -ltnpH "( sport = :$SERVER_PORT )" 2>/dev/null | sed -n 's/.*pid=\([0-9]\+\).*/\1/p' | head -1
}

build_java_command() {
  if [[ ! -f "$JAR_FILE" ]]; then
    echo "JAR 不存在: $JAR_FILE" >&2
    return 1
  fi

  JAVA_COMMAND=(
    java
    -Xms512m -Xmx2g
    -XX:+UseG1GC
    -XX:MaxGCPauseMillis=200
    -XX:+HeapDumpOnOutOfMemoryError
    -XX:HeapDumpPath="$STEELX_ROOT/shared/heapdump.hprof"
    -Dserver.port="$SERVER_PORT"
    -Dspring.profiles.active=prod
  )

  if [[ -f "$DEPENDENCY_MARKER" ]]; then
    if [[ ! -d "$LIB_DIR" ]]; then
      echo "外置依赖目录不存在: $LIB_DIR" >&2
      return 1
    fi
    JAVA_COMMAND+=(-cp "$JAR_FILE:$LIB_DIR/*" "$JAVA_MAIN_CLASS")
    JAVA_LAUNCH_MODE="external-classpath"
  else
    JAVA_COMMAND+=(-jar "$JAR_FILE")
    JAVA_LAUNCH_MODE="spring-boot-fat-jar"
  fi
}

run_backend() {
  build_java_command
  exec env -u RUNNER_TRACKING_ID "${JAVA_COMMAND[@]}"
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
  build_java_command
  {
    printf '\n===== %s starting %s mode=%s =====\n' "$(date -Is)" "$JAR_FILE" "$JAVA_LAUNCH_MODE"
    env -u RUNNER_TRACKING_ID setsid "${JAVA_COMMAND[@]}"
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
  run) run_backend ;;
  start) start_backend ;;
  stop) stop_backend ;;
  status) status_backend ;;
  *)
    usage
    exit 1
    ;;
esac
