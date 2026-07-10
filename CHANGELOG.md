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
