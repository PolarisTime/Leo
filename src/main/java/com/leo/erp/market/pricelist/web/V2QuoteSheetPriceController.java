package com.leo.erp.market.pricelist.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.market.pricelist.service.QuoteSheetPriceService;
import com.leo.erp.market.pricelist.web.dto.PricePullRequest;
import com.leo.erp.market.pricelist.web.dto.PricePullResponse;
import com.leo.erp.market.pricelist.web.dto.QuoteSheetPriceCellRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 比价单现货价的手填覆盖与可选固化入口。
 *
 * <p>主路径是"读时自动推导"(见 {@code GET /quote-sheets/{id}}): 现货价/供应商由供应商价格表
 * 按单据报价时刻自动带出, 无需用户拉取。本控制器只承载显式写操作:</p>
 * <ul>
 *   <li>{@code PUT}/{@code DELETE} 单格手填覆盖, 写/删 {@code mk_quote_item_price} 行;</li>
 *   <li>{@code POST} 价格固化(可选, 把推导值快照落库供归档审计)。</li>
 * </ul>
 */
@Tag(name = "比价单现货价")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/quote-sheets/{sheetId}")
public class V2QuoteSheetPriceController {

    private final QuoteSheetPriceService priceService;

    public V2QuoteSheetPriceController(QuoteSheetPriceService priceService) {
        this.priceService = priceService;
    }

    @Operation(summary = "手填覆盖单个价格格(幂等 PUT)",
            description = "写 mk_quote_item_price 行并置 price_source='MANUAL'; 该格优先于价格表推导值。")
    @PutMapping("/items/{itemId}/price-overrides/{brandName}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public QuoteSheetResponse.ItemPriceResponse overrideCell(
            @PathVariable Long sheetId,
            @PathVariable Long itemId,
            @PathVariable String brandName,
            @Valid @RequestBody QuoteSheetPriceCellRequest request) {
        return priceService.overrideCell(sheetId, itemId, brandName, request, currentUserId());
    }

    @Operation(summary = "清除单个价格格的手填覆盖(幂等 DELETE)",
            description = "删除覆盖行后该格回到价格表推导值; 无覆盖行时同样返回 204。")
    @DeleteMapping("/items/{itemId}/price-overrides/{brandName}")
    @V2NoContent
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<Void> clearCell(@PathVariable Long sheetId,
                                          @PathVariable Long itemId,
                                          @PathVariable String brandName) {
        priceService.clearCell(sheetId, itemId, brandName);
        return V2ResponseSupport.noContent();
    }

    @Operation(summary = "价格固化(可选动作)",
            description = "把当前价格表推导值快照写入单据现货价(供归档/审计); 默认不覆盖 MANUAL 格子, "
                    + "overwriteManual=true 才覆盖并置 price_source='PRICE_LIST'。")
    @PostMapping("/price-pulls")
    @V2Created
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<PricePullResponse> pull(@PathVariable Long sheetId,
                                                  @Valid @RequestBody PricePullRequest request) {
        PricePullResponse response = priceService.pull(sheetId, request);
        return V2ResponseSupport.created("/quote-sheets/" + sheetId + "/price-pulls", response);
    }


    /** 当前操作人(与 JpaAuditConfig 同源): 未认证时回退 0。 */
    private static Long currentUserId() {
        return java.util.Optional.ofNullable(org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication())
                .filter(org.springframework.security.core.Authentication::isAuthenticated)
                .map(org.springframework.security.core.Authentication::getPrincipal)
                .filter(SecurityPrincipal.class::isInstance)
                .map(SecurityPrincipal.class::cast)
                .map(SecurityPrincipal::id)
                .orElse(0L);
    }
}
