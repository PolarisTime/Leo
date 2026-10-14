#!/usr/bin/env bash
#
# V1__baseline.sql 基线冻结门禁（独立于 flyway:validate 的政策门禁）
#
# 为什么需要独立门禁：官方 flyway:validate 是“库中记录与本地脚本对齐”的收官校验，
# 不是“基线文件有没有被人改写”的政策闸门；当基线是 baselineOnMigrate 写入的
# BASELINE 记录（checksum 为空）时，validate 更无从比对。本仓“基线不可改写”属于
# 交付政策，因此用独立的 sha256 冻结清单把 V1 钉死：文件内容一旦变化即失败。
#
# 这不是 drift 检测（drift 属 Flyway Enterprise 功能），只检测“本地基线文件被篡改”。
# 冻结清单自身也在 Git 里，改动清单必须走代码评审。
#
# 用法：
#   bash scripts/deploy/assert-flyway-baseline-frozen.sh [leo 仓库根目录] [冻结清单文件]
#
# 需要重建基线时（仅限“尚未部署生产、按当前开发库重建基线”这一前提下）：
#   sha256sum src/main/resources/db/migration/V1__baseline.sql > scripts/deploy/flyway-baseline.sha256
# 任何共享环境或生产环境执行过基线之后，禁止重建基线，也禁止更新本清单，只能新增更高版本迁移。

set -euo pipefail

REPO_DIR="${1:-.}"
CHECKSUM_FILE="${2:-$REPO_DIR/scripts/deploy/flyway-baseline.sha256}"
BASELINE_REL="src/main/resources/db/migration/V1__baseline.sql"

fail() {
  printf 'FLYWAY_BASELINE_FREEZE_FAIL: %s\n' "$*" >&2
}

if [[ ! -f "$CHECKSUM_FILE" ]]; then
  fail "缺少基线冻结清单: $CHECKSUM_FILE"
  exit 2
fi

mapfile -t entries < <(grep -vE '^[[:space:]]*(#|$)' "$CHECKSUM_FILE")
if ((${#entries[@]} != 1)); then
  fail "基线冻结清单必须且只能有 1 条记录，实际 ${#entries[@]} 条: $CHECKSUM_FILE"
  exit 2
fi

read -r expected_hash frozen_path <<<"${entries[0]}"
frozen_path="${frozen_path# }"
frozen_path="${frozen_path#\*}"
if [[ ! "$expected_hash" =~ ^[0-9a-f]{64}$ ]]; then
  fail "基线冻结清单中的 sha256 格式非法: ${expected_hash}"
  exit 2
fi
if [[ "$frozen_path" != "$BASELINE_REL" ]]; then
  fail "基线冻结清单只允许冻结 $BASELINE_REL，实际记录的是 ${frozen_path}"
  exit 2
fi

baseline_file="$REPO_DIR/$BASELINE_REL"
if [[ ! -f "$baseline_file" ]]; then
  fail "基线文件不存在: $baseline_file"
  exit 2
fi

actual_hash="$(sha256sum "$baseline_file" | awk '{print $1}')"
if [[ "$actual_hash" != "$expected_hash" ]]; then
  fail "$BASELINE_REL 内容已变化（冻结 $expected_hash，实际 $actual_hash）。"
  printf '  V1 基线不可改写：结构变更必须新增更高版本的 V*.sql 迁移脚本，不得修改或重建已应用的基线。\n' >&2
  exit 1
fi

printf 'FLYWAY_BASELINE_FREEZE_OK: %s sha256=%s\n' "$BASELINE_REL" "$actual_hash"
