# Leo ERP 生产 CI/CD 部署

## 目标

生产发布必须走受控 CI/CD 流程：

```text
开发机脚本
  -> GitHub Actions workflow_dispatch
  -> 构建后端 JAR 与前端 dist
  -> 拆分应用 JAR 与运行时依赖 bundle
  -> 分别打包应用归档与依赖归档
  -> 校验 SHA256
  -> 本机 self-hosted runner 下载应用归档
  -> 依赖 bundle 缓存未命中时才下载依赖归档
  -> 获取 /instance/steelx/backend 生产发布锁
  -> 执行本机 pre-deploy hook
  -> 重启 steelx 后端进程
  -> 健康检查
  -> Nginx 前端软链切换
  -> 执行本机 post-deploy hook
  -> 失败自动回滚
```

开发机不直接停止、启动或重启生产服务。真实部署由 GitHub Actions 调度到本机 self-hosted runner 执行。

## GitHub 配置

在 `PolarisTime/Leo` 仓库配置 Environment：`production`。

后端自动发版使用 `.github/workflows/release.yml`。`main` 分支推送后，`semantic-release` 会根据 Conventional Commits 计算版本，更新 `pom.xml` 和 `CHANGELOG.md`，创建 `vX.Y.Z` tag 与 GitHub Release。该 tag 会继续触发 `.github/workflows/deploy-production.yml`，完成后端生产部署。

仓库必须配置 `SEMANTIC_RELEASE_TOKEN` secret。该 token 需要具备向 `PolarisTime/Leo` 推送 release commit/tag 并创建 GitHub Release 的权限；不要只依赖默认 `GITHUB_TOKEN`，否则由 workflow 创建的 tag 不会继续触发部署 workflow。

仓库级 Variable `PROD_FLYWAY_TARGET` 必须配置为发布包内最高的 `V*.sql` 迁移版本。tag 自动发布使用该值；手工 `workflow_dispatch` 使用显式 `flyway_target` 输入。构建期会校验 target 与发布包最高迁移版本完全一致，随后 `flyway:migrate/validate`、local/SSH 部署前校验和生产 `SPRING_FLYWAY_TARGET` 也必须保持一致；缺失、`latest`、落后或超前都会停止发布，禁止应用代码先于数据库结构上线。

默认生产目标是 `local`，要求本机已注册 GitHub self-hosted runner，并带有以下标签：

- `self-hosted`
- `linux`
- `steelx-production`

本机 runner 可以用仓库脚本安装：

```bash
bash leo/scripts/deploy/install-github-runner.sh --start
```

该脚本优先用已登录且具备仓库 Admin 权限的 `gh` 创建 runner 注册 token。若当前 token 权限不足，可在 GitHub 页面生成 self-hosted runner 注册 token 后执行：

```bash
GITHUB_RUNNER_TOKEN="<registration-token>" \
  bash leo/scripts/deploy/install-github-runner.sh --start
```

本机部署不需要 SSH secrets。

SSH 部署目标 `deploy_target=ssh` 才需要以下 Secrets：

- `PROD_SSH_HOST`：生产机地址
- `PROD_SSH_USER`：SSH 用户
- `PROD_SSH_PRIVATE_KEY`：部署私钥

SSH 可选 Secrets：

- `PROD_SSH_PORT`：SSH 端口，默认 `22`
- `PROD_SSH_KNOWN_HOSTS`：生产机 known_hosts，未配置时 workflow 使用 `ssh-keyscan`
- `ARIES_REPOSITORY_TOKEN`：如果 `PolarisTime/Aries` 是私有仓库，需要配置可读 token

可选 Environment Variables：

- `PROD_RELEASE_ROOT`：后端发布根目录，默认 `/instance/steelx/backend`
- `PROD_SHARED_DIR`：后端共享配置与 hook 目录，默认 `/instance/steelx/shared`
- `PROD_FRONTEND_ROOT`：前端发布根目录，默认 `/instance/steelx/frontend`
- `PROD_BACKEND_SERVICE`：systemd 服务名，默认 `steelx-local`
- `PROD_HEALTHCHECK_URL`：健康检查地址，默认 `http://127.0.0.1:57217/api/health`
- `PROD_KEEP_RELEASES`：保留 release 数量，默认 `5`
- `PROD_START_COMMAND`：非 systemd 部署的后端启动命令，默认 `STEELX_ROOT=/instance/steelx bash /instance/steelx/shared/steelx-process.sh start`
- `PROD_STOP_COMMAND`：非 systemd 部署的后端停止命令，默认 `STEELX_ROOT=/instance/steelx bash /instance/steelx/shared/steelx-process.sh stop`

当前 steelx 生产实例部署在 `/instance/steelx`。若后续迁移到其他生产实例，可用 GitHub Variables 覆盖默认值：

```text
PROD_RELEASE_ROOT=/instance/steelx/backend
PROD_SHARED_DIR=/instance/steelx/shared
PROD_FRONTEND_ROOT=/instance/steelx/frontend
PROD_BACKEND_SERVICE=steelx-local
PROD_HEALTHCHECK_URL=http://127.0.0.1:57217/api/health
PROD_START_COMMAND=STEELX_ROOT=/instance/steelx bash /instance/steelx/shared/steelx-process.sh start
PROD_STOP_COMMAND=STEELX_ROOT=/instance/steelx bash /instance/steelx/shared/steelx-process.sh stop
```

生产机必须已存在 `/instance/steelx/shared/steelx-process.sh` 与 `/instance/steelx/shared/steelx.env`。首次迁移到 GitHub 部署前，先用本地 steelx 部署脚本完成目录、数据库、密钥、Nginx 和进程脚本初始化。

## 生产机准备

当前 steelx 本机生产实例使用 `/instance/steelx`，首次初始化优先使用“本机 steelx 生产部署”章节。

以下 `/opt/leo` systemd/Nginx 模板用于通用 SSH/systemd 部署目标，不是当前本机 steelx 默认路径。

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
SPRING_FLYWAY_TARGET=94
LEO_JWT_SECRET=change-me-at-least-32-chars
LEO_DATA_ENCRYPTION_KEY=change-me-at-least-32-chars
LEO_SETUP_BOOTSTRAP_TOKEN=<32-byte-base64url-token>
LEO_MACHINE_ID=1
LEO_ATTACHMENT_LOCAL_PATH=/var/lib/leo/uploads
LEO_AUTH_REFRESH_COOKIE_SECURE=true
LEO_AUTH_REFRESH_COOKIE_SAME_SITE=Strict
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

两份 Nginx 模板都允许公网读取 `GET /api/setup/status`，以便前端判断是否需要首次初始化；`POST`、`PUT`、`PATCH`、`DELETE` 等写请求默认只允许从 `127.0.0.1` 或 `::1` 发起。因而公网前端可以展示初始化状态，但不能直接提交初始化数据。需要从远程管理终端完成网页初始化时，只在 `location ^~ /api/setup` 的 `deny all` 前临时增加精确的管理源 IP/CIDR `allow`，先执行 `nginx -t` 再 reload，初始化完成后立即移除。该 location 故意不带末尾 `/`，并使用不带 URI 的 `proxy_pass`，以同时覆盖并原样转发 `/api/setup`、其子路径以及 `/api/setup;...` 路径参数，避免请求落入通用 `/api/` 代理。`X-Setup-Token` 仍必须同时校验，不能用扩大 Nginx 来源范围代替应用凭证。

`LEO_SETUP_BOOTSTRAP_TOKEN` 必须是 32 字节随机值的 Base64URL 编码，并按生产密码管理，不得写入 Git、命令行参数或部署日志。初始化完成前保持该值稳定；完成后可从运行环境移除。

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

数据库迁移遵循 forward-only 原则。所有数据库结构、约束、索引、种子数据、数据修复和清理变更必须新增递增的 Flyway 脚本，禁止修改已经合入或已经在任一环境执行过的历史迁移。后端启动会执行 Flyway，应用回滚不会自动反向回滚数据库结构；涉及破坏性 schema 变更时必须拆成“兼容发布 -> 数据迁移 -> 清理发布”多步。

## PostgreSQL 调优检查

生产调优先执行只读检查脚本，确认扩展、参数、缓存命中率、死元组和表维护状态：

```bash
bash scripts/postgres-tuning-check.sh \
  --env prod \
  --env-file /instance/steelx/shared/steelx.env
```

脚本默认不修改数据库或系统配置。若仅需补齐 `pg_stat_statements` 扩展，可在确认后追加 `--apply-extension`；实例参数如 `track_io_timing`、`shared_buffers`、`effective_cache_size` 仍需数据库管理员按脚本输出的建议执行，并按参数要求 reload 或 restart PostgreSQL。

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
bash leo/scripts/deploy/trigger-production-deploy.sh --dry-run --flyway-target 94 --watch
```

真实生产发布：

```bash
bash leo/scripts/deploy/trigger-production-deploy.sh \
  --confirm-production \
  --deploy-target local \
  --flyway-target 94 \
  --leo-ref main \
  --aries-ref dev \
  --watch
```

`--deploy-target local` 是默认值，可省略。若要走旧 SSH 目标，显式传 `--deploy-target ssh` 并配置 SSH secrets。

脚本会拒绝部署未提交或未推送到远端的本地改动，因为 GitHub Actions 只能构建远端 Git ref。

## 本机 steelx 生产部署

本机 steelx 部署用于把当前开发环境构建为本地生产实例，流程仍保持 release 包、SHA256 校验、发布锁、软链切换、健康检查和可回滚。默认配置：

- 部署目录：`/instance/steelx`
- 后端发布目录：`/instance/steelx/backend`
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
  bash leo/scripts/deploy/trigger-local-steelx-deploy.sh --confirm --flyway-target 94
```

脚本会安装 `/etc/nginx/conf.d/steelx.conf`，执行 `nginx -t` 后 reload Nginx。执行用户需要 root 权限，或具备免密 sudo 执行 `install`、`nginx -t`、`nginx -s reload` 的权限。生产实例对外地址为 `https://in1ove.com`，`http://in1ove.com` 会跳转到 HTTPS。

脚本重写 `/instance/steelx/shared/steelx.env` 时会保留已有的 `LEO_SETUP_BOOTSTRAP_TOKEN`；变量缺失或为空时，使用 OpenSSL 生成 32 字节随机值并转换为无填充 Base64URL。token 只写入权限为 `600` 的 env 文件，不在脚本输出中回显。若已有值不符合 32 字节 Base64URL 格式，脚本会停止并保留原文件，避免静默轮换导致初始化客户端失效。

完成首次初始化后，后续自动化发布走 GitHub Actions `deploy_target=local`。本机 runner 直接下载 release archive 并调用 `scripts/deploy/install-production-release.sh` 发布到 `/instance/steelx/backend`，不会重新写数据库。

后端制品采用“应用 JAR + 外置依赖 bundle”结构：

```text
/instance/steelx/backend/
  dependencies/<dependency-bundle-id>/lib/*.jar
  releases/<release-id>/leo.jar
  releases/<release-id>/lib -> ../../dependencies/<dependency-bundle-id>/lib
  current -> releases/<release-id>
  previous -> releases/<release-id>
```

`dependency-bundle-id` 是按文件名排序后的依赖 JAR SHA-256 清单摘要。普通业务代码发布只下载约 2 MiB 的应用归档；只有 Maven 运行时依赖实际变化、缓存缺失或校验失败时，runner 才下载并安装约 132 MiB 的依赖归档。安装脚本会逐文件校验依赖内容，损坏的同 ID 目录会先隔离再替换。`steelx-process.sh` 对新 release 使用外置 classpath，对历史 fat JAR release 保留 `java -jar` 兼容，因此可以跨两种格式回滚。

从旧后端根目录切换到独立后端目录时，首次自动化发布会创建 `/instance/steelx/backend/releases`、`/instance/steelx/backend/current` 和 `/instance/steelx/backend/previous`。如果新目录还没有 `current`，脚本会识别旧的 `/instance/steelx/current` 软链，并把它作为 `/instance/steelx/backend/previous`，用于首次发布失败回滚或手动回滚。脚本不会移动、重命名或删除旧的 `/instance/steelx/current` 与 `/instance/steelx/releases` 内容；旧目录清理必须在新目录稳定运行并确认不再需要回滚后手动执行。

本机 runner 会在 release 安装成功后调用 `scripts/deploy/install-steelx-nginx-config.sh` 安装 SteelX Nginx 配置。该脚本会生成 `/instance/steelx/shared/nginx-steelx.conf`，备份并覆盖 `/etc/nginx/conf.d/steelx.conf`，执行 `nginx -t` 后 reload Nginx；如校验或 reload 失败，会尝试恢复上一版配置。SteelX 上传请求体临时目录固定在 `/instance/steelx/frontend/nginx-client-body`，不放入 `/instance/steelx/frontend/current`。

## 生产部署核验规范

任何声称“代码已经部署进生产”的结论，必须同时核验生产 release manifest 和 GitHub Actions 成功记录，不允许只依据本地文件、分支名或 runner 最近运行状态判断。

核验步骤：

1. 读取当前生产 release manifest：

```bash
jq . /instance/steelx/backend/current/manifest.json
```

重点字段：

- `runId`：本次生产部署对应的 GitHub Actions run。
- `runNumber`：部署序号。
- `leoSha`：实际打包部署的 Leo commit。
- `leoRef` / `ariesRef`：触发部署时选择的后端和前端 ref。
- `deployTarget`：必须为 `local`。
- `flywayTarget`：必须等于本次批准的生产迁移阶段上限。
- `applicationSha256`：本次薄应用 JAR 的 SHA-256。
- `dependencyBundleId`：当前 release 绑定的运行时依赖 bundle ID。
- `dryRun`：必须为 `false`。

2. 使用 manifest 中的 `runId` 查询 GitHub Actions：

```bash
gh run view "$(jq -r '.runId' /instance/steelx/backend/current/manifest.json)" \
  --repo PolarisTime/Leo \
  --json databaseId,displayTitle,status,conclusion,headBranch,headSha,jobs,url
```

必须确认：

- workflow conclusion 为 `success`。
- `Deploy Production Local` job 为 `success`。
- `headSha` 与 manifest 的 `leoSha` 一致。
- run title 中 `target=local` 且 `dry_run=false`。

3. 对比当前仓库代码与已部署 commit：

```bash
deployed_sha="$(jq -r '.leoSha' /instance/steelx/backend/current/manifest.json)"
git rev-parse HEAD
git merge-base --is-ancestor "$deployed_sha" HEAD
git rev-list --count "$deployed_sha"..HEAD
git status --short
```

判断规则：

- `git rev-parse HEAD` 等于 `deployed_sha`，且 `git status --short` 为空：当前工作区代码与生产后端代码一致。
- `git rev-list --count "$deployed_sha"..HEAD` 大于 `0`：当前分支有提交尚未部署。
- `git status --short` 非空：当前工作区有未提交改动，必然尚未通过 CI/CD 部署。
- 新增或修改 `.github/workflows/*`、`scripts/deploy/*`、`deploy/nginx/*`、`docs/deployment/*` 后，必须提交并推送到部署使用的 `leo_ref`，再触发 `deploy_target=local`，生产才会应用。

4. Nginx 配置类变更的额外核验：

```bash
rg -n "client_body_temp_path|client_body_buffer_size" \
  /instance/steelx/shared/nginx-steelx.conf \
  /etc/nginx/conf.d/steelx.conf
```

只有当上述文件内容与本次目标配置一致，且对应的 GitHub Actions run 成功，才能判定 Nginx 配置变更已通过 CI/CD 生效。

生产回滚到上一版：

```bash
bash leo/scripts/deploy/trigger-production-rollback.sh \
  --confirm-production \
  --deploy-target local \
  --target-release previous \
  --watch
```

## 回滚逻辑

远端脚本 `scripts/deploy/install-production-release.sh` 使用 release 目录和软链发布：

- 后端当前版本：`/instance/steelx/backend/current`
- 后端上个版本：`/instance/steelx/backend/previous`
- 后端共享依赖：`/instance/steelx/backend/dependencies/<dependency-bundle-id>`
- 前端当前版本：`/instance/steelx/frontend/current`

如果后端重启后健康检查失败，脚本会恢复旧的后端软链并重启服务。新格式 release 回滚前会确认外置依赖链接完整；旧 fat JAR release 不需要依赖 bundle，可直接回滚启动。
