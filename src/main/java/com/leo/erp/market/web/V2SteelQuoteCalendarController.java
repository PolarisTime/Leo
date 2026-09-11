package com.leo.erp.market.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.repository.SteelArticleRepository;
import com.leo.erp.market.web.dto.SteelQuoteCalendarResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "钢材行情日历")
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/steel-quote-calendars")
public class V2SteelQuoteCalendarController {

    private final SteelArticleRepository articleRepository;

    public V2SteelQuoteCalendarController(SteelArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Operation(summary = "行情日历", description = "返回指定日期区间内有行情的日期及各日可用时段(上午/中午/下午)")
    @GetMapping
    public List<SteelQuoteCalendarResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "from/to 不能为空");
        }
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "to 不能早于 from");
        }
        Map<LocalDate, List<String>> calendar = new LinkedHashMap<>();
        Map<LocalDate, Map<String, Integer>> rows = new HashMap<>();
        for (SteelArticle article : articleRepository
                .findByArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(from, to)) {
            calendar.computeIfAbsent(article.getArticleDate(), key -> new ArrayList<>());
            List<String> periods = calendar.get(article.getArticleDate());
            String period = article.getPeriod();
            if (period != null && !period.isBlank() && !periods.contains(period)) {
                periods.add(period);
            }
            if (period != null && !period.isBlank()) {
                rows.computeIfAbsent(article.getArticleDate(), key -> new HashMap<>())
                        .merge(period, article.getRowCount() == null ? 0 : article.getRowCount(), Integer::sum);
            }
        }
        List<SteelQuoteCalendarResponse> result = new ArrayList<>(calendar.size());
        for (Map.Entry<LocalDate, List<String>> entry : calendar.entrySet()) {
            result.add(new SteelQuoteCalendarResponse(entry.getKey(), entry.getValue(),
                    rows.getOrDefault(entry.getKey(), Map.of())));
        }
        return result;
    }
}
