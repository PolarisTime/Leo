package com.leo.erp.system.service;

import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;

@Component
class HealthPageRenderer {

    private static final long BYTES_PER_KB = 1024L;
    private static final long BYTES_PER_MB = BYTES_PER_KB * 1024;
    private static final long BYTES_PER_GB = BYTES_PER_MB * 1024;
    private static final long MILLIS_PER_SECOND = 1000;
    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = 3600;
    private static final long SECONDS_PER_DAY = 86400;

    String renderPublic(String checkedAt, JvmStatus jvm) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Leo Health Check</title>
                    <style>
                        :root{color-scheme:light;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei","Noto Sans CJK SC",sans-serif}
                        body{margin:0;padding:24px;background:#f4f7fb;color:#172033}
                        .wrap{max-width:920px;margin:0 auto}
                        .hero{padding:24px 28px;border-radius:18px;background:linear-gradient(135deg,#173b63,#245b93);color:#fff;box-shadow:0 18px 40px rgba(23,59,99,.18)}
                        .hero h1{margin:0 0 8px;font-size:30px}
                        .hero p{margin:0;opacity:.9}
                        .card{margin-top:18px;background:#fff;border-radius:16px;padding:20px;box-shadow:0 10px 30px rgba(15,23,42,.08)}
                        .status{display:inline-block;padding:6px 10px;border-radius:999px;font-size:13px;font-weight:700;background:#e7f8ef;color:#157347}
                        dl{margin:16px 0 0;display:grid;grid-template-columns:120px 1fr;row-gap:10px;column-gap:12px}
                        dt{color:#60708a}dd{margin:0;word-break:break-word;font-weight:600}
                    </style>
                </head>
                <body><div class="wrap">
                    <section class="hero"><h1>Leo 后端健康检查</h1><p>公开页仅展示基础可用性，登录后可查看详细运行状态。</p></section>
                    <article class="card"><h2>应用</h2><p><span class="status">UP</span></p>
                    <dl><dt>服务名</dt><dd>leo</dd><dt>检查时间</dt><dd>%s</dd><dt>运行时长</dt><dd>%s</dd><dt>Java 版本</dt><dd>%s</dd><dt>访问路径</dt><dd>/api/system/health</dd></dl>
                    </article>
                </div></body></html>
                """.formatted(esc(checkedAt), esc(jvm.uptime()), esc(jvm.javaVersion()));
    }

    String renderDetailed(String checkedAt, JvmStatus jvm, DatabaseStatusResponse.PostgresStatus pg,
                          DatabaseStatusResponse.RedisStatus redis, String debugOutput) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>Leo Health Check</title>
                    <style>
                        :root{color-scheme:light;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Hiragino Sans GB","Microsoft YaHei","Noto Sans CJK SC",sans-serif}
                        body{margin:0;padding:24px;background:#f4f7fb;color:#172033}
                        .wrap{max-width:1080px;margin:0 auto}
                        .hero{padding:24px 28px;border-radius:18px;background:linear-gradient(135deg,#173b63,#245b93);color:#fff;box-shadow:0 18px 40px rgba(23,59,99,.18)}
                        .hero h1{margin:0 0 8px;font-size:30px}.hero p{margin:0;opacity:.9}
                        .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px;margin-top:18px}
                        .card{background:#fff;border-radius:16px;padding:20px;box-shadow:0 10px 30px rgba(15,23,42,.08)}
                        .card h2{margin:0 0 16px;font-size:18px}
                        .status{display:inline-block;padding:6px 10px;border-radius:999px;font-size:13px;font-weight:700}
                        .status-up{background:#e7f8ef;color:#157347}.status-down{background:#fdeceb;color:#bb2d3b}
                        .card-wide{grid-column:1/-1}
                        dl{margin:0;display:grid;grid-template-columns:120px 1fr;row-gap:10px;column-gap:12px}
                        dt{color:#60708a}dd{margin:0;word-break:break-word;font-weight:600}
                        pre{margin:0;padding:14px 16px;border-radius:12px;background:#0f172a;color:#d7e3f4;font-size:13px;line-height:1.6;overflow-x:auto;white-space:pre-wrap;word-break:break-word}
                    </style>
                </head>
                <body><div class="wrap">
                    <section class="hero"><h1>Leo 后端健康检查</h1><p>服务状态实时检查页面，包含应用、PostgreSQL、Redis 当前状态。</p></section>
                    <section class="grid">
                    <article class="card"><h2>应用</h2><p><span class="status %s">%s</span></p>
                    <dl><dt>服务名</dt><dd>leo</dd><dt>检查时间</dt><dd>%s</dd><dt>访问路径</dt><dd>/api/system/health</dd></dl></article>
                    <article class="card"><h2>JVM</h2><p><span class="status %s">%s</span></p>
                    <dl><dt>Java 版本</dt><dd>%s</dd><dt>JVM</dt><dd>%s</dd><dt>运行时长</dt><dd>%s</dd><dt>堆内存</dt><dd>%s / %s</dd><dt>非堆内存</dt><dd>%s</dd><dt>线程数</dt><dd>%d</dd><dt>处理器</dt><dd>%d</dd></dl></article>
                    <article class="card"><h2>PostgreSQL</h2><p><span class="status %s">%s</span></p>
                    <dl><dt>主机</dt><dd>%s:%d</dd><dt>数据库</dt><dd>%s</dd><dt>版本</dt><dd>%s</dd><dt>连接数</dt><dd>%d / %d</dd><dt>活跃连接</dt><dd>%d</dd><dt>数据库大小</dt><dd>%s</dd><dt>表数量</dt><dd>%d</dd><dt>启动时间</dt><dd>%s</dd></dl></article>
                    <article class="card"><h2>Redis</h2><p><span class="status %s">%s</span></p>
                    <dl><dt>主机</dt><dd>%s:%d / DB %d</dd><dt>版本</dt><dd>%s</dd><dt>已用内存</dt><dd>%s</dd><dt>峰值内存</dt><dd>%s</dd><dt>Key 数量</dt><dd>%d</dd><dt>客户端数</dt><dd>%d</dd><dt>运行时长</dt><dd>%s</dd><dt>命中率</dt><dd>%.2f%%</dd></dl></article>
                    <article class="card card-wide"><h2>Debug 输出</h2><pre>%s</pre></article>
                    </section></div></body></html>
                """.formatted(
                statusClass("UP"), "UP", esc(checkedAt),
                statusClass("UP"), "UP", esc(jvm.javaVersion()), esc(jvm.vmName()), esc(jvm.uptime()),
                formatBytes(jvm.heapUsedBytes()), formatBytes(jvm.heapMaxBytes()), formatBytes(jvm.nonHeapUsedBytes()),
                jvm.threadCount(), jvm.availableProcessors(),
                statusClass(pg.status()), esc(pg.status()), esc(pg.host()), pg.port(), esc(pg.database()), esc(pg.version()),
                pg.totalConnections(), pg.maxConnections(), pg.activeConnections(),
                esc(pg.databaseSize()), pg.tableCount(),
                esc(pg.serverStartTime() == null ? "未知" : pg.serverStartTime().toString()),
                statusClass(redis.status()), esc(redis.status()), esc(redis.host()), redis.port(), redis.database(),
                esc(redis.version()), formatBytes(redis.usedMemory()), formatBytes(redis.usedMemoryPeak()),
                redis.totalKeys(), redis.connectedClients(), esc(redis.uptime()), redis.hitRate(),
                esc(debugOutput));
    }

    String renderDebug(String checkedAt, JvmStatus jvm, DatabaseStatusResponse.PostgresStatus pg,
                       DatabaseStatusResponse.RedisStatus redis) {
        return """
                [DEBUG] health page requested
                checkedAt=%s app.service=leo app.status=UP
                jvm.version=%s jvm.vmName=%s jvm.uptime=%s jvm.heap=%s / %s jvm.nonHeap=%s jvm.threads=%d jvm.processors=%d
                postgres.status=%s postgres.host=%s:%d postgres.database=%s postgres.connections=%d / %d postgres.active=%d postgres.size=%s postgres.tables=%d
                redis.status=%s redis.host=%s:%d/%d redis.memory=%s / %s redis.keys=%d redis.clients=%d redis.uptime=%s redis.hitRate=%.2f%%
                """.formatted(
                checkedAt, jvm.javaVersion(), jvm.vmName(), jvm.uptime(),
                formatBytes(jvm.heapUsedBytes()), formatBytes(jvm.heapMaxBytes()), formatBytes(jvm.nonHeapUsedBytes()),
                jvm.threadCount(), jvm.availableProcessors(),
                pg.status(), pg.host(), pg.port(), pg.database(), pg.totalConnections(), pg.maxConnections(),
                pg.activeConnections(), pg.databaseSize(), pg.tableCount(),
                redis.status(), redis.host(), redis.port(), redis.database(),
                formatBytes(redis.usedMemory()), formatBytes(redis.usedMemoryPeak()),
                redis.totalKeys(), redis.connectedClients(), redis.uptime(), redis.hitRate());
    }

    JvmStatus getJvmStatus() {
        var runtime = Runtime.getRuntime();
        var runtimeMx = ManagementFactory.getRuntimeMXBean();
        var memoryMx = ManagementFactory.getMemoryMXBean();
        var threadMx = ManagementFactory.getThreadMXBean();
        MemoryUsage heap = memoryMx.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryMx.getNonHeapMemoryUsage();
        return new JvmStatus(
                System.getProperty("java.version", "未知"),
                System.getProperty("java.vm.name", "未知"),
                formatDuration(runtimeMx.getUptime()),
                heap.getUsed(), heap.getMax(), nonHeap.getUsed(),
                threadMx.getThreadCount(), runtime.availableProcessors());
    }

    private String esc(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String statusClass(String s) {
        return "正常".equals(s) || "UP".equalsIgnoreCase(s) ? "status-up" : "status-down";
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "未知";
        if (bytes < BYTES_PER_KB) return bytes + " B";
        if (bytes < BYTES_PER_MB) return String.format("%.2f KB", bytes / (double) BYTES_PER_KB);
        if (bytes < BYTES_PER_GB) return String.format("%.2f MB", bytes / (double) BYTES_PER_MB);
        return String.format("%.2f GB", bytes / (double) BYTES_PER_GB);
    }

    private String formatDuration(long ms) {
        long s = ms / MILLIS_PER_SECOND;
        if (s < SECONDS_PER_MINUTE) return s + " 秒";
        if (s < SECONDS_PER_HOUR) return (s / SECONDS_PER_MINUTE) + " 分钟";
        if (s < SECONDS_PER_DAY) return (s / SECONDS_PER_HOUR) + " 小时 " + ((s % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE) + " 分钟";
        return (s / SECONDS_PER_DAY) + " 天 " + ((s % SECONDS_PER_DAY) / SECONDS_PER_HOUR) + " 小时";
    }

    record JvmStatus(String javaVersion, String vmName, String uptime, long heapUsedBytes,
                     long heapMaxBytes, long nonHeapUsedBytes, int threadCount, int availableProcessors) {}
}
