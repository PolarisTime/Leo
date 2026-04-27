# Leo ERP Backend

钢材贸易业务中台后端服务，基于 Spring Boot 3.5 + Java 21。

## 技术栈

- **语言**: Java 21
- **框架**: Spring Boot 3.5, Spring Security, Spring Data JPA
- **数据库**: PostgreSQL 16 (Flyway 迁移)
- **缓存**: Redis 7
- **认证**: JWT (jjwt) + TOTP 两步验证
- **文档**: OpenAPI 3 / Swagger UI
- **构建**: Maven 3.9

## 业务模块

| 模块 | 说明 |
|------|------|
| `auth` | 认证、API Key 管理、用户账户管理、角色权限 |
| `attachment` | 附件上传/下载、上传规则配置 |
| `contract` | 采购/销售合同管理 |
| `purchase` | 采购订单、入库管理 |
| `sales` | 销售订单管理 |
| `finance` | 收付款、发票、应收应付 |
| `statement` | 客户/供应商/运费对账 |
| `logistics` | 物流运输管理 |
| `master` | 基础资料（物料、客户、供应商） |
| `report` | 财务报表 |
| `ops` | 运维工具（数据库备份、健康检查） |
| `system` | 系统配置、单号规则、菜单管理 |

## 快速开始

### 环境要求

- Java 21+
- Maven 3.9+
- PostgreSQL 16+
- Redis 7+

### 本地运行

```bash
# 配置环境变量
cp scripts/env-local.sh.example scripts/env-local.sh
vim scripts/env-local.sh

# 启动
./scripts/start-local.sh
```

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SPRING_DATASOURCE_HOST` | `localhost` | 数据库主机 |
| `SPRING_DATASOURCE_PORT` | `5432` | 数据库端口 |
| `SPRING_DATASOURCE_DB` | `leo` | 数据库名 |
| `SPRING_DATASOURCE_USERNAME` | `leo` | 数据库用户 |
| `SPRING_DATASOURCE_PASSWORD` | — | 数据库密码 |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis 主机 |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis 端口 |
| `LEO_AUTH_JWT_SECRET` | — | JWT 签名密钥 (必填) |
| `LEO_AUTH_REFRESH_COOKIE_SECURE` | `true` | Refresh Token Cookie Secure |
| `LEO_DOCS_PUBLIC_ACCESS_ENABLED` | `false` | 公开 Swagger 文档 |

### 测试

```bash
mvn test
```

### 构建

```bash
mvn package -DskipTests
```

## API 文档

开发环境访问 http://localhost:11211/api/swagger-ui.html

## 许可证

MIT License - 详见 [LICENSE](LICENSE)
