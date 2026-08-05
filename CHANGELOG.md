# [9.9.0](https://github.com/PolarisTime/Leo/compare/v9.8.0...v9.9.0) (2026-08-05)


### Bug Fixes

* **print:** 打印模板文件同步的状态写入收敛到 ApplyService ([10e41f5](https://github.com/PolarisTime/Leo/commit/10e41f5a403a5e73a1b036779d245f9c88dbfae4))
* **sales-outbound:** 兼容精简保存 payload，材料类字段按来源明细重载 ([0f9d91c](https://github.com/PolarisTime/Leo/commit/0f9d91c2d3bab45ef61fed527226bd2afbd89b83))


### Features

* **print:** 打印模板文件跟随部署自动同步 ([d99e0ff](https://github.com/PolarisTime/Leo/commit/d99e0ff7acceeda4020742f639aafd6b43b4da46))

# [9.8.0](https://github.com/PolarisTime/Leo/compare/v9.7.2...v9.8.0) (2026-08-05)


### Features

* **print:** 物流对账单来源物流单分组头显示单据日期 ([8083474](https://github.com/PolarisTime/Leo/commit/808347456a378d090939433fc185170252550f8a))

## [9.7.2](https://github.com/PolarisTime/Leo/compare/v9.7.1...v9.7.2) (2026-08-05)


### Bug Fixes

* **print:** 物流对账单打印模板优化 ([2581337](https://github.com/PolarisTime/Leo/commit/25813372fc2a1da47c8e5f5deeaaf3c00346ff52))

## [9.7.1](https://github.com/PolarisTime/Leo/compare/v9.7.0...v9.7.1) (2026-08-05)


### Bug Fixes

* **print:** 通用打印模板适用于所有结算主体 ([e3ac9e3](https://github.com/PolarisTime/Leo/commit/e3ac9e368eb28d0b87edd4df8a2dca699187c38b))

# [9.7.0](https://github.com/PolarisTime/Leo/compare/v9.6.0...v9.7.0) (2026-08-05)


### Features

* **master:** 主数据改名级联同步引用表快照 ([6797bf5](https://github.com/PolarisTime/Leo/commit/6797bf55de9f20ad4029c2c2d55b7597c62fe75a))

# [9.6.0](https://github.com/PolarisTime/Leo/compare/v9.5.0...v9.6.0) (2026-08-05)


### Bug Fixes

* **statement:** 对账/入库来源主体一致性按 ID 判断 ([dc2f0d2](https://github.com/PolarisTime/Leo/commit/dc2f0d260a1d09a77243277fb3f4daec76a44c16))


### Features

* **company:** 结算主体改名级联同步引用表快照 ([a63830a](https://github.com/PolarisTime/Leo/commit/a63830ad27b229d6c760e74c04e3fed117fdc28a))

# [9.5.0](https://github.com/PolarisTime/Leo/compare/v9.4.0...v9.5.0) (2026-08-05)


### Features

* **print:** 登记生产物流对账单 PDF 打印模板 ([8d55ae7](https://github.com/PolarisTime/Leo/commit/8d55ae777e72603953718d1f04f8ab6a06e482df))

# [9.4.0](https://github.com/PolarisTime/Leo/compare/v9.3.0...v9.4.0) (2026-08-05)


### Features

* **print:** 物流对账单 PDF 分组打印 ([e92ca91](https://github.com/PolarisTime/Leo/commit/e92ca913cd2c18c9137e9d994b0b764c2f3312ae))

# [9.3.0](https://github.com/PolarisTime/Leo/compare/v9.2.2...v9.3.0) (2026-08-04)


### Bug Fixes

* **freight-statement:** 修复新建对账单结算校验 ([657fd88](https://github.com/PolarisTime/Leo/commit/657fd880bb7763b3fb154a053749f9421981fb38))
* **observability:** 补全未采样请求的追踪标识 ([825a6fb](https://github.com/PolarisTime/Leo/commit/825a6fbeb93286c1db6d4d9278ade18a77c4a18c))
* **print:** 打印字段补充 SQL 增加只读断言 ([8777b5d](https://github.com/PolarisTime/Leo/commit/8777b5d27de0cc1c2995b0a4876afdc46bc28019))
* **statement:** 修复新建客户对账单误执行结算校验 ([93da71f](https://github.com/PolarisTime/Leo/commit/93da71fc45092b291763ba3381757cdff3f15abf))


### Features

* **print:** 生产库手动打印模板迁移为文件托管 ([7bf4b0a](https://github.com/PolarisTime/Leo/commit/7bf4b0a402515c0fa6be80024bcf244a8e4337eb))

## [9.2.2](https://github.com/PolarisTime/Leo/compare/v9.2.1...v9.2.2) (2026-08-03)


### Bug Fixes

* **purchase-inbound:** 放宽多仓库入库表头约束 ([7082be4](https://github.com/PolarisTime/Leo/commit/7082be4cf17857abf85f67f80207e8fed1db80ce))

## [9.2.1](https://github.com/PolarisTime/Leo/compare/v9.2.0...v9.2.1) (2026-08-03)


### Bug Fixes

* **purchase-inbound:** 允许明细使用不同仓库 ([5d6d3c0](https://github.com/PolarisTime/Leo/commit/5d6d3c0dff9d33104997170247f0437cf5fb4817))

# [9.2.0](https://github.com/PolarisTime/Leo/compare/v9.1.0...v9.2.0) (2026-08-01)


### Features

* **print:** 支持切换销售订单明细合并模式 ([2d5e124](https://github.com/PolarisTime/Leo/commit/2d5e124d32bf87224224fe73838bd5eaf7dbec02))

# [9.1.0](https://github.com/PolarisTime/Leo/compare/v9.0.1...v9.1.0) (2026-08-01)


### Features

* **print:** 合并A4打印同款明细 ([78bfb99](https://github.com/PolarisTime/Leo/commit/78bfb9905e11c431215dd0292fdddc3fbcbae4aa))

## [9.0.1](https://github.com/PolarisTime/Leo/compare/v9.0.0...v9.0.1) (2026-08-01)


### Bug Fixes

* **api:** 修复标量响应导致的编码解析失败 ([b664a1a](https://github.com/PolarisTime/Leo/commit/b664a1a6845f38e5b97dd08309402aa3e945275d))

# [9.0.0](https://github.com/PolarisTime/Leo/compare/v8.4.0...v9.0.0) (2026-08-01)


* feat(api)!: 移除 V1 并统一 V2 契约 ([2834765](https://github.com/PolarisTime/Leo/commit/2834765840ae9d520a223adf896c2e064d4712c1))


### Bug Fixes

* **ci:** 更新 ArchUnit V2 冻结基线 ([3ca023d](https://github.com/PolarisTime/Leo/commit/3ca023d356b7444935f8cfc7406946412011020c))


### BREAKING CHANGES

* 移除所有未版本化 V1 API，客户端必须改用 /api/v2.0。

# [8.4.0](https://github.com/PolarisTime/Leo/compare/v8.3.5...v8.4.0) (2026-07-30)


### Features

* **purchase:** 新增采购订单提货清单预览接口 ([230cebc](https://github.com/PolarisTime/Leo/commit/230cebc1756de97d94a3b456af763d69fbf5b70f))

## [8.3.5](https://github.com/PolarisTime/Leo/compare/v8.3.4...v8.3.5) (2026-07-30)


### Bug Fixes

* **print:** 统一 PDF 文件名日期格式 ([781267d](https://github.com/PolarisTime/Leo/commit/781267d1930eeea6ee96a1214185de3dc70d80fd))

## [8.3.4](https://github.com/PolarisTime/Leo/compare/v8.3.3...v8.3.4) (2026-07-29)


### Bug Fixes

* **print:** 统一使用苹方字体生成PDF ([74ee082](https://github.com/PolarisTime/Leo/commit/74ee082debaf93734227baf3de2d5c19fec440f4))

## [8.3.3](https://github.com/PolarisTime/Leo/compare/v8.3.2...v8.3.3) (2026-07-27)


### Bug Fixes

* **master-data:** 修复客户项目关联查询 ([7368739](https://github.com/PolarisTime/Leo/commit/736873901a5637d1682ac3c2b25ae3e69a9988a7))

## [8.3.2](https://github.com/PolarisTime/Leo/compare/v8.3.1...v8.3.2) (2026-07-27)


### Bug Fixes

* **purchase:** 恢复采购入库全量审核校验 ([8ef57ba](https://github.com/PolarisTime/Leo/commit/8ef57baaf08cb87a567932c34f6b4b46a897d94a))

## [8.3.1](https://github.com/PolarisTime/Leo/compare/v8.3.0...v8.3.1) (2026-07-26)


### Bug Fixes

* **api:** 修复筛选参数冲突与术语数据 ([efdc0e2](https://github.com/PolarisTime/Leo/commit/efdc0e2c275e1ad0fabfe640e98044d274753157))

# [8.3.0](https://github.com/PolarisTime/Leo/compare/v8.2.3...v8.3.0) (2026-07-26)


### Features

* **api:** 增加物流对账汇总并收敛模块边界 ([6aa8c9d](https://github.com/PolarisTime/Leo/commit/6aa8c9de3c725ca49bffecbaaf7a97776645e743))
* **statement:** 恢复对账能力并收敛后端模块边界 ([006d5eb](https://github.com/PolarisTime/Leo/commit/006d5eb8ef8e05bde962e845e845f601a7f41c5f))

## [8.2.3](https://github.com/PolarisTime/Leo/compare/v8.2.2...v8.2.3) (2026-07-25)


### Bug Fixes

* **print:** 支持A4项目名称两行自动缩放 ([0006eb0](https://github.com/PolarisTime/Leo/commit/0006eb0c879bce5530fa24c14d06292f00a72394))

## [8.2.2](https://github.com/PolarisTime/Leo/compare/v8.2.1...v8.2.2) (2026-07-25)


### Bug Fixes

* **master:** 修复客户项目选项 ID 精度丢失 ([d852920](https://github.com/PolarisTime/Leo/commit/d852920492cdb44abf3dfe0bc51d85c355492c85))

## [8.2.1](https://github.com/PolarisTime/Leo/compare/v8.2.0...v8.2.1) (2026-07-25)


### Bug Fixes

* **logistics:** 允许保存未建档车辆车牌 ([ccdce9a](https://github.com/PolarisTime/Leo/commit/ccdce9a0a56b9ac53092845defb0c61dc808d66b))

# [8.2.0](https://github.com/PolarisTime/Leo/compare/v8.1.0...v8.2.0) (2026-07-22)


### Features

* **purchase:** 新增采购仓库历史推荐接口 ([bb4560a](https://github.com/PolarisTime/Leo/commit/bb4560ac6a853224ee293caf868a90e881dcc49d))

# [8.1.0](https://github.com/PolarisTime/Leo/compare/v8.0.6...v8.1.0) (2026-07-22)


### Features

* **print:** 统一打印导出文件命名 ([3623214](https://github.com/PolarisTime/Leo/commit/362321498dfd53340c4defeaa12c10462e8eb055))

## [8.0.6](https://github.com/PolarisTime/Leo/compare/v8.0.5...v8.0.6) (2026-07-22)


### Bug Fixes

* **workflow:** 增加单据保存并审核原子接口 ([9c93c52](https://github.com/PolarisTime/Leo/commit/9c93c527d67ec9ec229d4cc3cb8dd05f19a424ab))

## [8.0.5](https://github.com/PolarisTime/Leo/compare/v8.0.4...v8.0.5) (2026-07-22)


### Bug Fixes

* **print:** 修复 A4 PDF 车号关联 ([1630519](https://github.com/PolarisTime/Leo/commit/16305197eb86b217fd5b3231798967969ad78088))

## [8.0.4](https://github.com/PolarisTime/Leo/compare/v8.0.3...v8.0.4) (2026-07-21)


### Bug Fixes

* **purchase:** 移除采购入库混合结算阻断 ([b88d2dd](https://github.com/PolarisTime/Leo/commit/b88d2dd2b5e640e54b3b9d4d9d567f8c666f2cc1))

## [8.0.3](https://github.com/PolarisTime/Leo/compare/v8.0.2...v8.0.3) (2026-07-20)


### Bug Fixes

* **document:** 默认隐藏已删除单据 ([424aa80](https://github.com/PolarisTime/Leo/commit/424aa80f8c6c656a7c5dbbb6bd326994d339cf60))

## [8.0.2](https://github.com/PolarisTime/Leo/compare/v8.0.1...v8.0.2) (2026-07-20)


### Bug Fixes

* **backend:** 修复订单链路并收紧部署配置 ([71f87f4](https://github.com/PolarisTime/Leo/commit/71f87f4086d5b4254c4d07653d9b649ff91986ae))

## [8.0.1](https://github.com/PolarisTime/Leo/compare/v8.0.0...v8.0.1) (2026-07-20)


### Bug Fixes

* **sales:** 支持多仓库销售出库保存 ([e1bd30d](https://github.com/PolarisTime/Leo/commit/e1bd30d41b8270d7b4c6e88989d376ff58715cb4))

# [8.0.0](https://github.com/PolarisTime/Leo/compare/v7.0.0...v8.0.0) (2026-07-20)


* refactor(purchase)!: 收敛采购入库候选接口 ([084e5a5](https://github.com/PolarisTime/Leo/commit/084e5a5d720dfe3bbd78110b85f10bf35e7c1cd3))


### BREAKING CHANGES

* 采购订单候选接口由 /purchase-orders/import-candidates 改为 /purchase-orders/inbound-import-candidates，并移除 usage 参数及 /purchase-orders/prepayment-candidates。

# [7.0.0](https://github.com/PolarisTime/Leo/compare/v6.0.0...v7.0.0) (2026-07-19)


* feat(master)!: 基础资料编码统一使用雪花ID ([1fd59ff](https://github.com/PolarisTime/Leo/commit/1fd59ffa81b95be4e09ee7ead4f5dc4a558efdd1))
* refactor(master)!: 统一基础资料编码并移除部门管理 ([8ecf942](https://github.com/PolarisTime/Leo/commit/8ecf9421c6a864620a18e7b1169aac0640279f75))


### Bug Fixes

* **purchase:** 恢复采购入库新建保存接口 ([ea99f74](https://github.com/PolarisTime/Leo/commit/ea99f744424dfe63968f4ece18be51d2648aba18))


### Features

* **deploy:** 支持后端应用与依赖分离部署 ([53239d4](https://github.com/PolarisTime/Leo/commit/53239d477777c0c395b778b371877d3ab8c1ba9d))


### BREAKING CHANGES

* 基础资料创建必须提交后端签发且未过期的编码；部门管理 API、用户部门字段及数据库结构已移除。
* 基础资料新建和导入不再接受手工编码。

# [6.0.0](https://github.com/PolarisTime/Leo/compare/v5.1.0...v6.0.0) (2026-07-18)


* refactor(purchase)!: 移除逐件重量与入库拆分流程 ([5d28973](https://github.com/PolarisTime/Leo/commit/5d28973aaaf147898df27f4a4e7e036bce054b7e))


### BREAKING CHANGES

* 移除逐件重量 API、数据表和采购入库拆分批次接口。

# [5.1.0](https://github.com/PolarisTime/Leo/compare/v5.0.1...v5.1.0) (2026-07-18)


### Features

* **finance:** 新增财务概览并简化收付款 ([f633931](https://github.com/PolarisTime/Leo/commit/f633931ac9dce95fd094aa719d6f49b4d6da8ff2))

## [5.0.1](https://github.com/PolarisTime/Leo/compare/v5.0.0...v5.0.1) (2026-07-18)


### Bug Fixes

* **database:** 恢复已执行的 V76 迁移脚本 ([16c7ec9](https://github.com/PolarisTime/Leo/commit/16c7ec9b664e0604766a95948d4fced69048cb12))

# [5.0.0](https://github.com/PolarisTime/Leo/compare/v4.0.1...v5.0.0) (2026-07-18)


* refactor(api)!: 移除页面及附件水印能力 ([a42007a](https://github.com/PolarisTime/Leo/commit/a42007a816e684b842d7716c1438c3b865cc8a02))
* refactor(batch)!: 固定启用批号管理 ([2dfbdd7](https://github.com/PolarisTime/Leo/commit/2dfbdd75a9af59960eea8fc63421478c10498056))
* refactor(report)!: 移除报表模块 ([63c6a06](https://github.com/PolarisTime/Leo/commit/63c6a065c54cd303b1dbac140384a6915187189b))
* refactor(security)!: 全量切换纯 RBAC 并移除安全设置 ([dce6702](https://github.com/PolarisTime/Leo/commit/dce67021e0acf1bf6b7b4a77fa45e1dee9dc1413))
* refactor(security)!: 移除 RBAC 与 MCP 授权体系 ([49c71e5](https://github.com/PolarisTime/Leo/commit/49c71e5920d95606f88f5a8c9fcbb1a927f343da))
* refactor(security)!: 移除通用数据范围权限模型 ([569258b](https://github.com/PolarisTime/Leo/commit/569258b9f941c91545085e1952af0e4477fe893a))
* refactor(settings)!: 移除动态设置与公司初始化引导 ([7f65fa0](https://github.com/PolarisTime/Leo/commit/7f65fa03cac1f0c262135ba300391132b25fa2a4))
* refactor(settings)!: 移除默认税率 ([9a993e5](https://github.com/PolarisTime/Leo/commit/9a993e577e4f96a4c9d4471ee6c30a868b450111))
* refactor(system)!: 移除数据库备份与监控能力 ([075dc8b](https://github.com/PolarisTime/Leo/commit/075dc8b5eb0fd22be41181f0e45adf326bf6d74f))


### Bug Fixes

* **access-control:** 统一权限管理接口与授权边界 ([e74de70](https://github.com/PolarisTime/Leo/commit/e74de70899c479306f66611951b4e0624d58840b))
* **material:** 跳过一致商品并继续导入 ([867e15d](https://github.com/PolarisTime/Leo/commit/867e15db223650f19a474d8731471795f8f14252))


### Features

* **logistics:** 重构物流单与物流对账状态机 ([297d09f](https://github.com/PolarisTime/Leo/commit/297d09f09ab8666f23d07a72ca3ea5ad55ac98eb))
* **observability:** 完成审计与日志分区治理 ([7966b7a](https://github.com/PolarisTime/Leo/commit/7966b7aac96463507da55666757273f5ab754ad8))
* **observability:** 重构业务审计与日志基础设施 ([9066f55](https://github.com/PolarisTime/Leo/commit/9066f5583026e2a3224273cd438038439d1fa772))


### BREAKING CHANGES

* 删除系统设置、OSS 设置及 OOBE 公司初始化接口，旧前端和外部调用方需停止使用相关端点。
* 全量移除 jCasbin、角色权限、MCP 及安全中心，系统仅保留 JWT 身份认证。
* 商品 API 不再包含 batchNoEnabled，采购订单明细 batchNo 改为必填。
* 移除 inventory-report 与 io-report 后端接口、权限资源、菜单及打印模板。
* 公司设置、首次初始化和运行时配置不再包含 taxRate/defaultTaxRate 字段。
* 移除旧安全中心、API Key、动态编号及限流配置接口。
* 客户端运行时配置接口不再返回 ui.watermark 字段。
* 用户与角色接口不再提供 dataScope/dataScopes 字段，V84 删除 sys_role.data_scope 和 sys_user.data_scope，旧版后端无法直接回滚。
* 移除数据库管理相关后端接口、CLI 与定时备份配置。

## [4.0.1](https://github.com/PolarisTime/Leo/compare/v4.0.0...v4.0.1) (2026-07-15)


### Bug Fixes

* **system:** 修复系统参数加载并强化迁移门禁 ([24a08e3](https://github.com/PolarisTime/Leo/commit/24a08e3481710f22465f8fbc782764ecbf9145ff))

# [4.0.0](https://github.com/PolarisTime/Leo/compare/v3.1.2...v4.0.0) (2026-07-15)


* feat(finance)!: 重构财务为资金流水模式 ([f7a664f](https://github.com/PolarisTime/Leo/commit/f7a664fdfc72cde1d47d42ac894a34d27d677b33))
* feat(order-flow)!: 重构采购销售物流及财务单据流 ([3149a05](https://github.com/PolarisTime/Leo/commit/3149a05512b3f8e0a204efb6799e821a106b0123))


### Bug Fixes

* **purchase:** 修复采购逐件重量同步逻辑 ([6da9fee](https://github.com/PolarisTime/Leo/commit/6da9feeff4fb22aabed6e71601518921b4ac4cec))
* **purchase:** 完善采购全量入库及状态流转 ([5806cbc](https://github.com/PolarisTime/Leo/commit/5806cbc2fa1f4d68c0a2226bf9b4ba9cf4e6ae28))


### BREAKING CHANGES

* 删除旧采购财务、发票及资金冲销接口，原应收应付入口替换为资金流水。
* 移除采购退款单和供应商退款到账 API，采购完成、财务流及收票容量改用实际入库与通用收付款口径。

## [3.1.2](https://github.com/PolarisTime/Leo/compare/v3.1.1...v3.1.2) (2026-07-13)


### Bug Fixes

* **order:** 完善销售订单保护更新校验 ([cd576d9](https://github.com/PolarisTime/Leo/commit/cd576d9c3d9b721a8083863124729600b8d98999))

## [3.1.1](https://github.com/PolarisTime/Leo/compare/v3.1.0...v3.1.1) (2026-07-13)


### Bug Fixes

* **ci:** 补齐后端烟雾测试迁移目标 ([1b12d9b](https://github.com/PolarisTime/Leo/commit/1b12d9bff5b9a6528689a3a4b2f91e76e90892cf))
* **order:** 补齐源单下游校验与合同状态接口 ([7b9ae40](https://github.com/PolarisTime/Leo/commit/7b9ae407bc07f6bbbc54ef2261d5e8e92c9d4390))
* **order:** 避免新建单据触发下游校验 ([f75be34](https://github.com/PolarisTime/Leo/commit/f75be347801dd901a91a10bffd64ee9cf14f8a41))

# [3.1.0](https://github.com/PolarisTime/Leo/compare/v3.0.1...v3.1.0) (2026-07-13)


### Features

* **import:** 完善来源候选筛选与完成状态反审核 ([438be46](https://github.com/PolarisTime/Leo/commit/438be46601ee9889da6fa9a2f742de0b6850aac0))

## [3.0.1](https://github.com/PolarisTime/Leo/compare/v3.0.0...v3.0.1) (2026-07-13)


### Bug Fixes

* **identity:** 补充开发库身份与打印模板关联修复 ([0644e49](https://github.com/PolarisTime/Leo/commit/0644e49e31d337b33372a036a46acb445481afcf))
* **sales-order:** 允许导入完成采购订单 ([75fa42d](https://github.com/PolarisTime/Leo/commit/75fa42df7de560a96a71db95269d16b0f792919b))

# [3.0.0](https://github.com/PolarisTime/Leo/compare/v2.2.0...v3.0.0) (2026-07-13)


* feat(finance)!: 完善采购退款与结算闭环 ([7c1f0c4](https://github.com/PolarisTime/Leo/commit/7c1f0c4299aa83acb6203482d2b0a85aadd0f04f))


### Bug Fixes

* **flyway:** 补齐物流商编码检查约束 ([8f096c8](https://github.com/PolarisTime/Leo/commit/8f096c8270833c788a1c527508dad8bad77423c3))
* **purchase:** 修复采购入库审核后订单未自动完成 ([ebe813d](https://github.com/PolarisTime/Leo/commit/ebe813dc7124cd32f66b55a3c2583c538b303a81))


### Features

* **identity:** 完成全系统雪花稳定身份迁移 ([ac3ace1](https://github.com/PolarisTime/Leo/commit/ac3ace15e6a8b7d0d4878aa40230b87db533139f))
* **identity:** 完成生产稳定身份迁移发布 ([0c7225f](https://github.com/PolarisTime/Leo/commit/0c7225f2c9c56081a30a7dd9ac4e7fe1c326c2ab))
* **sales:** 新增销售订单交付核定流程 ([dd5488c](https://github.com/PolarisTime/Leo/commit/dd5488c6b9135303f591e91ebad15155c85f7b39))


### BREAKING CHANGES

* 物流单与物流对账单现在要求稳定 carrierCode；V18 迁移后旧版本服务无法继续写入缺少 carrier_code 的物流单。

# [2.2.0](https://github.com/PolarisTime/Leo/compare/v2.1.2...v2.2.0) (2026-07-10)


### Bug Fixes

* **cache:** 修复业务缓存巡检与失效机制 ([66fa4f0](https://github.com/PolarisTime/Leo/commit/66fa4f070b499d34bb26649349dbf21c4a61b94b))


### Features

* **api:** 物流商选项返回默认结算主体 ([fb6e364](https://github.com/PolarisTime/Leo/commit/fb6e3643be5b944da652ab812b84d2012999376c))

## [2.1.2](https://github.com/PolarisTime/Leo/compare/v2.1.1...v2.1.2) (2026-07-10)


### Bug Fixes

* **backend:** 修复安全边界与核心业务并发一致性 ([ec18ee8](https://github.com/PolarisTime/Leo/commit/ec18ee8f6d89438f7f6cb551d3118cf7fc9c7ba3))

## [2.1.1](https://github.com/PolarisTime/Leo/compare/v2.1.0...v2.1.1) (2026-07-10)


### Bug Fixes

* **api:** 保留软删除业务状态 ([1c84a57](https://github.com/PolarisTime/Leo/commit/1c84a57e8a30aca4de2ab029f689f363eb439016))
* **print:** 修复单据打印项目地址补齐 ([346f14f](https://github.com/PolarisTime/Leo/commit/346f14f57e1c9b778e81f69fc7a8b2e9cfb0cc5c))
* **print:** 修复销售单PDF项目地址与字号缩放 ([c5f6b7a](https://github.com/PolarisTime/Leo/commit/c5f6b7a6fab68918cd2241770e5f67e9078bed58))
* **print:** 调整A4打印模板项目名称换行 ([9922860](https://github.com/PolarisTime/Leo/commit/992286063134e6d671dd120cfcb0c616e45d3647))
* **purchase:** 修复采购逐件重量尾差分配 ([c8793d8](https://github.com/PolarisTime/Leo/commit/c8793d86a6911b28ab18ff9a5e8f222573e105f1))

# [2.1.0](https://github.com/PolarisTime/Leo/compare/v2.0.2...v2.1.0) (2026-07-09)


### Bug Fixes

* **attachment:** 兼容删除竞态下附件计数 ([943b595](https://github.com/PolarisTime/Leo/commit/943b595adf531e4f769b090a8cd512527d2f7441))
* **backend:** 统一软删除状态语义 ([08e319f](https://github.com/PolarisTime/Leo/commit/08e319fc56e04264810eca1fda790b650ea8e8a9))
* **cache:** 修复 BigDecimal 缓存序列化失败 ([5cb336c](https://github.com/PolarisTime/Leo/commit/5cb336c1acf8c61b3ec31e70c5c4df932e3f65c7))
* **material:** 增加商品资料重复校验 ([e5a6854](https://github.com/PolarisTime/Leo/commit/e5a68542eb8943ce17596a467ac07b68070cd017))
* **purchase:** 修复采购订单结束日期筛选漏单 ([76e0e6d](https://github.com/PolarisTime/Leo/commit/76e0e6d9765e9ee931feca8b0d87542350ebd978))
* **purchase:** 增加采购重量锁定校验提示 ([2989f0a](https://github.com/PolarisTime/Leo/commit/2989f0a0d14ae2a701f8ec74aa3714a05c274304))


### Features

* **document:** 支持通用单据费用与打印多明细区 ([781e610](https://github.com/PolarisTime/Leo/commit/781e610f713fc55e38cc2f7650d168e83fba83bd))
* **logistics:** 支持预出库提货与计划态重量同步 ([f074d7e](https://github.com/PolarisTime/Leo/commit/f074d7ebbf02afcce12ddeca06c37ae25c0c928b))
* **print-template:** 补齐页面级默认 PDF 模板 ([7fbfd0f](https://github.com/PolarisTime/Leo/commit/7fbfd0fe68416cff20e2f8a5bf75f4e63e1ccfe4))


### Reverts

* **document:** 回退单据费用与打印多明细区 ([a0a89fa](https://github.com/PolarisTime/Leo/commit/a0a89faa3c220d8e17e871d70787e881250fb14e))

## [2.0.2](https://github.com/PolarisTime/Leo/compare/v2.0.1...v2.0.2) (2026-07-08)


### Bug Fixes

* **backend:** 修复缓存序列化与部署发布问题 ([285f635](https://github.com/PolarisTime/Leo/commit/285f6359a5677168176387ce25c9d96e9c6aefa0))

## [2.0.1](https://github.com/PolarisTime/Leo/compare/v2.0.0...v2.0.1) (2026-07-07)


### Bug Fixes

* **attachment:** 兼容商品类别模块别名 ([a8d7308](https://github.com/PolarisTime/Leo/commit/a8d730869429a00b6215357b42c9f830acd72359))

# [2.0.0](https://github.com/PolarisTime/Leo/compare/v1.1.2...v2.0.0) (2026-07-06)


* refactor(api)!: 统一集合型接口复数命名 ([88e1a27](https://github.com/PolarisTime/Leo/commit/88e1a27132b5b900fe61eeb7f28953918a9f2c20))


### Bug Fixes

* **api:** 修复刷新后仪表盘缓存类型错误 ([ce49b0b](https://github.com/PolarisTime/Leo/commit/ce49b0b3ed7ed3859812afdb16be4948c27f47ee))
* **deploy:** 部署脚本 healthcheck 对齐生产 + 新增 systemd 守护单元 ([12053f1](https://github.com/PolarisTime/Leo/commit/12053f11cdb25a96319061bb899109046443cc96))
* **health:** 收敛 /health readiness 与 /version 放行 ([ac5bb2e](https://github.com/PolarisTime/Leo/commit/ac5bb2e9f5ab198531a3c100a34ed301134b3d47))


### Features

* **flyway:** 资源化内置打印模板 ([7bbb2af](https://github.com/PolarisTime/Leo/commit/7bbb2af8182d3369015d51236b5e58bb7920f085))


### BREAKING CHANGES

* 移除旧的单数集合型接口路径，调用方需要切换到 /options、/candidates、/grades 等新路径。
