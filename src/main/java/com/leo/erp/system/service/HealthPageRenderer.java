package com.leo.erp.system.service;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;

@Component
public class HealthPageRenderer {

    private static final long MILLIS_PER_SECOND = 1000;
    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = 3600;
    private static final long SECONDS_PER_DAY = 86400;

    String render(String checkedAt, JvmStatus jvm) {
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
                        .hero{padding:24px 28px;border-radius:18px;background:#173b63;color:#fff;box-shadow:0 18px 40px rgba(23,59,99,.18)}
                        .hero h1{margin:0 0 8px;font-size:30px}.hero p{margin:0;opacity:.9}
                        .card{margin-top:18px;background:#fff;border-radius:16px;padding:20px;box-shadow:0 10px 30px rgba(15,23,42,.08)}
                        .status{display:inline-block;padding:6px 10px;border-radius:999px;font-size:13px;font-weight:700;background:#e7f8ef;color:#157347}
                        dl{margin:16px 0 0;display:grid;grid-template-columns:120px 1fr;row-gap:10px;column-gap:12px}
                        dt{color:#60708a}dd{margin:0;word-break:break-word;font-weight:600}
                    </style>
                </head>
                <body><div class="wrap">
                    <section class="hero"><h1>Leo 后端健康检查</h1><p>基础服务可用性与运行时状态。</p></section>
                    <article class="card"><h2>应用</h2><p><span class="status">UP</span></p>
                    <dl><dt>服务名</dt><dd>leo</dd><dt>检查时间</dt><dd>%s</dd><dt>运行时长</dt><dd>%s</dd><dt>Java 版本</dt><dd>%s</dd><dt>访问路径</dt><dd>/api/system/health</dd></dl>
                    </article>
                </div></body></html>
                """.formatted(escape(checkedAt), escape(jvm.uptime()), escape(jvm.javaVersion()));
    }

    JvmStatus getJvmStatus() {
        var runtimeMx = ManagementFactory.getRuntimeMXBean();
        return new JvmStatus(
                System.getProperty("java.version", "未知"),
                formatDuration(runtimeMx.getUptime())
        );
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String formatDuration(long milliseconds) {
        long seconds = milliseconds / MILLIS_PER_SECOND;
        if (seconds < SECONDS_PER_MINUTE) {
            return seconds + " 秒";
        }
        if (seconds < SECONDS_PER_HOUR) {
            return (seconds / SECONDS_PER_MINUTE) + " 分钟";
        }
        if (seconds < SECONDS_PER_DAY) {
            return (seconds / SECONDS_PER_HOUR) + " 小时 "
                    + ((seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE) + " 分钟";
        }
        return (seconds / SECONDS_PER_DAY) + " 天 "
                + ((seconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR) + " 小时";
    }

    record JvmStatus(String javaVersion, String uptime) {
    }
}
