package com.leo.erp.master.material.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.excel.dto.ImportResult;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.master.material.service.MaterialService;
import com.leo.erp.master.material.web.dto.MaterialImportResultResponse;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaterialControllerTest {

    private final MaterialService materialService = mock(MaterialService.class);
    private final MaterialController controller = new MaterialController(materialService);

    @Test
    void searchReturnsMaterialList() {
        MaterialResponse material = mock(MaterialResponse.class);
        when(materialService.search("test", 100)).thenReturn(List.of(material));

        ApiResponse<List<MaterialResponse>> response = controller.search("test", 100);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly(material);
        verify(materialService).search("test", 100);
    }

    @Test
    void searchWithNullKeywordUsesEmptyString() {
        when(materialService.search("", 100)).thenReturn(List.of());

        ApiResponse<List<MaterialResponse>> response = controller.search(null, 100);

        assertThat(response.data()).isEmpty();
        verify(materialService).search("", 100);
    }

    @Test
    void searchLimitsMaxTo500() {
        when(materialService.search("test", 500)).thenReturn(List.of());

        controller.search("test", 1000);

        verify(materialService).search("test", 500);
    }

    @Test
    void pageReturnsPaginatedMaterials() {
        MaterialResponse material = mock(MaterialResponse.class);
        Page<MaterialResponse> page = new PageImpl<>(List.of(material));
        PageQuery query = new PageQuery(0, 20, null, null);
        when(materialService.page(any(), eq("test"), eq("category"), eq("material"))).thenReturn(page);

        ApiResponse<PageResponse<MaterialResponse>> response = controller.page(query, "test", "category", "material");

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data().content()).hasSize(1);
    }

    @Test
    void detailReturnsMaterialById() {
        MaterialResponse material = mock(MaterialResponse.class);
        when(materialService.detail(1L)).thenReturn(material);

        ApiResponse<MaterialResponse> response = controller.detail(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).isEqualTo(material);
    }

    @Test
    void createReturnsCreatedMaterial() {
        MaterialRequest request = mock(MaterialRequest.class);
        MaterialResponse created = mock(MaterialResponse.class);
        when(materialService.create(request)).thenReturn(created);

        ApiResponse<MaterialResponse> response = controller.create(request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("创建成功");
        verify(materialService).create(request);
    }

    @Test
    void updateReturnsUpdatedMaterial() {
        MaterialRequest request = mock(MaterialRequest.class);
        MaterialResponse updated = mock(MaterialResponse.class);
        when(materialService.update(1L, request)).thenReturn(updated);

        ApiResponse<MaterialResponse> response = controller.update(1L, request);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("更新成功");
        verify(materialService).update(1L, request);
    }

    @Test
    void deleteCallsServiceDelete() {
        ApiResponse<Void> response = controller.delete(1L);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("删除成功");
        verify(materialService).delete(1L);
    }

    @Test
    void materialGradesReturnsGradesList() {
        when(materialService.materialGrades()).thenReturn(List.of("A", "B"));

        ApiResponse<List<String>> response = controller.materialGrades();

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.data()).containsExactly("A", "B");
    }

    @Test
    void downloadTemplateReturnsExcelTemplate() {
        byte[] content = new byte[]{1, 2, 3};
        FileDownloadResponse fileResponse = new FileDownloadResponse(
                "template.xlsx",
                MediaType.APPLICATION_OCTET_STREAM,
                content
        );
        when(materialService.excelTemplate()).thenReturn(fileResponse);

        var response = controller.downloadTemplate();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(content);
    }

    @Test
    void downloadCsvTemplateReturnsCsvTemplate() {
        byte[] content = new byte[]{1, 2, 3};
        FileDownloadResponse fileResponse = new FileDownloadResponse(
                "template.csv",
                MediaType.TEXT_PLAIN,
                content
        );
        when(materialService.downloadTemplateFile()).thenReturn(fileResponse);

        var response = controller.downloadCsvTemplate();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(content);
    }

    @Test
    void exportReturnsExcelFile() {
        byte[] content = new byte[]{1, 2, 3};
        FileDownloadResponse fileResponse = new FileDownloadResponse(
                "export.xlsx",
                MediaType.APPLICATION_OCTET_STREAM,
                content
        );
        when(materialService.exportExcel("test")).thenReturn(fileResponse);

        var response = controller.export("test");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(content);
    }

    @Test
    void exportCsvReturnsCsvFile() {
        byte[] content = new byte[]{1, 2, 3};
        FileDownloadResponse fileResponse = new FileDownloadResponse(
                "export.csv",
                MediaType.TEXT_PLAIN,
                content
        );
        when(materialService.exportFile("test")).thenReturn(fileResponse);

        var response = controller.exportCsv("test");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(content);
    }

    @Test
    void importMaterialsReturnsImportResult() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        ImportResult result = mock(ImportResult.class);
        when(materialService.importExcel(file)).thenReturn(result);

        ApiResponse<ImportResult> response = controller.importMaterials(file);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("导入成功");
        verify(materialService).importExcel(file);
    }

    @Test
    void importCsvMaterialsReturnsImportResult() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "test.csv", "text/plain", new byte[]{1, 2, 3});
        MaterialImportResultResponse result = mock(MaterialImportResultResponse.class);
        when(materialService.importCsv(file)).thenReturn(result);

        ApiResponse<MaterialImportResultResponse> response = controller.importCsvMaterials(file);

        assertThat(response.code()).isEqualTo(0);
        assertThat(response.message()).isEqualTo("导入成功");
        verify(materialService).importCsv(file);
    }
}