#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEFAULT_JAR="$LEO_DIR/target/leo-1.0.0.jar"

CHECK_ONLY=false
JAR_FILE="$DEFAULT_JAR"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check) CHECK_ONLY=true; shift ;;
    --jar) JAR_FILE="$2"; shift 2 ;;
    --help|-h)
      echo "用法: start-prod.sh [--check] [--jar <path>]"
      exit 0
      ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

# shellcheck disable=SC1090
source "$LEO_DIR/scripts/env/prod.sh"

export PATH="$JAVA_HOME/bin:$PATH"

echo "=== 环境检查 ==="
bash "$LEO_DIR/scripts/env/check.sh" prod
if $CHECK_ONLY; then
  exit 0
fi

if ss -ltnpH "( sport = :$SERVER_PORT )" 2>/dev/null | grep -q .; then
  echo "端口 $SERVER_PORT 已被占用" >&2
  exit 1
fi

if [[ ! -f "$JAR_FILE" ]]; then
  echo "JAR 不存在: $JAR_FILE"
  echo "正在构建..."
  cd "$LEO_DIR"
  mvn -q -Dmaven.test.skip=true package
  JAR_FILE=$(ls "$LEO_DIR"/target/leo-*.jar 2>/dev/null | grep -v sources | head -1)
  if [[ -z "$JAR_FILE" ]]; then
    echo "构建失败：未找到 JAR 文件" >&2
    exit 1
  fi
  echo "构建完成: $JAR_FILE"
fi

echo ""
echo "=== 启动 Leo ERP 后端 (prod) ==="
echo "JAR:  $JAR_FILE"
echo "端口: $SERVER_PORT"
echo "DB:   $SPRING_DATASOURCE_USERNAME@$SPRING_DATASOURCE_HOST:$SPRING_DATASOURCE_PORT/$SPRING_DATASOURCE_DB"
echo ""

exec java \
  -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/tmp/leo-heapdump.hprof \
  -Dserver.port="$SERVER_PORT" \
  -Dspring.profiles.active=prod \
  -jar "$JAR_FILE"
