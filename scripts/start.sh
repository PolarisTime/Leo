#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPTS_DIR="$ROOT_DIR/scripts"
LEO_DIR="$ROOT_DIR/leo"

BACKEND_PORT=11211
FRONTEND_PORT=3100
BACKEND_READY_TIMEOUT=120
FRONTEND_READY_TIMEOUT=60
LOG_DIR="$ROOT_DIR/.local/logs"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"

mkdir -p "$LOG_DIR"

# ---- 颜色 ----
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# ---- 清理 ----
BACKEND_PID=""
FRONTEND_PID=""
cleanup() {
  trap - EXIT INT TERM
  if [[ -n "${BACKEND_PID:-}" ]] && kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
    kill "$BACKEND_PID" >/dev/null 2>&1 || true
  fi
  if [[ -n "${FRONTEND_PID:-}" ]] && kill -0 "$FRONTEND_PID" >/dev/null 2>&1; then
    kill "$FRONTEND_PID" >/dev/null 2>&1 || true
  fi
  wait "${BACKEND_PID:-}" 2>/dev/null || true
  wait "${FRONTEND_PID:-}" 2>/dev/null || true
}
trap cleanup INT TERM

# ---- 状态变量 ----
backend_ready=false
frontend_ready=false

# ---- 加载环境 ----
echo "=============================="
echo "  Gemini ERP 本地启动"
echo "=============================="
echo ""

# shellcheck disable=SC1090
source "$LEO_DIR/scripts/env-local.sh"

# ---- 启动后端 ----
echo "  后端  启动中 ..."
bash "$SCRIPTS_DIR/start-backend.sh" > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!

# ---- 启动前端 ----
echo "  前端  启动中 ..."
bash "$SCRIPTS_DIR/start-frontend.sh" > "$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!

echo ""

# ---- 等待后端就绪 ----
echo -n "  等待后端 :$BACKEND_PORT "
elapsed=0
while [[ $elapsed -lt $BACKEND_READY_TIMEOUT ]]; do
  if grep -qF '[ready] backend' "$BACKEND_LOG" 2>/dev/null; then
    backend_ready=true
    echo ""
    echo -e "  ${GREEN}后端${NC}  就绪 — http://localhost:${BACKEND_PORT}"
    echo "  后端日志: $BACKEND_LOG"
    break
  fi
  if [[ -n "${BACKEND_PID:-}" ]] && ! kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
    echo ""
    echo -e "  ${RED}后端${NC}  进程异常退出"
    echo "  后端日志: $BACKEND_LOG (最后 20 行)"
    echo "  --------------------------------------------------"
    tail -20 "$BACKEND_LOG" 2>/dev/null || true
    echo "  --------------------------------------------------"
    break
  fi
  echo -n "."
  sleep 3
  elapsed=$((elapsed + 3))
done

if [[ "$backend_ready" != "true" ]] && [[ $elapsed -ge $BACKEND_READY_TIMEOUT ]]; then
  echo ""
  echo -e "  ${RED}后端${NC}  启动超时 (${BACKEND_READY_TIMEOUT}s)"
  echo "  后端日志: $BACKEND_LOG (最后 20 行)"
  echo "  --------------------------------------------------"
  tail -20 "$BACKEND_LOG" 2>/dev/null || true
  echo "  --------------------------------------------------"
fi

# ---- 等待前端就绪 ----
echo -n "  等待前端 :$FRONTEND_PORT "
elapsed=0
while [[ $elapsed -lt $FRONTEND_READY_TIMEOUT ]]; do
  if grep -qF '[ready] frontend' "$FRONTEND_LOG" 2>/dev/null; then
    frontend_ready=true
    echo ""
    echo -e "  ${GREEN}前端${NC}  就绪 — http://localhost:${FRONTEND_PORT}"
    echo "  前端日志: $FRONTEND_LOG"
    break
  fi
  if [[ -n "${FRONTEND_PID:-}" ]] && ! kill -0 "$FRONTEND_PID" >/dev/null 2>&1; then
    echo ""
    echo -e "  ${RED}前端${NC}  进程异常退出"
    echo "  前端日志: $FRONTEND_LOG (最后 20 行)"
    echo "  --------------------------------------------------"
    tail -20 "$FRONTEND_LOG" 2>/dev/null || true
    echo "  --------------------------------------------------"
    break
  fi
  echo -n "."
  sleep 2
  elapsed=$((elapsed + 2))
done

if [[ "$frontend_ready" != "true" ]] && [[ $elapsed -ge $FRONTEND_READY_TIMEOUT ]]; then
  echo ""
  echo -e "  ${RED}前端${NC}  启动超时 (${FRONTEND_READY_TIMEOUT}s)"
  echo "  前端日志: $FRONTEND_LOG (最后 20 行)"
  echo "  --------------------------------------------------"
  tail -20 "$FRONTEND_LOG" 2>/dev/null || true
  echo "  --------------------------------------------------"
fi

# ---- 结果汇总 ----
echo ""
echo "=============================="
echo "  启动结果"
echo "=============================="

if [[ "$backend_ready" == "true" ]]; then
  echo -e "  ${GREEN}后端${NC}  http://localhost:${BACKEND_PORT}  ✓"
else
  echo -e "  ${RED}后端${NC}  未就绪  ✗"
fi
if [[ "$frontend_ready" == "true" ]]; then
  echo -e "  ${GREEN}前端${NC}  http://localhost:${FRONTEND_PORT}  ✓"
else
  echo -e "  ${RED}前端${NC}  未就绪  ✗"
fi

echo ""
disown "$BACKEND_PID" 2>/dev/null || true
disown "$FRONTEND_PID" 2>/dev/null || true

if [[ "$backend_ready" == "true" ]] && [[ "$frontend_ready" == "true" ]]; then
  echo -e "  ${GREEN}全部就绪${NC}"
  echo ""
  echo "  打开浏览器访问: http://localhost:${FRONTEND_PORT}"
  echo "  首次启动请进入 /setup 完成初始化"
  echo ""
  echo "  停止: bash leo/scripts/dev.sh stop"
  echo "  日志: $LOG_DIR"
else
  echo -e "  ${YELLOW}存在未就绪服务${NC}，请检查上方日志"
  exit 1
fi
