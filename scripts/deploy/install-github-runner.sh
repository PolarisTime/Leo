#!/usr/bin/env bash

set -euo pipefail

REPO="PolarisTime/Leo"
RUNNER_DIR="$HOME/actions-runner-steelx"
RUNNER_NAME="$(hostname)-steelx"
RUNNER_VERSION=""
RUNNER_LABELS="steelx-production"
RUNNER_GROUP=""
FORCE=false
START=false

usage() {
  cat <<'EOF'
用法:
  bash leo/scripts/deploy/install-github-runner.sh [选项]

选项:
  --repo <owner/repo>        GitHub 仓库，默认 PolarisTime/Leo
  --runner-dir <dir>         runner 安装目录，默认 ~/actions-runner-steelx
  --runner-name <name>       runner 名称，默认 <hostname>-steelx
  --runner-version <version> runner 版本，默认自动读取 GitHub 最新版本
  --labels <labels>          额外标签，默认 steelx-production
  --runner-group <group>     runner group，默认不指定
  --force                    若 runner 已配置，先移除再重新配置
  --start                    配置后启动 runner 用户服务
  -h, --help                 查看帮助

凭据:
  优先使用已登录且具备仓库 Admin 权限的 gh 自动创建注册 token。
  如果 gh 权限不足，可在网页端生成 self-hosted runner 注册 token 后设置:
    GITHUB_RUNNER_TOKEN=<token>

代理:
  如当前环境设置了 HTTP_PROXY、HTTPS_PROXY、ALL_PROXY，会写入 runner 用户服务。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO="$2"; shift 2 ;;
    --runner-dir) RUNNER_DIR="$2"; shift 2 ;;
    --runner-name) RUNNER_NAME="$2"; shift 2 ;;
    --runner-version) RUNNER_VERSION="$2"; shift 2 ;;
    --labels) RUNNER_LABELS="$2"; shift 2 ;;
    --runner-group) RUNNER_GROUP="$2"; shift 2 ;;
    --force) FORCE=true; shift ;;
    --start) START=true; shift ;;
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

require_command curl
require_command jq
require_command tar
require_command systemctl

if [[ "$(id -u)" -eq 0 ]]; then
  echo "请不要用 root 运行 GitHub Actions runner。请使用拥有 /instance/steelx 的普通用户执行。" >&2
  exit 1
fi

normalize_runner_proxy() {
  local proxy_var="$1"
  local proxy_value="${!proxy_var:-}"
  if [[ "$proxy_value" == socks5h://* ]]; then
    export "$proxy_var=socks5://${proxy_value#socks5h://}"
  fi
}

for proxy_var in HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy; do
  normalize_runner_proxy "$proxy_var"
done

owner="${REPO%%/*}"
repo_name="${REPO#*/}"
if [[ -z "$owner" || -z "$repo_name" || "$owner" == "$repo_name" ]]; then
  echo "--repo 格式必须是 owner/repo: $REPO" >&2
  exit 1
fi

service_name="github-actions-runner-${owner}-${repo_name}-steelx.service"
service_path="$HOME/.config/systemd/user/$service_name"

resolve_runner_version() {
  if [[ -n "$RUNNER_VERSION" ]]; then
    printf '%s\n' "$RUNNER_VERSION"
    return
  fi
  curl -fsSL "https://api.github.com/repos/actions/runner/releases/latest" |
    jq -r ".tag_name" |
    sed "s/^v//"
}

get_runner_token() {
  if [[ -n "${GITHUB_RUNNER_TOKEN:-}" ]]; then
    printf '%s\n' "$GITHUB_RUNNER_TOKEN"
    return
  fi

  require_command gh
  gh auth status >/dev/null
  if ! gh api \
    --method POST \
    "/repos/$REPO/actions/runners/registration-token" \
    --jq ".token"; then
    echo "无法创建 runner 注册 token。请使用具备仓库 Admin 权限的 gh 登录，或设置 GITHUB_RUNNER_TOKEN。" >&2
    return 1
  fi
}

mkdir -p "$RUNNER_DIR"
RUNNER_DIR="$(cd "$RUNNER_DIR" && pwd -P)"
cd "$RUNNER_DIR"
RUNNER_VERSION="$(resolve_runner_version)"
if [[ -z "$RUNNER_VERSION" || "$RUNNER_VERSION" == "null" ]]; then
  echo "无法解析 GitHub Actions runner 最新版本" >&2
  exit 1
fi
runner_url="https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz"
runner_archive="$RUNNER_DIR/actions-runner-linux-x64-${RUNNER_VERSION}.tar.gz"

if [[ -f ".runner" && "$FORCE" != "true" ]]; then
  echo "runner 已配置: $RUNNER_DIR" >&2
  echo "如需重配，请追加 --force。" >&2
  exit 1
fi

if [[ -f ".runner" && "$FORCE" == "true" ]]; then
  remove_token="$(get_runner_token)"
  ./config.sh remove --unattended --token "$remove_token" || true
  rm -f -- ".runner"
fi

if [[ ! -x "./config.sh" ]]; then
  echo "下载 GitHub Actions runner: $runner_url"
  curl -fL "$runner_url" -o "$runner_archive"
  tar -xzf "$runner_archive"
fi

runner_token="$(get_runner_token)"
config_args=(
  --unattended
  --url "https://github.com/$REPO"
  --token "$runner_token"
  --name "$RUNNER_NAME"
  --labels "$RUNNER_LABELS"
  --work "_work"
  --replace
)
if [[ -n "$RUNNER_GROUP" ]]; then
  config_args+=(--runnergroup "$RUNNER_GROUP")
fi
./config.sh "${config_args[@]}"

mkdir -p "$HOME/.config/systemd/user"
cat > "$service_path" <<EOF
[Unit]
Description=GitHub Actions Runner for $REPO steelx production
After=network-online.target

[Service]
Type=simple
WorkingDirectory=$RUNNER_DIR
Environment=PATH=$PATH
Environment=RUNNER_ALLOW_RUNASROOT=0
EOF

for proxy_var in HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY http_proxy https_proxy all_proxy no_proxy; do
  if [[ -n "${!proxy_var:-}" ]]; then
    printf 'Environment=%s=%s\n' "$proxy_var" "${!proxy_var}" >> "$service_path"
  fi
done

cat >> "$service_path" <<'EOF'
ExecStart=/usr/bin/env bash ./run.sh
Restart=always
RestartSec=10

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable "$service_name"

if [[ "$START" == "true" ]]; then
  systemctl --user restart "$service_name"
  systemctl --user status "$service_name" --no-pager
fi

echo "GitHub Actions runner 已配置:"
echo "  repo:    $REPO"
echo "  dir:     $RUNNER_DIR"
echo "  name:    $RUNNER_NAME"
echo "  labels:  self-hosted, linux, x64, $RUNNER_LABELS"
echo "  service: $service_name"
if command -v loginctl >/dev/null 2>&1; then
  linger="$(loginctl show-user "$(id -un)" -p Linger --value 2>/dev/null || true)"
  if [[ "$linger" != "yes" ]]; then
    echo "  note:    如需退出登录后继续运行，请由管理员执行: sudo loginctl enable-linger $(id -un)"
  fi
fi
