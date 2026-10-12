package com.leo.erp.market.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.api.ProjectPricingBasisQuery.Basis;
import com.leo.erp.master.api.ProjectPricingBasisQuery.PeriodPolicy;
import com.leo.erp.market.service.PricingQuoteResolver;
import com.leo.erp.market.web.dto.PricingQuoteResolutionResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 项目取价基准解析：只读地把「项目配置 + 单据日期」换算成实际取价日与实际时段。
 *
 * <p>解析与匹配分离：本接口负责「取哪天哪时段」（含休市回退与中文说明），
 * 真正取价仍走 {@code GET /api/v2/material-price-matches?quoteDate&period&source&region}，
 * 这样前端可以先展示解析结果、允许人工覆盖，再发起取价。</p>
 */
@Tag(name = "项目取价基准解析")
@RestController
@org.springframework.validation.annotation.Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/projects/{projectId}/pricing-quote-resolutions")
public class V2ProjectPricingQuoteResolutionController {

    private final PricingQuoteResolver pricingQuoteResolver;

    public V2ProjectPricingQuoteResolutionController(PricingQuoteResolver pricingQuoteResolver) {
        this.pricingQuoteResolver = pricingQuoteResolver;
    }

    @Operation(summary = "解析项目取价基准",
            description = "按项目配置(或调用方覆盖)与单据日期解析实际取价日与时段; 休市/缺时段会回退并在响应中说明, "
                    + "手工基准不自动替换只给建议日期")
    @GetMapping
    @RequirePermission(PermissionCodes.MATERIAL_PRICE_MATCHES_READ)
    public PricingQuoteResolutionResponse resolve(
            @PathVariable Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliveryDate,
            @RequestParam(required = false) String basis,
            @RequestParam(required = false) Integer offsetDays,
            @RequestParam(required = false) Boolean settlePrevOnClosed,
            @RequestParam(required = false) String periodPolicy,
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate quoteDate) {
        PricingQuoteResolver.Command command = new PricingQuoteResolver.Command(
                parseBasis(basis),
                offsetDays,
                settlePrevOnClosed,
                parsePeriodPolicy(periodPolicy),
                period,
                quoteDate,
                orderDate,
                deliveryDate);
        return pricingQuoteResolver.resolve(projectId, command);
    }

    private Basis parseBasis(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Basis parsed = Basis.parse(value);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "取价基准仅支持 DELIVERY/ORDER/MANUAL");
        }
        return parsed;
    }

    private PeriodPolicy parsePeriodPolicy(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        PeriodPolicy parsed = PeriodPolicy.parse(value);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "时段策略仅支持 AUTO/FIXED/MANUAL");
        }
        return parsed;
    }
}
