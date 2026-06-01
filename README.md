# Leo ERP Backend

Leo 是钢材贸易 ERP 的后端服务，负责认证授权、业务单据、对账结算、附件、系统设置、全局搜索和审计能力。

## 技术栈

- Java 21
- Spring Boot 3.5
- Spring Security
- Spring Data JPA
- PostgreSQL + Flyway
- Redis
- JWT + TOTP
- OpenAPI / Swagger UI
- Maven 3.9

## 业务范围

- 采购：采购订单、采购入库
- 销售：销售订单、销售出库
- 合同：采购合同、销售合同
- 物流：物流单、物流对账单
- 对账：客户、供应商对账单
- 财务：收款单、付款单、收票单、开票单、应收应付
- 系统：通用设置、系统开关、单号规则、权限、附件、密钥轮转
- 聚合全局搜索：按权限和数据范围搜索多类业务单据

## 环境要求

- Java 21+
- Maven 3.9+
- PostgreSQL 16+
- Redis 6+

## 本地运行

1. 准备根目录 `.env.local`，供 `scripts/env/dev.sh` 自动加载。

2. 如需初始化数据库和应用账号：

```bash
bash scripts/init-database.sh
```

3. 启动本地后端：

```bash
bash leo/scripts/dev.sh start
```

启动脚本会检查：

- `JAVA_HOME`
- PostgreSQL 连通性和认证
- Redis 连通性和认证
- 可选启动密钥 `LEO_JWT_SECRET` / `TOTP_ENCRYPTION_KEY`

## 常用命令

```bash
mvn -q -DskipTests compile
mvn test
mvn -DskipTests package
```

Swagger UI：

- `http://localhost:11211/api/swagger-ui.html`

健康检查：

- `http://localhost:11211/api/health`

首次初始化页面由前端提供：

- `http://localhost:3100/setup`

## 生产测试

生产测试环境使用独立入口，默认连接 `prod` 数据库，并使用 Spring `prod` profile 与前端生产构建预览：

```bash
bash leo/scripts/prod.sh start
bash leo/scripts/prod.sh stop
```

开发和生产测试默认使用相同端口，切换环境前请先执行对应脚本的 `stop`。

后端生产启动逻辑位于 `scripts/backend/start-prod.sh`，环境默认值位于 `scripts/env/prod.sh`。

## 关键配置

常见环境变量：

- `SPRING_DATASOURCE_HOST`
- `SPRING_DATASOURCE_PORT`
- `SPRING_DATASOURCE_DB`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATA_REDIS_HOST`
- `SPRING_DATA_REDIS_PORT`
- `SPRING_DATA_REDIS_DATABASE`
- `SPRING_DATA_REDIS_PASSWORD`
- `LEO_JWT_SECRET`
- `TOTP_ENCRYPTION_KEY`
- `LEO_MACHINE_ID`

## CI 校验

GitHub Actions 当前会执行以下步骤：

```bash
mvn -B -ntp -DskipTests checkstyle:check spotbugs:spotbugs
mvn -B -ntp test
mvn -B -ntp -DskipTests package
docker build -t leo-backend:ci .
```

CI 中还会拉起 PostgreSQL 和 Redis 服务，并做镜像启动冒烟检查。

## 安全说明

- 不要把真实数据库密码、Redis 密码、JWT 密钥、TOTP 密钥提交到仓库。
- 生产环境建议使用数据库托管密钥或外部密钥管理。
- Swagger 和健康检查的匿名访问应通过配置显式控制。
