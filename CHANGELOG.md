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
