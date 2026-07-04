#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LEO_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

REPO="PolarisTime/Leo"
WORKFLOW="deploy-production.yml"
WORKFLOW_REF="main"
LEO_REF=""
DRY_RUN=false
DEPLOY_TARGET="local"
CONFIRM_PRODUCTION=false
ALLOW_DIRTY=false
WATCH=false
RELEASE_NOTE=""

usage() {
  cat <<'EOF'
用法:
  bash leo/scripts/deploy/trigger-production-deploy.sh [选项]

选项:
  --leo-ref <ref>          后端部署 ref，默认使用 leo 当前分支
  --workflow-ref <ref>     触发 workflow 所在 ref，默认 main
  --repo <owner/repo>      GitHub 仓库，默认 PolarisTime/Leo
  --dry-run               只触发构建打包，不部署生产
  --deploy-target <target> 部署目标，local 或 ssh，默认 local
  --confirm-production    确认触发真实生产部署
  --allow-dirty           允许本地工作区存在未提交改动
  --watch                 触发后跟踪 Actions 运行状态
  --release-note <text>   发布备注
  -h, --help              查看帮助

示例:
  bash leo/scripts/deploy/trigger-production-deploy.sh --dry-run
  bash leo/scripts/deploy/trigger-production-deploy.sh --confirm-production --leo-ref main --watch
  bash leo/scripts/deploy/trigger-production-deploy.sh --confirm-production --deploy-target ssh --leo-ref main --watch
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --leo-ref) LEO_REF="$2"; shift 2 ;;
    --workflow-ref) WORKFLOW_REF="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    --dry-run) DRY_RUN=true; shift ;;
    --deploy-target) DEPLOY_TARGET="$2"; shift 2 ;;
    --confirm-production) CONFIRM_PRODUCTION=true; shift ;;
    --allow-dirty) ALLOW_DIRTY=true; shift ;;
    --watch) WATCH=true; shift ;;
    --release-note) RELEASE_NOTE="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 1 ;;
  esac
done

require_command() {
  local command_name="$1"
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "缺少命令: $command_name" >&2
    exit 1
  fi
}

current_branch_or_head() {
  local repo_dir="$1"
  local branch
  branch="$(git -C "$repo_dir" branch --show-current)"
  if [[ -n "$branch" ]]; then
    echo "$branch"
    return
  fi
  git -C "$repo_dir" rev-parse HEAD
}

ensure_clean_worktree() {
  local repo_dir="$1"
  local name="$2"
  if [[ "$ALLOW_DIRTY" == "true" ]]; then
    return
  fi
  if [[ -n "$(git -C "$repo_dir" status --porcelain)" ]]; then
    echo "$name 工作区存在未提交改动。CI/CD 只能部署已推送到远端的提交。" >&2
    echo "请先提交并推送，或仅在明确知道风险时追加 --allow-dirty。" >&2
    exit 1
  fi
}

ensure_branch_pushed() {
  local repo_dir="$1"
  local ref="$2"
  local name="$3"
  if ! git -C "$repo_dir" show-ref --verify --quiet "refs/heads/$ref"; then
    return
  fi
  local upstream
  upstream="$(git -C "$repo_dir" rev-parse --abbrev-ref "$ref@{upstream}" 2>/dev/null || true)"
  if [[ -z "$upstream" ]]; then
    echo "$name 分支 $ref 没有关联 upstream，无法确认远端是否包含当前提交。" >&2
    echo "请先设置 upstream 或显式传入已存在远端的 tag/sha。" >&2
    exit 1
  fi
  local local_sha upstream_sha
  local_sha="$(git -C "$repo_dir" rev-parse "$ref")"
  upstream_sha="$(git -C "$repo_dir" rev-parse "$upstream")"
  if [[ "$local_sha" != "$upstream_sha" ]]; then
    echo "$name 分支 $ref 与 upstream $upstream 不一致。" >&2
    echo "请先 push，确保 GitHub Actions 能取到相同代码。" >&2
    exit 1
  fi
}

require_command git
require_command gh

if [[ "$DEPLOY_TARGET" != "local" && "$DEPLOY_TARGET" != "ssh" ]]; then
  echo "--deploy-target 只支持 local 或 ssh: $DEPLOY_TARGET" >&2
  exit 1
fi

LEO_REF="${LEO_REF:-$(current_branch_or_head "$LEO_DIR")}"

ensure_clean_worktree "$LEO_DIR" "Leo"
ensure_branch_pushed "$LEO_DIR" "$LEO_REF" "Leo"

if [[ "$DRY_RUN" != "true" && "$CONFIRM_PRODUCTION" != "true" ]]; then
  cat >&2 <<EOF
拒绝触发真实生产部署。

本脚本会触发 GitHub Actions 的 production-deploy 工作流，并在 dry_run=false 时发布到指定生产目标。
默认部署目标是 local，需要 GitHub self-hosted runner 运行在本机并带有 steelx-production 标签。
如需真实发布，请追加:
  --confirm-production

如只想验证构建与打包，请使用:
  --dry-run
EOF
  exit 1
fi

gh auth status >/dev/null

echo "触发生产发布工作流:"
echo "  repo:         $REPO"
echo "  workflow:     $WORKFLOW"
echo "  workflow ref: $WORKFLOW_REF"
echo "  leo ref:      $LEO_REF"
echo "  target:       $DEPLOY_TARGET"
echo "  dry run:      $DRY_RUN"

gh workflow run "$WORKFLOW" \
  --repo "$REPO" \
  --ref "$WORKFLOW_REF" \
  -f "leo_ref=$LEO_REF" \
  -f "dry_run=$DRY_RUN" \
  -f "deploy_target=$DEPLOY_TARGET" \
  -f "release_note=$RELEASE_NOTE"

if [[ "$WATCH" == "true" ]]; then
  sleep 3
  gh run list --repo "$REPO" --workflow "$WORKFLOW" --limit 1
fi
