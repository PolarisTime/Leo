# Leo ERP 压测套件（k6）

针对 `leo` 后端 dev 环境的 HTTP 压测脚本，用于测出读写接口的延迟基线、吞吐拐点与报错阈值。

## 目录结构

```
leo/perf/
├── README.md                  本说明
├── run.sh                     分阶段调度脚本（smoke|baseline|read|write|spike|all）
├── cleanup-perf-data.sh       清理压测产生的客户数据
├── reports/                   压测报告（results/ 为本地生成物，已 gitignore）
├── k6/
│   ├── lib/common.js          公共库：登录、token 共享、请求参数构造、读接口清单
│   ├── 01-smoke.js            冒烟：1 VU 打通读接口与写链路，写后即删
│   ├── 02-baseline.js         基线：1 VU 顺序轮询，测无竞争延迟
│   ├── 03-read-mixed.js       读混合阶梯爬坡（默认至 300 VU）
│   ├── 04-write-mixed.js      写压测：签发编码 -> 创建客户（默认自清理）
│   ├── 05-spike.js            激进冲击：默认拉到 800 VU 并保持
│   ├── 06-login.js            登录专项：量化同账号/多账号并发登录行为
│   ├── 07-heavy.js            重负载专项：xlsx 导出与计算密集接口
│   ├── 08-concurrency.js      并发正确性：同一行/同一单据并发写 + 幂等重放
│   ├── 09-soak.js             Soak 长时稳定性：恒定负载找出泄漏类问题
│   └── 10-attachments.js      附件专项：上传/下载（需后端以 local 附件存储启动）
├── collect-metrics.sh         采集服务端 Prometheus 指标（连接池/JVM/缓存）
├── soak-runner.sh             分段运行 2 小时 soak 并逐段校验失败率
├── soak-aggregate.py          聚合分段 soak 结果
├── redis-degradation.sh       Redis 故障降级验证（双实例对照）
├── test/                      离线自检（不需要后端与 Redis，可随时重跑）
│   ├── token-guard-selftest.sh      token 有效期守卫双向验证
│   ├── leak-detector-selftest.sh    泄漏检测器双向验证（合成锯齿数据）
│   └── collector-login-selftest.sh  采集器登录次数验证（本地桩服务）
└── results/                   运行输出（本地生成，已 gitignore）
```

## 前置条件

1. **k6**：`K6_BIN=/path/to/k6` 指定，或装到 PATH，或解压到 `tmp/` 下由 `run.sh` 自动查找。
2. **dev 服务在跑**：后端 `127.0.0.1:11211`、前端 `127.0.0.1:3100`。
   ```bash
   bash leo/scripts/dev.sh start
   bash leo/scripts/dev.sh status
   ```
3. **测试凭据**：通过环境变量注入，不要写进脚本。
   ```bash
   export LEO_PERF_BASE_URL='http://127.0.0.1:11211/api'
   export LEO_PERF_LOGIN_NAME='<登录名>'
   export LEO_PERF_PASSWORD='<密码>'
   ```
   也可以用 `LEO_PERF_ENV_FILE=/path/to/creds.env` 指向一个仅本机保留的 shell 文件。

## 用法

```bash
# 冒烟（先跑这个，确认认证与读写链路可用）
LEO_PERF_ENV_FILE=/path/to/creds.env bash leo/perf/run.sh smoke

# 单阶段
bash leo/perf/run.sh baseline
bash leo/perf/run.sh read
bash leo/perf/run.sh write
bash leo/perf/run.sh spike
bash leo/perf/run.sh heavy        # 导出与计算密集接口（含导入预览）
bash leo/perf/run.sh race         # 并发写与幂等重放
bash leo/perf/run.sh soak         # 长时稳定（耗时以小时计，建议单独跑）
bash leo/perf/run.sh metrics 300 5   # 仅采集服务端指标（秒数 间隔）

# 全流程（不含 soak，因其耗时为小时级）
bash leo/perf/run.sh all
```

`run.sh` 会在开始阶段**只登录一次**并导出共享 token 给 k6 与采集器，
这是必需的（见下方「会话唯一性」）。

强度可用环境变量覆盖：

| 变量 | 作用 | 默认 |
|---|---|---|
| `LEO_PERF_MAX_VUS` | 读爬坡并发上限 | 300 |
| `LEO_PERF_HOLD` | 每档保持时长 | 45s |
| `LEO_PERF_WRITE_VUS` / `LEO_PERF_WRITE_ITERATIONS` | 写压测并发 / 总轮次 | 25 / 250 |
| `LEO_PERF_KEEP_DATA` | 写压测保留数据（`1` 则不删除） | 不保留 |
| `LEO_PERF_SPIKE_MAX` | 激进档并发上限 | 800 |
| `LEO_PERF_BASELINE_DURATION` | 基线时长 | 60s |
| `LEO_PERF_LOGIN_VUS` / `LEO_PERF_LOGIN_ITERATIONS` | 登录专项并发 / 每 VU 轮次 | 20 / 10 |
| `LEO_PERF_LOGIN_POOL` | 登录账号池 `账号:密码,账号:密码`，用于区分单账号争用与多用户吞吐 | 单账号 |

想同时拿到逐秒数据以定位拐点，可自行加 `--out csv=xxx.csv`。

登录专项单独运行：

```bash
source tmp/perf/creds.env && k6 run leo/perf/k6/06-login.js
LEO_PERF_LOGIN_VUS=50 LEO_PERF_LOGIN_ITERATIONS=20 k6 run leo/perf/k6/06-login.js
```

## 接口契约要点（写脚本时踩过的坑）

- **登录**：`POST /v2.0/auth/login`，体 `{"loginName","password"}`，响应是**扁平结构**（`accessToken` 在顶层，没有 `code/message/data` 包装），`expiresIn` 600 秒。
- **幂等键**：所有写请求（含登录）都必须带 `X-Idempotency-Key`，否则返回 `422 缺少幂等键`。
- **k6 的认证头**：`http.get(url, params)` 只认 `params.headers`，认证头平铺到 `params` 顶层不会生效（会得到 401）。公共库的 `readParams`/`writeParams` 已封装。
- **客户编码**：`POST /v2.0/customers` 的 `customerCode` 必须是系统签发的雪花 ID，需先 `POST /v2.0/master-data/code-issuances/customer`（模块键小写单数：`customer`）。
- **雪花 ID**：JSON 中以字符串传输；脚本同样以字符串发送 `defaultSettlementCompanyId`。
- **结算主体 ID**：`GET /v2.0/company-settings` 取；`run.sh` 在写测前会自动解析，也可显式设置 `LEO_PERF_COMPANY_ID`。
- **不要每个 VU 各自登录**：`LoginService` 每次登录都写 `sys_user.last_login_date`，该实体带 `@Version` 乐观锁且冲突不重试，**同一账号并发登录会大量返回 409**（详见 `reports/2026-09-29-dev-stress.md`）。若让每个 VU 自己登录，VU 会拿不到 token 而空转，读吞吐会被这个假象压垮。公共库因此统一在 `setup()` 串行登录一次并共享 token；登录并发本身用 `06-login.js` 单独测。
- **`__VU`/`__ITER` 在 setup() 中不存在**：只有 VU 执行期才有，在 `setup()` 里引用会抛 `ReferenceError`；公共库的 `idempotencyKey` 已做兼容。
- **幂等键必须每次唯一**：并发重复提交更惯例返回 409，本项目返回 422+4220（见上文并发结论）。
- **测耗时的第一步是校验状态码**：本套件曾用**已过期 token** 量出一批 2.5–2.8 ms 的「接口耗时」，
  其实那是 401 的耗时。任何耗时数字都必须先断言 `/account` 返回 200。
- **单请求耗时必须标明冷/热与并发度**：导出接口首次 133.8 ms（POI 类加载 + JIT），
  稳态只要 4.5 ms，30 VU 并发下 100 ms。同一句「导出 47–65 ms」既不说明状态也无法复现，
  早期报告因此得出过「导出是普通接口 8 倍」的错误结论（稳态下导出甚至比一次分页读更快）。

## 压测数据与清理

写压测产生的客户 `customerName` 统一以 `PERF-LOAD-<RUN_ID>` 开头，`remark` 标注 `perf load test data, safe to delete`。

```bash
# 先统计不删除
bash leo/perf/cleanup-perf-data.sh
# 确认后删除全部压测客户
bash leo/perf/cleanup-perf-data.sh --yes
# 只删指定批次
bash leo/perf/cleanup-perf-data.sh --yes PERF-LOAD-20260929-215438
```

默认情况下 `04-write-mixed.js` 创建后立即删除，自身不留残余数据；`LEO_PERF_KEEP_DATA=1` 才保留。

## 指标口径

- `http_req_duration{kind:read}` / `{kind:write}`：按读写区分延迟，p95/p99 为主要判定。
- `http_req_failed`：非 2xx/3xx 记为失败。
- `checks`：接口契约断言通过率（状态码）。
- `02-baseline` 会为每个读接口单独输出 `baseline_*` 指标，便于逐接口对比。
- `05-spike` 额外输出 `spike_server_errors`（5xx）与 `spike_client_errors`（4xx）。
- 读压测默认阈值：`http_req_failed < 1%` 且读 p95 < 800ms、p99 < 2000ms。

## 已知限制

- 后端进程跑在宿主上，沙箱内 `ps` 看不到，无法直接采集后端 CPU/内存；只能用 `/proc/loadavg`、`free` 与 HTTP 指标间接推断。
- access token 有效期 10 分钟。公共库在 `setup()` 登录一次并对各 VU 共享，TTL 内无感；单次压测时长应控制在 8 分钟以内，超过后 VU 会兜底重登并可能撞上并发登录争用。
- k6 与被压服务同机运行，高并发档（500+ VU）下 k6 自身也占用 CPU/内存，测得的吞吐是「同机竞争」后的结果，会低估独立压测机下的真实上限。


## 服务端指标采集（collect-metrics.sh）

前端视角的延迟看不到连接池耗尽、GC 与堆压力，必须同时采集服务端指标。

前置：后端需暴露 prometheus 端点

```bash
LEO_MANAGEMENT_ENDPOINTS=health,prometheus,loggers bash leo/scripts/backend/start-dev.sh
```

用法（与压测共用同一个 token，见下方「会话唯一性」）：

```bash
export LEO_PERF_TOKEN=<共享 token>
bash leo/perf/collect-metrics.sh watch <输出.csv> <秒数> [采样间隔秒] &
bash leo/perf/collect-metrics.sh summary <输出.csv>
bash leo/perf/collect-metrics.sh trend <输出.csv>     # 长跑用：前后半段对比 + 堆增长斜率
```

`trend` 用于判定长跑是否泄漏：泄漏表现为「后半段均值显著高于前半段」而非绝对值大小。
它用最小二乘给出堆内存增长斜率（MB/小时），>8 MB/h 报「疑似泄漏」，>2 MB/h 提示复核。
该检测器已用合成数据集验证（注入 +20 MB/h 能精确检出 +20.00，平稳数据集正确报无泄漏）。

采集内容：Hikari 活跃/空闲/等待/获取超时、JVM 堆与存活线程、Tomcat 忙碌线程、
进程与系统 CPU、Spring Cache 命中率、服务端请求计数与最大耗时。

注意：
- `cache_*` 只覆盖 Spring Cache region（options/static/project-options）。
  权限缓存走 RedisJsonCacheSupport 的裸 Redis 写入，不产生 micrometer 缓存指标。
- Tomcat 线程/连接指标需额外设置 `SERVER_TOMCAT_MBEANREGISTRY_ENABLED=true`，
  未开启时 `tomcat_*` 列恒为 0。

## 会话唯一性（重要，否则压测结果完全失真）

**先记住结论**：服务端同账号会话上限硬编码为 3（`SessionManagementService.DEFAULT_MAX_REFRESH_TOKENS`），
第 4 次登录会吊销并拉黑最旧会话，其 access token 立即失效。所以整套流程（用例、采集器、
辅助 curl）**必须共用同一个 token**。以下三个坑都真实发生过，都会让结果完全失真：

### 坑 1：setup 漏传 `expiresAt` → 每个 VU 各自重登

`ensureToken(shared)` 以 JWT 真实 `exp` 判断有效性。若 `setup()` 只回传 `{token, issuedAt}`
而漏掉 `expiresAt`，判断恒为「无效」，于是**每个 VU 都登录一次**，
触发上述会话吊销级联。实测一次 07-heavy：346,686 个请求里 **99.3% 变成 401**，
而业务日志里几乎没有异常——极易被误读成「导出接口故障」。

现在 `setup()` 一律 `Object.assign({}, base, {...})` 整体展开，且 `ensureToken` 在
`expiresAt` 缺失时会回退解析 token 自身的 `exp`（双保险）。

### 坑 2：采集器「每采样一次登录一次」

`body="$(fetch_prometheus)"` 是**命令替换**，函数里的 `TOKEN=...` 只存在于子 shell，
父 shell 永远拿不到，于是每采样一次登录一次。实测它每 ~6 秒登录一次，
把正在被压测的会话顶掉，造成 99.3% 的 401。

现在采集器把指标写进临时文件（避免命令替换），并统计登录次数：
`LOGIN_COUNT > 1` 会显式打印「多余登录会吊销既有会话」的告警。
验证方式见 `test/collector-login-selftest.sh`（桩服务计数：无 token 时恰好 1 次、有 token 时 0 次）。

### 坑 3：k6 没有阈值 → run.sh 假通过

`07-heavy` 曾在 **98.05% 请求失败**的情况下打印「07-heavy 通过」——因为脚本没有 thresholds，
k6 退出码为 0，而 `run.sh` 只看退出码。现在：

- `07-heavy.js` 设有 `http_req_failed{kind:read}` / `{kind:write}` ≤ 1% 与 `checks` > 99%；
- `08-concurrency.js` 设有 `race_unexpected_status == 0`（只允许 200/201/409/422；
  409/422 是被测现象，401/5xx 一律判失败）；
- `run.sh` 额外读取 `http_req_failed` 做兜底（> 1% 即失败），并**以非零退出码结束**。
  `race` 阶段的兜底阈值放宽到 90%，因为它以 409 为被测现象。



服务端对同一账号存在**会话数上限**（`SessionManagementService.DEFAULT_MAX_REFRESH_TOKENS = 3`，
硬编码不可配置）。第 4 次登录会吊销并拉黑最旧会话，该会话的 access token 立即返回 401。

因此**压测用例、指标采集器与任何辅助调用必须共用同一个会话**，绝不能各自登录：

```bash
export LEO_PERF_TOKEN=<只登录一次得到>     # run.sh 会自动导出
k6 run leo/perf/k6/03-read-mixed.js         # setup() 复用该 token，不再登录
```

历史上因为各自登录，曾出现 86.77% 与 92.52% 的失败率（18,254 请求中有 3,615 次
登录、11,248 个 401、7,902 次连接池获取超时），吞吐与延迟数据全部不可用。

## 并发正确性专项结论（08-concurrency.js）

末次运行 9,538 请求、checks 100%、**非预期状态码 0**（无 401/403/5xx 混入）。四个场景：

| 场景 | 结果 | 业务数据核对（SQL） | 判定 |
|---|---|---|---|
| A 同一角色权限并发替换（10 并发） | 200×1,844 / 409×5,512（74.9% 冲突） | 该角色权限**恰为 `["roles:read"]`、count=1** | 安全但噪声大 |
| B 幂等键并发重放（同键） | 201×1 / 422×9 | 只创建 1 条客户 | exactly-once 成立 |
| C 同一客户编码并发创建 | 1 成功 / 9 冲突 | 该编码只创建 1 条客户 | 唯一性正确 |
| D **同一单据并发整体替换**（销售订单，10 并发） | 200×216 / 409×1,939（**90.0% 冲突**） | **version 220 = 3+1+216**、明细行数恒为 1 | **无丢失更新** |

要点：

- **A 的 409 不是乐观锁**：`SysRolePermission` 没有 `@Version`，冲突来自
  `deleteByRoleId`+`saveAll` 的「先删后插」撞唯一索引，失败事务整体回滚。
- **D 的 409 来自销售订单实体的 `@Version`**：单据带明细行，并发整体替换若发生部分写入，
  风险是明细行重复或丢失；实测明细行恒为 1，且成功次数与 version 增量**精确相等**。
- **幂等重放的 422 是正确的**：落败请求返回 `422 + code 4220`「请勿重复提交，请等待当前请求处理完成」，
  即正确识别「同一幂等键正在处理中」。该响应经 `ApiErrorResponseWriter` 直接写出，
  **绕过全局异常处理器因而没有日志**（早期误判的根因）。唯一可议之处是状态码更惯例用 409。
- **删掉的旧结论**：早期文档写过「落败请求返回 422『编码已失效』」，那是**测试设计缺陷**
  （复用了已被消耗的编码）造成的误判，不是幂等行为。

### 写这个专项时必须注意的两点

1. **并发写的内容必须互不相同**。若所有 VU 提交完全相同的 body，服务端会把
   「无变化」的整体替换**优化成空操作**：实测 596 次并发 PUT 全部 200、零冲突，
   但 version 一次都没增长——那不是「服务端串行化很好」，而是根本没写库。
   改成每请求唯一 remark 后，冲突率立刻变成 90.0%。
2. **必须先预检靶子可写**。`PUT` 对**已软删**单据返回 404（dev 库 10 张草稿里 9 张是 E2E 残留），
   若不做预检，场景会把「单据不可写」误读成「并发冲突」。
   脚本会先筛草稿单、再预检一次 PUT 必须 200，否则直接抛错中止。
   全库可写草稿单**只有 1 张**，测试后需还原 remark（见脚本注释与报告第九节）。

## Soak 长时稳定性（09-soak.js）

```bash
# 分段运行（推荐）：token 有效期 600s 不可配置，2 小时必须分段
bash leo/perf/soak-runner.sh 120 8      # 总 120 分钟，每段 8 分钟

# 分析与聚合
python3 leo/perf/soak-aggregate.py tmp/perf              # 跨段总览
bash leo/perf/collect-metrics.sh trend tmp/perf/metrics-soak-all.csv   # 泄漏趋势
```

`09-soak.js` 单次运行会在 setup 里**校验 token 是否覆盖计划时长**，不足即失败——
这正是为了防止「跑了几小时才发现 auth 早已失效」。段长默认受 9 分钟安全边界保护。

`soak-aggregate.py` 已用合成分段数据验证：累计请求/失败率/吞吐 min-中位-max/
p95/p99/5xx 统计与预期逐项吻合，能正确标记异常段。

只读、不产生业务数据。40 VU 是该实例接近饱和但不自我压垮的档位
（实测 40 VU 约 1,010 req/s，高于 300 VU 的 909 req/s，说明 300 VU 已过饱和点）。

**实测结果（15 段 × 8 分钟 = 2 小时）**：7,173,430 请求、**0 失败**、服务端 5xx/4xx 均为 0，
段吞吐 989.0–998.9 req/s（极差 1.0%）、段 p95 63.8–64.6 ms、连接池获取超时增量 **0**。

### 泄漏判定必须用「存活水位」而不是均值

`heap_used` 在持续压力下是**锯齿波**（本次 2 小时：峰谷幅度 418.9 MB、86 次 GC、回收 15,027 MB）。
只看**均值**的最小二乘斜率会被 GC 相位主导，正负都会翻：同一份稳态负载在
71.8 分钟窗口算出 **−65.13 MB/h**、在 119.9 分钟窗口算出 **+18.92 MB/h**，
后者会被误报成「疑似内存泄漏」。

现在 `trend` 改用**分桶最小值（≈GC 后存活水位）的斜率 + 显著性检验**：

```
锯齿诊断: 峰谷幅度 418.9 MB，GC 回收(单次 >10MB) 86 次，共回收 15027 MB
堆内存存活水位斜率(GC 后最小值, 12 桶): +6.98 ± 10.83 MB/小时（t=0.64）
  首桶水位 274.7 MB → 末桶水位 259.3 MB
堆内存增长判定: +6.98 MB/小时，但 t=0.64 < 2（漂移在噪声范围内）  ✅ 未见泄漏迹象
```

阈值：|t| < 2 视为噪声；否则按水位斜率分档（>8 MB/h 报泄漏，2–8 MB/h 提示复核）。
检测器已用合成锯齿数据双向验证 —— 见 `test/leak-detector-selftest.sh`。


## Redis 故障降级验证（redis-degradation.sh）

```bash
bash leo/perf/redis-degradation.sh --yes     # 需要 11211 上已有正常后端在跑，且 11212 空闲
```

隔离方式：**只在 11212 上另起一个「故障实例」**，把它的 Redis 端口指向一个不存在的端口；
11211 上的正常实例、以及共享 Redis 实例都**不被触碰**。

### 为什么是「双实例并行 + 同 token 活性对照」

最初的设计是「先起正常后端取 token → 停掉 → 换坏 Redis 端口重启 → 探测」。实测中这段等待
耗掉约 11 分钟，**超过了 600s 的 access token 有效期**，观察到的 401 其实来自
`ExpiredJwtException`，与 Redis 无关。报告初稿据此断言「Redis 不可用时已登录用户全部 401」，
该结论**已撤回**。现设计用三道对照排除混淆因素：

1. **双实例并行**：探测前不需要任何重启等待，token 寿命由 `assert_token_valid` 保证（阈值 120s）。
2. **同 token 活性对照**：探测故障实例前，立刻用同一 token 再打一次正常实例，两处都必须 200；
   否则脚本**中止**，不产出可被误读的证据。
3. **坏签名判别探针**：故障实例上用篡改过签名段的 token 再打一次，用于区分
   「签名/密钥不一致导致的 401」与「Redis 故障导致的 5xx」。
4. 故障实例显式 `source scripts/env/dev.sh`，与正常实例从同一份工作区 `.env.local`
   读取同一个 `LEO_JWT_SECRET`，排除「两实例密钥不同」这一伪因果源。

`token_remaining_seconds`/`assert_token_valid` 已做双向验证：`exp=+300s` 通过、
`exp=-60s` 被拦、`exp=+30s`（低于阈值）被拦、非 JWT 字符串被拦。
离线自检（不需要后端与 Redis，随时可跑）：

```bash
bash leo/perf/test/token-guard-selftest.sh
```

该自检从 `redis-degradation.sh` 里**现场提取**函数定义再测，避免「测的代码和跑的代码不是同一份」。

实测结论（详见 `reports/` 下的压测报告）：

| 请求 | Redis 正常 | Redis 不可用 |
|---|---|---|
| `GET /v2.0/health` | 200 | **503 `DEGRADED`**（优雅） |
| `GET /v2.0/version` | 200 | 200（不依赖 Redis） |
| `POST /v2.0/auth/login` | 200 | **503「幂等服务暂不可用」** |
| 已登录用户的接口 | 200 | 由本脚本复测后填入，不再沿用被撤回的 401 断言 |

已确证的部分：**Redis 不是可降级的缓存，而是登录链路的硬依赖**——它不可用时连重新登录都做不到。
认证链路对 Redis 异常**没有兜底**：`CacheConfig` 的 `CacheErrorHandler` 优雅降级只覆盖
Spring Cache，而认证链路用的是裸 `StringRedisTemplate`，
`AuthenticatedUserCacheService.getActivePrincipal` 对 Redis 调用无 try/catch，
异常不在 `JwtAuthenticationFilter` 捕获的 `JwtException | IllegalArgumentException` 之内。
其**实际后果**（401 还是 5xx）以脚本实测为准。

另注意 `scripts/backend/start-dev.sh` 含 Redis 预检，Redis 不可用时**直接拒绝启动**，
因此本脚本直接调 `scripts/maven.sh spring-boot:run` 以绕开该预检。


## 附件专项（10-attachments.js）

```bash
# 必须让后端以本地附件存储启动：S3 凭据已失效，而且上传失败时连一次都跑不起来。
# 注意 SPRING_APPLICATION_JSON 的优先级高于 OS 环境变量——scripts/env/dev.sh 会把
# .env.local 里的 LEO_ATTACHMENT_STORAGE_TYPE=s3 再导出一次，普通 env 覆盖会被它盖住。
SPRING_APPLICATION_JSON='{"leo":{"attachment":{"storage":{"type":"local","local":{"path":"/abs/path/uploads"}}}}}' \
  LEO_MANAGEMENT_ENDPOINTS=health,prometheus,loggers bash leo/scripts/backend/start-dev.sh

LEO_PERF_ENV_FILE=tmp/perf/creds.env bash leo/perf/run.sh attachments
```

实测（local 存储口径）：上传 64 KB multipart、10 VU 并发 200 次 → p95 119.88 ms；
下载 10 VU、30 s → 21,327 次、p95 19.54 ms，**0 失败**。
口径限制：**不含 S3 客户端、签名 URL 与对象存储网络往返**。

清理（附件没有删除接口，只能走 SQL；脚本会校验路径不越界且与 id 一致）：

```bash
source leo/scripts/env/dev.sh
LEO_ATTACHMENT_LOCAL_PATH=/abs/path/uploads bash leo/perf/cleanup-perf-data.sh --attachments        # 预演
LEO_ATTACHMENT_LOCAL_PATH=/abs/path/uploads bash leo/perf/cleanup-perf-data.sh --attachments --yes    # 执行
```

`sourceType` 是服务端白名单（只允许 `PAGE_UPLOAD` / `CLIPBOARD`），不能当清理标记；
因此附件**文件名**带运行标记 `perf-attach-<RUN_ID>.txt`。

## 编辑 shell 脚本后必须做静态检查

```bash
python3 leo/perf/check-undefined-funcs.py leo/perf/*.sh
```

`bash -n` 只做语法检查，**抓不到「调用了未定义的函数」**——那是运行时错误。
本套件曾因一次脚本替换把函数定义头 `resolve_shared_token() {` 改写成了裸调用，
导致 `run.sh` 对所有阶段都不可用，而 `bash -n` 全程无告警。

检测器已做双向验证：合成脚本能检出未定义调用且不误报变量赋值与 case 标签；
把上述回归还原后能准确报出 `resolve_shared_token`。
局限（有意为之）：引号内 `$(...)` 的内层命令不检查，宁可漏报不误报。

## 离线自检（不依赖后端与 Redis）

压测套件里的「守卫」本身也会出错：token 有效期判断曾把 `ExpiredJwtException` 误判成
「Redis 故障导致登出」；泄漏检测器曾把锯齿波误报成泄漏；采集器的「只登录一次」注释
与实现不符。因此这些守卫都有**可执行的**双向验证，随时可离线重跑：

```bash
bash leo/perf/test/token-guard-selftest.sh        # token 有效期守卫（有效放行 / 过期・阈值内・非 JWT 拦截）
bash leo/perf/test/leak-detector-selftest.sh      # 泄漏检测器（注入 +60/+20 MB/h 必报；±25/±35MB 抖动不得误报）
bash leo/perf/test/collector-login-selftest.sh    # 采集器登录次数（桩服务计数：无 token 恰好 1 次、有 token 0 次）
```

三个自检的共同原则：**只验证「能通过」是单向验证，等于没验证**——
每个用例都必须同时包含正例与反例（能放行 / 必须拦下、能报泄漏 / 不得误报、能复用 / 不得多登）。
