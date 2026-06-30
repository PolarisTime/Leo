# Leo ERP 生产 CI/CD 部署

## 目标

生产发布必须走受控 CI/CD 流程：

```text
开发机脚本
  -> GitHub Actions workflow_dispatch
  -> 构建后端 JAR 与前端 dist
  -> 打包 release archive
  -> 校验 SHA256
  -> SSH 上传生产机
  -> 获取生产发布锁
  -> 执行生产机 pre-deploy hook
  -> systemd 重启后端
  -> 健康检查
  -> Nginx 前端软链切换
  -> 执行生产机 post-deploy hook
  -> 失败自动回滚
```

开发机不直接停止、启动或重启生产服务。

## GitHub 配置

在 `PolarisTime/Leo` 仓库配置 Environment：`production`。

必需 Secrets：

- `PROD_SSH_HOST`：生产机地址
- `PROD_SSH_USER`：SSH 用户
- `PROD_SSH_PRIVATE_KEY`：部署私钥

建议 Secrets：

- `PROD_SSH_PORT`：SSH 端口，默认 `22`
- `PROD_SSH_KNOWN_HOSTS`：生产机 known_hosts，未配置时 workflow 使用 `ssh-keyscan`
- `ARIES_REPOSITORY_TOKEN`：如果 `PolarisTime/Aries` 是私有仓库，需要配置可读 token

可选 Environment Variables：

- `PROD_RELEASE_ROOT`：后端发布根目录，默认 `/opt/leo`
- `PROD_FRONTEND_ROOT`：前端发布根目录，默认 `/var/www/leo`
- `PROD_BACKEND_SERVICE`：systemd 服务名，默认 `leo-backend`
- `PROD_HEALTHCHECK_URL`：健康检查地址，默认 `http://127.0.0.1:11211/api/auth/ping`
- `PROD_KEEP_RELEASES`：保留 release 数量，默认 `5`
- `PROD_START_COMMAND`：可选；非 systemd 部署的后端启动命令。
- `PROD_STOP_COMMAND`：可选；非 systemd 部署的后端停止命令。

当前 steelx 生产实例部署在 `/instance/steelx`，不是默认的 `/opt/leo`。若 GitHub `production` 环境要部署 steelx，配置 Environment Variables：

```text
PROD_RELEASE_ROOT=/instance/steelx
PROD_FRONTEND_ROOT=/instance/steelx/frontend
PROD_BACKEND_SERVICE=steelx-local
PROD_HEALTHCHECK_URL=http://127.0.0.1:57217/api/health
PROD_START_COMMAND=STEELX_ROOT=/instance/steelx bash /instance/steelx/shared/steelx-process.sh start
PROD_STOP_COMMAND=STEELX_ROOT=/instance/steelx bash /instance/steelx/shared/steelx-process.sh stop
```

生产机必须已存在 `/instance/steelx/shared/steelx-process.sh` 与 `/instance/steelx/shared/steelx.env`。首次迁移到 GitHub 部署前，先用本地 steelx 部署脚本完成目录、数据库、密钥、Nginx 和进程脚本初始化。

## 生产机准备

创建运行用户和目录：

```bash
sudo useradd --system --home /opt/leo --shell /usr/sbin/nologin leo
sudo mkdir -p /opt/leo/releases /opt/leo/shared /var/www/leo/releases /var/lib/leo
sudo chown -R leo:leo /opt/leo /var/lib/leo
sudo chown -R root:root /var/www/leo
```

创建 `/opt/leo/shared/leo.env`，只放生产机真实配置，不提交到 Git：

```bash
SERVER_PORT=11211
SPRING_DATASOURCE_HOST=127.0.0.1
SPRING_DATASOURCE_PORT=5432
SPRING_DATASOURCE_DB=prod
SPRING_DATASOURCE_USERNAME=leo
SPRING_DATASOURCE_PASSWORD=change-me
SPRING_DATA_REDIS_HOST=127.0.0.1
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_DATABASE=3
SPRING_DATA_REDIS_PASSWORD=change-me
LEO_JWT_SECRET=change-me-at-least-32-chars
TOTP_ENCRYPTION_KEY=change-me-at-least-16-chars
LEO_MACHINE_ID=1
LEO_ATTACHMENT_LOCAL_PATH=/var/lib/leo/uploads
LEO_AUTH_REFRESH_COOKIE_SECURE=true
LEO_AUTH_REFRESH_COOKIE_SAME_SITE=Strict
SPRING_AI_MCP_SERVER_ENABLED=false
```

安装 systemd 服务模板：

```bash
sudo cp deploy/systemd/leo-backend.service /etc/systemd/system/leo-backend.service
sudo systemctl daemon-reload
sudo systemctl enable leo-backend
```

安装 Nginx 配置模板：

```bash
sudo cp deploy/nginx/leo.conf /etc/nginx/conf.d/leo.conf
sudo nginx -t
sudo systemctl reload nginx
```

部署 SSH 用户需要能执行发布脚本中的 `sudo bash`、`systemctl restart leo-backend`、`systemctl daemon-reload`。建议限制 sudo 权限到部署命令范围，不要给通用 root shell。

## 成熟发布节奏

推荐流程：

1. 功能分支开发完成，本地通过单元测试和 lint。
2. 分别提交并推送 `leo`、`aries`。
3. 通过 PR 合入受保护分支。
4. 先触发 dry-run，只构建、测试、打包，不连接生产机。
5. 触发真实生产发布，GitHub Environment `production` 做人工审批。
6. 发布成功后检查登录、核心单据列表、关键报表。
7. 如发现问题，使用回滚 workflow 回到上一版。

数据库迁移遵循 forward-only 原则。后端启动会执行 Flyway，应用回滚不会自动反向回滚数据库结构；涉及破坏性 schema 变更时必须拆成“兼容发布 -> 数据迁移 -> 清理发布”多步。

## 生产机 Hook

远端发布脚本会在 `/opt/leo/shared` 下按需执行可执行 hook：

- `pre-deploy.sh <release_dir> <release_id>`：发布前执行，适合做数据库备份或外部通知。
- `post-deploy.sh <release_dir> <release_id>`：发布成功后执行。
- `pre-rollback.sh <release_dir> <release_id>`：回滚前执行。
- `post-rollback.sh <release_dir> <release_id>`：回滚成功后执行。

Hook 不提交到仓库，保留在生产机。

## 开发机触发

首次确认 GitHub CLI 已登录：

```bash
gh auth status
```

只构建打包，不部署生产：

```bash
bash leo/scripts/deploy/trigger-production-deploy.sh --dry-run --watch
```

真实生产发布：

```bash
bash leo/scripts/deploy/trigger-production-deploy.sh \
  --confirm-production \
  --leo-ref main \
  --aries-ref dev \
  --watch
```

脚本会拒绝部署未提交或未推送到远端的本地改动，因为 GitHub Actions 只能构建远端 Git ref。

## 本机 steelx 生产部署

本机 steelx 部署用于把当前开发环境构建为本地生产实例，流程仍保持 release 包、SHA256 校验、发布锁、软链切换、健康检查和可回滚。默认配置：

- 部署目录：`/instance/steelx`
- 服务域名：`in1ove.com`
- 前端入口：Nginx `443` HTTPS
- HTTP 跳转：Nginx `80` 301 跳转到 HTTPS
- 后端端口：`57217`
- 数据库：沿用当前开发 PostgreSQL 服务，生产库名 `Master_Prod`
- Redis：沿用当前开发 Redis 服务
- TLS 证书：`/instance/ssl/fullchain.crt`
- TLS 私钥：`/instance/ssl/privkey.key`

执行入口：

```bash
PGPASSWORD="<postgres-admin-password>" \
  bash leo/scripts/deploy/trigger-local-steelx-deploy.sh --confirm
```

脚本会安装 `/etc/nginx/conf.d/steelx.conf`，执行 `nginx -t` 后 reload Nginx。执行用户需要 root 权限，或具备免密 sudo 执行 `install`、`nginx -t`、`nginx -s reload` 的权限。生产实例对外地址为 `https://in1ove.com`，`http://in1ove.com` 会跳转到 HTTPS。

生产回滚到上一版：

```bash
bash leo/scripts/deploy/trigger-production-rollback.sh \
  --confirm-production \
  --target-release previous \
  --watch
```

## 回滚逻辑

远端脚本 `scripts/deploy/install-production-release.sh` 使用 release 目录和软链发布：

- 后端当前版本：`/opt/leo/current`
- 后端上个版本：`/opt/leo/previous`
- 前端当前版本：`/var/www/leo/current`

如果后端重启后健康检查失败，脚本会恢复旧的后端软链、重启 systemd 服务，并恢复旧的前端软链。
