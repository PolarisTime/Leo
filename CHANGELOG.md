# [10.10.0](https://github.com/PolarisTime/Leo/compare/v10.9.0...v10.10.0) (2026-09-16)


### Features

* **market:** 行情同步支持按时段(上午/中午/下午)选择性同步 ([301a170](https://github.com/PolarisTime/Leo/commit/301a1705601ac49797ddb845a5377aebfbd5bc50))

# [10.9.0](https://github.com/PolarisTime/Leo/compare/v10.8.0...v10.9.0) (2026-09-16)


### Bug Fixes

* **auth:** 修复账号删除会话吊销顺序并补齐撤销原因约束 ([553a7e7](https://github.com/PolarisTime/Leo/commit/553a7e711b8944d82b0df6d1b7869c442be5cda7))
* **erp:** 修复审计发现的正确性、并发、索引与性能问题 ([cb205f1](https://github.com/PolarisTime/Leo/commit/cb205f1a9dbcac6bec08111b8f30687fbf846192))
* **inventory:** 稳定来源占用加锁顺序并消除移动加权舍入残值 ([42a820e](https://github.com/PolarisTime/Leo/commit/42a820e4108733900b657a81bc75e623069c9d09))
* **sales-return:** 退货来源候选仅返回可退明细 ([33b803e](https://github.com/PolarisTime/Leo/commit/33b803e26ab46cb5058725178136263e00cc0292))
* **security:** 角色响应补充权限数与用户数 ([a43a0ea](https://github.com/PolarisTime/Leo/commit/a43a0ea0415a47554f39a6e1522d34915f98d240))
* **statement:** 客户对账汇总支持方向筛选并补充查询层测试 ([94be9b2](https://github.com/PolarisTime/Leo/commit/94be9b2def0e29028fb3b824afd9e58b3def1cca))


### Features

* **auth:** 新增多用户账号管理 API 并放开单账号约束 ([d1acc34](https://github.com/PolarisTime/Leo/commit/d1acc34fe76a3f93aafcf25f13d6dc13a0236aa9))
* **inventory:** 库存响应富化物料信息并支持期初回填 ([92f2438](https://github.com/PolarisTime/Leo/commit/92f243864c8ae3e466a61501a47c460415618037))
* **inventory:** 新增库存台账与移动加权成本 ([01aec99](https://github.com/PolarisTime/Leo/commit/01aec994aa917e56aaa8bfac11bb568030481a52))
* **master-data:** 新增商品主数据版本历史、导入预览与批次回滚 ([635db8d](https://github.com/PolarisTime/Leo/commit/635db8d168a783c9b6beaacb13ed8f7ee7533acc))
* **print:** 支持按每份件数拆分打印 ([cbaa4fa](https://github.com/PolarisTime/Leo/commit/cbaa4fa6bfec159b9aa21e84ffe911535ac55108))
* **print:** 支持销售退货单打印数据来源 ([6e9d097](https://github.com/PolarisTime/Leo/commit/6e9d09776a1440f0fc7cb56c003ae12999d59805))
* **sales-outbound:** 支持部分出库（累计覆盖） ([1c1412a](https://github.com/PolarisTime/Leo/commit/1c1412a7202a70e1c418a3374dddc33c3f224b66))
* **sales-return:** 新增销售退货单与订单派生数量、单据流 ([9c2f368](https://github.com/PolarisTime/Leo/commit/9c2f3681eb5dd3547e4d73944cb0a7c9f51810ef))
* **security:** 可扩展端点权限模型（资源:动作细化） ([deb0335](https://github.com/PolarisTime/Leo/commit/deb03350543ce4abc35248988c50abc13fcb7164))
* **security:** 权限全量铺开并支持 resource:* 通配 ([9b9e868](https://github.com/PolarisTime/Leo/commit/9b9e868269a122688fa814ab171e8355091bc146))
* **security:** 落地 RBAC0 用户-角色-权限落库 ([7336933](https://github.com/PolarisTime/Leo/commit/733693342a697b5e2e73b44789a2fe8778588c4f))
* **statement:** 新增红字对账单与退货净额冲销 ([00c7fc7](https://github.com/PolarisTime/Leo/commit/00c7fc70c083c4ae20f98b45aa18029393d60db5))


### Performance Improvements

* **inventory:** 库存余额改为增量快照表 ([b0886b6](https://github.com/PolarisTime/Leo/commit/b0886b66ccc727c89d9eb77fa869379d90530dc0))

# [10.8.0](https://github.com/PolarisTime/Leo/compare/v10.7.0...v10.8.0) (2026-09-12)


### Bug Fixes

* **print:** 修正物流对账模板校验值口径并收敛 seed 状态 ([00b15b5](https://github.com/PolarisTime/Leo/commit/00b15b573a73959e861935449df42517cda9542e))
* **print:** 加固打印模板上传与唯一性契约 ([70da2a5](https://github.com/PolarisTime/Leo/commit/70da2a534e4b1b43fa758be0a24cd73057f9f098))
* **print:** 无附加费用时隐藏费用段并将汇总行居中 ([1e49ecb](https://github.com/PolarisTime/Leo/commit/1e49ecb694065f91ba8ffe4d3752689959a4e0a7))
* **print:** 销售订单A4汇总区改两行并对齐熠祺版式 ([78c5193](https://github.com/PolarisTime/Leo/commit/78c519341a8a28241b9f656bac69acd1c808a934))


### Features

* **print:** 文件托管模板支持默认标记 ([b29e3ca](https://github.com/PolarisTime/Leo/commit/b29e3ca6c75793e984f5bd0b87b5d68873f2b1a8))
* **print:** 销售订单 A4 模板打印附加费用 ([c7535aa](https://github.com/PolarisTime/Leo/commit/c7535aa1b4fe09c5ce939cae2327079563a87b71))
* **print:** 销售订单A4附加费用展示明细与中文大写金额 ([a127a64](https://github.com/PolarisTime/Leo/commit/a127a64eecadce2d07e222d5a723982775ca9d6d))


### Reverts

* **print:** 回退销售订单模板附加费用打印 ([c713f7b](https://github.com/PolarisTime/Leo/commit/c713f7b1e432ea092a945e1ac0a7d4a29e7559c6))

# [10.7.0](https://github.com/PolarisTime/Leo/compare/v10.6.2...v10.7.0) (2026-09-12)


### Bug Fixes

* **api:** 分页参数、日期区间与数据库约束异常统一为 4xx ([639d312](https://github.com/PolarisTime/Leo/commit/639d312af22674b85525f0e80cbfc4dc7c9c1ef8))
* **api:** 分页排序加唯一兜底并将语义校验统一为 422 ([46f4b11](https://github.com/PolarisTime/Leo/commit/46f4b1166165875f2f508263a07549b1bdbbe9c4))
* **api:** 编码签发 Location 提供可回读 GET 资源 ([3bfaec3](https://github.com/PolarisTime/Leo/commit/3bfaec34a447ad8fa8f623b5c669b2141b4b5ace))
* **auth:** 登录令牌过期时间以数值返回 ([b57991e](https://github.com/PolarisTime/Leo/commit/b57991eaa9d164b1835550810933f5105b94aa37))
* **ci:** 更新 ArchUnit 冻结基线以匹配服务拆分后的类 ([2526a2d](https://github.com/PolarisTime/Leo/commit/2526a2dc9bc294488365fbee037f3a1c79e5de53))
* **ci:** 清理未使用 import 并替换 Executors 工厂方法 ([0b6d577](https://github.com/PolarisTime/Leo/commit/0b6d5772adac63db0aeadbf88f8156a1510ef51e))
* **db:** 清洗结算主体名称并重跑名称快照同步 ([4cb5daa](https://github.com/PolarisTime/Leo/commit/4cb5daaf47fc7d873c189feea32d16036ad4da98))
* **market:** 修复中文键配置导致应用启动失败 ([fd99438](https://github.com/PolarisTime/Leo/commit/fd994385613d8dcd78def8c497b6c610dcf9a003))
* **market:** 行情标题时间解析兼容全角括号 ([8b2c1f0](https://github.com/PolarisTime/Leo/commit/8b2c1f0988c1a9220b73259bfb7bc2873d668e2a))
* **material:** 请求字段按列长度校验，超长返回 422 ([9d362de](https://github.com/PolarisTime/Leo/commit/9d362de360cbfe5b4596b85052dced672ed955b8))
* **purchase:** 修复采购订单可用量汇总空映射下的空指针并补充单测 ([b809013](https://github.com/PolarisTime/Leo/commit/b809013b3e4937df51395add837dc3b6c273974a))
* **validation:** 数值精度显式校验与主数据字符串归一化 ([79696b5](https://github.com/PolarisTime/Leo/commit/79696b559b6f47099934718edf844d093cd7acba))
* **validation:** 请求体字符串反序列化统一 trim 并清洗历史主数据 ([d626a2f](https://github.com/PolarisTime/Leo/commit/d626a2f4af34170ebe4407804171aa99aae74f93))


### Features

* **api:** 增加 ETag/条件请求与 multipart 幂等 ([048870c](https://github.com/PolarisTime/Leo/commit/048870c21fa31df2529b5ce6e48e17bc139ea021))
* **api:** 对高风险写接口强制幂等键 ([5ad166e](https://github.com/PolarisTime/Leo/commit/5ad166ed438afdc39d71b339ae9ecd351204d289))
* **api:** 幂等强制扩面到全部副作用写接口 ([e62448a](https://github.com/PolarisTime/Leo/commit/e62448a0b05a0f73a8cc72ac2038f4ef44ecd78c))
* **api:** 选项接口增加返回上界，避免无界列表 ([92f2a62](https://github.com/PolarisTime/Leo/commit/92f2a62706902f0aabb2d9bd60b4c80534a2a160))
* **api:** 雪花 ID 入参拒绝超出 JS 安全整数范围的数值 ([786e71f](https://github.com/PolarisTime/Leo/commit/786e71fa31e9d06ccc369231243712098153273c))
* **attachment:** 新增资源型附件内容端点 ([50746e2](https://github.com/PolarisTime/Leo/commit/50746e254e0fa22d3e06d7f149eab8a425b32043))
* **cache:** 缓存故障改为 fail-open 并记录日志 ([c4758fa](https://github.com/PolarisTime/Leo/commit/c4758fab945182cc756835059fa4bfcd9494ed9e))
* **cache:** 补齐项目/仓库/物料类别/结算主体选项缓存与失效 ([396c08d](https://github.com/PolarisTime/Leo/commit/396c08d78bf7f7bb10dae383e969dca9981c393c))
* **market:** 抓取支持经跳板机 SSH 远程取数 ([91aefdb](https://github.com/PolarisTime/Leo/commit/91aefdb4cfb8b40ef0ba019d2db81e05ae252b7d))
* **market:** 新增同步记录分页接口 ([7f3d864](https://github.com/PolarisTime/Leo/commit/7f3d8641fb44547adf85acbde0294a298308d824))
* **market:** 新增比价报价单落库与采购订单选项接口 ([b0ed01c](https://github.com/PolarisTime/Leo/commit/b0ed01c2a44c2805c249c3c2499f8d7c47a9f347))
* **market:** 新增行情日历接口给前端日历点位 ([561f2a5](https://github.com/PolarisTime/Leo/commit/561f2a5ce8778e441e35f1559440d9004d96d9fe))
* **market:** 新增行情补数(启动按配置同步最近N天) ([0cb0853](https://github.com/PolarisTime/Leo/commit/0cb0853197d8a49cd5f16d1e349631b082ba4d52))
* **market:** 新增钢材行情抓取、按时段入库与商品价格匹配模块 ([05ccc26](https://github.com/PolarisTime/Leo/commit/05ccc269e4e0d1870b61637bd1b975861f3fd251))
* **market:** 日历返回时段行数并新增区间补数接口 ([a33c34f](https://github.com/PolarisTime/Leo/commit/a33c34f0d5296dd2ce406300d7e0c88df6e9fc8d))
* **market:** 行情同步支持一天多时段并优化抓取 ([e4050f2](https://github.com/PolarisTime/Leo/commit/e4050f2a0703a695e342b713cde055ab20f72735))
* **market:** 补数失败明细与行情涨跌筛选 ([2f4ea75](https://github.com/PolarisTime/Leo/commit/2f4ea75ee0a9b6e509b707e88828afa7d0301a1c))
* **master:** 主数据聚合增加乐观锁版本并新增 Flyway 迁移 ([837302d](https://github.com/PolarisTime/Leo/commit/837302d46be2d7d9963860974a7d39433b37eed0))
* **master:** 新增商品品牌选项接口 GET /materials/brands ([4e05e7c](https://github.com/PolarisTime/Leo/commit/4e05e7c66ee3a84c5e50c87b3add92cec0869d1d))
* **material:** 新增商品导出与导入资源端点 ([f549441](https://github.com/PolarisTime/Leo/commit/f549441199174be9c6cf5cf289f4bd5d5b86be4c))
* **print:** 打印输出资源化为 print-exports / print-previews ([d6ebf78](https://github.com/PolarisTime/Leo/commit/d6ebf78798da316930ae5fdf97f2b960f05381ed))
* **sales,purchase:** 出库/入库审核资源化 ([c28077c](https://github.com/PolarisTime/Leo/commit/c28077cd98ba82a6ee5f99913e825bd102fc9af5))
* **sales:** 销售订单交付核定/完成资源化 ([f8511cf](https://github.com/PolarisTime/Leo/commit/f8511cf868048e744849f20ef83bbf85df1b6618))
* **statement,logistics:** 物流单/对账单审核确认资源化 ([36cecd9](https://github.com/PolarisTime/Leo/commit/36cecd98ed879f5298b3f748bfc811833cd65cca))
* **validation:** 查询与路径参数绑定统一 trim ([d3af2df](https://github.com/PolarisTime/Leo/commit/d3af2df7a37fdf9223263d68582a0b0d0cfe1b5f))

## [10.6.2](https://github.com/PolarisTime/Leo/compare/v10.6.1...v10.6.2) (2026-09-08)


### Bug Fixes

* **api:** 采购订单被销售订单关联纳入经采购入库的间接引用链路 ([9846575](https://github.com/PolarisTime/Leo/commit/9846575cf8c2dc2fcbddcf82e917debed3eb1bf5))

## [10.6.1](https://github.com/PolarisTime/Leo/compare/v10.6.0...v10.6.1) (2026-09-08)


### Bug Fixes

* **api:** 修复仅按下游模块关联筛选时订单列表为空的问题 ([9bd28b3](https://github.com/PolarisTime/Leo/commit/9bd28b3864f6c1545ea59b367f8fc77ea4a500f6))

# [10.6.0](https://github.com/PolarisTime/Leo/compare/v10.5.0...v10.6.0) (2026-09-07)


### Features

* **api:** 订单分页查询新增下游模块关联筛选参数 ([9a56e12](https://github.com/PolarisTime/Leo/commit/9a56e12d22a622220049d1e63249382839d1fba1))

# [10.5.0](https://github.com/PolarisTime/Leo/compare/v10.4.3...v10.5.0) (2026-09-02)


### Features

* **orders:** 增加订单关联状态筛选 ([0094122](https://github.com/PolarisTime/Leo/commit/0094122c7e7fde9ed12744b6e41f3a1e5ed22448))

## [10.4.3](https://github.com/PolarisTime/Leo/compare/v10.4.2...v10.4.3) (2026-09-01)


### Bug Fixes

* **orders:** 修复待处理查询日期参数类型 ([b20bc9e](https://github.com/PolarisTime/Leo/commit/b20bc9ed26403c72604bfc7b3b62aed216364c0d))

## [10.4.2](https://github.com/PolarisTime/Leo/compare/v10.4.1...v10.4.2) (2026-09-01)


### Bug Fixes

* **orders:** 处理空关键字查询参数 ([05987ed](https://github.com/PolarisTime/Leo/commit/05987ed183ec643b78b885e9ccffcdcd30cb08ce))

## [10.4.1](https://github.com/PolarisTime/Leo/compare/v10.4.0...v10.4.1) (2026-09-01)


### Bug Fixes

* **orders:** 修复待处理查询字符串匹配 ([093feec](https://github.com/PolarisTime/Leo/commit/093feec91e3cfa902556cc39392f93aeba68e797))

# [10.4.0](https://github.com/PolarisTime/Leo/compare/v10.3.2...v10.4.0) (2026-09-01)


### Bug Fixes

* **orders:** 修复待处理订单查询架构依赖 ([c33382d](https://github.com/PolarisTime/Leo/commit/c33382d6a48801dc98d7db85030f473b83445449))


### Features

* **orders:** 增加订单下游引用状态与待处理筛选 ([69c802d](https://github.com/PolarisTime/Leo/commit/69c802d39486c63ad940d93091e237bf077b95c4))

## [10.3.2](https://github.com/PolarisTime/Leo/compare/v10.3.1...v10.3.2) (2026-09-01)


### Bug Fixes

* **print:** 优化物流对账单默认PDF模板 ([8c00060](https://github.com/PolarisTime/Leo/commit/8c00060928f8c88d2a224115846c5c5253a23c32))

## [10.3.1](https://github.com/PolarisTime/Leo/compare/v10.3.0...v10.3.1) (2026-09-01)


### Bug Fixes

* **print:** 移除物流对账单组内小计 ([0075418](https://github.com/PolarisTime/Leo/commit/0075418b29fecbbeb90aac981cf32938d8aa78db))

# [10.3.0](https://github.com/PolarisTime/Leo/compare/v10.2.1...v10.3.0) (2026-08-29)


### Features

* **statement:** 支持物流对账单明细按单号或日期排序 ([ea3b581](https://github.com/PolarisTime/Leo/commit/ea3b581665918ea1abd00d89d249152f559c6967))

## [10.2.1](https://github.com/PolarisTime/Leo/compare/v10.2.0...v10.2.1) (2026-08-26)


### Bug Fixes

* **print:** 省略 PDF 输出未使用字段 ([50d46e0](https://github.com/PolarisTime/Leo/commit/50d46e04d5b6bb7dff65b643007a3658d3e2d799))

# [10.2.0](https://github.com/PolarisTime/Leo/compare/v10.1.0...v10.2.0) (2026-08-25)


### Bug Fixes

* **architecture:** 移除项目模块跨边界实体依赖 ([b46ed61](https://github.com/PolarisTime/Leo/commit/b46ed612bfa4d14d8fccc19f5344fb19e488b55a))
* **build:** 隔离 Maven 用户级启动配置 ([7fbf021](https://github.com/PolarisTime/Leo/commit/7fbf021723b2318f272e6b01cc572518373a802e))
* **database:** 恢复已执行的 V126 迁移脚本 ([5518760](https://github.com/PolarisTime/Leo/commit/55187608cb3ff3ebba603bd918975c4b6a6c743a))
* **finance:** 让收款和台账调整遵循项目结算主体 ([8d43ff5](https://github.com/PolarisTime/Leo/commit/8d43ff5cd0903c75dfddd4ab2bc93e2a85cd6cfc))
* **print:** 适配对账单打印作业明细 ([0f04815](https://github.com/PolarisTime/Leo/commit/0f0481519bc8698e20e09f8c2bf9be51bc238cb1))
* **statement:** 候选订单主体跟随项目配置 ([40062d3](https://github.com/PolarisTime/Leo/commit/40062d349e3760637dbc10effe2e9160fd88dbd3))
* **statement:** 按项目配置唯一结算主体生成客户对账单 ([e571f4c](https://github.com/PolarisTime/Leo/commit/e571f4c6fca8dbceb74ec119d7b64ba0b8ce6289))


### Features

* **finance:** 返回客户对账单交货日期 ([69e53a4](https://github.com/PolarisTime/Leo/commit/69e53a48f54f70e4a0d48d2692463f247cfeca7c))
* **master:** 为项目增加结算主体 ([6e4ffa2](https://github.com/PolarisTime/Leo/commit/6e4ffa2e048199c8728fbeae4889d9cef5fd85c3))
* **print:** 新增客户对账单 iText 分组模板 ([315365a](https://github.com/PolarisTime/Leo/commit/315365a842fb69c3f230c6707f53effa975ee1f3))

# [10.1.0](https://github.com/PolarisTime/Leo/compare/v10.0.1...v10.1.0) (2026-08-24)


### Bug Fixes

* **startup:** 修复交易物料支持组件启动失败 ([e144f33](https://github.com/PolarisTime/Leo/commit/e144f33dc5edb21d5f2d30a02de2ef98bc2ab518))


### Features

* **finance:** 支持收付款资金账户 ([2119b37](https://github.com/PolarisTime/Leo/commit/2119b37a2a43d275bb2254d1515bc59449c972d9))

## [10.0.1](https://github.com/PolarisTime/Leo/compare/v10.0.0...v10.0.1) (2026-08-24)


### Bug Fixes

* **auth:** 优化鉴权快照与缓存失效链路 ([f38f086](https://github.com/PolarisTime/Leo/commit/f38f086dd5fbd91cdd6cd6f21fe2ddbcb21e2e59))
* **finance:** 修复财务概览默认排序报错 ([90201cd](https://github.com/PolarisTime/Leo/commit/90201cd17ac86a6b15c4e2af98ceb5f9394a2645))

# [10.0.0](https://github.com/PolarisTime/Leo/compare/v9.12.0...v10.0.0) (2026-08-24)


* refactor(api)!: 收付款与对账单保存并审核资源化 ([81660e2](https://github.com/PolarisTime/Leo/commit/81660e22b1e0756f5500e4c9c0c7800133c40c85))
* refactor(api)!: 采购销售物流五单据保存并审核资源化 ([2d4ff0f](https://github.com/PolarisTime/Leo/commit/2d4ff0fada60b7b69407fdb725a6ce071929bcf1))


### Bug Fixes

* **ci:** 修复后端架构与发布门禁 ([3cb8e6f](https://github.com/PolarisTime/Leo/commit/3cb8e6f289e5920bf26e2835ccdee6eecdb667e8))
* **common:** 统一关键字搜索为转义忽略大小写语义 ([31d87ae](https://github.com/PolarisTime/Leo/commit/31d87ae974496daafbe87cf65fed122fba382c33))
* **material:** 商品类别分页接入排序白名单 ([0dfa85a](https://github.com/PolarisTime/Leo/commit/0dfa85a44a2a040a79c419c5d9e760efa556d396))
* **printtemplate:** 打印取数显式列化与渲染健壮性 ([142388b](https://github.com/PolarisTime/Leo/commit/142388b792af093b69ca79ddd1991adf935d2f2e))
* **print:** 销售订单 Excel 导出重量强制 3 位小数并拼接 12 米规格 ([be92c59](https://github.com/PolarisTime/Leo/commit/be92c599185480b62a642d2450e15a6085674558))


### Features

* **api:** 创建语义端点状态码修正与信任模型文档 ([5b0bcf8](https://github.com/PolarisTime/Leo/commit/5b0bcf83032c92338d9ff722e6a4447816161604))
* **charge:** 通用单据附加费用表与三模块接入 ([e44c0e4](https://github.com/PolarisTime/Leo/commit/e44c0e4dd144a296d8e044223227a4b89360fb34))
* **common:** 补齐 429 限流错误映射 ([cd47d20](https://github.com/PolarisTime/Leo/commit/cd47d2083ceadf95f8b5262ea50bf8b3a330c88b))
* **material:** CSV 导入响应补齐行级结果明细 ([bc6e8b6](https://github.com/PolarisTime/Leo/commit/bc6e8b645970de5f6e22abbbd8b95ce2e93b88f5))
* **material:** 商品资料增加商品类型支持附加费用 ([aa0dcc5](https://github.com/PolarisTime/Leo/commit/aa0dcc5fedc3537e52537df8bf433acd5dc00afa))
* **printtemplate:** COORD 模板保存侧增加 LODOP 指令白名单校验 ([73954ac](https://github.com/PolarisTime/Leo/commit/73954ace93f551ba1714bb6a0d65e96e56c37a28))
* **printtemplate:** 模板表唯一约束与乐观锁 ([3a02197](https://github.com/PolarisTime/Leo/commit/3a02197c88e8b23287d1092a1558b10a67cb36c7))


### BREAKING CHANGES

* 五个单据模块的 save-and-audit 端点移除，
前端已同批切换为 audit 请求标志位
* /receipts、/payments、/customer-statements、/freight-statements
的 save-and-audit 端点移除，前端已同批切换为 audit 请求标志位

# [9.12.0](https://github.com/PolarisTime/Leo/compare/v9.11.0...v9.12.0) (2026-08-21)


### Features

* **material:** 商品导入身份冲突改为更新已有商品并返回行级结果明细 ([232bb00](https://github.com/PolarisTime/Leo/commit/232bb00b8f2236ad7b4da88cf6b7eafaeb635f27))

# [9.11.0](https://github.com/PolarisTime/Leo/compare/v9.10.4...v9.11.0) (2026-08-10)


### Features

* **print:** 物流对账单模板无框化并新增分组小计行 ([c38e559](https://github.com/PolarisTime/Leo/commit/c38e559d65f709019476e96091928a88781c204d))

## [9.10.4](https://github.com/PolarisTime/Leo/compare/v9.10.3...v9.10.4) (2026-08-09)


### Bug Fixes

* **freight-statement:** 返回来源物流单运费信息 ([4ad2feb](https://github.com/PolarisTime/Leo/commit/4ad2feb1bf567f23e4dcf6060991c056f0a16bab))
* **print:** 优化物流对账单续页与表头宽度 ([094af45](https://github.com/PolarisTime/Leo/commit/094af457f7292f53cba282e40a59848fc1c71f7e))

## [9.10.3](https://github.com/PolarisTime/Leo/compare/v9.10.2...v9.10.3) (2026-08-08)


### Bug Fixes

* **print:** 优化物流对账单分组表头与黑白打印 ([cf6f98d](https://github.com/PolarisTime/Leo/commit/cf6f98d6af563d2d157933031d69140f7604bd68))

## [9.10.2](https://github.com/PolarisTime/Leo/compare/v9.10.1...v9.10.2) (2026-08-08)


### Bug Fixes

* **print:** 修复物流对账单分组分页与模板渲染 ([e22d88f](https://github.com/PolarisTime/Leo/commit/e22d88f269c6ee31edec909f708e2deb2ae1cd15))

## [9.10.1](https://github.com/PolarisTime/Leo/compare/v9.10.0...v9.10.1) (2026-08-08)


### Bug Fixes

* **print:** 物流对账单表头仅在首页显示 ([85d9849](https://github.com/PolarisTime/Leo/commit/85d9849cb0fb34099ec00a87b167d608eb25a8df))

# [9.10.0](https://github.com/PolarisTime/Leo/compare/v9.9.0...v9.10.0) (2026-08-06)


### Bug Fixes

* **sales-order:** 交付核定状态下保存误判为不能编辑 ([b9d3bd9](https://github.com/PolarisTime/Leo/commit/b9d3bd97158d63eb53909f8857a2ffb425edd129))


### Features

* **column-settings:** 列设置支持 columnSizes 远程同步 ([a56d7a0](https://github.com/PolarisTime/Leo/commit/a56d7a00cbb40f66313d40d75ba74078dd68f2f9))

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
