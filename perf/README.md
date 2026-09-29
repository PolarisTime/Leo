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
│   └── 06-login.js            登录专项：量化同账号/多账号并发登录行为
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

# 全流程
bash leo/perf/run.sh all
```

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
