package com.leo.erp.market.mysteel;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.retry.TransientCallException;
import com.leo.erp.market.QuoteNotPublishedException;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link MysteelFetcher} 重试边界测试：用本地 HTTP 服务脚本化响应序列，
 * 断言「哪些失败会重试、重试次数上限、以及重试后仍失败时的对外异常」。
 */
class MysteelFetcherTest {

    /** 按调用次序返回的脚本化响应。 */
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
    private final AtomicReference<String> lastReferer = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        script = new Script();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/page", exchange -> {
            lastReferer.set(exchange.getRequestHeaders().getFirst("Referer"));
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
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/page";
    }

    /** 构造 fetcher；maxAttempts<=1 表示关闭重试，退避固定为 0 以避免测试等待。 */
    private MysteelFetcher fetcher(int maxAttempts) {
        MysteelProperties properties = new MysteelProperties();
        properties.setRequestTimeoutMs(3000);
        properties.setFetchSshHost("");
        properties.getRetry().setMaxAttempts(maxAttempts);
        properties.getRetry().setInitialBackoffMillis(0L);
        properties.getRetry().setMaxBackoffMillis(0L);
        return new MysteelFetcher(properties);
    }

    @Test
    void 首次成功时只请求一次() {
        script.then(200, "ok-html");

        assertThat(fetcher(3).fetch(url(), "行情列表页")).isEqualTo("ok-html");
        assertThat(script.calls.get()).isEqualTo(1);
    }

    @Test
    void 服务端5xx后重试并成功() {
        script.then(503, "unavailable").then(200, "ok-html");

        assertThat(fetcher(3).fetch(url(), "行情列表页")).isEqualTo("ok-html");
        assertThat(script.calls.get()).isEqualTo(2);
    }

    @Test
    void 限流429会重试() {
        script.then(429, "too-many").then(200, "ok-html");

        assertThat(fetcher(3).fetch(url(), "行情列表页")).isEqualTo("ok-html");
        assertThat(script.calls.get()).isEqualTo(2);
    }

    @Test
    void 客户端4xx不重试() {
        script.then(404, "not-found");

        assertThatThrownBy(() -> fetcher(3).fetch(url(), "行情列表页"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP 404");
        assertThat(script.calls.get()).isEqualTo(1);
    }

    @Test
    void 风控页会重试且重试成功时返回正常内容() {
        script.then(200, "<html>请完成安全验证</html>").then(200, "ok-html");

        assertThat(fetcher(3).fetch(url(), "行情列表页")).isEqualTo("ok-html");
        assertThat(script.calls.get()).isEqualTo(2);
    }

    @Test
    void 空响应会重试() {
        script.then(200, "").then(200, "ok-html");

        assertThat(fetcher(3).fetch(url(), "行情列表页")).isEqualTo("ok-html");
        assertThat(script.calls.get()).isEqualTo(2);
    }

    @Test
    void 重试次数用尽后抛出业务异常并标注尝试次数() {
        script.then(500, "boom").then(500, "boom").then(500, "boom");

        assertThatThrownBy(() -> fetcher(3).fetch(url(), "行情列表页"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageContaining("已尝试 3 次");
        assertThat(script.calls.get()).isEqualTo(3);
    }

    @Test
    void 关闭重试时5xx立即失败() {
        script.then(500, "boom");

        assertThatThrownBy(() -> fetcher(1).fetch(url(), "行情列表页"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP 500");
        assertThat(script.calls.get()).isEqualTo(1);
    }

    @Test
    void 自定义内容校验触发重试并在重试成功后返回() {
        script.then(200, "short").then(200, "x".repeat(12000));
        MysteelFetcher fetcher = fetcher(3);

        String html = fetcher.fetch(url(), "行情列表页", content -> {
            if (content.length() < 10000) {
                throw new TransientCallException("列表页内容不完整(长度 " + content.length() + "), 疑似被截断");
            }
        });

        assertThat(html).hasSize(12000);
        assertThat(script.calls.get()).isEqualTo(2);
    }

    @Test
    void 自定义内容校验始终不通过时用尽重试并抛出校验消息() {
        script.then(200, "short");

        assertThatThrownBy(() -> fetcher(2).fetch(url(), "行情列表页", content -> {
            throw new TransientCallException("内容不完整");
        }))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容不完整")
                .hasMessageContaining("已尝试 2 次");
        assertThat(script.calls.get()).isEqualTo(2);
    }

    @Test
    void 内容为空的校验结果上报为未发布报价异常() {
        script.then(200, "short");

        assertThatThrownBy(() -> fetcher(2).fetch(url(), "行情列表页", content -> {
            throw new TransientCallException(TransientCallException.Reason.CONTENT_EMPTY, "该日无行情");
        }))
                .isInstanceOf(QuoteNotPublishedException.class)
                .hasMessageContaining("该日无行情")
                .hasMessageContaining("已尝试 2 次");
    }

    @Test
    void 请求携带Referer() {
        script.then(200, "ok-html");

        fetcher(3).fetch(url(), "行情列表页");

        assertThat(lastReferer.get()).isEqualTo("https://hangzhou.mysteel.com/");
    }

    @Test
    void 仅429与5xx视为可重试状态() {
        assertThat(MysteelFetcher.isRetryableStatus(429)).isTrue();
        assertThat(MysteelFetcher.isRetryableStatus(500)).isTrue();
        assertThat(MysteelFetcher.isRetryableStatus(503)).isTrue();
        assertThat(MysteelFetcher.isRetryableStatus(400)).isFalse();
        assertThat(MysteelFetcher.isRetryableStatus(403)).isFalse();
        assertThat(MysteelFetcher.isRetryableStatus(404)).isFalse();
        assertThat(MysteelFetcher.isRetryableStatus(200)).isFalse();
    }

    @Test
    void 识别风控页标记() {
        MysteelFetcher fetcher = fetcher(1);

        assertThat(fetcher.looksLikeRiskControl("<html>安全验证</html>")).isTrue();
        assertThat(fetcher.looksLikeRiskControl("<html>normal</html>")).isFalse();
        assertThat(fetcher.looksLikeRiskControl(null)).isFalse();
    }
}
