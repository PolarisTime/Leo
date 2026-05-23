#!/usr/bin/env bash
# Leo ERP 生产环境检查
# 退出码 0 = 全部通过，非 0 = 有错误
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; NC='\033[0m'
PASS=0; WARN=0; FAIL=0

check() { echo -n "  $1 ... "; }
ok()   { echo -e "${GREEN}✓${NC} $2"; PASS=$((PASS+1)); }
warn() { echo -e "${YELLOW}⚠${NC} $2"; WARN=$((WARN+1)); }
fail() { echo -e "${RED}✗${NC} $2"; FAIL=$((FAIL+1)); }

echo "=== Leo ERP 环境检查 ==="
echo ""

# ---- Java ----
check "Java 版本"
JAVA_VER=$(java -version 2>&1 | head -1 | grep -oP '\d+' | head -1 || echo "0")
if [[ "$JAVA_VER" -ge 21 ]]; then
    ok "Java $JAVA_VER"
else
    fail "需要 Java 21+，当前为 $JAVA_VER"
fi

# ---- JVM 内存 ----
check "可用内存"
MEM_MB=$(free -m | awk '/^Mem:/{print $7}')
if [[ "$MEM_MB" -ge 512 ]]; then
    ok "${MEM_MB}MB 可用"
else
    warn "仅 ${MEM_MB}MB，建议 >= 512MB"
fi

# ---- 磁盘 ----
check "磁盘空间"
DISK_GB=$(df -BG . | tail -1 | awk '{print $4}' | tr -d 'G')
if [[ "$DISK_GB" -ge 10 ]]; then
    ok "${DISK_GB}GB 可用"
else
    fail "仅 ${DISK_GB}GB，需要 >= 10GB"
fi

# ---- 端口 ----
check "端口 $SERVER_PORT"
if ! ss -ltnpH "( sport = :${SERVER_PORT:-11211} )" 2>/dev/null | grep -q .; then
    ok "未占用"
else
    warn "已被占用，启动将失败"
fi

# ---- PostgreSQL ----
check "PostgreSQL 连接"
PG_HOST="${SPRING_DATASOURCE_HOST:-localhost}"
PG_PORT="${SPRING_DATASOURCE_PORT:-5432}"
PG_DB="${SPRING_DATASOURCE_DB:-leo}"
PG_USER="${SPRING_DATASOURCE_USERNAME:-leo}"
PG_PASS="${SPRING_DATASOURCE_PASSWORD:-}"

if timeout 5 bash -c "echo >/dev/tcp/$PG_HOST/$PG_PORT" 2>/dev/null; then
    ok "$PG_HOST:$PG_PORT 可达"
else
    fail "$PG_HOST:$PG_PORT 不可达"
fi

# ---- Redis ----
check "Redis 连接"
REDIS_HOST="${SPRING_DATA_REDIS_HOST:-localhost}"
REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6379}"
if timeout 3 bash -c "echo >/dev/tcp/$REDIS_HOST/$REDIS_PORT" 2>/dev/null; then
    ok "$REDIS_HOST:$REDIS_PORT 可达"
else
    fail "$REDIS_HOST:$REDIS_PORT 不可达"
fi

# ---- 安全密钥 ----
check "JWT 密钥"
if [[ -n "${LEO_JWT_SECRET:-}" ]]; then
    if [[ ${#LEO_JWT_SECRET} -ge 32 ]]; then
        ok "已配置 (${#LEO_JWT_SECRET}字符)"
    else
        warn "长度 ${#LEO_JWT_SECRET}，建议 >= 32"
    fi
else
    fail "LEO_JWT_SECRET 未设置"
fi

check "TOTP 密钥"
if [[ -n "${TOTP_ENCRYPTION_KEY:-}" ]]; then
    if [[ ${#TOTP_ENCRYPTION_KEY} -ge 16 ]]; then
        ok "已配置 (${#TOTP_ENCRYPTION_KEY}字符)"
    else
        warn "长度 ${#TOTP_ENCRYPTION_KEY}，建议 >= 16"
    fi
else
    fail "TOTP_ENCRYPTION_KEY 未设置"
fi

# ---- Profile ----
check "Spring Profile"
if [[ "${SPRING_PROFILES_ACTIVE:-}" == *"prod"* ]]; then
    ok "prod"
else
    warn "未激活 prod: SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-(未设置)}"
fi

# ---- 汇总 ----
echo ""
echo "=== 结果: ${GREEN}${PASS}通过${NC} / ${YELLOW}${WARN}警告${NC} / ${RED}${FAIL}失败${NC} ==="
if [[ $FAIL -gt 0 ]]; then
    echo "请修复以上失败项后重试"
    exit 1
fi
echo "环境检查通过，可以启动"
