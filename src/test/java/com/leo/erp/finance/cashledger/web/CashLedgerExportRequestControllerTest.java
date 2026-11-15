package com.leo.erp.finance.cashledger.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.export.ExportArtifactProperties;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.export.ExportConcurrencyProperties;
import com.leo.erp.common.export.InMemoryExportArtifactStore;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.cashledger.service.CashLedgerExportArtifactService;
import com.leo.erp.finance.cashledger.service.CashLedgerService;
import com.leo.erp.finance.cashledger.web.dto.CashLedgerExportRequest;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资金流水导出请求资源的 HTTP 契约测试。
 *
 * <p>覆盖：POST 体筛选（雪花 id 字符串保持精度）、201 + Location、内容端点、
 * 行数上限 422、未知 id 404、权限与幂等注解契约。</p>
 */
@ExtendWith(MockitoExtension.class)
class CashLedgerExportRequestControllerTest {

    private static final String COLLECTION = "/v2.0/cash-ledger-export-requests";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Mock
    private CashLedgerService cashLedgerService;

    private CashLedgerExportArtifactService artifactService;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ExportArtifactStore store = new InMemoryExportArtifactStore(
                new SnowflakeIdGenerator(), new ExportArtifactProperties());
        artifactService = new CashLedgerExportArtifactService(
                cashLedgerService,
                new ExportConcurrencyGuard(new ExportConcurrencyProperties()),
                store);
        objectMapper = jsonObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CashLedgerExportRequestController(artifactService, store))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new ResourceHttpMessageConverter(),
                        new ByteArrayHttpMessageConverter())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ObjectMapper jsonObjectMapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        new com.leo.erp.common.config.JacksonConfig("Asia/Shanghai")
                .jackson2ObjectMapperBuilderCustomizer()
                .customize(builder);
        return builder.build();
    }

    private void stubExport(byte[] content) {
        when(cashLedgerService.exportExcel(anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FileDownloadResponse(
                        "资金流水.xlsx", MediaType.parseMediaType(XLSX_CONTENT_TYPE), content));
    }

    @Test
    void create_shouldKeepSnowflakePrecisionAndReturn201() throws Exception {
        stubExport("xlsx-content".getBytes(StandardCharsets.UTF_8));

        MvcResult result = mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"settlementCompanyId":"9223372036854775807",
                                 "startDate":"2026-01-01","endDate":"2026-01-31"}"""))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value(isA(String.class)))
                .andExpect(jsonPath("$.fileName").value("资金流水.xlsx"))
                .andExpect(jsonPath("$.contentType").value(XLSX_CONTENT_TYPE))
                .andReturn();

        verify(cashLedgerService).exportExcel(
                org.mockito.ArgumentMatchers.eq(Long.MAX_VALUE),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 1, 1)),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 1, 31)),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull());

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .contains("/v2.0/cash-ledger-export-requests/")
                .endsWith("/" + id);
    }

    @Test
    void content_shouldReturnXlsxBytes() throws Exception {
        byte[] content = "xlsx-content".getBytes(StandardCharsets.UTF_8);
        stubExport(content);
        String id = createExportId();

        mockMvc.perform(get(COLLECTION + "/" + id + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                .andExpect(content().bytes(content));
    }

    @Test
    void create_whenRowsExceedLimit_shouldReturn422WithActionableMessage() throws Exception {
        when(cashLedgerService.exportExcel(anyLong(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "导出数据量超过上限 " + CashLedgerService.MAX_EXPORT_ROWS
                                + " 行，请缩小日期范围或增加筛选条件后重试"));

        mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementCompanyId\":\"1\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.detail").value(containsString("缩小日期范围")));
    }

    @Test
    void create_withoutSettlementCompany_shouldReturn422() throws Exception {
        mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    void content_withUnknownId_shouldReturn404() throws Exception {
        mockMvc.perform(get(COLLECTION + "/123456789/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void create_shouldRequirePermissionAndIdempotency() throws Exception {
        Method create = CashLedgerExportRequestController.class.getMethod(
                "create", SecurityPrincipal.class, CashLedgerExportRequest.class);
        RequirePermission permission = create.getAnnotation(RequirePermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly(PermissionCodes.CASH_LEDGER_EXPORT);
        assertThat(CashLedgerExportRequestController.class.getAnnotation(IdempotencyRequired.class)).isNotNull();
    }

    private String createExportId() throws Exception {
        MvcResult result = mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settlementCompanyId\":\"1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
