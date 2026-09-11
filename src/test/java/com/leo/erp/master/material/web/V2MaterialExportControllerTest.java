package com.leo.erp.master.material.web;

import com.leo.erp.master.material.service.MaterialDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2MaterialExportController 资源型导出端点测试：
 * 校验状态码、下载响应头（Content-Type / Content-Disposition）与响应体，覆盖 xlsx、csv 及默认格式。
 */
@ExtendWith(MockitoExtension.class)
class V2MaterialExportControllerTest {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    @Mock
    private MaterialDocumentService materialDocumentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V2MaterialExportController(materialDocumentService))
                .build();
    }

    @Test
    void create_shouldReturnXlsxWithDownloadHeaders() throws Exception {
        byte[] content = "xlsx-content".getBytes(StandardCharsets.UTF_8);
        when(materialDocumentService.exportSpreadsheet(eq("螺纹"))).thenReturn(content);

        mockMvc.perform(post("/v2.0/material-exports").param("keyword", "螺纹"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("material.xlsx")))
                .andExpect(content().bytes(content));
    }

    @Test
    void create_shouldReturnCsvWhenFormatRequested() throws Exception {
        byte[] content = "csv-content".getBytes(StandardCharsets.UTF_8);
        when(materialDocumentService.exportCsv(eq(null))).thenReturn(content);

        mockMvc.perform(post("/v2.0/material-exports").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, CSV_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("materials.csv")))
                .andExpect(content().bytes(content));
    }
}
