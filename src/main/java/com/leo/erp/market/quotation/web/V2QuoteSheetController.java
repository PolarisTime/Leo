package com.leo.erp.market.quotation.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.market.quotation.service.QuoteSheetService;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Tag(name = "比价报价单")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/quote-sheets")
public class V2QuoteSheetController {

    private final QuoteSheetService quoteSheetService;

    public V2QuoteSheetController(QuoteSheetService quoteSheetService) {
        this.quoteSheetService = quoteSheetService;
    }

    @Operation(summary = "分页查询报价单")
    @GetMapping
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_READ)
    public PageResponse<QuoteSheetResponse> page(
            @BindPageQuery(sortFieldKey = "quote-sheet") PageQuery query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate orderDate,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String keyword) {
        return PageResponse.from(quoteSheetService.page(query, orderDate, projectId, keyword));
    }

    @Operation(summary = "报价单详情")
    @GetMapping("/{id}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_READ)
    public QuoteSheetResponse detail(@PathVariable Long id) {
        return quoteSheetService.detail(id);
    }

    @Operation(summary = "创建报价单")
    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_CREATE)
    public ResponseEntity<QuoteSheetResponse> create(@Valid @RequestBody QuoteSheetRequest request) {
        return V2ResponseSupport.created("/quote-sheets", quoteSheetService.create(request));
    }

    @Operation(summary = "更新报价单",
            description = "未携带 brands/items 时仅更新表头; 携带时按整体替换处理(向后兼容)")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public QuoteSheetResponse update(@AuthenticationPrincipal SecurityPrincipal principal,
                                     @PathVariable Long id,
                                     @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                     @Valid @RequestBody QuoteSheetRequest request) {
        return quoteSheetService.update(id, request, IfMatchVersion.parse(ifMatch), ownerId(principal));
    }

    @Operation(summary = "删除报价单")
    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        quoteSheetService.delete(id);
        return V2ResponseSupport.noContent();
    }

    @Operation(summary = "新增商品行")
    @PostMapping("/{id}/items")
    @V2Created
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<QuoteSheetResponse.ItemResponse> addItem(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable Long id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody QuoteSheetRequest.ItemRequest request) {
        QuoteSheetResponse.ItemResponse item = quoteSheetService.addItem(
                id, request, IfMatchVersion.parse(ifMatch), ownerId(principal));
        return V2ResponseSupport.created("/quote-sheets/" + id + "/items", item);
    }

    @Operation(summary = "整行替换商品行")
    @PutMapping("/{id}/items/{itemId}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public QuoteSheetResponse.ItemResponse updateItem(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody QuoteSheetRequest.ItemRequest request) {
        return quoteSheetService.updateItem(id, itemId, request, IfMatchVersion.parse(ifMatch), ownerId(principal));
    }

    @Operation(summary = "删除商品行")
    @DeleteMapping("/{id}/items/{itemId}")
    @V2NoContent
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<Void> deleteItem(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        quoteSheetService.deleteItem(id, itemId, IfMatchVersion.parse(ifMatch), ownerId(principal));
        return V2ResponseSupport.noContent();
    }

    private Long ownerId(SecurityPrincipal principal) {
        return principal == null ? 0L : principal.id();
    }
}
