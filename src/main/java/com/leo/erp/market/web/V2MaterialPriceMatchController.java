package com.leo.erp.market.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.service.SteelQuoteMatchService;
import com.leo.erp.market.web.dto.MaterialPriceMatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "商品行情匹配")
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/material-price-matches")
public class V2MaterialPriceMatchController {

    private static final int MAX_PAGE_SIZE = 200;

    private final SteelQuoteMatchService steelQuoteMatchService;

    public V2MaterialPriceMatchController(SteelQuoteMatchService steelQuoteMatchService) {
        this.steelQuoteMatchService = steelQuoteMatchService;
    }

    @Operation(summary = "商品行情匹配查询",
            description = "按商品资料(品牌/材质/类别/规格/长度)匹配对应时段行情; 无对应价格时状态为\"无网价\"; 未传日期/时段时取最新文章")
    @GetMapping
    public List<MaterialPriceMatchResponse> match(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate quoteDate,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String brand,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        if (page < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page 不能小于0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "size 必须在1到" + MAX_PAGE_SIZE + "之间");
        }
        LocalDate effectiveDate = quoteDate;
        List<MaterialPriceMatchResponse> rows = steelQuoteMatchService.match(effectiveDate, period);
        return rows.stream()
                .filter(row -> status == null || status.isBlank() || status.equals(row.status()))
                .filter(row -> brand == null || brand.isBlank() || brand.equals(row.brand()))
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

}
