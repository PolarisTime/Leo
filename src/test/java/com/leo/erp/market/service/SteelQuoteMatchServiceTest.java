package com.leo.erp.market.service;

import com.leo.erp.master.api.MaterialQuery;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.domain.entity.SteelQuote;
import com.leo.erp.market.mysteel.MysteelProperties;
import com.leo.erp.market.repository.SteelArticleRepository;
import com.leo.erp.market.repository.SteelQuoteRepository;
import com.leo.erp.market.web.dto.MaterialPriceMatchResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteelQuoteMatchServiceTest {

    private static final LocalDate QUOTE_DATE = LocalDate.of(2026, 9, 9);

    @Mock
    private SteelQuoteRepository quoteRepository;

    @Mock
    private SteelArticleRepository articleRepository;

    @Mock
    private MaterialQuery materialQuery;

    private SteelQuoteMatchService service() {
        return new SteelQuoteMatchService(quoteRepository, articleRepository, materialQuery, new MysteelProperties());
    }

    @Test
    void match_mapsAliasBreedAndLengthPremium() {
        stubLatestArticle();
        when(quoteRepository.findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
                        "MYSTEEL", "杭州", QUOTE_DATE, "下午"))
                .thenReturn(List.of(quote("浙江万泰", "螺纹钢", "HRB400E", "Φ16-25", "3160", "货少")));
        when(materialQuery.findActiveProducts()).thenReturn(List.of(
                material(1L, "M001", "万泰", "HRB400E", "直条", "16", "12米"),
                material(2L, "M002", "万泰", "HRB400E", "直条", "18", "9米")));

        List<MaterialPriceMatchResponse> rows = service().match(QUOTE_DATE, "下午");

        assertThat(rows).hasSize(2);
        // 12米: 3160 + 30
        assertThat(rows.get(0).status()).isEqualTo("匹配");
        assertThat(rows.get(0).factory()).isEqualTo("浙江万泰");
        assertThat(rows.get(0).matchedSpec()).isEqualTo("Φ16-25");
        assertThat(rows.get(0).basePrice()).isEqualByComparingTo("3160");
        assertThat(rows.get(0).price()).isEqualByComparingTo("3190");
        assertThat(rows.get(0).priceType()).isEqualTo("12米(+30)");
        // 9米: 基准价
        assertThat(rows.get(1).price()).isEqualByComparingTo("3160");
        assertThat(rows.get(1).priceType()).isEqualTo("基准价");
    }

    @Test
    void match_prefersExactSpecOverRange() {
        stubLatestArticle();
        when(quoteRepository.findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
                        "MYSTEEL", "杭州", QUOTE_DATE, "下午"))
                .thenReturn(List.of(
                        quote("中天", "螺纹钢", "HRB400E", "Φ16-25", "3100", ""),
                        quote("中天", "螺纹钢", "HRB400E", "Φ18", "3120", "")));
        when(materialQuery.findActiveProducts())
                .thenReturn(List.of(material(1L, "M001", "中天", "HRB400E", "直条", "18", "9米")));

        MaterialPriceMatchResponse row = service().match(QUOTE_DATE, "下午").get(0);

        assertThat(row.matchedSpec()).isEqualTo("Φ18");
        assertThat(row.price()).isEqualByComparingTo("3120");
    }

    @Test
    void match_prefersSingleSpecPriceInRemark() {
        stubLatestArticle();
        when(quoteRepository.findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
                        "MYSTEEL", "杭州", QUOTE_DATE, "下午"))
                .thenReturn(List.of(quote("中天", "螺纹钢", "HRB400E", "Φ16-25", "3100", "Φ18:3130;货少")));
        when(materialQuery.findActiveProducts())
                .thenReturn(List.of(material(1L, "M001", "中天", "HRB400E", "直条", "18", "9米")));

        MaterialPriceMatchResponse row = service().match(QUOTE_DATE, "下午").get(0);

        assertThat(row.singleSpecPrice()).isTrue();
        assertThat(row.basePrice()).isEqualByComparingTo("3130");
        assertThat(row.price()).isEqualByComparingTo("3130");
        assertThat(row.remark()).isEqualTo("Φ18:3130;货少");
    }

    @Test
    void match_ignoresConfiguredMaterials() {
        stubLatestArticle();
        when(quoteRepository.findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
                        "MYSTEEL", "杭州", QUOTE_DATE, "下午"))
                .thenReturn(List.of());
        when(materialQuery.findActiveProducts())
                .thenReturn(List.of(material(1L, "M001", "万泰", "HRB500E", "直条", "16", "9米")));

        MaterialPriceMatchResponse row = service().match(QUOTE_DATE, "下午").get(0);

        assertThat(row.status()).isEqualTo("无网价");
        assertThat(row.factory()).isNull();
        assertThat(row.price()).isNull();
    }

    @Test
    void match_unknownBrandFallsBackToNoPrice() {
        stubLatestArticle();
        when(quoteRepository.findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
                        "MYSTEEL", "杭州", QUOTE_DATE, "下午"))
                .thenReturn(List.of(quote("中天", "盘螺", "HRB400E", "Φ8-10", "3510", "")));
        when(materialQuery.findActiveProducts()).thenReturn(List.of(
                material(1L, "M001", "新澎辉", "HRB400E", "盘螺", "8", "-"),
                material(2L, "M002", "中天", "HRB400E", "盘螺", "8", "-")));

        List<MaterialPriceMatchResponse> rows = service().match(QUOTE_DATE, "下午");

        assertThat(rows.get(0).status()).isEqualTo("无网价");
        assertThat(rows.get(1).status()).isEqualTo("匹配");
        assertThat(rows.get(1).factory()).isEqualTo("中天");
        // 盘螺长度 "-" 无加价
        assertThat(rows.get(1).price()).isEqualByComparingTo("3510");
        assertThat(rows.get(1).priceType()).isEqualTo("基准价");
    }

    @Test
    void match_returnsNoPriceWhenNoQuotes() {
        stubLatestArticle();
        when(quoteRepository.findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
                        "MYSTEEL", "杭州", QUOTE_DATE, "下午"))
                .thenReturn(List.of());
        when(materialQuery.findActiveProducts())
                .thenReturn(List.of(material(1L, "M001", "中天", "HRB400E", "直条", "16", "9米")));

        MaterialPriceMatchResponse row = service().match(QUOTE_DATE, "下午").get(0);

        assertThat(row.status()).isEqualTo("无网价");
        assertThat(row.quoteDate()).isEqualTo(QUOTE_DATE);
        assertThat(row.period()).isEqualTo("下午");
    }

    @Test
    void match_usesRequestedPeriodOverArticlePeriod() {
        SteelArticle article = article();
        when(articleRepository.findFirstBySourceAndMarketAndArticleDateAndDeletedFlagFalseOrderByArticleTimeDesc(
                "MYSTEEL", "杭州", QUOTE_DATE)).thenReturn(Optional.of(article));
        when(quoteRepository.findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
                        "MYSTEEL", "杭州", QUOTE_DATE, "上午"))
                .thenReturn(List.of(quote("中天", "螺纹钢", "HRB400E", "Φ16", "3140", "")));
        when(materialQuery.findActiveProducts())
                .thenReturn(List.of(material(1L, "M001", "中天", "HRB400E", "直条", "16", "9米")));

        MaterialPriceMatchResponse row = service().match(QUOTE_DATE, "上午").get(0);

        assertThat(row.status()).isEqualTo("匹配");
        assertThat(row.period()).isEqualTo("上午");
        assertThat(row.price()).isEqualByComparingTo("3140");
    }

    @Test
    void match_defaultsToLatestArticleWhenDateMissing() {
        SteelArticle article = article();
        when(articleRepository.findFirstBySourceAndMarketAndDeletedFlagFalseOrderByArticleDateDescArticleTimeDesc(
                "MYSTEEL", "杭州")).thenReturn(Optional.of(article));
        when(quoteRepository.findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
                        "MYSTEEL", "杭州", QUOTE_DATE, "下午"))
                .thenReturn(List.of(quote("中天", "螺纹钢", "HRB400E", "Φ16", "3140", "")));
        when(materialQuery.findActiveProducts())
                .thenReturn(List.of(material(1L, "M001", "中天", "HRB400E", "直条", "16", "9米")));

        MaterialPriceMatchResponse row = service().match(null, null).get(0);

        assertThat(row.quoteDate()).isEqualTo(QUOTE_DATE);
        assertThat(row.period()).isEqualTo("下午");
        assertThat(row.status()).isEqualTo("匹配");
    }

    @Test
    void match_returnsEmptyWhenNoArticle() {
        when(articleRepository.findFirstBySourceAndMarketAndDeletedFlagFalseOrderByArticleDateDescArticleTimeDesc(
                "MYSTEEL", "杭州")).thenReturn(Optional.empty());

        assertThat(service().match(null, null)).isEmpty();
    }

    @Test
    void specCovers_rangeAndExactBoundaries() {
        assertThat(SteelQuoteMatchService.specCovers("Φ16-25", 16)).isTrue();
        assertThat(SteelQuoteMatchService.specCovers("Φ16-25", 25)).isTrue();
        assertThat(SteelQuoteMatchService.specCovers("Φ16-25", 26)).isFalse();
        assertThat(SteelQuoteMatchService.specCovers("Φ16-25", 15)).isFalse();
        assertThat(SteelQuoteMatchService.specCovers("Φ16", 16)).isTrue();
        assertThat(SteelQuoteMatchService.specCovers("Φ16", 17)).isFalse();
        assertThat(SteelQuoteMatchService.specCovers("", 16)).isFalse();
        assertThat(SteelQuoteMatchService.specCovers(null, 16)).isFalse();
    }

    @Test
    void singleSpecPrice_parsesRemarkOverrides() {
        assertThat(SteelQuoteMatchService.singleSpecPrice("Φ16:3160;Φ22:3160;货少", 22))
                .hasValue(new BigDecimal("3160"));
        assertThat(SteelQuoteMatchService.singleSpecPrice("Φ16:3160", 18)).isEmpty();
        assertThat(SteelQuoteMatchService.singleSpecPrice("货少", 16)).isEmpty();
        assertThat(SteelQuoteMatchService.singleSpecPrice(null, 16)).isEmpty();
    }

    private void stubLatestArticle() {
        when(articleRepository.findFirstBySourceAndMarketAndArticleDateAndDeletedFlagFalseOrderByArticleTimeDesc(
                "MYSTEEL", "杭州", QUOTE_DATE)).thenReturn(Optional.of(article()));
    }

    private SteelArticle article() {
        SteelArticle article = new SteelArticle();
        article.setId(100L);
        article.setArticleUrl("https://jiancai.mysteel.com/m/x.html");
        article.setArticleDate(QUOTE_DATE);
        article.setArticleTime("1540");
        article.setTitle("2026年9月9日(15:40)杭州市场建筑钢材价格行情");
        article.setPeriod("下午");
        article.setRowCount(1);
        article.setMarket("杭州");
        article.setSource("MYSTEEL");
        article.setFetchedAt(QUOTE_DATE.atTime(15, 45));
        return article;
    }

    private SteelQuote quote(String factory, String breed, String material, String spec, String price, String remark) {
        SteelQuote quote = new SteelQuote();
        quote.setId(200L);
        quote.setArticleId(100L);
        quote.setMarket("杭州");
        quote.setQuoteDate(QUOTE_DATE);
        quote.setPeriod("下午");
        quote.setBreed(breed);
        quote.setSpec(spec);
        quote.setMaterial(material);
        quote.setFactory(factory);
        quote.setPrice(new BigDecimal(price));
        quote.setChangeVal("-");
        quote.setRemark(remark);
        quote.setScrapedAt(QUOTE_DATE.atTime(15, 45));
        return quote;
    }

    private MaterialQuery.MaterialSnapshot material(Long id, String code, String brand, String materialName,
                                                    String category, String spec, String length) {
        return new MaterialQuery.MaterialSnapshot(id, code, brand, materialName, category, spec, length);
    }

    @Test
    void match_steelxIgnoresBrandAndMatchesByBreedSpecMaterial() {
        SteelArticle steelx = article();
        steelx.setSource("STEELX");
        steelx.setMarket("杭州");
        steelx.setPeriod("上午");
        when(articleRepository.findFirstBySourceAndMarketAndDeletedFlagFalseOrderByArticleDateDescArticleTimeDesc(
                "STEELX", "杭州")).thenReturn(Optional.of(steelx));
        SteelQuote q = quote("", "螺纹钢", "HRB400E", "Φ12", "3560", null);
        q.setSource("STEELX");
        when(quoteRepository.findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
                "STEELX", "杭州", QUOTE_DATE, "上午")).thenReturn(List.of(q));
        when(materialQuery.findActiveProducts()).thenReturn(List.of(
                material(1L, "M001", "任意品牌", "HRB400E", "直条", "12", "9米"),
                material(2L, "M002", "另一品牌", "HRB400E", "直条", "12", "9米")));

        List<MaterialPriceMatchResponse> rows = service().match(null, null, "STEELX", "杭州");

        // 忽略品牌: 两种品牌同规格同为 3560
        assertThat(rows).hasSize(2);
        assertThat(rows).allMatch(r -> "匹配".equals(r.status()));
        assertThat(rows).allMatch(r -> r.price().compareTo(new java.math.BigDecimal("3560")) == 0);
    }

    @Test
    void match_steelxTwelveMeterRequiresLengthMatch() {
        SteelArticle steelx = article();
        steelx.setSource("STEELX");
        steelx.setMarket("杭州");
        steelx.setPeriod("上午");
        when(articleRepository.findFirstBySourceAndMarketAndDeletedFlagFalseOrderByArticleDateDescArticleTimeDesc(
                "STEELX", "杭州")).thenReturn(Optional.of(steelx));
        SteelQuote q12 = quote("", "螺纹钢", "HRB400E", "Φ12", "3580", "12米");
        q12.setSource("STEELX");
        when(quoteRepository.findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
                "STEELX", "杭州", QUOTE_DATE, "上午")).thenReturn(List.of(q12));
        when(materialQuery.findActiveProducts()).thenReturn(List.of(
                material(1L, "M001", "品牌", "HRB400E", "直条", "12", "12米"),
                material(2L, "M002", "品牌", "HRB400E", "直条", "12", "9米")));

        List<MaterialPriceMatchResponse> rows = service().match(null, null, "STEELX", "杭州");

        // 仅 12 米行命中(备注标记长度); 9 米行无价
        assertThat(rows.get(0).status()).isEqualTo("匹配");
        assertThat(rows.get(0).price()).isEqualByComparingTo("3580");
        assertThat(rows.get(1).status()).isEqualTo("无网价");
    }
}
