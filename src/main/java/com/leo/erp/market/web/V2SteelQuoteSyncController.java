package com.leo.erp.market.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.service.SteelQuoteSyncService;
import com.leo.erp.market.service.SteelQuoteSyncService.SyncResult;
import com.leo.erp.market.web.dto.SteelQuoteSyncRequest;
import com.leo.erp.market.web.dto.SteelQuoteSyncResponse;
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

@Tag(name = "钢材行情同步")
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/steel-quote-syncs")
public class V2SteelQuoteSyncController {

    private final SteelQuoteSyncService steelQuoteSyncService;
    private final ZoneId zone;

    public V2SteelQuoteSyncController(SteelQuoteSyncService steelQuoteSyncService,
                                      @Value("${leo.timezone:Asia/Shanghai}") String timezone) {
        this.steelQuoteSyncService = steelQuoteSyncService;
        this.zone = ZoneId.of(timezone);
    }

    @Operation(summary = "手动同步行情", description = "抓取指定日期(默认今天)最新行情文章并解密入库; 重复同步同一文章幂等返回既有结果")
    @ApiResponse(responseCode = "202", description = "同步完成")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<SteelQuoteSyncResponse> create(@Valid @RequestBody(required = false)
                                                         SteelQuoteSyncRequest request) {
        LocalDate date = request != null && request.date() != null ? request.date() : LocalDate.now(zone);
        if (date.isAfter(LocalDate.now(zone))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不能同步未来日期的行情");
        }
        SyncResult result = steelQuoteSyncService.sync(date);
        return ResponseEntity.accepted().body(new SteelQuoteSyncResponse(result.articleId(), result.articleUrl(),
                result.articleDate(), result.articleTime(), result.period(), result.rowCount(), result.created()));
    }
}
