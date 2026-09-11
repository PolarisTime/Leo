package com.leo.erp.market.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.market.service.SteelArticleQueryService;
import com.leo.erp.market.web.dto.SteelQuoteCalendarResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "钢材行情日历")
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/steel-quote-calendars")
public class V2SteelQuoteCalendarController {

    private final SteelArticleQueryService articleQueryService;

    public V2SteelQuoteCalendarController(SteelArticleQueryService articleQueryService) {
        this.articleQueryService = articleQueryService;
    }

    @Operation(summary = "行情日历", description = "返回指定日期区间内有行情的日期及各日可用时段(上午/中午/下午)与时段行数")
    @GetMapping
    public List<SteelQuoteCalendarResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return articleQueryService.calendar(from, to);
    }
}
