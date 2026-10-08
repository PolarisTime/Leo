package com.leo.erp.market.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.market.QuoteNotPublishedException;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.mysteel.MysteelClient;
import com.leo.erp.market.mysteel.MysteelProperties;
import com.leo.erp.market.repository.SteelArticleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteelQuoteSyncServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 9);
    private static final String ARTICLE_URL = "https://jiancai.mysteel.com/m/26090915/X.html";
    private static final String MORNING_URL = "https://jiancai.mysteel.com/m/26090910/M.html";

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

        // 该日无行情文章属于「休市/未发布」而不是故障：单独抛 QuoteNotPublishedException，
        // 供补数记为跳过；对外仍是业务异常(4220)，HTTP 契约不变。
        assertThatThrownBy(() -> service.sync(DATE))
                .isInstanceOf(QuoteNotPublishedException.class)
                .hasMessageContaining("未发布");
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

    @Test
    void syncAll_withPeriods_onlyImportsRequestedPeriods() {
        SteelQuoteSyncService service = service();
        when(mysteelClient.findArticleUrls(DATE)).thenReturn(List.of(ARTICLE_URL, MORNING_URL));
        when(articleRepository.findByArticleUrlAndDeletedFlagFalse(ARTICLE_URL)).thenReturn(Optional.empty());
        when(articleRepository.findByArticleUrlAndDeletedFlagFalse(MORNING_URL)).thenReturn(Optional.empty());
        when(mysteelClient.fetchArticle(ARTICLE_URL)).thenReturn(articleHtml("15:40"));
        when(mysteelClient.fetchArticle(MORNING_URL)).thenReturn(articleHtml("10:30"));
        when(steelQuoteStore.persistArticle(ARTICLE_URL, articleHtml("15:40"), "杭州")).thenReturn(article());

        List<SteelQuoteSyncService.SyncResult> results = service.syncAll(DATE, List.of("下午"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).period()).isEqualTo("下午");
        verify(steelQuoteStore, never()).persistArticle(eq(MORNING_URL), anyString(), anyString());
    }

    @Test
    void syncAll_withPeriods_importsEveryRequestedPeriod() {
        SteelQuoteSyncService service = service();
        when(mysteelClient.findArticleUrls(DATE)).thenReturn(List.of(ARTICLE_URL, MORNING_URL));
        when(articleRepository.findByArticleUrlAndDeletedFlagFalse(ARTICLE_URL)).thenReturn(Optional.empty());
        when(articleRepository.findByArticleUrlAndDeletedFlagFalse(MORNING_URL)).thenReturn(Optional.empty());
        when(mysteelClient.fetchArticle(ARTICLE_URL)).thenReturn(articleHtml("15:40"));
        when(mysteelClient.fetchArticle(MORNING_URL)).thenReturn(articleHtml("10:30"));
        SteelArticle morning = article();
        morning.setPeriod("上午");
        when(steelQuoteStore.persistArticle(eq(ARTICLE_URL), anyString(), anyString())).thenReturn(article());
        when(steelQuoteStore.persistArticle(eq(MORNING_URL), anyString(), anyString())).thenReturn(morning);

        List<SteelQuoteSyncService.SyncResult> results = service.syncAll(DATE, List.of("上午", "下午"));

        assertThat(results).extracting(SteelQuoteSyncService.SyncResult::period)
                .containsExactlyInAnyOrder("上午", "下午");
    }

    @Test
    void syncAll_withPeriods_throwsWhenNoArticleMatches() {
        SteelQuoteSyncService service = service();
        when(mysteelClient.findArticleUrls(DATE)).thenReturn(List.of(MORNING_URL));
        when(articleRepository.findByArticleUrlAndDeletedFlagFalse(MORNING_URL)).thenReturn(Optional.empty());
        when(mysteelClient.fetchArticle(MORNING_URL)).thenReturn(articleHtml("10:30"));

        assertThatThrownBy(() -> service.syncAll(DATE, List.of("下午")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所选时段");
        verify(steelQuoteStore, never()).persistArticle(anyString(), anyString(), anyString());
    }

    @Test
    void syncAll_withPeriods_skipsExistingArticleOfOtherPeriod() {
        SteelQuoteSyncService service = service();
        SteelArticle morning = article();
        morning.setPeriod("上午");
        when(mysteelClient.findArticleUrls(DATE)).thenReturn(List.of(ARTICLE_URL));
        when(articleRepository.findByArticleUrlAndDeletedFlagFalse(ARTICLE_URL)).thenReturn(Optional.of(morning));

        assertThatThrownBy(() -> service.syncAll(DATE, List.of("下午")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("所选时段");
        verify(mysteelClient, never()).fetchArticle(anyString());
    }

    @Test
    void syncAll_rejectsUnknownPeriod() {
        SteelQuoteSyncService service = service();

        assertThatThrownBy(() -> service.syncAll(DATE, List.of("晚上")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知时段");
        verify(mysteelClient, never()).findArticleUrls(any(LocalDate.class));
    }

    @Test
    void backfill_该日无行情记为跳过而不是失败() {
        SteelQuoteSyncService service = service();
        // 周六: 没有发布行情 -> 跳过; 周日(调休补班): 有行情 -> 成功。
        LocalDate saturday = LocalDate.of(2026, 9, 19);
        LocalDate sunday = LocalDate.of(2026, 9, 20);
        when(mysteelClient.findArticleUrls(saturday)).thenReturn(List.of());
        when(mysteelClient.findArticleUrls(sunday)).thenReturn(List.of(ARTICLE_URL));
        when(articleRepository.findByArticleUrlAndDeletedFlagFalse(ARTICLE_URL)).thenReturn(Optional.empty());
        when(mysteelClient.fetchArticle(ARTICLE_URL)).thenReturn(articleHtml("15:40"));
        when(steelQuoteStore.persistArticle(eq(ARTICLE_URL), anyString(), eq("杭州"))).thenReturn(article());

        SteelQuoteSyncService.BackfillResult result = service.backfill(saturday, sunday);

        assertThat(result.syncedDays()).isEqualTo(1);
        assertThat(result.skippedDays()).isEqualTo(1);
        assertThat(result.skippedDates()).containsExactly(saturday);
        assertThat(result.failedDays()).isZero();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void backfill_不再盲跳周末() {
        SteelQuoteSyncService service = service();
        LocalDate saturday = LocalDate.of(2026, 9, 19);
        when(mysteelClient.findArticleUrls(saturday)).thenReturn(List.of());

        service.backfill(saturday, saturday);

        // 周末照常发起查询: 是否入库由站点发布情况决定, 而不是由本地星期规则丢弃。
        verify(mysteelClient).findArticleUrls(saturday);
    }

    @Test
    void backfill_真失败仍进失败清单() {
        SteelQuoteSyncService service = service();
        LocalDate day = LocalDate.of(2026, 9, 9);
        when(mysteelClient.findArticleUrls(day))
                .thenThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "HTTP 500"));

        SteelQuoteSyncService.BackfillResult result = service.backfill(day, day);

        assertThat(result.failedDays()).isEqualTo(1);
        assertThat(result.skippedDays()).isZero();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).message()).contains("HTTP 500");
    }

    private String articleHtml(String time) {
        return "<html><title>2026年9月9日(" + time + ")杭州市场建筑钢材价格行情</title></html>";
    }

    private SteelQuoteSyncService service() {
        MysteelProperties properties = new MysteelProperties();
        properties.setRateLimitMillis(0);
        return new SteelQuoteSyncService(properties, mysteelClient,
                new com.leo.erp.market.mysteel.MysteelRateLimiter(properties),
                steelQuoteStore, articleRepository);
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
