package com.leo.erp.market.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.market.service.SteelQuoteQueryService;
import com.leo.erp.market.web.dto.SteelQuoteResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "钢材行情")
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/steel-quotes")
public class V2SteelQuoteController {

    private final SteelQuoteQueryService steelQuoteQueryService;

    public V2SteelQuoteController(SteelQuoteQueryService steelQuoteQueryService) {
        this.steelQuoteQueryService = steelQuoteQueryService;
    }

    @Operation(summary = "分页查询行情明细", description = "按日期/时段/品名/规格/材质/钢厂筛选; 时段为 上午/中午/下午")
    @GetMapping
    @RequirePermission(PermissionCodes.STEEL_QUOTES_READ)
    public PageResponse<SteelQuoteResponse> page(
            @BindPageQuery(sortFieldKey = "steel-quote") PageQuery query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate quoteDate,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String breed,
            @RequestParam(required = false) String spec,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String change,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String region) {
        Page<SteelQuoteResponse> page = steelQuoteQueryService.page(query, quoteDate, period, breed, spec,
                material, factory, change, source, region);
        return PageResponse.from(page);
    }
}
