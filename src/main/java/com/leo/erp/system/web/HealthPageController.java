package com.leo.erp.system.web;

import com.leo.erp.security.permission.PermissionService;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.database.service.DatabaseStatusService;
import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.time.OffsetDateTime;

@Controller
@ConditionalOnProperty(prefix = "leo.health.page", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HealthPageController {

    private static final Logger log = LoggerFactory.getLogger(HealthPageController.class);

    private final DatabaseStatusService databaseStatusService;
    private final PermissionService permissionService;

    public HealthPageController(DatabaseStatusService databaseStatusService, PermissionService permissionService) {
        this.databaseStatusService = databaseStatusService;
        this.permissionService = permissionService;
    }

    @ResponseBody
    @GetMapping(value = "/system/health", produces = MediaType.TEXT_HTML_VALUE)
    public String health() {
        String checkedAt = OffsetDateTime.now().toString();
        JvmStatus jvm = getJvmStatus();
        if (!isPrivilegedRequest()) {
            log.debug("public health page requested: appStatus=UP");
            return buildPublicPage(checkedAt, jvm);
        }

        DatabaseStatusResponse status = databaseStatusService.getStatus();
        DatabaseStatusResponse.PostgresStatus postgres = status.postgres();
        DatabaseStatusResponse.RedisStatus redis = status.redis();
        String debugOutput = buildDebugOutput(checkedAt, jvm, postgres, redis);

        log.debug(
                "health page requested: appStatus=UP, jvmVersion={}, jvmThreads={}, pgStatus={}, pgConnections={}/{}, redisStatus={}, redisKeys={}",
                jvm.javaVersion(),
                jvm.threadCount(),
                postgres.status(),
                postgres.totalConnections(),
                postgres.maxConnections(),
                redis.status(),
                redis.totalKeys()
        );

        return buildDetailedPage(checkedAt, jvm, postgres, redis, debugOutput);
    }

    private String buildPublicPage(String checkedAt, JvmStatus jvm) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Leo Health Check</title>
                    <style>
                        :root {
                            color-scheme: light;
                            font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
                        }
                        body {
                            margin: 0;
                            padding: 24px;
                            background: #f4f7fb;
                            color: #172033;
                        }
                        .wrap {
                            max-width: 920px;
                            margin: 0 auto;
                        }
                        .hero {
                            padding: 24px 28px;
                            border-radius: 18px;
                            background: linear-gradient(135deg, #173b63, #245b93);
                            color: #fff;
                            box-shadow: 0 18px 40px rgba(23, 59, 99, 0.18);
                        }
                        .hero h1 {
                            margin: 0 0 8px;
                            font-size: 30px;
                        }
                        .hero p {
                            margin: 0;
                            opacity: 0.9;
                        }
                        .card {
                            margin-top: 18px;
                            background: #fff;
                            border-radius: 16px;
                            padding: 20px;
                            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
                        }
                        .status {
                            display: inline-block;
                            padding: 6px 10px;
                            border-radius: 999px;
                            font-size: 13px;
                            font-weight: 700;
                            background: #e7f8ef;
                            color: #157347;
                        }
                        dl {
                            margin: 16px 0 0;
                            display: grid;
                            grid-template-columns: 120px 1fr;
                            row-gap: 10px;
                            column-gap: 12px;
                        }
                        dt {
                            color: #60708a;
                        }
                        dd {
                            margin: 0;
                            word-break: break-word;
                            font-weight: 600;
                        }
                    </style>
                </head>
                <body>
                    <div class="wrap">
                        <section class="hero">
                            <h1>Leo 后端健康检查</h1>
                            <p>公开页仅展示基础可用性，登录后可查看详细运行状态。</p>
                        </section>
                        <article class="card">
                            <h2>应用</h2>
                            <p><span class="status">UP</span></p>
                            <dl>
                                <dt>服务名</dt><dd>leo</dd>
                                <dt>检查时间</dt><dd>%s</dd>
                                <dt>运行时长</dt><dd>%s</dd>
                                <dt>Java 版本</dt><dd>%s</dd>
                                <dt>访问路径</dt><dd>/api/system/health</dd>
                            </dl>
                        </article>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(checkedAt),
                escapeHtml(jvm.uptime()),
                escapeHtml(jvm.javaVersion())
        );
    }

    private String buildDetailedPage(String checkedAt,
                                     JvmStatus jvm,
                                     DatabaseStatusResponse.PostgresStatus postgres,
                                     DatabaseStatusResponse.RedisStatus redis,
                                     String debugOutput) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Leo Health Check</title>
                    <style>
                        :root {
                            color-scheme: light;
                            font-family: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
                        }
                        body {
                            margin: 0;
                            padding: 24px;
                            background: #f4f7fb;
                            color: #172033;
                        }
                        .wrap {
                            max-width: 1080px;
                            margin: 0 auto;
                        }
                        .hero {
                            padding: 24px 28px;
                            border-radius: 18px;
                            background: linear-gradient(135deg, #173b63, #245b93);
                            color: #fff;
                            box-shadow: 0 18px 40px rgba(23, 59, 99, 0.18);
                        }
                        .hero h1 {
                            margin: 0 0 8px;
                            font-size: 30px;
                        }
                        .hero p {
                            margin: 0;
                            opacity: 0.9;
                        }
                        .grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
                            gap: 16px;
                            margin-top: 18px;
                        }
                        .card {
                            background: #fff;
                            border-radius: 16px;
                            padding: 20px;
                            box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
                        }
                        .card h2 {
                            margin: 0 0 16px;
                            font-size: 18px;
                        }
                        .status {
                            display: inline-block;
                            padding: 6px 10px;
                            border-radius: 999px;
                            font-size: 13px;
                            font-weight: 700;
                        }
                        .status-up {
                            background: #e7f8ef;
                            color: #157347;
                        }
                        .status-down {
                            background: #fdeceb;
                            color: #bb2d3b;
                        }
                        .card-wide {
                            grid-column: 1 / -1;
                        }
                        dl {
                            margin: 0;
                            display: grid;
                            grid-template-columns: 120px 1fr;
                            row-gap: 10px;
                            column-gap: 12px;
                        }
                        dt {
                            color: #60708a;
                        }
                        dd {
                            margin: 0;
                            word-break: break-word;
                            font-weight: 600;
                        }
                        pre {
                            margin: 0;
                            padding: 14px 16px;
                            border-radius: 12px;
                            background: #0f172a;
                            color: #d7e3f4;
                            font-size: 13px;
                            line-height: 1.6;
                            overflow-x: auto;
                            white-space: pre-wrap;
                            word-break: break-word;
                        }
                    </style>
                </head>
                <body>
                    <div class="wrap">
                        <section class="hero">
                            <h1>Leo 后端健康检查</h1>
                            <p>服务状态实时检查页面，包含应用、PostgreSQL、Redis 当前状态。</p>
                        </section>
                        <section class="grid">
                            <article class="card">
                                <h2>应用</h2>
                                <p><span class="status %s">%s</span></p>
                                <dl>
                                    <dt>服务名</dt><dd>leo</dd>
                                    <dt>检查时间</dt><dd>%s</dd>
                                    <dt>访问路径</dt><dd>/api/system/health</dd>
                                </dl>
                            </article>
                            <article class="card">
                                <h2>JVM</h2>
                                <p><span class="status %s">%s</span></p>
                                <dl>
                                    <dt>Java 版本</dt><dd>%s</dd>
                                    <dt>JVM</dt><dd>%s</dd>
                                    <dt>运行时长</dt><dd>%s</dd>
                                    <dt>堆内存</dt><dd>%s / %s</dd>
                                    <dt>非堆内存</dt><dd>%s</dd>
                                    <dt>线程数</dt><dd>%d</dd>
                                    <dt>处理器</dt><dd>%d</dd>
                                </dl>
                            </article>
                            <article class="card">
                                <h2>PostgreSQL</h2>
                                <p><span class="status %s">%s</span></p>
                                <dl>
                                    <dt>主机</dt><dd>%s:%d</dd>
                                    <dt>数据库</dt><dd>%s</dd>
                                    <dt>版本</dt><dd>%s</dd>
                                    <dt>连接数</dt><dd>%d / %d</dd>
                                    <dt>活跃连接</dt><dd>%d</dd>
                                    <dt>数据库大小</dt><dd>%s</dd>
                                    <dt>表数量</dt><dd>%d</dd>
                                    <dt>启动时间</dt><dd>%s</dd>
                                </dl>
                            </article>
                            <article class="card">
                                <h2>Redis</h2>
                                <p><span class="status %s">%s</span></p>
                                <dl>
                                    <dt>主机</dt><dd>%s:%d / DB %d</dd>
                                    <dt>版本</dt><dd>%s</dd>
                                    <dt>已用内存</dt><dd>%s</dd>
                                    <dt>峰值内存</dt><dd>%s</dd>
                                    <dt>Key 数量</dt><dd>%d</dd>
                                    <dt>客户端数</dt><dd>%d</dd>
                                    <dt>运行时长</dt><dd>%s</dd>
                                    <dt>命中率</dt><dd>%.2f%%</dd>
                                </dl>
                            </article>
                            <article class="card card-wide">
                                <h2>Debug 输出</h2>
                                <pre>%s</pre>
                            </article>
                        </section>
                    </div>
                </body>
                </html>
                """.formatted(
                statusClass("UP"), "UP",
                escapeHtml(checkedAt),
                statusClass("UP"), "UP",
                escapeHtml(jvm.javaVersion()),
                escapeHtml(jvm.vmName()),
                escapeHtml(jvm.uptime()),
                formatBytes(jvm.heapUsedBytes()),
                formatBytes(jvm.heapMaxBytes()),
                formatBytes(jvm.nonHeapUsedBytes()),
                jvm.threadCount(),
                jvm.availableProcessors(),
                statusClass(postgres.status()), escapeHtml(postgres.status()),
                escapeHtml(postgres.host()), postgres.port(),
                escapeHtml(postgres.database()),
                escapeHtml(postgres.version()),
                postgres.totalConnections(), postgres.maxConnections(),
                postgres.activeConnections(),
                escapeHtml(postgres.databaseSize()),
                postgres.tableCount(),
                escapeHtml(postgres.serverStartTime() == null ? "未知" : postgres.serverStartTime().toString()),
                statusClass(redis.status()), escapeHtml(redis.status()),
                escapeHtml(redis.host()), redis.port(), redis.database(),
                escapeHtml(redis.version()),
                formatBytes(redis.usedMemory()),
                formatBytes(redis.usedMemoryPeak()),
                redis.totalKeys(),
                redis.connectedClients(),
                escapeHtml(redis.uptime()),
                redis.hitRate(),
                escapeHtml(debugOutput)
        );
    }

    private boolean isPrivilegedRequest() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            return false;
        }
        return permissionService.can(principal.id(), "database", "read");
    }

    private String statusClass(String status) {
        return "正常".equals(status) || "UP".equalsIgnoreCase(status) ? "status-up" : "status-down";
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "未知";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.2f MB", bytes / 1024.0 / 1024.0);
        }
        return String.format("%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String buildDebugOutput(
            String checkedAt,
            JvmStatus jvm,
            DatabaseStatusResponse.PostgresStatus postgres,
            DatabaseStatusResponse.RedisStatus redis
    ) {
        return """
                [DEBUG] health page requested
                checkedAt=%s
                app.service=leo
                app.status=UP
                jvm.version=%s
                jvm.vmName=%s
                jvm.uptime=%s
                jvm.heap=%s / %s
                jvm.nonHeap=%s
                jvm.threads=%d
                jvm.processors=%d
                postgres.status=%s
                postgres.host=%s:%d
                postgres.database=%s
                postgres.connections=%d / %d
                postgres.activeConnections=%d
                postgres.size=%s
                postgres.tables=%d
                redis.status=%s
                redis.host=%s:%d/%d
                redis.memory=%s / %s
                redis.keys=%d
                redis.clients=%d
                redis.uptime=%s
                redis.hitRate=%.2f%%
                """.formatted(
                checkedAt,
                jvm.javaVersion(),
                jvm.vmName(),
                jvm.uptime(),
                formatBytes(jvm.heapUsedBytes()),
                formatBytes(jvm.heapMaxBytes()),
                formatBytes(jvm.nonHeapUsedBytes()),
                jvm.threadCount(),
                jvm.availableProcessors(),
                postgres.status(),
                postgres.host(), postgres.port(),
                postgres.database(),
                postgres.totalConnections(), postgres.maxConnections(),
                postgres.activeConnections(),
                postgres.databaseSize(),
                postgres.tableCount(),
                redis.status(),
                redis.host(), redis.port(), redis.database(),
                formatBytes(redis.usedMemory()),
                formatBytes(redis.usedMemoryPeak()),
                redis.totalKeys(),
                redis.connectedClients(),
                redis.uptime(),
                redis.hitRate()
        );
    }

    private JvmStatus getJvmStatus() {
        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        MemoryUsage heapUsage = memoryMxBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMxBean.getNonHeapMemoryUsage();

        return new JvmStatus(
                System.getProperty("java.version", "未知"),
                System.getProperty("java.vm.name", "未知"),
                formatDuration(runtimeMxBean.getUptime()),
                heapUsage.getUsed(),
                heapUsage.getMax(),
                nonHeapUsage.getUsed(),
                threadMxBean.getThreadCount(),
                runtime.availableProcessors()
        );
    }

    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + " 秒";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " 分钟";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + " 小时 " + ((seconds % 3600) / 60) + " 分钟";
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        return days + " 天 " + hours + " 小时";
    }

    private record JvmStatus(
            String javaVersion,
            String vmName,
            String uptime,
            long heapUsedBytes,
            long heapMaxBytes,
            long nonHeapUsedBytes,
            int threadCount,
            int availableProcessors
    ) {
    }
}
