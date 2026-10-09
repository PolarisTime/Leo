#!/usr/bin/env bash
# 安装 Leo ERP 生产健康守卫（需要 root）
#
#   sudo bash install-health-guard.sh [--shared-dir /instance/steelx/shared] [--dry-run]
#
# 动作：
#   1. 将 health-guard.sh 安装到 <shared-dir>/health-guard.sh（0755）
#   2. 安装 systemd unit/timer 到 /etc/systemd/system/
#   3. systemctl daemon-reload && enable --now leo-health-guard.timer
#   4. 立即执行一次自检并打印结果
# 幂等；--dry-run 只打印将执行的动作。
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
SHARED_DIR="/instance/steelx/shared"
DRY_RUN=false

usage() {
  sed -n '2,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --shared-dir) SHARED_DIR="${2:?--shared-dir 需要路径}"; shift 2 ;;
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage >&2; exit 1 ;;
  esac
done

if (( EUID != 0 )) && ! $DRY_RUN; then
  echo "需要 root 权限：sudo bash $(basename -- "$0")" >&2
  exit 1
fi

for cmd in install systemctl; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "缺少命令: $cmd" >&2; exit 1; }
done
[[ -f "$SCRIPT_DIR/health-guard.sh" ]] || { echo "缺少 health-guard.sh（应与本脚本同目录）" >&2; exit 1; }
[[ -d "$SHARED_DIR" ]] || { echo "共享目录不存在: $SHARED_DIR" >&2; exit 1; }

run() {
  if $DRY_RUN; then
    printf '[dry-run] %s\n' "$*"
  else
    printf '+ %s\n' "$*"
    "$@"
  fi
}

# 安装守卫脚本
run install -m 0755 -o "$(stat -c %U "$SHARED_DIR")" -g "$(stat -c %G "$SHARED_DIR")" \
  "$SCRIPT_DIR/health-guard.sh" "$SHARED_DIR/health-guard.sh"

# 安装 unit（service 内的 ExecStart 硬编码为 $SHARED_DIR/health-guard.sh）
tmp_service="$(mktemp)" || exit 1
trap 'rm -f -- "$tmp_service"' EXIT
sed "s#^ExecStart=.*#ExecStart=$SHARED_DIR/health-guard.sh#" \
  "$SCRIPT_DIR/systemd/leo-health-guard.service" > "$tmp_service"
run install -m 0644 "$tmp_service" /etc/systemd/system/leo-health-guard.service
run install -m 0644 "$SCRIPT_DIR/systemd/leo-health-guard.timer" /etc/systemd/system/leo-health-guard.timer

run systemctl daemon-reload
run systemctl enable --now leo-health-guard.timer

if $DRY_RUN; then
  echo "dry-run 结束，未做任何变更。"
  exit 0
fi

echo
echo "=== 立即自检 ==="
LEO_GUARD_ENV_FILE="${LEO_GUARD_ENV_FILE:-$SHARED_DIR/steelx.env}" "$SHARED_DIR/health-guard.sh" || true
echo
echo "=== 定时器状态 ==="
systemctl list-timers leo-health-guard.timer --no-pager || true
echo
echo "安装完成。后续排查:"
echo "  systemctl status leo-health-guard.service"
echo "  journalctl -u leo-health-guard -n 50 --no-pager"
