package com.leo.erp.market.steelx;

import com.leo.erp.common.error.BusinessException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SteelxFetcher} 重试边界测试：用本地 HTTP 服务脚本化响应序列，
 * 断言「哪些失败会重试、重试次数上限、以及重试后仍失败时的对外异常」。
 */
class SteelxFetcherTest {

    private static final class Script {
        private final List<Response> responses = new CopyOnWriteArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();
        private final Response fallback = new Response(200, "fallback-html");

        Script then(int status, String body) {
            responses.add(new Response(status, body));
            return this;
        }

        private Response next() {
            int index = calls.getAndIncrement();
            return index < responses.size() ? responses.get(index) : fallback;
        }
    }

    private record Response(int status, String body) {
    }

    private HttpServer server;
    private Script script;

    @BeforeEach
    void startServer() throws IOException {
        script = new Script();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/quotation", exchange -> {
            Response response = script.next();
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/quotation";
    }

    private SteelxFetcher fetcher(int maxAttempts) {
        SteelxProperties properties = new SteelxProperties();
        properties.setRequestTimeoutMs(3000);
        properties.setFetchSshHost("");
        properties.getRetry().setMaxAttempts(maxAttempts);
        properties.getRetry().setInitialBackoffMillis(0L);
        properties.getRetry().setMaxBackoffMillis(0L);
        return new SteelxFetcher(properties);
    }

    @Test
    void 首次成功时只请求一次() {
        script.then(200, "<html>杭州建材</html>");

        assertThat(fetcher(3).fetch(url(), "西本杭州报价")).isEqualTo("<html>杭州建材</html>");
        assertThat(script.calls.get()).isEqualTo(1);
    }

    @Test
    void 服务端5xx后重试并成功() {
        script.then(502, "bad-gateway").then(200, "ok-html");

        assertThat(fetcher(3).fetch(url(), "西本杭州报价")).isEqualTo("ok-html");
        assertThat(script.calls.get()).isEqualTo(2);
    }

    @Test
    void 限流429会重试() {
        script.then(429, "too-many").then(200, "ok-html");

        assertThat(fetcher(3).fetch(url(), "西本杭州报价")).isEqualTo("ok-html");
        assertThat(script.calls.get()).isEqualTo(2);
    }

    @Test
    void 空响应会重试() {
        script.then(200, "   ").then(200, "ok-html");

        assertThat(fetcher(3).fetch(url(), "西本杭州报价")).isEqualTo("ok-html");
        assertThat(script.calls.get()).isEqualTo(2);
    }

    @Test
    void 客户端4xx不重试() {
        script.then(403, "forbidden");

        assertThatThrownBy(() -> fetcher(3).fetch(url(), "西本杭州报价"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP 403");
        assertThat(script.calls.get()).isEqualTo(1);
    }

    @Test
    void 重试次数用尽后抛出业务异常并标注尝试次数() {
        script.then(503, "boom").then(503, "boom");

        assertThatThrownBy(() -> fetcher(2).fetch(url(), "西本杭州报价"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP 503")
                .hasMessageContaining("已尝试 2 次");
        assertThat(script.calls.get()).isEqualTo(2);
    }

    @Test
    void 仅429与5xx视为可重试状态() {
        assertThat(SteelxFetcher.isRetryableStatus(429)).isTrue();
        assertThat(SteelxFetcher.isRetryableStatus(500)).isTrue();
        assertThat(SteelxFetcher.isRetryableStatus(403)).isFalse();
        assertThat(SteelxFetcher.isRetryableStatus(404)).isFalse();
    }
}
