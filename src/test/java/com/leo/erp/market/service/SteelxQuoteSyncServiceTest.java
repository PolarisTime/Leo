package com.leo.erp.market.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.repository.SteelArticleRepository;
import com.leo.erp.market.steelx.SteelxArticleParser;
import com.leo.erp.market.steelx.SteelxFetcher;
import com.leo.erp.market.steelx.SteelxProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SteelxQuoteSyncService} 内容校验测试：确认「价格表为空」的中间态
 * 是以内容校验形式交给传输层在同一个重试预算内判定，而不是在业务层直接失败。
 */
@ExtendWith(MockitoExtension.class)
class SteelxQuoteSyncServiceTest {

    private static final String URL = "https://hangzhou.steelx2.com/city/Quotation/quotation/1/index.html";

    @Mock
    private SteelxProperties properties;

    @Mock
    private SteelxFetcher fetcher;

    @Mock
    private SteelQuoteStore steelQuoteStore;

    @Mock
    private SteelArticleRepository articleRepository;

    private static String page(String rows) {
        return "<html><head><title>杭州建材—2026-09-30—西本会员指导价</title></head><body>"
                + "<table>" + rows + "</table></body></html>";
    }

    private static String priceRow() {
        return "<tr><td>螺纹钢</td><td>φ12</td><td>HRB400E</td><td>3200</td></tr>";
    }

    private SteelxQuoteSyncService service() {
        return new SteelxQuoteSyncService(properties, fetcher, steelQuoteStore, articleRepository);
    }

    private void stubSupportedRegion() {
        when(properties.supportsRegion("杭州")).thenReturn(true);
        when(properties.quotationUrl("杭州", null)).thenReturn(URL);
    }

    @Test
    void 同步入库并返回入库结果() {
        stubSupportedRegion();
        when(properties.getSource()).thenReturn("STEELX");
        when(fetcher.fetch(eq(URL), anyString(), any())).thenReturn(page(priceRow()));
        SteelArticle article = new SteelArticle();
        article.setId(7L);
        article.setArticleDate(LocalDate.of(2026, 9, 30));
        article.setRowCount(1);
        when(steelQuoteStore.persistSteelxArticle(eq(URL), any(), eq("杭州"), any(), eq("STEELX")))
                .thenReturn(article);

        SteelxQuoteSyncService.SyncResult result = service().syncRegion("杭州");

        assertThat(result.articleId()).isEqualTo(7L);
        assertThat(result.rowCount()).isEqualTo(1);
        assertThat(result.region()).isEqualTo("杭州");
        assertThat(result.articleDate()).isEqualTo("2026-09-30");
    }

    @Test
    void 空价格表的内容校验返回问题描述以触发重试() {
        stubSupportedRegion();
        when(properties.getSource()).thenReturn("STEELX");
        when(fetcher.fetch(eq(URL), anyString(), any())).thenReturn(page(priceRow()));
        SteelArticle article = new SteelArticle();
        article.setId(7L);
        article.setArticleDate(LocalDate.of(2026, 9, 30));
        article.setRowCount(1);
        when(steelQuoteStore.persistSteelxArticle(anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(article);

        service().syncRegion("杭州");

        Function<String, String> validator = capturedValidator();
        // 表头存在但无数据行: 页面尚未更新的中间态, 应判为瞬时失败交给传输层重试。
        assertThat(validator.apply(page(""))).contains("未解析到任何价格行");
        // 正常页面: 校验通过。
        assertThat(validator.apply(page(priceRow()))).isNull();
    }

    @SuppressWarnings("unchecked")
    private Function<String, String> capturedValidator() {
        ArgumentCaptor<Function<String, String>> captor = ArgumentCaptor.forClass(Function.class);
        verify(fetcher).fetch(anyString(), anyString(), captor.capture());
        return captor.getValue();
    }

    @Test
    void 不支持的地区直接拒绝且不发起请求() {
        when(properties.supportsRegion("火星")).thenReturn(false);
        when(properties.supportedRegions()).thenReturn(List.of("杭州"));

        assertThatThrownBy(() -> service().syncRegion("火星"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的地区");
    }

    @Test
    void 全部地区同步按配置顺序逐个抓取() {
        LocalDate date = LocalDate.of(2026, 9, 30);
        when(properties.supportedRegions()).thenReturn(List.of("杭州"));
        when(properties.supportsRegion("杭州")).thenReturn(true);
        when(properties.quotationUrl("杭州", date)).thenReturn(URL);
        when(properties.getSource()).thenReturn("STEELX");
        when(fetcher.fetch(eq(URL), anyString(), any())).thenReturn(page(priceRow()));
        SteelArticle article = new SteelArticle();
        article.setId(8L);
        article.setArticleDate(date);
        article.setRowCount(1);
        when(steelQuoteStore.persistSteelxArticle(anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(article);

        List<SteelxQuoteSyncService.SyncResult> results = service().syncAllRegions(date);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).region()).isEqualTo("杭州");
    }

    @Test
    void 解析报价日期失败时抛出异常() {
        stubSupportedRegion();
        when(fetcher.fetch(eq(URL), anyString(), any())).thenReturn("<html>无标题</html>");

        assertThatThrownBy(() -> service().syncRegion("杭州"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法从西本页面解析报价日期");
    }

    @Test
    void 解析器可识别价格行() {
        assertThat(SteelxArticleParser.parseRows(page(priceRow()))).hasSize(1);
        assertThat(SteelxArticleParser.parseRows(page(""))).isEmpty();
    }
}
