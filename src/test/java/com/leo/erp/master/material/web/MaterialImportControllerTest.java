package com.leo.erp.master.material.web;

import com.leo.erp.master.material.web.dto.MaterialImportPreviewResponse;
import com.leo.erp.master.material.web.dto.MaterialImportResultResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MaterialImportController 端点语义测试：导入与 dry-run 预览都属于同步命令（200），
 * 导入结果不是可回读资源，不能返回 201/Location。
 */
@ExtendWith(MockitoExtension.class)
class MaterialImportControllerTest {

    @Mock
    private MaterialImportFileAdapter materialImportFileAdapter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MaterialImportController(materialImportFileAdapter))
                .build();
    }

    @Test
    void create_shouldReturnOkForSpreadsheetUpload() throws Exception {
        when(materialImportFileAdapter.importSpreadsheet(any()))
                .thenReturn(new MaterialImportResultResponse(2, 2, 1, 1, 0, 0, List.of(), List.of()));

        MockMultipartFile file = new MockMultipartFile(
                "file", "material.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx".getBytes());

        // 导入是同步命令：200 + 结果，不返回 201、不发 Location（结果没有可回读资源 id）
        mockMvc.perform(multipart("/v2.0/material-imports").file(file))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.successCount").value(2));
    }

    @Test
    void preview_shouldReturnOkForSpreadsheetUpload() throws Exception {
        when(materialImportFileAdapter.previewSpreadsheet(any()))
                .thenReturn(new MaterialImportPreviewResponse(2, 1, 1, 0, 0, List.of()));

        MockMultipartFile file = new MockMultipartFile(
                "file", "material.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx".getBytes());

        // dry-run 预览不是创建资源: 200 + 预览结果, 不发 Location
        mockMvc.perform(multipart("/v2.0/material-imports/previews").file(file))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(1));
    }
}
