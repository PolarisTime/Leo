package com.leo.erp.market.mysteel;

import com.leo.erp.common.retry.TransientCallException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MysteelClientTest {

    private static final String LIST_URL = "https://jiancai.mysteel.com/market/pa228a15472aa0aaaaa1.html";

    @Mock
    private MysteelProperties properties;

    @Mock
    private MysteelFetcher fetcher;

    @InjectMocks
    private MysteelClient client;

    private String listHtmlWith(String... urls) {
        StringBuilder sb = new StringBuilder("x".repeat(12000));
        for (String url : urls) {
            sb.append("<a href=\"").append(url)
                    .append("\" title=\"9月10日杭州市场建筑钢材价格行情\">");
        }
        return sb.toString();
    }

    @Test
    void findArticleUrls_返回当天全部文章并去重() {
        when(properties.getListUrl()).thenReturn(LIST_URL);
        when(fetcher.fetch(anyString(), anyString(), any()))
                .thenReturn(listHtmlWith(
                        "https://jiancai.mysteel.com/m/1.html",
                        "https://jiancai.mysteel.com/m/1.html",
                        "https://jiancai.mysteel.com/m/2.html"));

        List<String> urls = client.findArticleUrls(LocalDate.of(2026, 9, 10));

        assertThat(urls).containsExactly(
                "https://jiancai.mysteel.com/m/1.html",
                "https://jiancai.mysteel.com/m/2.html");
    }

    @Test
    void findArticleUrls_按目标日期查询列表页而不是读第一页() {
        when(properties.getListUrl()).thenReturn(LIST_URL);
        when(fetcher.fetch(anyString(), anyString(), any())).thenReturn(listHtmlWith(
                "https://jiancai.mysteel.com/m/1.html"));

        client.findArticleUrls(LocalDate.of(2026, 8, 10));

        // 列表页第 1 页只覆盖最近约 17 个交易日，历史日期必须靠日期过滤定位。
        verify(fetcher).fetch(eq(LIST_URL + "?startTime=2026-08-10&endTime=2026-08-10"), anyString(), any());
    }

    @Test
    void 相同日期的列表页短时缓存避免重复请求() {
        when(properties.getListUrl()).thenReturn(LIST_URL);
        when(fetcher.fetch(anyString(), anyString(), any())).thenReturn(listHtmlWith(
                "https://jiancai.mysteel.com/m/1.html"));

        client.findArticleUrls(LocalDate.of(2026, 9, 10));
        client.findArticleUrls(LocalDate.of(2026, 9, 10));

        verify(fetcher, times(1)).fetch(anyString(), anyString(), any());
    }

    @Test
    void 不同日期不复用缓存() {
        when(properties.getListUrl()).thenReturn(LIST_URL);
        when(fetcher.fetch(anyString(), anyString(), any())).thenReturn(listHtmlWith(
                "https://jiancai.mysteel.com/m/1.html"));

        client.findArticleUrls(LocalDate.of(2026, 9, 10));
        client.findArticleUrls(LocalDate.of(2026, 9, 11));

        verify(fetcher, times(2)).fetch(anyString(), anyString(), any());
    }

    @Test
    void 列表页被截断时交给传输层重试判定() {
        when(properties.getListUrl()).thenReturn(LIST_URL);
        when(fetcher.fetch(anyString(), anyString(), any())).thenReturn(listHtmlWith(
                "https://jiancai.mysteel.com/m/1.html"));

        client.findArticleUrls(LocalDate.of(2026, 9, 10));

        Consumer<String> check = capturedContentCheck();
        // 正常页面(含「该日无行情」的正常空结果): 校验通过, 是否跳过由调用方按文章数判定。
        assertThatCode(() -> check.accept("x".repeat(12000))).doesNotThrowAnyException();
        // 被截断的短页面: 抛可重试的瞬时失败, 由传输层在同一个预算内重试。
        assertThatThrownBy(() -> check.accept("被截断的短页面"))
                .isInstanceOf(TransientCallException.class)
                .hasMessageContaining("长度");
    }

    @Test
    void 日期过滤地址拼接正确() {
        assertThat(MysteelClient.urlFor(LIST_URL, LocalDate.of(2026, 8, 10)))
                .isEqualTo(LIST_URL + "?startTime=2026-08-10&endTime=2026-08-10");
        assertThat(MysteelClient.urlFor(LIST_URL + "?keyWord=", LocalDate.of(2026, 8, 10)))
                .isEqualTo(LIST_URL + "?keyWord=&startTime=2026-08-10&endTime=2026-08-10");
    }

    /** 取出客户端传给传输层的内容校验函数。 */
    @SuppressWarnings("unchecked")
    private Consumer<String> capturedContentCheck() {
        ArgumentCaptor<Consumer<String>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(fetcher).fetch(anyString(), anyString(), captor.capture());
        return captor.getValue();
    }
}
