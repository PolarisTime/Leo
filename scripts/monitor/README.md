# Leo ERP 生产健康守卫（P0）

对应事故：`docs/ops/2026-10-09-生产环境存储耗尽故障报告.md`。
本目录补齐该事故暴露的**监控盲区**，并把需要 root 的处置动作固化为可复核的步骤。

## 为什么需要它

本次事故中：

| 现象 | 现有监控是否发现 |
| --- | --- |
| 根文件系统 `/` 被写满约 1 小时（10:19:25–11:20:02） | ❌ 无磁盘水位采集（未安装 sysstat，`/var/log/sa` 不存在） |
| PostgreSQL 因无法写盘 PANIC 并循环崩溃恢复 6 分钟 | ❌ postmaster 进程未重启，`systemd` 仍显示 `active (running)` |
| 应用侧 `SQLState 53100`（写盘失败，53 次）/ `57P03`（恢复中，21 次） | ❌ 无日志特征告警 |
| Redis 因 RDB 失败拒写（`MISCONF`） | ❌ 无 Redis 可写性探测 |
| 依赖数据库的接口返回 503（21 次请求） | ⚠️ `/api/v2.0/health` 已做 DB/Redis 真实探测（v12.2.0 即包含），但**只有被轮询才有意义**，当时没有轮询与告警 |

结论：`/api/v2.0/health` 的实现本身是合格的，缺的是**「定期轮询 + 基础设施水位 + 日志特征」三块采集**与**告警通道**。

## 组成

| 文件 | 作用 |
| --- | --- |
| `health-guard.sh` | 主守卫脚本（只读为主，唯一写操作是 Redis 探针键，TTL 5s 且立即删除） |
| `systemd/leo-health-guard.service` | oneshot 服务，退出码 0/1/2 对应 OK/WARN/CRIT |
| `systemd/leo-health-guard.timer` | 每 60 秒触发一次 |
| `install-health-guard.sh` | 幂等安装器（需要 root），支持 `--dry-run` |
| `health-guard.test.sh` | 自测（20 项断言）：告警必须触发、健康不得误报、异常输入不得崩溃 |

## 自测

```bash
bash scripts/monitor/health-guard.test.sh
```

覆盖：参数与用法、磁盘阈值分支、PostgreSQL 分支（用伪造 `psql` 注入「崩溃恢复中」与
「无法识别输出」）、日志时间窗（窗口内命中 / 窗口外忽略 / 非 JSON 不误报 / 日志不可读降级为 WARN）、
HTTP 端点、Redis 认证失败，以及**用真实生产日志复核时间窗**（900s 不误报历史、
24h 可命中事故特征）。当前结果：`PASS=20 FAIL=0`。

## 检查项与阈值（均可用环境变量覆盖）

| 检查 | 默认阈值 | 判级 |
| --- | --- | --- |
| 磁盘水位 `/`、`/home` | WARN ≥ 80%，CRIT ≥ 90% | 磁盘是本次事故根因，故阈值为运维常用值 |
| inode 水位 | WARN ≥ 80%，CRIT ≥ 90% | 提前发现小文件耗尽 |
| PostgreSQL | `pg_is_in_recovery()=false` 且 `SELECT 1` 成功 | 处于恢复模式即 CRIT |
| Redis | `PING` 返回 PONG | 失败 CRIT |
| Redis 可写性 | `SET <probe> EX 5` 返回 OK 后立即 `DEL` | 被拒（MISCONF/只读）即 CRIT |
| 业务健康端点 | `200` 且响应含 `"UP"` | 其余 CRIT |
| 应用日志特征 | 最近 900s 内 `53100`/`57P03`/`MISCONF`/`No space left on device`/`recovery mode` | 命中即 CRIT |
| journal | 最近 900s 内 `No space left on device` | 命中即 CRIT |

`LEO_GUARD_MOUNTS`（默认 `/:/home`）、`LEO_GUARD_HEALTH_URL`、`LEO_GUARD_LOG_FILE`、
`LEO_GUARD_ENV_FILE` 等见脚本头部注释。

## 安装

```bash
# 1) 预演，确认动作无误
bash scripts/monitor/install-health-guard.sh --dry-run

# 2) 安装（需要 root；本仓库沙箱内 sudo 被 no_new_privs 阻断，请在宿主机直接执行）
sudo bash scripts/monitor/install-health-guard.sh

# 3) 验证
systemctl list-timers leo-health-guard.timer
journalctl -u leo-health-guard -n 30 --no-pager
```

安装器会：把守卫装到 `/instance/steelx/shared/health-guard.sh`（0755，属主为共享目录属主），
安装并启用 timer，随后立即跑一次自检并打印结果。幂等，可重复执行。

## 健康输出示例

```
[OK] disk/ - / 空间 68%, inode 2%
[OK] disk/home - /home 空间 45%, inode 2%
[OK] postgres - master_prod_cutover_current_20260713_102500 可连接且未处于恢复模式
[OK] redis - 127.0.0.1:16379/db3 PING 与写入探针均正常
[OK] health-api - HTTP 200 UP
[OK] applog - 最近 900s 无存储/数据库致命错误
[OK] journal - journal 无 ENOSPC 记录
RESULT=OK host=PolarisTime problems=0
```

`--json` 输出机器可读结果，便于接入外部告警网关（`LEO_GUARD_ALERT_WEBHOOK`）。

## 告警通道配置

默认只写 journal（返回码 0/1/2 + 文本行），可用日志采集侧规则告警。若已有告警网关：

```bash
# 在 /etc/systemd/system/leo-health-guard.service 中启用
Environment=LEO_GUARD_ALERT_WEBHOOK=https://<你的告警网关>/ingest
```

守卫会在 WARN/CRIT 时 POST 一条 JSON（`host`/`level`/`script`/`problems`）。
**注意：外发失败不影响退出码**，因此不要把"是否收到告警"当作唯一判据，应同时保留日志侧规则。

## 手动只读自检

```bash
LEO_GUARD_ENV_FILE=/instance/steelx/shared/steelx.env \
  /instance/steelx/shared/health-guard.sh --no-redis-write-probe
```

`--no-redis-write-probe` 完全不写 Redis，适合在只想观察时使用。
