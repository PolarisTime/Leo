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
