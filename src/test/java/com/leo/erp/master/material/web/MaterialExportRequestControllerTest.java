package com.leo.erp.master.material.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.export.ExportArtifactProperties;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.export.ExportConcurrencyProperties;
import com.leo.erp.common.export.InMemoryExportArtifactStore;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.material.service.MaterialDocumentService;
import com.leo.erp.master.material.service.MaterialExportArtifactService;
import com.leo.erp.master.material.web.dto.MaterialExportRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isA;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品资料导出请求资源的 HTTP 契约测试。
 *
 * <p>覆盖：201 + Location + 雪花 id 字符串、xlsx/csv 两种媒体类型的内容端点、
 * 非法格式 422、未知 id 404、权限与幂等注解契约。</p>
 */
@ExtendWith(MockitoExtension.class)
class MaterialExportRequestControllerTest {

    private static final String COLLECTION = "/v2.0/material-export-requests";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    @Mock
    private MaterialDocumentService materialDocumentService;

    private MaterialExportArtifactService artifactService;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ExportArtifactStore store = new InMemoryExportArtifactStore(
                new SnowflakeIdGenerator(), new ExportArtifactProperties());
        artifactService = new MaterialExportArtifactService(
                materialDocumentService,
                new ExportConcurrencyGuard(new ExportConcurrencyProperties()),
                store);
        objectMapper = jsonObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MaterialExportRequestController(artifactService, store))
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

    @Test
    void create_shouldReturn201WithLocationAndMetadata() throws Exception {
        byte[] content = "xlsx-content".getBytes(StandardCharsets.UTF_8);
        when(materialDocumentService.exportSpreadsheet(eq("螺纹"))).thenReturn(content);

        MvcResult result = mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"螺纹\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value(isA(String.class)))
                .andExpect(jsonPath("$.fileName").value("material.xlsx"))
                .andExpect(jsonPath("$.contentType").value(XLSX_CONTENT_TYPE))
                .andExpect(jsonPath("$.size").value(content.length))
                .andExpect(jsonPath("$.contentPath").value(containsString("/content")))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .contains("/v2.0/material-export-requests/")
                .endsWith("/" + id);
    }

    @Test
    void content_shouldReturnXlsxBytes() throws Exception {
        byte[] content = "xlsx-content".getBytes(StandardCharsets.UTF_8);
        when(materialDocumentService.exportSpreadsheet(isNull())).thenReturn(content);
        String id = createExportId("{}");

        mockMvc.perform(get(COLLECTION + "/" + id + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("material.xlsx")))
                .andExpect(content().bytes(content));
    }

    @Test
    void create_withCsvFormat_shouldReturnCsvContentType() throws Exception {
        byte[] content = "csv-content".getBytes(StandardCharsets.UTF_8);
        when(materialDocumentService.exportCsv(isNull())).thenReturn(content);

        MvcResult result = mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"csv\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("materials.csv"))
                .andExpect(jsonPath("$.contentType").value(CSV_CONTENT_TYPE))
                .andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get(COLLECTION + "/" + id + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, CSV_CONTENT_TYPE))
                .andExpect(content().bytes(content));
    }

    @Test
    void create_withUnsupportedFormat_shouldReturn422() throws Exception {
        mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"format\":\"pdf\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.detail").value(containsString("不支持的导出格式")));
    }

    @Test
    void content_withUnknownId_shouldReturn404() throws Exception {
        mockMvc.perform(get(COLLECTION + "/123456789/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void create_shouldRequirePermissionAndIdempotency() throws Exception {
        Method create = MaterialExportRequestController.class.getMethod(
                "create", SecurityPrincipal.class, MaterialExportRequest.class);
        RequirePermission permission = create.getAnnotation(RequirePermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly(PermissionCodes.MATERIAL_EXPORTS_EXPORT);
        assertThat(MaterialExportRequestController.class.getAnnotation(IdempotencyRequired.class)).isNotNull();
    }

    private String createExportId(String body) throws Exception {
        MvcResult result = mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
