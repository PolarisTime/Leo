# 项目采购范围规则表（维度化）设计草案

> 状态：**草案，待评审**。本文件只描述目标形态与迁移路径，不含实现承诺；
> 当前已落地的是「最小可用版」（见第 1 节），本草案描述它的收敛方向。

## 1. 现状（已落地）

| 位置 | 内容 |
| --- | --- |
| `md_project.allowed_brands` / `allowed_materials` | 逗号分隔的允许清单；空 = 不限制（V176） |
| `md_project.allowed_brand_severity` / `allowed_material_severity` | `OFF` / `WARN` / `BLOCK`，空 = 清单非空时按 `WARN`（V178） |
| 写路径硬闸 | `SalesOrderProjectScopeGuard`：销售订单创建 / 修改 / 交付核定三条路径，`BLOCK` 档返回 422 |
| 导入体验层 | `validateParentImport`（`BLOCK` 拦住导入）+ `warnParentImport`（`WARN` 导入后提示） |
| 跨模块端口 | `master.api.ProjectProcurementScopeQuery`（快照 + 违规判据纯函数） |

已经具备的能力：**同一份规则、力度可配**（提醒 / 阻断 / 关闭），前后端共用同一判据。
缺口：只能表达「品牌」「材质」两个维度、没有有效期与来源、没有层级继承、没有例外审批留痕。

## 2. 目标模型

```sql
CREATE TABLE public.md_project_procurement_scope (
    id            bigint       PRIMARY KEY,
    project_id    bigint       NOT NULL REFERENCES public.md_project(id),
    dimension     varchar(32)  NOT NULL,   -- BRAND | MATERIAL | CATEGORY | SPEC | LENGTH
                                           -- | SUPPLIER | WAREHOUSE | SETTLEMENT_COMPANY | UNIT
    operator      varchar(16)  NOT NULL,   -- IN(白名单) | NOT_IN(黑名单)
    values_text   text         NOT NULL,   -- 逗号分隔; 沿用既有 products/designated_brands 约定
    enforcement   varchar(16)  NOT NULL,   -- OFF | WARN | BLOCK
    effective_from date,
    effective_to   date,
    source         varchar(32),            -- CONTRACT(合同附件) | OWNER(业主批复) | MANUAL
    source_ref     varchar(128),           -- 合同号 / 批复文号, 用于追溯
    enabled       boolean      NOT NULL DEFAULT true,
    sort_order    integer      NOT NULL DEFAULT 0,
    remark        varchar(255),
    -- 审计列: created_by/created_at/updated_by/updated_at/deleted_flag
    CONSTRAINT chk_scope_enforcement CHECK (enforcement IN ('OFF','WARN','BLOCK')),
    CONSTRAINT chk_scope_operator CHECK (operator IN ('IN','NOT_IN')),
    CONSTRAINT chk_scope_period CHECK (effective_to IS NULL OR effective_from IS NULL
                                       OR effective_to >= effective_from)
);
CREATE INDEX idx_project_scope_project_dimension
    ON public.md_project_procurement_scope (project_id, dimension) WHERE deleted_flag = false;
```

### 语义约定（必须写进契约并在 UI 明示）

| 情形 | 含义 |
| --- | --- |
| `operator = IN` 且清单为空 | **不限制**（与现状一致，避免「空清单 = 全部禁止」这种灾难性误读） |
| `operator = NOT_IN` 且清单为空 | 同样不限制（有值才表示禁止） |
| 有效期为空 | 长期有效 |
| 记录 `enabled = false` | 停用但不删除（历史可复现，不做破坏性删除） |
| 同一维度多条 `IN` 规则 | 取并集；`NOT_IN` 优先于 `IN`（黑名单优先级更高） |
| 主数据下架 | 清单里已下架的品牌/材质保留展示，不因主数据变化而被静默清空 |

### API（沿用 V2 RESTful 约定）

- `GET /api/v2/projects/{id}/procurement-scopes` — 列表（含停用记录需显式参数）
- `PUT /api/v2/projects/{id}/procurement-scopes` — 整体替换（与 `price-rules` 现有做法一致，幂等）
- 校验：`dimension`/`operator`/`enforcement` 走白名单枚举；`values` 逐项长度 ≤64、单条 ≤100 项；
  同一项目同一维度不允许重复规则。
- 响应中的 ID 一律十进制字符串（雪花 ID 精度契约）。

## 3. 校验的统一入口

```java
// 建议形态：把判据收敛成一个服务，返回结构化结果而不是抛异常
public record ScopeViolation(String dimension, String value, String enforcement) {}
public record ScopeCheckResult(boolean blocked, List<ScopeViolation> warnings,
                               List<ScopeViolation> violations, Long scopeVersion) {}

public interface ProjectProcurementScopeChecker {
    ScopeCheckResult check(Long projectId, ItemFacts facts);        // 单行
    ScopeCheckResult checkAll(Long projectId, List<ItemFacts> facts); // 整单（聚合去重）
}
```

调用点与强度：

| 环节 | 调用方 | 强度 |
| --- | --- | --- |
| 录单候选 | 各 `getXxxOptions` 解析器 | 不合格货源不进候选（预防） |
| 导入父单 | `validateParentImport`（BLOCK）/ `warnParentImport`（WARN） | 前端体验层 |
| 保存 | `SalesOrderProjectScopeGuard` → 未来统一 checker | **服务端硬闸（422）** |
| 审核 | 状态流转前 | 硬闸 |
| 采购入库审核 | 同一 checker（收货才是既成事实，SAP 明确货源清单拦不住收货自动建单） | 硬闸 |
| 事后 | 「超范围采购明细」+「未维护范围的项目」两张报表 | 稽核 |

`scopeVersion` 用于单据冻结当时规则版本（见 4.2）。

## 4. 与现状的差异（需要评审确认的三点）

### 4.1 数据迁移

- 一次性把 `allowed_brands` / `allowed_materials` 转成 `dimension = BRAND` / `MATERIAL` 的两条 `IN` 规则，`enforcement` 取对应 severity（空 → `WARN`）。
- 保留旧列一个发布周期（只读回退用），确认无误后再由后续迁移删除；**不做双向写**，避免两处真值。

### 4.2 版本化与历史可复现

- 单据保存时把 `scopeVersion`（规则集合的哈希或自增版本号）快照到单据上。
- 打印 / 导出 / 复核按「单据上的版本」而非「当前规则」判定，否则改规则会让历史单据看起来违规。

### 4.3 例外通道（合规必需）

- `BLOCK` 命中后可发起「例外申请」，审批通过才放行，并写入操作日志；
- 所有对外文案保留「**或同等档次**」表述：限定特定品牌在招标投标法规下属限制竞争行为
  （《招标投标法实施条例》第三十二条），系统提示不能把项目约定写成没有出路的绝对禁止。

## 5. 分阶段实施建议

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| P0（已完成） | 品牌 / 材质 + 力度可配 + 保存硬闸 + 导入分流 | 本文档第 1 节 |
| P1 | 抽 `ProjectProcurementScopeChecker`，把「品牌 / 材质」迁进规则表；新增 **类别 / 规格 / 长度** 维度 | 迁移脚本 + 双跑对账（旧列与新表判定结果一致） |
| P2 | 有效期 + 来源（合同号 / 批复文号）+ 停用 | 历史单据按 `scopeVersion` 复核通过 |
| P3 | 层级继承（客户级 / 结算主体级默认，项目级覆盖） | 覆盖优先级有单测矩阵 |
| P4 | 例外申请审批 + 两张稽核报表 | 审批留痕可查；报表可导出 |

## 6. 明确不做（防止范围失控）

- 不引入通用规则引擎（Drools / 表达式语言）：维度是有限集合，配置表 + 枚举校验已足够；
  规则引擎会让「谁改了哪条规则」变得难追。
- 不让规则影响历史单据的既有状态：只做校验，不回写、不追溯阻断。
- 不在客户端做硬闸判定：前端只是体验层，阻断以服务端为准。

## 7. 适用范围与业务口径（2026-10-11 确认）

**只约束销售订单**，覆盖三条入口：

| 入口 | 行为 |
| --- | --- |
| 来源单据导入（采购订单 → 销售订单明细） | `WARN` 导入后提示；`BLOCK` **导入前拦截**（不让越界行落进草稿） |
| 手工加行 / 逐行改品牌或材质 | 即时 `message.warning` 提示；`BLOCK` 文案注明「保存时会被拦截」，**不打断编辑** |
| 服务端保存（新建 / 更新 / 交付核定） | 最终兜底：仅 `BLOCK` 拒绝（422），`WARN` / `OFF` 不拦 |

**采购订单与采购入库不做项目范围约束**：两者主表没有项目字段（明细的品牌 / 材质是业务字段，
但单据本身不按项目归属），业务确认「采购不应该添加项目限制」，因此既不为它们新增项目字段，
也不接这套校验。若将来采购需要按项目管控，应作为独立需求评估（新增可空项目字段 + 独立的校验策略），
不要在销售侧规则上打补丁。
