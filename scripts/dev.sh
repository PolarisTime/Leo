#!/usr/bin/env bash
# Leo ERP 本地开发管理脚本
# 用法: dev.sh {start|stop|restart|status|logs} [--rebuild]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEO_DIR="$SCRIPT_DIR/.."
ROOT_DIR="$LEO_DIR/.."
ARIES_DIR="$ROOT_DIR/aries"
BACKEND_PORT=11211
FRONTEND_PORT=3100

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
ok()   { echo -e "${GREEN}[✓]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
fail() { echo -e "${RED}[✗]${NC} $*"; }

find_pid() {
    local port="$1"
    ss -ltnpH "( sport = :$port )" 2>/dev/null | sed -n 's/.*pid=\([0-9]\+\).*/\1/p' | head -1
}

backend_status() { [[ -n "$(find_pid "$BACKEND_PORT")" ]] && echo "UP" || echo "DOWN"; }
frontend_status() { [[ -n "$(find_pid "$FRONTEND_PORT")" ]] && echo "UP" || echo "DOWN"; }

backend_log() { tail -f "$LEO_DIR"/target/*.log 2>/dev/null || warn "无日志文件"; }

# ---- status ----
do_status() {
    local be=$(backend_status); local fe=$(frontend_status)
    echo "后端 :$BACKEND_PORT  ${be}"
    echo "前端 :$FRONTEND_PORT  ${fe}"
    local be_pid=$(find_pid "$BACKEND_PORT"); local fe_pid=$(find_pid "$FRONTEND_PORT")
    [[ -n "$be_pid" ]] && echo "  后端 PID: $be_pid"
    [[ -n "$fe_pid" ]] && echo "  前端 PID: $fe_pid"
}

# ---- stop ----
do_stop() {
    local stopped=0
    for name in "aries(前端:$FRONTEND_PORT)" "leo(后端:$BACKEND_PORT)"; do
        local label="${name%%(*}"; local port="${name#*:}"; port="${port%)*}"
        local pid=$(find_pid "$port")
        if [[ -n "$pid" ]]; then
            kill "$pid" 2>/dev/null && sleep 1
            if kill -0 "$pid" 2>/dev/null; then kill -9 "$pid" 2>/dev/null; fi
            ok "已停止 $label (PID=$pid)"
            stopped=$((stopped + 1))
        else
            warn "$label 未运行"
        fi
    done
    [[ $stopped -gt 0 ]] && ok "全部已停止" || warn "没有需要停止的服务"
}

# ---- backend ----
start_backend() {
    local rebuild=false; [[ "${1:-}" == "--rebuild" ]] && rebuild=true
    if [[ -n "$(find_pid "$BACKEND_PORT")" ]]; then
        warn "后端已在运行"; return 0
    fi
    source "$LEO_DIR/scripts/env-local.sh"
    cd "$LEO_DIR"
    if $rebuild; then
        echo "[backend] 全量重编译..."
        mvn -q -Dmaven.test.skip=true clean compile
    else
        echo "[backend] 增量编译..."
        mvn -q -Dmaven.test.skip=true compile
    fi
    echo "[backend] 启动 (DevTools 热重启)..."
    nohup mvn -q -Dmaven.test.skip=true spring-boot:run \
        -Dspring-boot.run.jvmArguments="-XX:TieredStopAtLevel=1 -XX:+UseG1GC" \
        > /tmp/leo-backend.log 2>&1 &
    # 等待就绪
    for i in $(seq 1 60); do
        sleep 2
        if [[ -n "$(find_pid "$BACKEND_PORT")" ]]; then
            ok "后端就绪 :$BACKEND_PORT (${i}2s)"
            return 0
        fi
    done
    fail "后端启动超时"; return 1
}

# ---- frontend ----
start_frontend() {
    if [[ -n "$(find_pid "$FRONTEND_PORT")" ]]; then
        warn "前端已在运行"; return 0
    fi
    cd "$ARIES_DIR"
    echo "[frontend] 启动 (Vite HMR)..."
    nohup pnpm dev > /tmp/aries-frontend.log 2>&1 &
    for i in $(seq 1 30); do
        sleep 2
        if [[ -n "$(find_pid "$FRONTEND_PORT")" ]]; then
            ok "前端就绪 :$FRONTEND_PORT (${i}2s)"
            return 0
        fi
    done
    fail "前端启动超时"; return 1
}

# ---- start ----
do_start() {
    local rebuild=false; [[ "${1:-}" == "--rebuild" ]] && rebuild=true
    start_backend "$1"
    start_frontend
    echo ""
    do_status
}

# ---- restart ----
do_restart() {
    do_stop; sleep 2; do_start "$@"
}

# ---- logs ----
do_logs() {
    local target="${1:-all}"
    case "$target" in
        be|backend) backend_log ;;
        fe|frontend) tail -f /tmp/aries-frontend.log 2>/dev/null || warn "无前端日志";;
        *) echo "=== 后端 === (Ctrl-C 退出)"; backend_log ;;
    esac
}

# ---- 入口 ----
case "${1:-}" in
    start)   do_start "${2:-}" ;;
    stop)    do_stop ;;
    restart) do_restart "${2:-}" ;;
    status)  do_status ;;
    logs)    do_logs "${2:-}" ;;
    *)       echo "用法: dev.sh {start|stop|restart|status|logs} [--rebuild]"
             echo "  start            增量编译 + 启动前后端"
             echo "  start --rebuild   全量重编译 + 启动"
             echo "  stop             停止前后端"
             echo "  restart          重启前后端"
             echo "  status           查看服务状态"
             echo "  logs [be|fe]     查看日志（默认后端）"
             exit 1 ;;
esac
