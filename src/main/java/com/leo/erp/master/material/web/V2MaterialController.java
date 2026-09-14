package com.leo.erp.master.material.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.material.service.MaterialDocumentService;
import com.leo.erp.master.material.service.MaterialHistoryQueryService;
import com.leo.erp.master.material.service.MaterialService;
import com.leo.erp.master.material.web.dto.MaterialHistoryResponse;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.nio.charset.StandardCharsets;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;

@RestController
@Validated
@IdempotencyRequired
@Tag(name = "商品资料")
@RequestMapping(ApiVersion.V2_PREFIX + "/materials")
public class V2MaterialController {

    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final MediaType XLSX_MEDIA_TYPE = new MediaType(
            "application",
            "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final MaterialService materialService;
    private final MaterialDocumentService materialDocumentService;
    private final MaterialHistoryQueryService materialHistoryQueryService;

    public V2MaterialController(MaterialService materialService,
                                MaterialDocumentService materialDocumentService,
                                MaterialHistoryQueryService materialHistoryQueryService) {
        this.materialService = materialService;
        this.materialDocumentService = materialDocumentService;
        this.materialHistoryQueryService = materialHistoryQueryService;
    }

    @GetMapping
    @Operation(summary = "分页查询商品资料")
    @RequirePermission(PermissionCodes.MATERIALS_READ)
    public PageResponse<MaterialResponse> page(@BindPageQuery(sortFieldKey = "material") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String category, @RequestParam(required = false) String material, @RequestParam(required = false) String materialType) {
        return PageResponse.from(materialService.page(query, keyword, category, material, materialType));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询商品资料详情")
    @RequirePermission(PermissionCodes.MATERIALS_READ)
    public MaterialResponse detail(@PathVariable Long id) {
        return materialService.detail(id);
    }

    @GetMapping("/{id}/histories")
    @Operation(summary = "分页查询商品资料版本历史")
    @RequirePermission(PermissionCodes.MATERIALS_READ)
    public PageResponse<MaterialHistoryResponse> histories(
            @PathVariable Long id,
            @BindPageQuery(sortFieldKey = "material") PageQuery query) {
        return PageResponse.from(materialHistoryQueryService.page(id, query));
    }

    @PostMapping
    @V2Created
    @Operation(summary = "创建商品资料")
    @RequirePermission(PermissionCodes.MATERIALS_CREATE)
    public ResponseEntity<MaterialResponse> create(@Valid @RequestBody MaterialRequest request) {
        return V2ResponseSupport.created("/materials", materialService.create(request));
    }

    @GetMapping("/template")
    @Operation(summary = "下载商品资料导入模板（XLSX）")
    @RequirePermission(PermissionCodes.MATERIALS_READ)
    public ResponseEntity<byte[]> downloadTemplate() {
        return toDownloadResponse(
                "商品资料导入模板.xlsx", XLSX_MEDIA_TYPE, materialDocumentService.spreadsheetTemplate());
    }

    @GetMapping("/template/csv")
    @Operation(summary = "下载商品资料导入模板（CSV）")
    @RequirePermission(PermissionCodes.MATERIALS_READ)
    public ResponseEntity<byte[]> downloadCsvTemplate() {
        return toDownloadResponse(
                "商品资料导入模板.csv", CSV_MEDIA_TYPE, materialDocumentService.csvTemplate());
    }

    @GetMapping("/grades")
    @Operation(summary = "查询商品材质（品名）选项")
    @RequirePermission(PermissionCodes.MATERIALS_READ)
    public java.util.List<String> materialGrades() {
        return materialService.materialGrades();
    }

    @GetMapping("/brands")
    @Operation(summary = "查询商品品牌选项")
    @RequirePermission(PermissionCodes.MATERIALS_READ)
    public java.util.List<String> materialBrands() {
        return materialService.materialBrands();
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新商品资料")
    @RequirePermission(PermissionCodes.MATERIALS_UPDATE)
    public MaterialResponse update(@PathVariable Long id, @Valid @RequestBody MaterialRequest request) {
        return materialService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
    @Operation(summary = "删除商品资料")
    @RequirePermission(PermissionCodes.MATERIALS_DELETE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return V2ResponseSupport.noContent();
    }

    private ResponseEntity<byte[]> toDownloadResponse(String filename, MediaType contentType, byte[] content) {
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(content.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(content);
    }
}
