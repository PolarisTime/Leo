#!/usr/bin/env bash
# 架构冻结基线"只许收敛"守卫。
#
# 背景：`ModuleBoundaryVerifier` 默认只读，只有 `--write-baseline` 才会更新
# `config/archunit-boundary-store/`。但"显式 refreeze"仍可能把新增违规一次性塞进基线，
# 让后续 CI 永久通过。本脚本在 CI 中比较基线相对基准引用的行数变化：
# 新增条目必须不超过删除条目（即只允许净收敛）；确需净扩张时，必须显式设置
# ALLOW_ARCH_BASELINE_EXPANSION=true 并说明理由（只应由架构评审的提交使用）。
#
# 用法：
#   bash scripts/deploy/assert-arch-baseline-not-expanded.sh [base-ref]
# 默认 base-ref 依次尝试 origin/main、HEAD~1；都不可用时跳过（浅克隆场景）并打印跳过原因。
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd -P)"
STORE_PATH="config/archunit-boundary-store"

cd -- "$REPO_ROOT"

if ! command -v git >/dev/null 2>&1; then
    printf 'SKIP: git is unavailable, cannot verify arch baseline drift.\n'
    exit 0
fi

if ! git rev-parse --git-dir >/dev/null 2>&1; then
    printf 'SKIP: not a git work tree, cannot verify arch baseline drift.\n'
    exit 0
fi

BASE_REF="${1:-}"
if [[ -z "$BASE_REF" ]]; then
    if git rev-parse --verify -q "origin/main" >/dev/null 2>&1; then
        BASE_REF="origin/main"
    elif git rev-parse --verify -q "HEAD~1" >/dev/null 2>&1; then
        BASE_REF="HEAD~1"
    fi
fi

if [[ -z "$BASE_REF" ]]; then
    printf 'SKIP: no base ref (origin/main and HEAD~1 both unavailable; shallow clone).\n'
    exit 0
fi

if ! git rev-parse --verify -q "$BASE_REF^{commit}" >/dev/null 2>&1; then
    printf 'SKIP: base ref %s does not resolve to a commit.\n' "$BASE_REF"
    exit 0
fi

if ! git cat-file -e "$BASE_REF:$STORE_PATH" 2>/dev/null; then
    printf 'SKIP: base ref %s has no %s yet (first introduction).\n' "$BASE_REF" "$STORE_PATH"
    exit 0
fi

mapfile -t NUMSTAT < <(git diff --numstat "$BASE_REF" -- "$STORE_PATH")
ADDED=0
REMOVED=0
DETAIL=""
for line in "${NUMSTAT[@]}"; do
    [[ -z "$line" ]] && continue
    added="${line%%$'\t'*}"
    rest="${line#*$'\t'}"
    removed="${rest%%$'\t'*}"
    file="${rest#*$'\t'}"
    [[ "$added" == "-" ]] && added=0
    [[ "$removed" == "-" ]] && removed=0
    ADDED=$((ADDED + added))
    REMOVED=$((REMOVED + removed))
    DETAIL+="  ${file}: +${added} / -${removed}"$'\n'
done

printf 'Arch baseline drift vs %s: +%s / -%s\n' "$BASE_REF" "$ADDED" "$REMOVED"

if (( ADDED == 0 && REMOVED == 0 )); then
    printf 'PASS: arch baseline unchanged.\n'
    exit 0
fi

if (( ADDED <= REMOVED )); then
    printf 'PASS: arch baseline is net shrinking (ratchet only moves down).\n'
    exit 0
fi

if [[ "${ALLOW_ARCH_BASELINE_EXPANSION:-false}" == "true" ]]; then
    printf 'WARN: arch baseline expands (+%s > -%s) but ALLOW_ARCH_BASELINE_EXPANSION=true.\n' "$ADDED" "$REMOVED"
    printf '%s' "$DETAIL"
    exit 0
fi

printf 'FAIL: arch baseline expanded (+%s > -%s).\n' "$ADDED" "$REMOVED"
printf '%s' "$DETAIL"
printf '\n新增违规必须先修复；确需纳管例外时，用 bash scripts/check-java-module-boundaries.sh --write-baseline 显式 refreeze，并在架构评审中说明，同时 CI 设置 ALLOW_ARCH_BASELINE_EXPANSION=true。\n'
exit 1
