package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.web.BindPageQuery;
import com.leo.erp.master.material.service.MaterialService;
import com.leo.erp.master.material.web.dto.MaterialImportResultResponse;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import com.leo.erp.security.permission.RequiresPermission;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
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

@RestController
@RequestMapping("/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping("/search")
    @RequiresPermission(resource = "material", action = "read")
    public ApiResponse<java.util.List<MaterialResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return ApiResponse.success(materialService.search(keyword != null ? keyword : "", Math.min(limit, 500)));
    }

    @GetMapping
    @RequiresPermission(resource = "material", action = "read")
    public ApiResponse<PageResponse<MaterialResponse>> page(
            @BindPageQuery(sortFieldKey = "materials") PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String material
    ) {
        return ApiResponse.success(PageResponse.from(materialService.page(query, keyword, category, material)));
    }

    @GetMapping("/{id}")
    @RequiresPermission(resource = "material", action = "read")
    public ApiResponse<MaterialResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(materialService.detail(id));
    }

    @PostMapping
    @RequiresPermission(resource = "material", action = "create")
    public ApiResponse<MaterialResponse> create(@Valid @RequestBody MaterialRequest request) {
        return ApiResponse.success("创建成功", materialService.create(request));
    }

    @GetMapping("/template")
    @RequiresPermission(resource = "material", action = "export")
    public void downloadTemplate(HttpServletResponse response) throws IOException {
        byte[] file = materialService.downloadTemplateCsv();
        String filename = "商品资料导入模板.csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" +
                java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8) + "\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(file.length);
        response.getOutputStream().write(file);
        response.getOutputStream().flush();
    }

    @PostMapping("/export")
    @RequiresPermission(resource = "material", action = "export")
    public void export(@RequestParam(required = false) String keyword, HttpServletResponse response) throws IOException {
        byte[] file = materialService.exportCsv(keyword);
        String filename = "materials.csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(file.length);
        response.getOutputStream().write(file);
        response.getOutputStream().flush();
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission(resource = "material", action = "update")
    public ApiResponse<MaterialImportResultResponse> importMaterials(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.success("导入成功", materialService.importCsv(file));
    }

    @PutMapping("/{id}")
    @RequiresPermission(resource = "material", action = "update")
    public ApiResponse<MaterialResponse> update(@PathVariable Long id, @Valid @RequestBody MaterialRequest request) {
        return ApiResponse.success("更新成功", materialService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission(resource = "material", action = "delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return ApiResponse.success("删除成功", null);
    }
}
