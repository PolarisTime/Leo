package com.leo.erp.market.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.service.SteelQuoteSyncService;
import com.leo.erp.market.web.dto.SteelQuoteBackfillRequest;
import com.leo.erp.market.web.dto.SteelQuoteBackfillResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.CompletableFuture;

@Tag(name = "钢材行情补数")
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/steel-quote-backfills")
public class V2SteelQuoteBackfillController {

    private static final int MAX_DAYS = 60;

    private final SteelQuoteSyncService steelQuoteSyncService;
    private final ZoneId zone;

    public V2SteelQuoteBackfillController(SteelQuoteSyncService steelQuoteSyncService,
                                          @Value("${leo.timezone:Asia/Shanghai}") String timezone) {
        this.steelQuoteSyncService = steelQuoteSyncService;
        this.zone = ZoneId.of(timezone);
    }

    @Operation(summary = "行情补数", description = "按最近 N 天或 from/to 区间后台补数(跳过周末, 幂等); 立即返回受理")
    @ApiResponse(responseCode = "202", description = "已受理, 后台执行")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<SteelQuoteBackfillResponse> create(
            @Valid @RequestBody SteelQuoteBackfillRequest request) {
        LocalDate today = LocalDate.now(zone);
        LocalDate to = request.to() != null ? request.to() : today;
        LocalDate from;
        if (request.from() != null) {
            from = request.from();
        } else {
            int days = request.days() != null ? request.days() : 30;
            if (days < 1 || days > MAX_DAYS) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "days 必须在1到" + MAX_DAYS + "之间");
            }
            from = to.minusDays(days - 1L);
        }
        if (to.isAfter(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能补未来日期");
        }
        LocalDate start = from;
        LocalDate end = to;
        CompletableFuture.runAsync(() -> steelQuoteSyncService.backfill(start, end));
        int span = (int) java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        return ResponseEntity.accepted()
                .body(new SteelQuoteBackfillResponse(from, to, span, true));
    }
}
