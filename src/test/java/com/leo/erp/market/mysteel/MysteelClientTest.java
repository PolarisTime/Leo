package com.leo.erp.market.mysteel;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(fetcher.fetch(anyString(), anyString()))
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
    void findArticleUrls_列表页被风控时抛异常() {
        when(properties.getListUrl()).thenReturn("https://list");
        when(fetcher.fetch(anyString(), anyString())).thenReturn("安全验证");
        when(fetcher.looksLikeRiskControl("安全验证")).thenReturn(true);

        assertThatThrownBy(() -> client.findArticleUrls(LocalDate.of(2026, 9, 10)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("风控");
    }

    @Test
    void findArticleUrls_列表页短时缓存避免重复请求() {
        when(properties.getListUrl()).thenReturn("https://list");
        when(fetcher.fetch(anyString(), anyString())).thenReturn(listHtmlWith(
                "https://jiancai.mysteel.com/m/1.html"));

        client.findArticleUrls(LocalDate.of(2026, 9, 10));
        client.findArticleUrls(LocalDate.of(2026, 9, 10));

        verify(fetcher, times(1)).fetch(anyString(), anyString());
    }
}
