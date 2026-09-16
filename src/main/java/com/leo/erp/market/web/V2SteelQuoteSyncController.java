package com.leo.erp.market.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.market.service.SteelArticleQueryService;
import com.leo.erp.market.service.SteelQuoteSyncService;
import com.leo.erp.market.service.SteelQuoteSyncService.SyncResult;
import com.leo.erp.market.web.dto.SteelQuoteSyncRequest;
import com.leo.erp.market.web.dto.SteelQuoteSyncRecordResponse;
import com.leo.erp.market.web.dto.SteelQuoteSyncResponse;
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
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.ZoneId;

@Tag(name = "钢材行情同步")
@RestController
@org.springframework.validation.annotation.Validated
@IdempotencyRequired
@RequestMapping(ApiVersion.V2_PREFIX + "/steel-quote-syncs")
public class V2SteelQuoteSyncController {

    private final SteelQuoteSyncService steelQuoteSyncService;
    private final SteelArticleQueryService articleQueryService;
    private final ZoneId zone;

    public V2SteelQuoteSyncController(SteelQuoteSyncService steelQuoteSyncService,
                                      SteelArticleQueryService articleQueryService,
                                      @Value("${leo.timezone:Asia/Shanghai}") String timezone) {
        this.steelQuoteSyncService = steelQuoteSyncService;
        this.articleQueryService = articleQueryService;
        this.zone = ZoneId.of(timezone);
    }

    @Operation(summary = "同步记录分页", description = "按抓取时间倒序返回已入库的行情文章记录")
    @GetMapping
    @RequirePermission(PermissionCodes.STEEL_QUOTE_SYNCS_READ)
    public PageResponse<SteelQuoteSyncRecordResponse> page(@BindPageQuery(sortFieldKey = "steel-quote") PageQuery query) {
        Page<SteelQuoteSyncRecordResponse> page = articleQueryService.pageSyncRecords(query);
        return PageResponse.from(page);
    }

    @Operation(summary = "手动同步行情", description = "抓取指定日期(默认今天)的行情文章并解密入库; 可传 periods(上午/中午/下午)只同步所选时段, 缺省同步当天全部时段; 重复同步同一文章幂等")
    @ApiResponse(responseCode = "202", description = "同步完成")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @RequirePermission(PermissionCodes.STEEL_QUOTE_SYNCS_SYNC)
    public ResponseEntity<SteelQuoteSyncResponse> create(@Valid @RequestBody(required = false)
                                                         SteelQuoteSyncRequest request) {
        LocalDate date = request != null && request.date() != null ? request.date() : LocalDate.now(zone);
        if (date.isAfter(LocalDate.now(zone))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能同步未来日期的行情");
        }
        java.util.List<String> requestedPeriods = request != null ? request.periods() : java.util.List.of();
        java.util.List<SyncResult> results = steelQuoteSyncService.syncAll(date, requestedPeriods);
        SyncResult last = results.get(results.size() - 1);
        java.util.List<String> periods = results.stream().map(SyncResult::period).distinct().toList();
        int rowCount = results.stream().mapToInt(SyncResult::rowCount).sum();
        boolean created = results.stream().anyMatch(SyncResult::created);
        return ResponseEntity.accepted().body(new SteelQuoteSyncResponse(last.articleId(), last.articleUrl(),
                last.articleDate(), last.articleTime(), last.period(), periods, rowCount, created));
    }
}
