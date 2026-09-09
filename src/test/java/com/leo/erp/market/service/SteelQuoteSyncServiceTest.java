package com.leo.erp.market.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.mysteel.MysteelClient;
import com.leo.erp.market.mysteel.MysteelProperties;
import com.leo.erp.market.repository.SteelArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteelQuoteSyncServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 9);
    private static final String ARTICLE_URL = "https://jiancai.mysteel.com/m/26090915/X.html";

    @Mock
    private MysteelClient mysteelClient;

    @Mock
    private SteelQuoteStore steelQuoteStore;

    @Mock
    private SteelArticleRepository articleRepository;

    @Test
    void sync_fetchesAndImportsNewArticle() {
        SteelQuoteSyncService service = service();
        when(mysteelClient.findLatestArticleUrl(DATE)).thenReturn(Optional.of(ARTICLE_URL));
        when(articleRepository.findByArticleUrlAndDeletedFlagFalse(ARTICLE_URL)).thenReturn(Optional.empty());
        when(mysteelClient.fetchArticle(ARTICLE_URL)).thenReturn("<html>行情</html>");
        SteelArticle stored = article();
        when(steelQuoteStore.persistArticle(ARTICLE_URL, "<html>行情</html>", "杭州")).thenReturn(stored);

        SteelQuoteSyncService.SyncResult result = service.sync(DATE);

        assertThat(result.created()).isTrue();
        assertThat(result.articleId()).isEqualTo(100L);
        assertThat(result.articleUrl()).isEqualTo(ARTICLE_URL);
        assertThat(result.articleDate()).isEqualTo("2026-09-09");
        assertThat(result.articleTime()).isEqualTo("1540");
        assertThat(result.period()).isEqualTo("下午");
        assertThat(result.rowCount()).isEqualTo(540);
    }

    @Test
    void sync_isIdempotentForKnownArticle() {
        SteelQuoteSyncService service = service();
        when(mysteelClient.findLatestArticleUrl(DATE)).thenReturn(Optional.of(ARTICLE_URL));
        when(articleRepository.findByArticleUrlAndDeletedFlagFalse(ARTICLE_URL)).thenReturn(Optional.of(article()));

        SteelQuoteSyncService.SyncResult result = service.sync(DATE);

        assertThat(result.created()).isFalse();
        verify(mysteelClient, never()).fetchArticle(anyString());
        verify(steelQuoteStore, never()).persistArticle(anyString(), anyString(), anyString());
    }

    @Test
    void sync_rejectsMissingArticle() {
        SteelQuoteSyncService service = service();
        when(mysteelClient.findLatestArticleUrl(DATE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sync(DATE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到");
    }

    @Test
    void sync_rejectsRiskControlPage() {
        SteelQuoteSyncService service = service();
        when(mysteelClient.findLatestArticleUrl(DATE)).thenReturn(Optional.of(ARTICLE_URL));
        when(articleRepository.findByArticleUrlAndDeletedFlagFalse(ARTICLE_URL)).thenReturn(Optional.empty());
        when(mysteelClient.fetchArticle(ARTICLE_URL)).thenReturn("请完成安全验证");

        assertThatThrownBy(() -> service.sync(DATE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全验证");
        verify(steelQuoteStore, never()).persistArticle(anyString(), anyString(), anyString());
    }

    private SteelQuoteSyncService service() {
        MysteelProperties properties = new MysteelProperties();
        properties.setRateLimitMillis(0);
        return new SteelQuoteSyncService(properties, mysteelClient, steelQuoteStore, articleRepository);
    }

    private SteelArticle article() {
        SteelArticle article = new SteelArticle();
        article.setId(100L);
        article.setArticleUrl(ARTICLE_URL);
        article.setArticleDate(DATE);
        article.setArticleTime("1540");
        article.setTitle("2026年9月9日(15:40)杭州市场建筑钢材价格行情");
        article.setPeriod("下午");
        article.setRowCount(540);
        article.setMarket("杭州");
        article.setFetchedAt(DATE.atTime(15, 45));
        return article;
    }
}
