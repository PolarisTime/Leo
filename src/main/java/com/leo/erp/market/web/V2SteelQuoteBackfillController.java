package com.leo.erp.market.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.service.SteelQuoteBackfillService;
import com.leo.erp.market.web.dto.SteelQuoteBackfillRequest;
import com.leo.erp.market.web.dto.SteelQuoteBackfillResponse;
import com.leo.erp.market.web.dto.SteelQuoteBackfillStatusResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;

@Tag(name = "钢材行情补数")
@RestController
@org.springframework.validation.annotation.Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/steel-quote-backfills")
public class V2SteelQuoteBackfillController {

    private static final int MAX_DAYS = 60;

    private final SteelQuoteBackfillService backfillService;
    private final ZoneId zone;

    public V2SteelQuoteBackfillController(SteelQuoteBackfillService backfillService,
                                          @Value("${leo.timezone:Asia/Shanghai}") String timezone) {
        this.backfillService = backfillService;
        this.zone = ZoneId.of(timezone);
    }

    @Operation(summary = "补数任务状态", description = "返回当前/最近一次补数任务状态")
    @GetMapping("/current")
    @RequirePermission(PermissionCodes.STEEL_QUOTE_BACKFILLS_READ)
    public SteelQuoteBackfillStatusResponse current() {
        return backfillService.status();
    }

    @Operation(summary = "行情补数", description = "按最近 N 天或 from/to 区间后台补数(跳过周末, 幂等); 立即返回受理")
    @ApiResponse(responseCode = "202", description = "已受理, 后台执行")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequirePermission(PermissionCodes.STEEL_QUOTE_BACKFILLS_BACKFILL)
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
        boolean accepted = backfillService.submit(from, to);
        if (!accepted) {
            throw new BusinessException(ErrorCode.CONCURRENT_MODIFICATION, "已有补数任务进行中, 请稍后再试");
        }
        int span = (int) java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
        return ResponseEntity.accepted()
                .body(new SteelQuoteBackfillResponse(from, to, span, true));
    }
}
