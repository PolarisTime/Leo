package com.leo.erp.common.moduleexport.web;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.moduleexport.service.ModuleExportService;
import com.leo.erp.common.moduleexport.web.dto.ModuleExportRequest;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V2ModuleExportController 资源型导出端点契约测试：
 * 覆盖正常导出、空集合、非法 id、不存在的 id、不支持的模块，以及 ProblemDetail 与权限注解契约。
 */
@ExtendWith(MockitoExtension.class)
class V2ModuleExportControllerTest {

    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String ENDPOINT = "/v2.0/module-exports";

    @Mock
    private ModuleExportService moduleExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new V2ModuleExportController(moduleExportService))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .build();
    }

    @Test
    void create_withRecordIds_shouldReturnXlsxWithDownloadHeaders() throws Exception {
        byte[] content = "xlsx-content".getBytes(StandardCharsets.UTF_8);
        when(moduleExportService.export(anyString(), any()))
                .thenReturn(new FileDownloadResponse("销售订单.xlsx", ModuleExportService.xlsxMediaType(), content));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\",\"recordIds\":[\"101\",\"205\"]}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".xlsx")))
                .andExpect(content().bytes(content));

        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(moduleExportService).export(anyString(), ids.capture());
        assertThat(ids.getValue()).containsExactly(101L, 205L);
    }

    @Test
    void create_withMaxSnowflakeId_shouldKeepFullPrecision() throws Exception {
        when(moduleExportService.export(anyString(), any()))
                .thenReturn(new FileDownloadResponse("销售订单.xlsx", ModuleExportService.xlsxMediaType(), new byte[]{1}));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\",\"recordIds\":[\"9223372036854775807\"]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<Long>> ids = ArgumentCaptor.forClass(List.class);
        verify(moduleExportService).export(anyString(), ids.capture());
        assertThat(ids.getValue()).containsExactly(Long.MAX_VALUE);
    }

    @Test
    void create_withoutRecordIds_shouldExportWholeModule() throws Exception {
        when(moduleExportService.export(anyString(), any()))
                .thenReturn(new FileDownloadResponse("销售订单.xlsx", ModuleExportService.xlsxMediaType(), new byte[]{1}));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\"}"))
                .andExpect(status().isOk());

        verify(moduleExportService).export(anyString(), isNull());
    }

    @Test
    void create_withEmptyRecordIds_shouldReturn422() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\",\"recordIds\":[]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.type").value("urn:leo:problem:validation-error"));

        verify(moduleExportService, never()).export(anyString(), any());
    }

    @Test
    void create_withTooManyRecordIds_shouldReturn422() throws Exception {
        StringBuilder payload = new StringBuilder("{\"moduleKey\":\"sales-order\",\"recordIds\":[");
        for (int i = 1; i <= ModuleExportService.MAX_RECORD_IDS + 1; i++) {
            if (i > 1) {
                payload.append(',');
            }
            payload.append('"').append(i).append('"');
        }
        payload.append("]}");

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(moduleExportService, never()).export(anyString(), any());
    }

    @Test
    void create_withBlankModuleKey_shouldReturn422() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"\",\"recordIds\":[\"101\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    void create_withNumericSnowflakeId_shouldReturn400() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\",\"recordIds\":[9223372036854775807]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));

        verify(moduleExportService, never()).export(anyString(), any());
    }

    @Test
    void create_withNonDecimalRecordId_shouldReturn400() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\",\"recordIds\":[\"abc\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    void create_withNonPositiveRecordId_shouldReturn400() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\",\"recordIds\":[\"0\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_withUnknownRecordId_shouldReturn404ProblemDetail() throws Exception {
        when(moduleExportService.export(anyString(), any())).thenThrow(
                new BusinessException(ErrorCode.NOT_FOUND, "导出记录不存在或已删除: 999"));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\",\"recordIds\":[\"999\"]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.type").value("urn:leo:problem:not-found"))
                .andExpect(jsonPath("$.detail").value(containsString("999")));
    }

    @Test
    void create_withUnsupportedModule_shouldReturn422ProblemDetail() throws Exception {
        when(moduleExportService.export(anyString(), any())).thenThrow(
                new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的导出模块: supplier"));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"supplier\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.detail").value(containsString("不支持的导出模块")));
    }

    @Test
    void create_withDuplicateRecordIds_shouldReturn422() throws Exception {
        when(moduleExportService.export(anyString(), any())).thenThrow(
                new BusinessException(ErrorCode.VALIDATION_ERROR, "recordIds 存在重复记录 id"));

        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\",\"recordIds\":[\"101\",\"101\"]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("重复")));
    }

    @Test
    void create_shouldRequireModuleExportsExportPermission() throws Exception {
        Method method = V2ModuleExportController.class.getMethod("create", ModuleExportRequest.class);

        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(PermissionCodes.MODULE_EXPORTS_EXPORT);
    }
}
