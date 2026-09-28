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
# Spring Boot 独立管理端口(management.server.port)也必须一并等空闲:
# 只检查业务端口时, 新实例会在管理端口上撞到尚未退出的旧实例, 直接
# "APPLICATION FAILED TO START - Port 57218 was already in use"。
# 生产 v11.19.0 部署即因此失败并回滚(业务端口已起来, 管理端口被占)。
MANAGEMENT_PORT="${STEELX_MANAGEMENT_PORT:-57218}"
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

# `ss` 位于 /usr/sbin, 非登录 shell(运维手工执行、CI 之外的终端)常常不在 PATH 里;
# 解析不到时旧实现会因 `set -e` 静默中断, 表现为"start 什么都没做"。
SS_BIN="${SS_BIN:-$(command -v ss 2>/dev/null || true)}"
if [[ -z "$SS_BIN" && -x /usr/sbin/ss ]]; then
  SS_BIN=/usr/sbin/ss
fi

# 查询监听某端口的进程号。无权限读取 pid 信息时返回空, 由调用方退化处理。
port_pid() {
  local port="$1"
  [[ -n "$SS_BIN" ]] || return 0
  "$SS_BIN" -ltnpH "( sport = :$port )" 2>/dev/null \
    | sed -n 's/.*pid=\([0-9]\+\).*/\1/p' | head -1 || true
}

find_pid_by_port() {
  local pid
  pid="$(port_pid "$SERVER_PORT")"
  if [[ -z "$pid" ]]; then
    # 无特权时 ss 不输出 pid=, 退化到按当前 release 的 jar 匹配命令行
    pid="$(pgrep -f -- "$JAR_FILE" 2>/dev/null | head -1 || true)"
  fi
  printf '%s' "$pid"
}

# 等业务端口与管理端口都释放(旧实例优雅停机期间会短暂占用)。
wait_ports_free() {
  local timeout="${1:-30}" waited=0 busy_server="" busy_management=""
  while (( waited < timeout )); do
    busy_server="$(port_pid "$SERVER_PORT")"
    busy_management="$(port_pid "$MANAGEMENT_PORT")"
    if [[ -z "$busy_server" && -z "$busy_management" ]]; then
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done
  echo "端口未在 ${timeout}s 内释放: ${SERVER_PORT}=${busy_server:-空闲} ${MANAGEMENT_PORT}=${busy_management:-空闲}" >&2
  return 1
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
  # 旧实例可能还在优雅停机(或 systemd 侧刚重启过), 等业务端口与管理端口都空闲再起,
  # 否则新实例会先绑上业务端口、再在管理端口上失败退出(生产实测的失败形态)。
  wait_ports_free 60 || exit 1
  build_java_command
  {
    printf '\n===== %s starting %s mode=%s =====\n' "$(date -Is)" "$JAR_FILE" "$JAVA_LAUNCH_MODE"
    env -u RUNNER_TRACKING_ID setsid "${JAVA_COMMAND[@]}"
  } >> "$BACKEND_LOG_FILE" 2>&1 &
  local launcher_pid=$!
  echo "$launcher_pid" > "$BACKEND_PID_FILE"
  # setsid 会 fork, $! 只是启动器; kill 启动器不会停掉 java(stop 因此失效)。
  # 这里回填真实 java 进程号, 让 stop/status 可靠。
  local java_pid="" _i
  for _i in $(seq 1 30); do
    java_pid="$(find_pid_by_port)"
    if [[ -n "$java_pid" ]]; then
      break
    fi
    sleep 1
  done
  if [[ -n "$java_pid" ]]; then
    echo "$java_pid" > "$BACKEND_PID_FILE"
    echo "steelx 后端启动 PID=$java_pid"
  else
    echo "steelx 后端启动(暂未解析到 java 进程, 已记录启动器 PID=$launcher_pid)"
  fi
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
  # 强杀后同样要等端口真正释放, 否则紧接着的 start 会撞上残留监听(含管理端口)。
  wait_ports_free 30 || true
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
