#!/usr/bin/env bash
#
# 守卫：流水线与部署脚本中禁止出现 Flyway 的 repair goal / 子命令。
#
# repair 会修改 flyway_schema_history（例如改写 failed 行、补记缺失版本、修补 checksum），
# 它是人工处置手段，一旦放进 CI 或部署流程，就会把“迁移对不上”这类问题静默抹平，
# 使门禁失去意义。本守卫让这条政策可被机器检查：入口出现即失败。
#
# 注意：这是政策守卫，不是 drift 检测（drift 属 Flyway Enterprise 功能）。
#
# 用法：
#   bash scripts/deploy/assert-no-migration-repair.sh [待扫描路径...]
#   默认扫描 .github/workflows 与 scripts。

set -euo pipefail

GREP_PATTERN='flyway[[:space:]:_-]repair'
SCAN_PATHS=("$@")
if ((${#SCAN_PATHS[@]} == 0)); then
  SCAN_PATHS=(.github/workflows scripts)
fi

hits=""
scanned=()
for path in "${SCAN_PATHS[@]}"; do
  if [[ ! -e "$path" ]]; then
    continue
  fi
  scanned+=("$path")
  while IFS= read -r line; do
    if [[ -n "$line" ]]; then
      hits+="$line"$'\n'
    fi
  done < <(grep -rInE --include='*.sh' --include='*.bash' --include='*.yml' --include='*.yaml' \
    "$GREP_PATTERN" "$path" || true)
done

if ((${#scanned[@]} == 0)); then
  printf 'FLYWAY_REPAIR_GUARD_FAIL: 待扫描路径一个都不存在: %s\n' "${SCAN_PATHS[*]}" >&2
  exit 2
fi

if [[ -n "$hits" ]]; then
  printf 'FLYWAY_REPAIR_GUARD_FAIL: 检测到 Flyway repair 目标，严禁进入流水线:\n' >&2
  printf '%s' "$hits" >&2
  exit 1
fi

printf 'FLYWAY_REPAIR_GUARD_OK: 已扫描 %s，未发现 repair 目标\n' "${scanned[*]}"
