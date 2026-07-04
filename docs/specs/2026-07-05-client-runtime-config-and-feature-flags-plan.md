---
title: client-setting 破坏性重构与功能开关治理计划
date: 2026-07-05
status: draft
owner: PolarisTime / 浮浮酱
scope: aries 前端 + leo 后端
---

# 1. 背景

当前前端通过 `GET /api/general-settings/client-setting` 读取一组通用设置白名单。这个接口同时承载了：

1. UI 功能开关，例如重量视图、是否展示雪花 ID、是否启用页面水印。
2. 客户端运行时参数，例如默认分页大小、水印内容、字号、颜色、密度。
3. 业务规则参数，例如默认税率、对账生成规则、业务编号策略。

这三类配置的变更频率、权限模型、审计要求和运行时语义不同。继续放在同一个 `List<GeneralSettingResponse>` 接口里，会导致前端依赖字符串 code、缺少类型边界、配置含义不清晰，也不利于引入成熟的功能开关治理能力。

本计划以“**不考虑旧接口兼容**”为前提，允许删除或替换现有 `client-setting` 接口及前端消费方式。

# 2. 目标

- 废弃 `GET /api/general-settings/client-setting` 的列表式客户端配置接口。
- 引入强类型 `GET /api/runtime-config`，作为前端启动和运行时读取配置的唯一入口。
- 将功能开关治理迁移到 `OpenFeature + Unleash` 体系。
- 将业务参数继续保留在 ERP 数据库中，但通过强类型服务和 DTO 暴露。
- 删除前端散落的 `settingCode` 字符串判断，改为语义化字段访问。
- 明确功能开关、业务配置、系统配置三者边界，避免继续扩展“大杂烩”接口。

# 3. 非目标

- 不兼容旧版 `/api/general-settings/client-setting`。
- 不保留旧的 `ModuleRecord[]` 配置返回结构。
- 不把默认税率、对账规则、水印内容等业务参数迁入 Unleash。
- 不用 Nacos 或 Spring Cloud Config 替代业务后台配置。
- 不在本计划内实现灰度规则的复杂运营后台；Unleash 自身承担开关管理。

# 4. 架构决策

## 4.1 配置分类

| 分类 | 存储/治理系统 | 读取方式 | 示例 |
|---|---|---|---|
| 功能开关 | Unleash，经 OpenFeature 抽象访问 | 后端聚合到 runtime config；必要时前端 SDK 直连 | 重量视图、展示雪花 ID、启用水印 |
| 业务参数 | ERP 数据库 | leo 强类型业务配置服务 | 默认税率、对账生成规则、业务编号策略 |
| 客户端显示参数 | ERP 数据库 | leo 强类型客户端配置服务 | 默认分页、水印内容、字号、颜色、密度 |
| 服务运行配置 | Spring 配置体系 | Spring `@ConfigurationProperties` | Redis、数据库、JWT、CORS |

## 4.2 推荐技术组合

- `OpenFeature`：作为代码侧功能开关标准 API，避免业务代码绑定 Unleash SDK。
- `Unleash`：作为功能开关管理平台，提供开关、环境、策略、灰度、审计能力。
- ERP DB：继续作为业务参数和可由系统管理员维护的设置源。

不推荐用 Nacos / Spring Cloud Config 接管当前 `client-setting` 的主要原因：

- 它们更适合服务配置和运维配置，不适合 ERP 业务用户维护税率、对账规则、水印文案。
- 业务配置需要与权限、操作日志、审计、租户/公司上下文保持一致。
- 当前需求的主要缺口是“类型边界”和“功能开关治理”，不是配置中心能力不足。

# 5. 新接口设计

## 5.1 废弃接口

```http
GET /api/general-settings/client-setting
```

废弃后删除前端 `listClientSettings()` 及所有依赖 `QUERY_KEYS.clientSettings` 的直接消费点。

## 5.2 新增接口

```http
GET /api/runtime-config
```

响应示例：

```json
{
  "ui": {
    "defaultPageSize": 20,
    "showSnowflakeId": true,
    "watermark": {
      "enabled": true,
      "content": "{username}  {time}",
      "fontSize": 18,
      "color": "rgba(0,0,0,0.08)",
      "rotate": -22,
      "density": 200
    }
  },
  "business": {
    "defaultTaxRate": 0.13,
    "statement": {
      "customerReceiptAmountZero": true,
      "supplierFullPayment": false
    },
    "businessNo": {
      "useSnowflakeId": false
    }
  },
  "features": {
    "weightOnlyPurchaseInbound": false,
    "weightOnlySalesOutbound": false
  }
}
```

## 5.3 后端 DTO 草案

```java
public record RuntimeConfigResponse(
        RuntimeUiConfig ui,
        RuntimeBusinessConfig business,
        RuntimeFeatureConfig features
) {
}

public record RuntimeUiConfig(
        int defaultPageSize,
        boolean showSnowflakeId,
        RuntimeWatermarkConfig watermark
) {
}

public record RuntimeWatermarkConfig(
        boolean enabled,
        String content,
        int fontSize,
        String color,
        int rotate,
        int density
) {
}

public record RuntimeBusinessConfig(
        BigDecimal defaultTaxRate,
        RuntimeStatementConfig statement,
        RuntimeBusinessNoConfig businessNo
) {
}

public record RuntimeFeatureConfig(
        boolean weightOnlyPurchaseInbound,
        boolean weightOnlySalesOutbound
) {
}
```

# 6. 功能迁移映射

## 6.1 迁入 Unleash 的开关

| 原 settingCode | 新 feature key | 说明 |
|---|---|---|
| `UI_WEIGHT_ONLY_PURCHASE_INBOUNDS` | `purchase-inbound.weight-only-view` | 采购入库重量视图 |
| `UI_WEIGHT_ONLY_SALES_OUTBOUNDS` | `sales-outbound.weight-only-view` | 销售出库重量视图 |
| `UI_SHOW_SNOWFLAKE_ID` | `ui.show-snowflake-id` | 列表/详情显示系统 ID |
| `UI_WATERMARK_ENABLED` | `ui.watermark.enabled` | 页面水印启用开关 |

## 6.2 保留在 ERP DB 的业务配置

| 原 settingCode | 新字段 | 说明 |
|---|---|---|
| `SYS_DEFAULT_TAX_RATE` | `business.defaultTaxRate` | 发票收票/开票默认税率 |
| `SYS_CUSTOMER_STATEMENT_RECEIPT_ZERO_FROM_SALES_ORDER` | `business.statement.customerReceiptAmountZero` | 客户对账生成规则 |
| `SYS_SUPPLIER_STATEMENT_FULL_PAYMENT_FROM_PURCHASE` | `business.statement.supplierFullPayment` | 供应商对账生成规则 |
| `SYS_USE_SNOWFLAKE_ID_AS_BUSINESS_NO` | `business.businessNo.useSnowflakeId` | 业务编号策略 |

## 6.3 保留在 ERP DB 的 UI 参数

| 原 settingCode | 新字段 | 说明 |
|---|---|---|
| `UI_DEFAULT_LIST_PAGE_SIZE` | `ui.defaultPageSize` | 默认分页大小 |
| `SYS_WATERMARK_CONTENT` | `ui.watermark.content` | 水印模板 |
| `SYS_WATERMARK_FONT_SIZE` | `ui.watermark.fontSize` | 水印字号 |
| `SYS_WATERMARK_ROTATE` | `ui.watermark.rotate` | 水印角度 |
| `SYS_WATERMARK_COLOR` | `ui.watermark.color` | 水印颜色 |
| `SYS_WATERMARK_DENSITY` | `ui.watermark.density` | 水印密度 |

# 7. 后端改造计划

## 7.1 新增模块边界

新增运行时配置聚合层：

- `RuntimeConfigController`
- `RuntimeConfigService`
- `RuntimeBusinessConfigService`
- `RuntimeUiConfigService`
- `FeatureFlagService`

职责划分：

- `RuntimeConfigController` 只负责 HTTP 入口。
- `RuntimeConfigService` 聚合 UI、业务、feature 三类结果。
- `RuntimeBusinessConfigService` 从 ERP DB 读取强类型业务配置。
- `RuntimeUiConfigService` 从 ERP DB 读取强类型 UI 参数。
- `FeatureFlagService` 通过 OpenFeature 获取功能开关结果，隐藏 Unleash 细节。

## 7.2 OpenFeature 集成

后端以 OpenFeature 作为唯一开关访问 API：

```java
public interface FeatureFlagService {
    boolean isEnabled(String key, FeatureContext context, boolean fallback);
}
```

实现层再绑定 Unleash provider。业务代码不得直接依赖 Unleash SDK。

## 7.3 缓存策略

- `runtime-config` 可按用户缓存 30-60 秒。
- 业务配置 DB 读取可复用现有 Redis 缓存命名空间。
- feature flag 读取优先依赖 Unleash SDK 本地缓存。
- 配置后台保存后必须失效 runtime config 缓存。

# 8. 前端改造计划

## 8.1 新增 API

新增：

- `src/api/runtime-config.ts`
- `src/types/runtime-config.ts`
- `src/hooks/useRuntimeConfig.ts`

删除或废弃：

- `listClientSettings()`
- `listDisplaySwitches()`
- `isDisplaySwitchEnabled()`
- `getClientSettingNumber()`
- `QUERY_KEYS.clientSettings`
- `QUERY_KEYS.displaySwitches`

## 8.2 消费点替换

| 当前消费点 | 新消费方式 |
|---|---|
| `useDefaultPageSize` | `runtimeConfig.ui.defaultPageSize` |
| `useAppWatermark` | `runtimeConfig.ui.watermark` |
| `useModulePageConfig` | `runtimeConfig.features.weightOnly*` + `runtimeConfig.ui.showSnowflakeId` |
| `useBusinessGridStatementActions` | `runtimeConfig.business.statement` |
| `use-module-editor-workspace` 默认税率 | `runtimeConfig.business.defaultTaxRate` |
| 业务编号预分配提示 | `runtimeConfig.business.businessNo.useSnowflakeId` |

## 8.3 类型约束

前端禁止继续通过裸字符串查找配置：

```ts
rows.find((item) => item.settingCode === 'SYS_DEFAULT_TAX_RATE')
```

必须改为：

```ts
runtimeConfig.business.defaultTaxRate
```

# 9. 数据迁移计划

所有数据库变更必须通过新增 Flyway 脚本完成。

建议步骤：

1. 新增 Unleash 部署配置，不立即删除 ERP DB 中的旧开关行。
2. 新增 Flyway 脚本标记已迁入 Unleash 的 settingCode 为废弃或从通用设置页面隐藏。
3. 保留业务配置行，后端通过强类型服务读取。
4. 前端切到 `/api/runtime-config` 后，删除旧接口和旧前端 API。
5. 在确认无旧代码引用后，再新增清理 Flyway 脚本删除废弃开关行。

破坏性清理必须分阶段执行，避免应用版本回滚时无法读取必要配置。

# 10. 实施顺序

## PR1：运行时配置类型与后端聚合接口

- 新增 `RuntimeConfigResponse` 及子 DTO。
- 新增 `RuntimeConfigController` 和 `RuntimeConfigService`。
- 先从现有 ERP DB settingCode 聚合出强类型响应。
- 添加后端单元测试，覆盖默认值、异常值、缺失值。

## PR2：前端切换到 `useRuntimeConfig`

- 新增 `runtime-config.ts` API 与 React Query hook。
- 替换默认分页、水印、默认税率、对账规则、重量视图、雪花 ID 消费点。
- 删除前端 `clientSettings` 直接消费。
- 添加前端单元测试。

## PR3：接入 OpenFeature + Unleash

- 新增 Unleash 服务部署配置。
- 后端新增 OpenFeature provider 配置。
- 将 feature 类开关迁入 Unleash。
- `RuntimeConfigService` 从 `FeatureFlagService` 读取 feature 结果。
- 添加 Unleash 不可用时 fallback 测试。

## PR4：删除旧接口与旧前端 API

- 删除 `GET /api/general-settings/client-setting`。
- 删除 `listClientSettings()`、`listDisplaySwitches()` 等旧 API。
- 删除旧 query key。
- 更新相关测试。

## PR5：数据库清理

- 新增 Flyway 脚本隐藏或删除已迁入 Unleash 的旧开关行。
- 保留业务配置行。
- 更新通用设置页面分组，避免展示已由 Unleash 管理的开关。

# 11. 风险与验证

| ID | 风险 | 缓解 | 验证 |
|---|---|---|---|
| R1 | 配置语义迁移错误 | 强类型 DTO + 单项映射表 | 后端单测逐项断言 |
| R2 | Unleash 不可用导致前端行为异常 | OpenFeature fallback | 模拟 provider 异常，验证 fallback |
| R3 | 前端仍残留 settingCode 字符串访问 | 删除旧 API + `rg` 检查 | `rg "clientSettings|listClientSettings|settingCode.*SYS_" aries/src` |
| R4 | 业务参数误迁到 Feature Flag | 分类表约束 | Code review 阻断 |
| R5 | runtime config 过期 | 保存设置后清缓存 | 集成测试：保存后重新读取 |
| R6 | 回滚风险 | 分阶段 Flyway，业务配置先保留 | 回滚演练 |

# 12. 验收标准

- [ ] 前端无 `GET /api/general-settings/client-setting` 调用。
- [ ] 前端无 `QUERY_KEYS.clientSettings` / `QUERY_KEYS.displaySwitches`。
- [ ] 前端业务代码不再按 `settingCode` 字符串读取运行时配置。
- [ ] 后端存在 `GET /api/runtime-config`，返回强类型结构。
- [ ] 功能开关类配置通过 `FeatureFlagService` 读取。
- [ ] 业务参数仍由 ERP DB 管理，并保留后台权限与操作日志。
- [ ] Unleash 不可用时系统使用明确 fallback，不阻断登录和工作台加载。
- [ ] 新增/调整测试全部通过。

# 13. 参考资料

- OpenFeature: https://openfeature.dev/
- Unleash: https://docs.getunleash.io/
- Spring Cloud Config: https://spring.io/projects/spring-cloud-config
- Nacos: https://nacos.io/
