package com.leo.erp.market.quotation.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.market.quotation.QuotationProperties;
import com.leo.erp.market.quotation.service.QuoteSheetItemWrite;
import com.leo.erp.market.quotation.service.QuoteSheetService;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.market.quotation.web.dto.PurchaseOrderTonnageResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import java.util.List;

@Tag(name = "比价报价单")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/quote-sheets")
public class V2QuoteSheetController {

    private static final String VERSION_HEADER = ResourceVersionPrecondition.HEADER;
    private static final String VERSION_ALIAS_NOTE =
            "也接受兼容别名 If-Match(非标准: 数据库版本号是弱验证器, 不符合 RFC 9110 强比较要求)。";

    private final QuoteSheetService quoteSheetService;
    private final QuotationProperties properties;

    public V2QuoteSheetController(QuoteSheetService quoteSheetService, QuotationProperties properties) {
        this.quoteSheetService = quoteSheetService;
        this.properties = properties;
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

    @Operation(summary = "采购订单已开吨位汇总",
            description = "列出采购订单及其订货吨数、报单已开吨位与剩余可开吨(订货 - 已开), "
                    + "供吨位列关联采购订单时选择与展示。传 purchaseOrderIds 按 id 汇总, "
                    + "否则按 keyword/status 列出选项; 可选 excludeSheetId 排除当前报价单自身已保存吨位。")
    @GetMapping("/purchase-order-tonnages")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_READ)
    public List<PurchaseOrderTonnageResponse> purchaseOrderTonnages(
            @RequestParam(required = false) @Size(max = 200) List<@Positive Long> purchaseOrderIds,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long excludeSheetId) {
        return quoteSheetService.summarizeTonnages(purchaseOrderIds, keyword, status, excludeSheetId);
    }

    @Operation(summary = "创建报价单")
    @PostMapping
    @V2Created
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_CREATE)
    public ResponseEntity<QuoteSheetResponse> create(@Valid @RequestBody QuoteSheetRequest request) {
        return V2ResponseSupport.created("/quote-sheets", quoteSheetService.create(request));
    }

    @Operation(summary = "更新报价单",
            description = "未携带 brands/items 时仅更新表头; 携带时按整体替换处理(向后兼容)。"
                    + "写操作要求资源版本前置条件头 " + VERSION_HEADER + ", " + VERSION_ALIAS_NOTE
                    + " 缺少版本返回 428, 版本不匹配返回 412; 响应头回传最新版本。")
    @PutMapping("/{id}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<QuoteSheetResponse> update(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable Long id,
            @Parameter(description = "资源版本(强比较), 如 3")
            @RequestHeader(value = VERSION_HEADER, required = false) String resourceVersion,
            @Parameter(description = "兼容别名, 非标准弱验证器用法")
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody QuoteSheetRequest request) {
        QuoteSheetResponse response = quoteSheetService.update(id, request,
                ResourceVersionPrecondition.parse(resourceVersion, ifMatch, properties.isRequireResourceVersion()),
                ownerId(principal));
        return withVersion(response, response.version());
    }

    @Operation(summary = "删除报价单")
    @DeleteMapping("/{id}")
    @V2NoContent
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        quoteSheetService.delete(id);
        return V2ResponseSupport.noContent();
    }

    @Operation(summary = "新增商品行",
            description = "要求 " + VERSION_HEADER + ", " + VERSION_ALIAS_NOTE
                    + " 缺少版本返回 428, 版本不匹配返回 412; 响应头回传单据最新版本。")
    @PostMapping("/{id}/items")
    @V2Created
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<QuoteSheetResponse.ItemResponse> addItem(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable Long id,
            @Parameter(description = "资源版本(强比较), 如 3")
            @RequestHeader(value = VERSION_HEADER, required = false) String resourceVersion,
            @Parameter(description = "兼容别名, 非标准弱验证器用法")
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody QuoteSheetRequest.ItemRequest request) {
        QuoteSheetItemWrite write = quoteSheetService.addItem(id, request,
                ResourceVersionPrecondition.parse(resourceVersion, ifMatch, properties.isRequireResourceVersion()),
                ownerId(principal));
        ResponseEntity<QuoteSheetResponse.ItemResponse> created =
                V2ResponseSupport.created("/quote-sheets/" + id + "/items", write.item());
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(created.getHeaders().getLocation())
                .header(VERSION_HEADER, String.valueOf(write.version()))
                .body(write.item());
    }

    @Operation(summary = "整行替换商品行",
            description = "要求 " + VERSION_HEADER + ", " + VERSION_ALIAS_NOTE
                    + " 缺少版本返回 428, 版本不匹配返回 412; 响应头回传单据最新版本。")
    @PutMapping("/{id}/items/{itemId}")
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<QuoteSheetResponse.ItemResponse> updateItem(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Parameter(description = "资源版本(强比较), 如 3")
            @RequestHeader(value = VERSION_HEADER, required = false) String resourceVersion,
            @Parameter(description = "兼容别名, 非标准弱验证器用法")
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody QuoteSheetRequest.ItemRequest request) {
        QuoteSheetItemWrite write = quoteSheetService.updateItem(id, itemId, request,
                ResourceVersionPrecondition.parse(resourceVersion, ifMatch, properties.isRequireResourceVersion()),
                ownerId(principal));
        return withVersion(write.item(), write.version());
    }

    @Operation(summary = "删除商品行",
            description = "要求 " + VERSION_HEADER + ", " + VERSION_ALIAS_NOTE
                    + " 缺少版本返回 428, 版本不匹配返回 412; 响应头回传单据最新版本。")
    @DeleteMapping("/{id}/items/{itemId}")
    @V2NoContent
    @RequirePermission(PermissionCodes.QUOTE_SHEETS_UPDATE)
    public ResponseEntity<Void> deleteItem(
            @AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long itemId,
            @Parameter(description = "资源版本(强比较), 如 3")
            @RequestHeader(value = VERSION_HEADER, required = false) String resourceVersion,
            @Parameter(description = "兼容别名, 非标准弱验证器用法")
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        Long version = quoteSheetService.deleteItem(id, itemId,
                ResourceVersionPrecondition.parse(resourceVersion, ifMatch, properties.isRequireResourceVersion()),
                ownerId(principal));
        return ResponseEntity.noContent().header(VERSION_HEADER, String.valueOf(version)).build();
    }

    private static <T> ResponseEntity<T> withVersion(T body, Long version) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (version != null) {
            builder.header(VERSION_HEADER, version.toString());
        }
        return builder.body(body);
    }

    private Long ownerId(SecurityPrincipal principal) {
        return principal == null ? 0L : principal.id();
    }
}
