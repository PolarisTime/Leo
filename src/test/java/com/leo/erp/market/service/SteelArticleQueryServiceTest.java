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
}
