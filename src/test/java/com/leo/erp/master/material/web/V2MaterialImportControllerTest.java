package com.leo.erp.master.material.web;

import com.leo.erp.master.material.service.MaterialBatchRollbackService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2MaterialImportController 资源型导入端点测试：导入属于创建，校验 201 与结果资源体。
 */
@ExtendWith(MockitoExtension.class)
class V2MaterialImportControllerTest {

    @Mock
    private MaterialImportFileAdapter materialImportFileAdapter;
    @Mock
    private MaterialBatchRollbackService materialBatchRollbackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V2MaterialImportController(
                        materialImportFileAdapter, materialBatchRollbackService))
                .build();
    }

    @Test
    void create_shouldReturnCreatedForSpreadsheetUpload() throws Exception {
        when(materialImportFileAdapter.importSpreadsheet(any()))
                .thenReturn(new MaterialImportResultResponse(2, 2, 1, 1, 0, 0, List.of(), List.of()));

        MockMultipartFile file = new MockMultipartFile(
                "file", "material.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx".getBytes());

        mockMvc.perform(multipart("/v2.0/material-imports").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.successCount").value(2));
    }
}
