package com.leo.erp.system.service;

import com.leo.erp.system.database.web.dto.DatabaseStatusResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class HealthPageRendererTest {

    private final HealthPageRenderer renderer = new HealthPageRenderer();

    @Test
    void shouldRenderPublicPage_withValidData() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "17.0.1", "OpenJDK 64-Bit Server VM", "2 小时 15 分钟",
                100_000_000L, 500_000_000L, 50_000_000L, 50, 8
        );

        String html = renderer.renderPublic("2024-01-01 12:00:00", jvm);

        assertThat(html).contains("Leo 后端健康检查");
        assertThat(html).contains("2024-01-01 12:00:00");
        assertThat(html).contains("2 小时 15 分钟");
        assertThat(html).contains("17.0.1");
    }

    @Test
    void shouldEscapeHtml_inPublicPage() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "<script>alert(1)</script>", "VM", "1 秒",
                0L, 0L, 0L, 1, 1
        );

        String html = renderer.renderPublic("2024-01-01", jvm);

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void shouldRenderDetailedPage_withAllData() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "17.0.1", "OpenJDK 64-Bit Server VM", "1 天 2 小时",
                200_000_000L, 1_000_000_000L, 100_000_000L, 100, 8
        );
        var pg = new DatabaseStatusResponse.PostgresStatus(
                "localhost", 5432, "leo", "15.0",
                20L, 5L, 100L, "100 MB", 50L, LocalDateTime.now(), "正常"
        );
        var redis = new DatabaseStatusResponse.RedisStatus(
                "localhost", 6379, 0, "7.0.0",
                50_000_000L, 100_000_000L, 1000L, 10L, "1 天", 9500L, 500L, 95.5, "正常"
        );

        String html = renderer.renderDetailed("2024-01-01 12:00:00", jvm, pg, redis, "debug output");

        assertThat(html).contains("Leo 后端健康检查");
        assertThat(html).contains("PostgreSQL");
        assertThat(html).contains("Redis");
        assertThat(html).contains("debug output");
    }

    @Test
    void shouldRenderDebugPage_withAllData() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "17.0.1", "OpenJDK", "1 小时",
                100_000_000L, 500_000_000L, 50_000_000L, 50, 8
        );
        var pg = new DatabaseStatusResponse.PostgresStatus(
                "localhost", 5432, "leo", "15.0",
                20L, 5L, 100L, "100 MB", 50L, LocalDateTime.now(), "正常"
        );
        var redis = new DatabaseStatusResponse.RedisStatus(
                "localhost", 6379, 0, "7.0.0",
                50_000_000L, 100_000_000L, 1000L, 10L, "1 天", 9500L, 500L, 95.5, "正常"
        );

        String debug = renderer.renderDebug("2024-01-01 12:00:00", jvm, pg, redis);

        assertThat(debug).contains("[DEBUG]");
        assertThat(debug).contains("postgres.status=正常");
        assertThat(debug).contains("redis.status=正常");
    }

    @Test
    void shouldGetJvmStatus() {
        HealthPageRenderer.JvmStatus jvm = renderer.getJvmStatus();

        assertThat(jvm).isNotNull();
        assertThat(jvm.javaVersion()).isNotBlank();
        assertThat(jvm.vmName()).isNotBlank();
        assertThat(jvm.heapMaxBytes()).isGreaterThan(0);
        assertThat(jvm.availableProcessors()).isGreaterThan(0);
    }

    @Test
    void shouldHandleNegativeBytes() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "17", "VM", "1 秒", -1L, -1L, -1L, 1, 1
        );
        var pg = new DatabaseStatusResponse.PostgresStatus(
                "localhost", 5432, "leo", "15.0",
                20L, 5L, 100L, "100 MB", 50L, LocalDateTime.now(), "正常"
        );
        var redis = new DatabaseStatusResponse.RedisStatus(
                "localhost", 6379, 0, "7.0.0",
                50_000_000L, 100_000_000L, 1000L, 10L, "1 天", 9500L, 500L, 95.5, "正常"
        );

        String html = renderer.renderDetailed("2024-01-01", jvm, pg, redis, "debug");

        assertThat(html).contains("未知");
    }

    @Test
    void shouldFormatKiloBytes() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "17", "VM", "1 秒", 2048L, 4096L, 1024L, 1, 1
        );
        var pg = new DatabaseStatusResponse.PostgresStatus(
                "localhost", 5432, "leo", "15.0",
                20L, 5L, 100L, "100 MB", 50L, LocalDateTime.now(), "正常"
        );
        var redis = new DatabaseStatusResponse.RedisStatus(
                "localhost", 6379, 0, "7.0.0",
                50_000_000L, 100_000_000L, 1000L, 10L, "1 天", 9500L, 500L, 95.5, "正常"
        );

        String html = renderer.renderDetailed("2024-01-01", jvm, pg, redis, "debug");

        assertThat(html).contains("KB");
    }

    @Test
    void shouldFormatMegaBytes() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "17", "VM", "1 秒", 2_000_000L, 4_000_000L, 1_000_000L, 1, 1
        );
        var pg = new DatabaseStatusResponse.PostgresStatus(
                "localhost", 5432, "leo", "15.0",
                20L, 5L, 100L, "100 MB", 50L, LocalDateTime.now(), "正常"
        );
        var redis = new DatabaseStatusResponse.RedisStatus(
                "localhost", 6379, 0, "7.0.0",
                50_000_000L, 100_000_000L, 1000L, 10L, "1 天", 9500L, 500L, 95.5, "正常"
        );

        String html = renderer.renderDetailed("2024-01-01", jvm, pg, redis, "debug");

        assertThat(html).contains("MB");
    }

    @Test
    void shouldFormatGigaBytes() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "17", "VM", "1 秒", 2_000_000_000L, 4_000_000_000L, 1_000_000_000L, 1, 1
        );
        var pg = new DatabaseStatusResponse.PostgresStatus(
                "localhost", 5432, "leo", "15.0",
                20L, 5L, 100L, "100 MB", 50L, LocalDateTime.now(), "正常"
        );
        var redis = new DatabaseStatusResponse.RedisStatus(
                "localhost", 6379, 0, "7.0.0",
                50_000_000L, 100_000_000L, 1000L, 10L, "1 天", 9500L, 500L, 95.5, "正常"
        );

        String html = renderer.renderDetailed("2024-01-01", jvm, pg, redis, "debug");

        assertThat(html).contains("GB");
    }

    @Test
    void shouldFormatSeconds() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "17", "VM", "30 秒", 0L, 0L, 0L, 1, 1
        );

        String html = renderer.renderPublic("2024-01-01", jvm);

        assertThat(html).contains("30 秒");
    }

    @Test
    void shouldFormatMinutes() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "17", "VM", "5 分钟", 0L, 0L, 0L, 1, 1
        );

        String html = renderer.renderPublic("2024-01-01", jvm);

        assertThat(html).contains("5 分钟");
    }

    @Test
    void shouldFormatDays() {
        var jvm = new HealthPageRenderer.JvmStatus(
                "17", "VM", "3 天 2 小时", 0L, 0L, 0L, 1, 1
        );

        String html = renderer.renderPublic("2024-01-01", jvm);

        assertThat(html).contains("3 天 2 小时");
    }
}
