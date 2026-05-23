#!/usr/bin/env bash
# Leo ERP 生产启动脚本
# 用法: start-prod.sh [--check] [--jar <path>]
#   --check   仅执行环境检查，不启动
#   --jar      指定 JAR 文件路径（默认 target/leo-0.0.1-SNAPSHOT.jar）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEO_DIR="$SCRIPT_DIR/.."
DEFAULT_JAR="$LEO_DIR/target/leo-0.0.1-SNAPSHOT.jar"

CHECK_ONLY=false
JAR_FILE="$DEFAULT_JAR"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --check) CHECK_ONLY=true; shift ;;
        --jar) JAR_FILE="$2"; shift 2 ;;
        --help|-h) echo "用法: $0 [--check] [--jar <path>]"; exit 0 ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

# ---- 加载配置 ----
if [[ -f "$SCRIPT_DIR/env-local.sh" ]]; then
    source "$SCRIPT_DIR/env-local.sh"
fi

# ---- 环境检查 ----
echo "=== 环境检查 ==="
bash "$SCRIPT_DIR/env-check.sh"
if $CHECK_ONLY; then exit 0; fi

# ---- 激活 prod profile ----
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

# ---- 查找/构建 JAR ----
if [[ ! -f "$JAR_FILE" ]]; then
    echo "JAR 不存在: $JAR_FILE"
    echo "正在构建..."
    cd "$LEO_DIR"
    mvn -q -Dmaven.test.skip=true package -Pprod
    # 查找构建产物
    JAR_FILE=$(ls "$LEO_DIR"/target/leo-*.jar 2>/dev/null | grep -v sources | head -1)
    if [[ -z "$JAR_FILE" ]]; then
        echo "构建失败：未找到 JAR 文件" >&2
        exit 1
    fi
    echo "构建完成: $JAR_FILE"
fi

# ---- 端口检查 ----
SERVER_PORT="${SERVER_PORT:-11211}"
if ss -ltnpH "( sport = :$SERVER_PORT )" 2>/dev/null | grep -q .; then
    echo "端口 $SERVER_PORT 已被占用" >&2
    exit 1
fi

# ---- 启动 ----
echo ""
echo "=== 启动 Leo ERP (prod) ==="
echo "JAR:  $JAR_FILE"
echo "端口: $SERVER_PORT"
echo ""

exec java \
    -Xms512m -Xmx2g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/leo-heapdump.hprof \
    -Dserver.port="$SERVER_PORT" \
    -Dspring.profiles.active="$SPRING_PROFILES_ACTIVE" \
    -jar "$JAR_FILE"
