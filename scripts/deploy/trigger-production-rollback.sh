#!/usr/bin/env bash

set -euo pipefail

REPO="PolarisTime/Leo"
WORKFLOW="rollback-production.yml"
WORKFLOW_REF="main"
TARGET_RELEASE="previous"
DEPLOY_TARGET="local"
CONFIRM_PRODUCTION=false
WATCH=false

usage() {
  cat <<'EOF'
用法:
  bash leo/scripts/deploy/trigger-production-rollback.sh [选项]

选项:
  --target-release <release-id|previous>  回滚目标，默认 previous
  --deploy-target <target>                回滚目标，local 或 ssh，默认 local
  --workflow-ref <ref>                    触发 workflow 所在 ref，默认 main
  --repo <owner/repo>                     GitHub 仓库，默认 PolarisTime/Leo
  --confirm-production                    确认触发真实生产回滚
  --watch                                 触发后跟踪 Actions 运行状态
  -h, --help                             查看帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-release) TARGET_RELEASE="$2"; shift 2 ;;
    --deploy-target) DEPLOY_TARGET="$2"; shift 2 ;;
    --workflow-ref) WORKFLOW_REF="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    --confirm-production) CONFIRM_PRODUCTION=true; shift ;;
    --watch) WATCH=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "未知参数: $1" >&2; usage; exit 1 ;;
  esac
done

if ! command -v gh >/dev/null 2>&1; then
  echo "缺少命令: gh" >&2
  exit 1
fi

if [[ "$DEPLOY_TARGET" != "local" && "$DEPLOY_TARGET" != "ssh" ]]; then
  echo "--deploy-target 只支持 local 或 ssh: $DEPLOY_TARGET" >&2
  exit 1
fi

if [[ "$CONFIRM_PRODUCTION" != "true" ]]; then
  cat >&2 <<EOF
拒绝触发真实生产回滚。

如需回滚生产环境，请追加:
  --confirm-production
EOF
  exit 1
fi

gh auth status >/dev/null

echo "触发生产回滚工作流:"
echo "  repo:           $REPO"
echo "  workflow:       $WORKFLOW"
echo "  workflow ref:   $WORKFLOW_REF"
echo "  target release: $TARGET_RELEASE"
echo "  target:         $DEPLOY_TARGET"

gh workflow run "$WORKFLOW" \
  --repo "$REPO" \
  --ref "$WORKFLOW_REF" \
  -f "target_release=$TARGET_RELEASE" \
  -f "deploy_target=$DEPLOY_TARGET"

if [[ "$WATCH" == "true" ]]; then
  sleep 3
  gh run list --repo "$REPO" --workflow "$WORKFLOW" --limit 1
fi
