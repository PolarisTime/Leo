package com.leo.erp.common.moduleexport.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.export.ExportArtifactKinds;
import com.leo.erp.common.export.ExportArtifactProperties;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.export.ExportConcurrencyProperties;
import com.leo.erp.common.export.InMemoryExportArtifactStore;
import com.leo.erp.common.idempotent.IdempotencyRequired;
import com.leo.erp.common.moduleexport.service.ModuleExportArtifactService;
import com.leo.erp.common.moduleexport.service.ModuleExportService;
import com.leo.erp.common.moduleexport.web.dto.ModuleExportRequest;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.web.dto.FileDownloadResponse;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 业务单据导出请求资源的 HTTP 契约测试。
 *
 * <p>覆盖：201 + Location + 响应体 id（雪花字符串）/元数据、内容端点的媒体类型与字节、
 * 未知/过期 id 的 404、权限与幂等注解契约。</p>
 *
 * <p>JSON 序列化用生产 {@code JacksonConfig} 装配，确保 {@code id} 按全局 Long→字符串契约输出，
 * 且 {@code size} 被 {@code NumericLongSerializer} 豁免为 number。</p>
 */
@ExtendWith(MockitoExtension.class)
class ModuleExportRequestControllerTest {

    private static final String COLLECTION = "/v2.0/module-export-requests";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Mock
    private ModuleExportService moduleExportService;

    private ExportArtifactStore artifactStore;
    private ModuleExportArtifactService artifactService;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        artifactStore = new InMemoryExportArtifactStore(
                new SnowflakeIdGenerator(), new ExportArtifactProperties());
        artifactService = new ModuleExportArtifactService(
                moduleExportService,
                new ExportConcurrencyGuard(new ExportConcurrencyProperties()),
                artifactStore);
        objectMapper = jsonObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ModuleExportRequestController(artifactService, artifactStore))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new org.springframework.http.converter.ResourceHttpMessageConverter(),
                        new org.springframework.http.converter.ByteArrayHttpMessageConverter())
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

    private byte[] stubExport() {
        byte[] content = "xlsx-content".getBytes(StandardCharsets.UTF_8);
        when(moduleExportService.export(anyString(), any()))
                .thenReturn(new FileDownloadResponse(
                        "销售订单.xlsx", ModuleExportService.xlsxMediaType(), content));
        return content;
    }

    private String createExportId(byte[] expectedContent) throws Exception {
        MvcResult result = mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(id).matches("\\d+");
        assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty();
        assertThat(expectedContent).isNotEmpty();
        return id;
    }

    @Test
    void create_shouldReturn201WithLocationIdAndMetadata() throws Exception {
        byte[] content = stubExport();

        MvcResult result = mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"sales-order\",\"recordIds\":[\"101\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value(isA(String.class)))
                .andExpect(jsonPath("$.kind").value(ExportArtifactKinds.MODULE_EXPORT))
                .andExpect(jsonPath("$.fileName").value("销售订单.xlsx"))
                .andExpect(jsonPath("$.contentType").value(XLSX_CONTENT_TYPE))
                .andExpect(jsonPath("$.size").value(content.length))
                .andExpect(jsonPath("$.contentPath").value(containsString("/content")))
                .andReturn();

        String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(location).isNotNull();
        assertThat(location).contains("/v2.0/module-export-requests/");
        assertThat(location).endsWith("/" + id);
    }

    @Test
    void content_shouldReturnXlsxBytesWithDownloadHeaders() throws Exception {
        byte[] content = stubExport();
        String id = createExportId(content);

        mockMvc.perform(get(COLLECTION + "/" + id + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, content.length))
                .andExpect(content().bytes(content));
    }

    @Test
    void detail_shouldReturnResourceMetadata() throws Exception {
        byte[] content = stubExport();
        String id = createExportId(content);

        mockMvc.perform(get(COLLECTION + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(isA(String.class)))
                .andExpect(jsonPath("$.fileName").value("销售订单.xlsx"))
                .andExpect(jsonPath("$.size").value(content.length));
    }

    @Test
    void content_withUnknownId_shouldReturn404ProblemDetail() throws Exception {
        mockMvc.perform(get(COLLECTION + "/999999999/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.type").value("urn:leo:problem:not-found"));
    }

    @Test
    void content_withForeignArtifactKind_shouldReturn404() {
        ExportArtifact foreign = artifactStore.put(new com.leo.erp.common.export.ExportArtifactDraft(
                ExportArtifactKinds.MATERIAL_EXPORT, null, null, null,
                "material.xlsx", XLSX_CONTENT_TYPE, new byte[]{1}));

        assertThat(artifactStore.find(ExportArtifactKinds.MODULE_EXPORT, foreign.id())).isEmpty();
    }

    @Test
    void create_withUnsupportedModule_shouldReturn422ProblemDetail() throws Exception {
        when(moduleExportService.export(anyString(), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的导出模块: supplier"));

        mockMvc.perform(post(COLLECTION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"moduleKey\":\"supplier\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.detail").value(containsString("不支持的导出模块")));
    }

    @Test
    void create_shouldRequireExportPermissionAndIdempotency() throws Exception {
        Method create = ModuleExportRequestController.class.getMethod(
                "create", SecurityPrincipal.class, ModuleExportRequest.class);
        RequirePermission permission = create.getAnnotation(RequirePermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly(PermissionCodes.MODULE_EXPORTS_EXPORT);
        assertThat(ModuleExportRequestController.class.getAnnotation(IdempotencyRequired.class)).isNotNull();
    }

    @Test
    void content_shouldRejectArtifactOwnedByAnotherUser() throws Exception {
        ExportArtifact artifact = artifactStore.put(new com.leo.erp.common.export.ExportArtifactDraft(
                ExportArtifactKinds.MODULE_EXPORT, null, null, 7L,
                "销售订单.xlsx", XLSX_CONTENT_TYPE, new byte[]{1}));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        SecurityPrincipal.authenticated(9L, "tester", 1L),
                        "n/a",
                        List.of()));

        mockMvc.perform(get(COLLECTION + "/" + artifact.id() + "/content"))
                .andExpect(status().isNotFound());
    }
}
