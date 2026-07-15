# Leo 可观测基础设施

该目录提供单机生产部署所需的 Alloy、Loki、Tempo、Prometheus 和 Grafana 配置，不依赖项目根目录中已禁用的 Docker Compose。

## 网络边界

所有采集和查询端口默认只监听 `127.0.0.1`：

| 组件 | 端口 | 用途 |
| --- | --- | --- |
| Alloy | 4317、4318 | 接收 Leo OTLP Trace |
| Loki | 3100 | 保存 ECS JSON 日志 |
| Tempo | 3200、14317、14318 | 查询 Trace、接收 Alloy 转发 |
| Prometheus | 9090 | 拉取指标和执行告警规则 |
| Leo management | 57218 | 暴露 health 与 prometheus |

Grafana 的访问入口、TLS 和认证由独立运维配置负责，不应直接暴露上述内部端口。

## 应用环境变量

```properties
LEO_OTLP_TRACING_EXPORT_ENABLED=true
LEO_OTLP_TRACING_ENDPOINT=http://127.0.0.1:4318/v1/traces
LEO_TRACING_SAMPLING_PROBABILITY=0.1
LEO_MANAGEMENT_SERVER_ADDRESS=127.0.0.1
LEO_MANAGEMENT_SERVER_PORT=57218
```

## 文件布局

将配置安装到对应组件的标准目录：

- Alloy：`/etc/alloy/config.alloy`
- Loki：`/etc/loki/loki.yml`
- Tempo：`/etc/tempo/tempo.yml`
- Prometheus：`/etc/prometheus/prometheus.yml` 与 `/etc/prometheus/rules/leo-alerts.yml`
- Grafana：`/etc/grafana/provisioning/...`

运行账户必须只读访问 `/home/instance/steelx/logs/backend.log`，并分别拥有 `/var/lib/loki`、`/var/lib/tempo` 和组件数据目录。启用服务前先使用各组件自带的配置校验命令检查当前安装版本，再逐个启动并检查 Prometheus targets、Loki 写入和 Tempo Trace。

生产安装、端口开放和服务启动属于运维变更，必须通过部署审批执行；应用发布不会自动安装或启动这些组件。
