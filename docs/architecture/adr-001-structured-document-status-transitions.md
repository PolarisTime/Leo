# ADR-001：使用结构化值对象管理单据状态迁移

## 状态

已接受，2026-07-26。

## 背景

Leo 的采购、销售、物流、对账和资金单据均在单个 Spring 事务内完成状态变更。聚合服务在变更前持有行锁或乐观锁，并执行来源单据校验、结算约束、余额同步和审计事件等业务副作用。当前没有跨服务编排、长事务、定时器或人工任务恢复需求。

原实现以 `Set<String>` 保存 `起始状态 + "->" + 目标状态`，并由通用 CRUD 基类通过反射查找 `getStatus`、`setStatus`。该实现可以运行，但迁移边缺少结构约束，状态实体是否接入状态保护也无法在编译期验证。

现有 API 和数据库中的中文状态值、合法迁移边以及专用完成命令已经稳定，本次决策不得改变这些业务契约。

## 方案比较

| 方案 | 收益 | 成本与适用边界 | 结论 |
| --- | --- | --- | --- |
| 不可变 `StatusTransition` 值对象 + 聚合服务 | 用 `from` / `to` 字段约束迁移边结构；无新增运行时依赖；保留现有事务、锁和副作用位置 | 状态值仍是字符串；不提供流程图、持久化流程实例、定时器和任务队列 | 采用 |
| Spring Statemachine | Spring 风格的状态机抽象 | 4.0.x 已是最后一条 OSS 版本线，官方仓库于 2026-07 归档；引入后仍需将现有锁、校验和副作用接回聚合服务 | 不采用 |
| Stateless4j | 轻量 Java 状态机 DSL | 最新正式版本停留在 2019 年，最后代码提交停留在 2023 年；对当前简单迁移图的收益不足以抵消依赖和适配成本 | 不采用 |
| Flowable 8 | 可嵌入 Spring 的 BPMN 引擎，支持持久化流程实例、人工任务和定时器 | 引入流程表、流程版本以及单据状态与流程状态的一致性治理 | 当前不采用；出现人工审批时优先验证 |
| Camunda 8 | 跨系统 BPMN、Job Worker 和集中式流程运维能力成熟 | 需要独立编排集群、Worker、幂等和最终一致性改造，自托管生产许可也需单独评估 | 当前不采用 |
| Temporal | 代码式耐久执行、持久化重试和故障恢复能力成熟 | 需要独立服务、Worker、确定性回放及 Activity 幂等；与当前单库短事务模型不匹配 | 当前不采用 |

## 决策

1. 使用不可变 `StatusTransition(from, to)` 表达合法迁移边，所有迁移集合使用 `Set<StatusTransition>`，在编译期固定迁移边的结构；状态字符串本身仍由常量和运行时校验约束，并继续作为 API 和数据库契约。
2. 有迁移规则的实体实现 `StatusAwareEntity`，其服务继承 `AbstractStatusCrudService`，由泛型边界在编译期保证可读写状态。
3. 普通 CRUD 使用显式无状态 guard，不再反射探测实体方法；调用其通用状态接口仍返回“当前模块不支持状态变更”。
4. 状态迁移表只负责判断边是否合法。触发器、来源单据锁、业务前置校验、结算同步、审计事件和其他副作用继续由各聚合服务负责。
5. 自动完成、交付核定等专用命令保留独立语义，不强行改造成统一的通用状态接口；不引入状态枚举数据库迁移或第三方流程引擎。

## 接受的权衡

- 迁移图仍以代码维护，没有可视化设计器；当前迁移边数量少，代码审查成本可控。
- 聚合服务继续承载状态触发后的业务编排；这是为了保留事务和锁边界，不将领域副作用隐藏在通用状态框架中。
- `String` 状态值仍可能被外部调用方拼写错误，但现有请求校验与合法迁移集合会拒绝未知状态；为保持兼容性暂不改为数据库枚举。

## 重评条件

出现以下任一条件时，重新评估 BPMN/持久化工作流或专用编排组件：

- 一个流程需要跨越多个数据库事务或服务，并要求失败补偿和断点恢复；
- 需要数分钟以上等待、定时器、超时升级或持久化重试；
- 需要人工任务、候选人分配、会签、动态分支或流程版本迁移；
- 业务人员需要可视化建模、运行中实例追踪或合规流程报表；
- 单据状态副作用无法继续在一个聚合事务内保持一致性。

出现人工审批和定时升级时优先验证嵌入式 Flowable；出现跨系统 BPMN 和集中运维需求时评估 Camunda；出现代码式跨服务长流程和持久化重试需求时评估 Temporal。

## 官方资料

- [Spring 关于 Statemachine OSS 版本线的公告](https://spring.io/blog/2025/04/21/spring-cloud-data-flow-commercial/)
- [Spring Statemachine 官方 GitHub 仓库](https://github.com/spring-attic/spring-statemachine)（截至决策日标记为 archived）
- [Stateless4j 官方 GitHub 仓库](https://github.com/stateless4j/stateless4j)
- [Camunda Processes 概念文档](https://docs.camunda.io/docs/components/concepts/processes/)
- [Flowable BPMN Getting Started](https://www.flowable.com/open-source/docs/bpmn/ch02-GettingStarted/)
- [Temporal Workflow Execution 文档](https://docs.temporal.io/workflow-execution)
