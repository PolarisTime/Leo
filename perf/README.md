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
│   ├── 08-concurrency.js      并发正确性：同一行并发写 + 幂等重放
│   └── 09-soak.js             Soak 长时稳定性：恒定负载找出泄漏类问题
├── collect-metrics.sh         采集服务端 Prometheus 指标（连接池/JVM/缓存）
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

| 场景 | 结果 | 判定 |
|---|---|---|
| 同一角色权限并发替换（10 并发） | 200×1811 / 409×6293（77.6% 冲突） | 最终权限集正确无重复；`SysRolePermission` 无 `@Version`，冲突源自 `deleteByRoleId`+`saveAll` 撞唯一索引，失败事务整体回滚 |
| 幂等键并发重放（10 并发同键） | 201×1 / 422×9 | exactly-once 成立（只创建 1 条）；但落败请求返回 422「编码已失效」而非重放原始响应 |
| 同一客户编码并发创建（10 并发） | 201×1 / 409×9 | 唯一性正确，其余请求得到明确的冲突信号 |

导入/导出类接口未覆盖并发写；导出（07-heavy）实测单请求 47–65ms，是普通接口的约 8 倍。

## Soak 长时稳定性（09-soak.js）

```bash
export LEO_PERF_TOKEN=<共享 token>
bash leo/perf/collect-metrics.sh watch tmp/soak.csv 7300 30 &
LEO_PERF_SOAK_VUS=40 LEO_PERF_SOAK_DURATION=2h k6 run leo/perf/k6/09-soak.js
```

只读、不产生业务数据。40 VU 是该实例接近饱和但不自我压垮的档位
（实测 40 VU 约 1,010 req/s，高于 300 VU 的 909 req/s，说明 300 VU 已过饱和点）。
用 `summary` 看 JVM 堆与连接池的**趋势**而非绝对值——泄漏表现为单调上升。


## Redis 故障降级验证（redis-degradation.sh）

```bash
bash leo/perf/redis-degradation.sh --yes     # 需要 11211 空闲；脚本自行启停后端并负责恢复
```

隔离方式：只把 **leo 后端** 指向一个不存在的 Redis 端口，**不触碰共享 Redis 实例**。

实测结论（详见 docs/reports 下的压测报告）：

| 请求 | Redis 正常 | Redis 不可用 |
|---|---|---|
| `GET /v2.0/health` | 200 | **503 `DEGRADED`**（优雅） |
| `GET /v2.0/version` | 200 | 200（不依赖 Redis） |
| 已登录用户的任何接口 | 200 | **401「登录状态已失效，请重新登录」** |
| `POST /v2.0/auth/login` | 200 | **503「幂等服务暂不可用」** |

即：**Redis 不是可降级的缓存，而是认证链路与幂等过滤器的硬依赖**——一旦不可用，
全部在线用户被静默登出且无法重新登录。`CacheConfig` 的 `CacheErrorHandler` 优雅降级
只覆盖 Spring Cache；认证链路用的是裸 `StringRedisTemplate`，
`AuthenticatedUserCacheService.getActivePrincipal` 对 Redis 调用无 try/catch，
异常直接穿透 `JwtAuthenticationFilter`。

另注意 `scripts/backend/start-dev.sh` 含 Redis 预检，Redis 不可用时**直接拒绝启动**，
因此本脚本绕过该预检以完成降级验证。


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
