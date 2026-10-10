package com.leo.erp.market.service;

import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.repository.SteelArticleRepository;
import com.leo.erp.market.web.dto.SteelQuoteCalendarResponse;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SteelArticleQueryServiceTest {

    @Mock
    private SteelArticleRepository articleRepository;

    @InjectMocks
    private SteelArticleQueryService service;

    private SteelArticle article(LocalDate date, String period, int rows) {
        SteelArticle article = new SteelArticle();
        article.setArticleDate(date);
        article.setPeriod(period);
        article.setRowCount(rows);
        return article;
    }

    @Test
    void calendar_聚合日期时段与行数() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(articleRepository
                .findByArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(any(), any()))
                .thenReturn(List.of(
                        article(day, "上午", 100),
                        article(day, "上午", 50),
                        article(day, "下午", 80)));

        List<SteelQuoteCalendarResponse> result =
                service.calendar(day, day);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).periods()).containsExactly("上午", "下午");
        assertThat(result.get(0).periodRows()).containsEntry("上午", 150).containsEntry("下午", 80);
    }

    @Test
    void calendar_区间非法时抛校验异常() {
        assertThatThrownBy(() -> service.calendar(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 10)))
                .hasMessageContaining("to 不能早于 from");
        assertThatThrownBy(() -> service.calendar(null, LocalDate.of(2026, 9, 10)))
                .hasMessageContaining("不能为空");
    }

    @Test
    void calendar_只给数据源不给地区时不按market空值过滤() {
        // 回归: market 是 NOT NULL 列, 空地区透传成 market = null 会恒为空集,
        // 前端「数据源已选、地区未选」的默认视图因此整片显示缺失。
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(articleRepository
                .findBySourceAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
                        "MYSTEEL", day, day))
                .thenReturn(List.of(article(day, "上午", 540)));

        List<SteelQuoteCalendarResponse> result =
                service.calendar(day, day, "MYSTEEL", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).periods()).containsExactly("上午");
        assertThat(result.get(0).periodRows()).containsEntry("上午", 540);
        verify(articleRepository, never())
                .findBySourceAndMarketAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
                        any(), any(), any(), any());
    }

    @Test
    void calendar_空白与首尾空格的过滤条件视为未提供() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(articleRepository
                .findByArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(day, day))
                .thenReturn(List.of(article(day, "上午", 10)));

        assertThat(service.calendar(day, day, "  ", "")).hasSize(1);
    }

    @Test
    void calendar_数据源与地区都给时按两者交集过滤并使用去空格后的值() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(articleRepository
                .findBySourceAndMarketAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
                        "STEELX", "杭州", day, day))
                .thenReturn(List.of(article(day, "上午", 2698)));

        List<SteelQuoteCalendarResponse> result =
                service.calendar(day, day, " STEELX ", " 杭州 ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).periodRows()).containsEntry("上午", 2698);
    }

    @Test
    void calendar_只给地区时按地区过滤() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(articleRepository
                .findByMarketAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
                        "杭州", day, day))
                .thenReturn(List.of(article(day, "下午", 20)));

        List<SteelQuoteCalendarResponse> result = service.calendar(day, day, null, "杭州");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).periods()).containsExactly("下午");
    }
}
