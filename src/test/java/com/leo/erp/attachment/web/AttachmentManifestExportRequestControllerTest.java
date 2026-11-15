package com.leo.erp.attachment.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.attachment.api.AttachmentManifestArchive;
import com.leo.erp.attachment.api.AttachmentManifestExporter;
import com.leo.erp.attachment.service.AttachmentManifestExportArtifactService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 附件恢复清单导出请求资源的 HTTP 契约测试。
 *
 * <p>覆盖：201 + Location + gzip 元数据、内容端点媒体类型与字节、
 * 未知 id 404、权限与幂等注解契约。</p>
 */
@ExtendWith(MockitoExtension.class)
class AttachmentManifestExportRequestControllerTest {

    private static final String COLLECTION = "/v2.0/attachment-manifest-export-requests";
    private static final String GZIP_MEDIA_TYPE = "application/gzip";
    private static final String FILE_NAME = "manifest-20260102T000000Z.jsonl.gz";

    @Mock
    private AttachmentManifestExporter attachmentManifestExporter;

    private AttachmentManifestExportArtifactService artifactService;
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ExportArtifactStore store = new InMemoryExportArtifactStore(
                new SnowflakeIdGenerator(), new ExportArtifactProperties());
        artifactService = new AttachmentManifestExportArtifactService(
                attachmentManifestExporter,
                new ExportConcurrencyGuard(new ExportConcurrencyProperties()),
                store);
        objectMapper = jsonObjectMapper();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AttachmentManifestExportRequestController(artifactService, store))
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

    private byte[] stubArchive() {
        byte[] content = "manifest-content".getBytes(StandardCharsets.UTF_8);
        when(attachmentManifestExporter.exportDailyArchive()).thenReturn(new AttachmentManifestArchive(
                "attachment-manifests/daily/2026/01/02/" + FILE_NAME,
                "local:/2026/01/02/" + FILE_NAME,
                FILE_NAME,
                GZIP_MEDIA_TYPE,
                content,
                2,
                3
        ));
        return content;
    }

    private String createExportId() throws Exception {
        MvcResult result = mockMvc.perform(post(COLLECTION))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void create_shouldReturn201WithLocationAndGzipMetadata() throws Exception {
        byte[] content = stubArchive();

        MvcResult result = mockMvc.perform(post(COLLECTION))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.id").value(isA(String.class)))
                .andExpect(jsonPath("$.fileName").value(FILE_NAME))
                .andExpect(jsonPath("$.contentType").value(GZIP_MEDIA_TYPE))
                .andExpect(jsonPath("$.size").value(content.length))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION))
                .contains("/v2.0/attachment-manifest-export-requests/")
                .endsWith("/" + id);
    }

    @Test
    void content_shouldReturnGzipBytesWithDisposition() throws Exception {
        byte[] content = stubArchive();
        String id = createExportId();

        mockMvc.perform(get(COLLECTION + "/" + id + "/content"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, GZIP_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(FILE_NAME)))
                .andExpect(content().bytes(content));
    }

    @Test
    void content_withUnknownId_shouldReturn404() throws Exception {
        mockMvc.perform(get(COLLECTION + "/123456789/content"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void create_shouldRequirePermissionAndIdempotency() throws Exception {
        Method create = AttachmentManifestExportRequestController.class.getMethod(
                "create", SecurityPrincipal.class);
        RequirePermission permission = create.getAnnotation(RequirePermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly(PermissionCodes.ATTACHMENT_MANIFEST_EXPORTS_EXPORT);
        assertThat(AttachmentManifestExportRequestController.class.getAnnotation(IdempotencyRequired.class))
                .isNotNull();
    }
}
