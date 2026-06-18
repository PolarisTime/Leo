# Leo Go Backend

这是 `go` 分支上的 Go 后端入口，用于逐步替换 Java Spring Boot 后端。

当前实现范围保持克制，只覆盖迁移基础设施和前端启动所需的轻量接口：

- `GET /api/health`
- `GET /api/auth/ping`
- `GET /api/auth/captcha`
- `POST /api/auth/login`
- `POST /api/auth/login-2fa`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/setup/status`
- `GET /api/meta/code`
- `GET /api/api/meta/code`，兼容 Java 版 `MetaController` 的当前路径
- `GET /api/account/security`
- `POST /api/account/security/password`
- `POST /api/account/security/2fa/setup`
- `POST /api/account/security/2fa/enable`
- `POST /api/account/security/2fa/disable`

`GET /api/setup/status` 会按 Java 版逻辑查询 `sys_role`、`sys_user_role`、`sys_user`
和 `sys_company_setting` 判断首次初始化状态。

## 运行

```bash
go run ./cmd/server
```

默认监听 `0.0.0.0:11211`，与原后端端口一致。

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_HOST` | `0.0.0.0` | HTTP 监听地址 |
| `SERVER_PORT` | `11211` | HTTP 监听端口 |
| `SPRING_APPLICATION_NAME` / `LEO_APP_NAME` | `leo` | 应用名称 |
| `LOG_LEVEL` | `info` | `debug`、`info`、`warn`、`error` |
| `SPRING_DATASOURCE_URL` | 空 | PostgreSQL URL，兼容 `jdbc:postgresql://...` |
| `SPRING_DATASOURCE_HOST` | `localhost` | PostgreSQL 主机 |
| `SPRING_DATASOURCE_PORT` | `5432` | PostgreSQL 端口 |
| `SPRING_DATASOURCE_DB` | `leo` | PostgreSQL 数据库 |
| `SPRING_DATASOURCE_USERNAME` | `leo` | PostgreSQL 用户名 |
| `SPRING_DATASOURCE_PASSWORD` | 空 | PostgreSQL 密码 |
| `SPRING_DATA_REDIS_HOST` | `127.0.0.1` | Redis 主机 |
| `SPRING_DATA_REDIS_PORT` | `16379` | Redis 端口 |
| `SPRING_DATA_REDIS_PASSWORD` | 空 | Redis 密码 |
| `SPRING_DATA_REDIS_DATABASE` | `3` | Redis DB |
| `LEO_SETUP_REQUIRED` | `true` | 无数据库状态服务时的兜底初始化状态 |
| `LEO_ADMIN_CONFIGURED` | `false` | 无数据库状态服务时的兜底管理员状态 |
| `LEO_COMPANY_CONFIGURED` | `false` | 无数据库状态服务时的兜底公司主体状态 |
| `LEO_JWT_SECRET` | 空 | JWT 主密钥，长度至少 32 |
| `LEO_SECURITY_JWT_ISSUER` | `leo-erp` | JWT issuer |
| `LEO_SECURITY_JWT_ACCESS_EXPIRATION_MS` | `10m` | 访问令牌过期时间 |
| `LEO_SECURITY_JWT_REFRESH_EXPIRATION_MS` | `7d` | 刷新令牌过期时间 |
| `LEO_AUTH_REFRESH_COOKIE_SECURE` | `false` | refresh cookie 是否仅在 HTTPS 传输 |
| `LEO_MACHINE_ID` | `0` | 雪花 ID 机器号 |
| `TOTP_ENCRYPTION_KEY` | 空 | TOTP 主密钥兜底，账号安全 2FA 相关接口需要 |
| `LEO_SECURITY_TOTP_ISSUER` / `TOTP_ISSUER` | `LeoERP` | TOTP 二维码 issuer |

## 验证

```bash
go test ./...
```

## 迁移原则

- 先迁移无状态基础设施，再按业务模块迁移 CRUD 和领域规则。
- 不在 Go 版中复制 Java 框架层复杂度，只有在真实业务需要时才引入外部依赖。
- API 响应信封、错误码、端口和 `/api` 前缀保持与现有前端契约兼容。
- refresh token 使用 `leo_refresh_token` HttpOnly cookie，路径为 `/api/auth`。

## 账号安全接口

这些接口都要求已登录，并与 Java 版账号安全页保持同一路径。

### `POST /api/account/security/password`

请求体：

```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password"
}
```

返回：`200 OK`，`密码修改成功`

### `POST /api/account/security/2fa/setup`

返回：

```json
{
  "qrCodeBase64": "...",
  "secret": "JBSWY3DPEHPK3PXP"
}
```

### `POST /api/account/security/2fa/enable`

请求体：

```json
{
  "totpCode": "123456"
}
```

说明：也支持从 `X-TOTP-Code` 请求头读取验证码。

### `POST /api/account/security/2fa/disable`

请求体与 `enable` 相同，也支持 `X-TOTP-Code` 请求头。
是否允许关闭 2FA 由数据库开关 `SYS_FORBID_DISABLE_2FA` 控制，与 Java 版一致。
