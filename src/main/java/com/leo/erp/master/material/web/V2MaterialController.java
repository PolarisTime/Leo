package com.leo.erp.master.material.web;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.material.service.MaterialDocumentService;
import com.leo.erp.master.material.service.MaterialService;
import com.leo.erp.master.material.web.dto.MaterialImportResultResponse;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.master.material.web.dto.MaterialResponse;
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
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2NoContent;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/materials")
public class V2MaterialController {

    private static final int MAX_SEARCH_LIMIT = 500;
    private static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private static final MediaType XLSX_MEDIA_TYPE = new MediaType(
            "application",
            "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final MaterialService materialService;
    private final MaterialDocumentService materialDocumentService;
    private final MaterialImportFileAdapter materialImportFileAdapter;

    public V2MaterialController(MaterialService materialService,
                                MaterialDocumentService materialDocumentService,
                                MaterialImportFileAdapter materialImportFileAdapter) {
        this.materialService = materialService;
        this.materialDocumentService = materialDocumentService;
        this.materialImportFileAdapter = materialImportFileAdapter;
    }

    @GetMapping("/search")
    public java.util.List<MaterialResponse> search(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "100") int limit) {
        return materialService.search(keyword != null ? keyword : "", Math.min(limit, MAX_SEARCH_LIMIT));
    }

    @GetMapping
    public PageResponse<MaterialResponse> page(@BindPageQuery(sortFieldKey = "material") PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) String category, @RequestParam(required = false) String material) {
        return PageResponse.from(materialService.page(query, keyword, category, material));
    }

    @GetMapping("/{id}")
    public MaterialResponse detail(@PathVariable Long id) {
        return materialService.detail(id);
    }

    @PostMapping
    @V2Created
    public ResponseEntity<MaterialResponse> create(@Valid @RequestBody MaterialRequest request) {
        return V2ResponseSupport.created("/materials", materialService.create(request));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        return toDownloadResponse(
                "商品资料导入模板.xlsx", XLSX_MEDIA_TYPE, materialDocumentService.spreadsheetTemplate());
    }

    @GetMapping("/template/csv")
    public ResponseEntity<byte[]> downloadCsvTemplate() {
        return toDownloadResponse(
                "商品资料导入模板.csv", CSV_MEDIA_TYPE, materialDocumentService.csvTemplate());
    }

    @GetMapping("/grades")
    public java.util.List<String> materialGrades() {
        return materialService.materialGrades();
    }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String keyword) {
        return toDownloadResponse(
                "material.xlsx", XLSX_MEDIA_TYPE, materialDocumentService.exportSpreadsheet(keyword));
    }

    @PostMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) String keyword) {
        return toDownloadResponse(
                "materials.csv", CSV_MEDIA_TYPE, materialDocumentService.exportCsv(keyword));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialImportResultResponse importMaterials(@RequestParam("file") MultipartFile file) throws IOException {
        return materialImportFileAdapter.importSpreadsheet(file);
    }

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MaterialImportResultResponse importCsvMaterials(@RequestParam("file") MultipartFile file) throws IOException {
        return materialImportFileAdapter.importCsv(file);
    }

    @PutMapping("/{id}")
    public MaterialResponse update(@PathVariable Long id, @Valid @RequestBody MaterialRequest request) {
        return materialService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @V2NoContent
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
