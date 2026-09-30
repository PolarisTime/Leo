package com.leo.erp.market.mysteel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MysteelClientTest {

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
        when(properties.getListUrl()).thenReturn("https://list");
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
    void findArticleUrls_列表页被截断时交给传输层重试判定() {
        when(properties.getListUrl()).thenReturn("https://list");
        when(fetcher.fetch(anyString(), anyString(), any())).thenReturn(listHtmlWith(
                "https://jiancai.mysteel.com/m/1.html"));

        List<String> urls = client.findArticleUrls(LocalDate.of(2026, 9, 10));

        assertThat(urls).containsExactly("https://jiancai.mysteel.com/m/1.html");
        // 列表页长度校验必须以内容校验形式交给传输层，在同一个重试预算内执行（不在客户端另包一层重试）。
        Function<String, String> validator = capturedValidator();
        assertThat(validator.apply("x".repeat(12000))).isNull();
        assertThat(validator.apply("被截断的短页面")).contains("长度");
    }

    /** 取出客户端传给传输层的内容校验函数。 */
    @SuppressWarnings("unchecked")
    private Function<String, String> capturedValidator() {
        ArgumentCaptor<Function<String, String>> captor = ArgumentCaptor.forClass(Function.class);
        verify(fetcher).fetch(anyString(), anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    void findArticleUrls_列表页短时缓存避免重复请求() {
        when(properties.getListUrl()).thenReturn("https://list");
        when(fetcher.fetch(anyString(), anyString(), any())).thenReturn(listHtmlWith(
                "https://jiancai.mysteel.com/m/1.html"));

        client.findArticleUrls(LocalDate.of(2026, 9, 10));
        client.findArticleUrls(LocalDate.of(2026, 9, 10));

        verify(fetcher, times(1)).fetch(anyString(), anyString(), any());
    }
}
