# 2026-10-09 生产事故 —— P0 处置运行手册（需要 root）

配套：`docs/ops/2026-10-09-生产环境存储耗尽故障报告.md`、`scripts/monitor/README.md`。

本文件只列**必须 root** 的三件事。全部为可复核的显式动作，含回滚方式。
执行前请确认 `/instance/steelx/deploy.lock` 无发布在跑（`flock -n /instance/steelx/deploy.lock true`）。

---

## P0-1 停止 `mcsm-web` 崩溃重启循环

**为什么**：`mcsm-web.service` 每 5 秒崩溃一次（2026-10-09 11:19:45 时重启计数已达 **1,021,129**，
至少自 09-23 起），每次都触发 core dump 尝试。该单元为 `disabled`（属被手工拉起），
与 `mcsm-daemon` 抢占 `:23333`（`EADDRINUSE`）。它持续向 journal 与 `/` 写入，
既干扰告警又消耗根盘，是本次事故中明确的噪音源。

```bash
# 1) 现状确认
systemctl status mcsm-web --no-pager | head -5
journalctl -u mcsm-web --no-pager | tail -5          # 应见 EADDRINUSE :::23333
ss -ltnp '( sport = :23333 )'                        # 确认当前监听者

# 2) 停止并禁止自启（该单元本就 disabled，--now 停止运行实例）
sudo systemctl disable --now mcsm-web

# 3) 验证：不再有新的重启日志
sleep 30
journalctl -u mcsm-web --since "-30 seconds" --no-pager | wc -l    # 期望 0
systemctl status mcsm-web --no-pager | head -3                     # 期望 inactive (dead)
```

**回滚**：`sudo systemctl start mcsm-web`（但会立刻恢复崩溃循环——若要长期使用，
应先解决它与 `mcsm-daemon` 的 `:23333` 端口冲突，例如修改其中一个的监听端口）。

> 注：`mcsm-web` 属 MCSManager 游戏面板，与本 ERP 生产无关，停止它不影响 `leo`。

---

## P0-2 安装生产健康守卫（磁盘 / DB / Redis / 端点 / 日志）

**为什么**：本次故障全程无告警。健康端点实现本身合格，缺的是定期轮询与基础设施水位采集。

```bash
cd /home/instance/Gemini

# 1) 预演
bash scripts/monitor/install-health-guard.sh --dry-run

# 2) 安装（装脚本 + unit + timer，并启用）
sudo bash scripts/monitor/install-health-guard.sh

# 3) 验证
systemctl list-timers leo-health-guard.timer --no-pager
journalctl -u leo-health-guard -n 30 --no-pager     # 期望 RESULT=OK
```

**回滚**：

```bash
sudo systemctl disable --now leo-health-guard.timer
sudo rm -f /etc/systemd/system/leo-health-guard.{service,timer} \
           /instance/steelx/shared/health-guard.sh
sudo systemctl daemon-reload
```

### 接入告警通道

默认只写 journal。若已有告警网关（webhook 接收 JSON POST）：

```bash
sudo systemctl edit leo-health-guard.service
# 在打开的 override 中加入：
#   [Service]
#   Environment=LEO_GUARD_ALERT_WEBHOOK=https://<告警网关>/ingest
sudo systemctl daemon-reload
```

**务必同时保留日志侧规则**（守卫的告警外发失败不影响其退出码）：

- `journalctl -u leo-health-guard` 出现 `RESULT=WARN` / `RESULT=CRIT`
- `systemctl --failed` 出现 `leo-health-guard.service`（退出码 1/2 会标记失败）
- 应用日志 `SQLState: 53100`、`SQLState: 57P03`、`MISCONF`、`No space left on device`
- `/` 或 `/home` 水位 > 80% / > 90%

---

## P0-3 给 Docker 容器日志加上限

**为什么**：`/etc/docker/daemon.json` 不存在，`Logging Driver: json-file` 且容器
`LogConfig={json-file map[]}` 为空 —— **`max-size`/`max-file` 全部未设置**，容器日志可无限增长。
本次事故中它不是主因（当前最大仅 15.6 MB），但属于必须消除的长期风险。

```bash
# 1) 现状
docker info 2>/dev/null | grep -i 'Logging Driver'
ls -la /etc/docker/daemon.json 2>&1     # 期望 No such file（即从未配置）

# 2) 写入配置（保留可能已有的其它键；此处 daemon.json 不存在，直接创建）
sudo tee /etc/docker/daemon.json >/dev/null <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "3"
  }
}
JSON

# 3) 校验 JSON 合法后重载（reload 不会重启容器）
python3 -c "import json,sys; json.load(open('/etc/docker/daemon.json')); print('JSON OK')"
sudo systemctl reload docker

# 4) 验证：司机为 json-file 且默认带上限
docker info 2>/dev/null | grep -i -A3 'Logging Driver'
docker run --rm alpine:3.20 true 2>/dev/null; \
  docker inspect --format '{{.HostConfig.LogConfig}}' "$(docker ps -lq)" 2>/dev/null
```

**适用性**：`log-opts` 只对**新建**容器生效，已运行的容器（napcat、cli-proxy-api）
需重建容器才会应用；其现有日志不受影响，也不会被自动截断。

**回滚**：删除 `/etc/docker/daemon.json` 后 `sudo systemctl reload docker`。

---

## 建议的验收顺序与判据

| 步骤 | 判据 |
| --- | --- |
| P0-1 | `mcsm-web` 30 秒内无新日志，状态 `inactive (dead)` |
| P0-2 | `leo-health-guard.timer` 处于 `active (waiting)`；连续两次执行 `journalctl` 均为 `RESULT=OK` |
| P0-3 | `docker info` 的 Logging Driver 为 `json-file`，新建容器 `LogConfig` 含 `max-size=100m` |
| 回归 | `/api/v2.0/health` = 200 UP；`df -h /` 稳定；`journalctl --since "-5 min"` 无 ENOSPC |

## 尚未纳入本次 P0（见报告 P1）

- 根盘容量单点：把 PG 数据目录/`pg_wal`、Redis 数据迁至 875 G 的 `/home`，或扩容 `/`；
- 打开可归因能力（auditd 文件审计或定时占用快照），以便下次能定位"谁写满了磁盘"；
- 安装 sysstat/netdata 采集磁盘与 IO 历史；
- 审计缺口对账（10:20–11:20 期间的操作日志未落库）。
