package com.leo.erp.attachment.web.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 附件响应 JSON 契约。
 *
 * <p>全局 {@code Long -> 字符串} 规则只服务于雪花 ID 精度保护；{@code fileSize} 是普通字节数，
 * 必须保持 JSON number。前端 Zod schema 按 number 声明该字段，一旦后端输出字符串，
 * 整条附件记录校验失败，表现为附件列表「加载失败」——销售合同附件入口因此完全不可用。
 * 这里同时锁定雪花 ID 仍是十进制字符串，防止修复时把 ID 精度保护一起改掉。</p>
 */
class AttachmentJsonSerializationTest {

    private ObjectMapper objectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig("Asia/Shanghai").jackson2ObjectMapperBuilderCustomizer().customize(builder);
        return builder.build();
    }

    private AttachmentResponse attachmentResponse() {
        return new AttachmentResponse(
                9223372036854775807L,
                "合同扫描件.png",
                "9223372036854775807.png",
                "合同扫描件.png",
                "image/png",
                8550L,
                "PAGE_UPLOAD",
                "系统管理员",
                LocalDateTime.of(2026, 10, 14, 21, 0),
                true,
                "image",
                "/api/v2.0/attachments/1/preview?accessKey=k",
                "/api/v2.0/attachments/1/download?accessKey=k",
                "local",
                "本地存储"
        );
    }

    @Test
    void bindingResponse_keepsFileSizeNumericAndSnowflakeIdsAsStrings() throws Exception {
        AttachmentBindingResponse binding = new AttachmentBindingResponse(
                "sales-contract",
                9223372036854775807L,
                List.of(attachmentResponse())
        );

        String json = objectMapper().writeValueAsString(binding);

        assertThat(json).contains("\"moduleKey\":\"sales-contract\"");
        assertThat(json).contains("\"recordId\":\"9223372036854775807\"");
        assertThat(json).contains("\"id\":\"9223372036854775807\"");
        assertThat(json).contains("\"fileSize\":8550");
        assertThat(json).doesNotContain("\"fileSize\":\"8550\"");
    }

    @Test
    void uploadResponse_keepsFileSizeNumeric() throws Exception {
        AttachmentUploadResponse upload = new AttachmentUploadResponse(
                9223372036854775807L,
                "合同扫描件.png",
                "9223372036854775807.png",
                "合同扫描件.png",
                "image/png",
                8550L,
                "PAGE_UPLOAD",
                "系统管理员",
                LocalDateTime.of(2026, 10, 14, 21, 0),
                true,
                "image",
                "/api/v2.0/attachments/1/preview?accessKey=k",
                "/api/v2.0/attachments/1/download?accessKey=k",
                "local",
                "本地存储"
        );

        String json = objectMapper().writeValueAsString(upload);

        assertThat(json).contains("\"fileSize\":8550");
        assertThat(json).doesNotContain("\"fileSize\":\"8550\"");
        assertThat(json).contains("\"id\":\"9223372036854775807\"");
    }

    @Test
    void bindingCountResponse_keepsSnowflakeRecordIdKeysPrecise() throws Exception {
        AttachmentBindingCountResponse counts = new AttachmentBindingCountResponse(
                "sales-contract",
                Map.of(9223372036854775807L, 2)
        );

        String json = objectMapper().writeValueAsString(counts);

        // counts 的键是雪花 ID, JSON 对象键本身即字符串, 必须逐位精确; 计数是普通整数。
        assertThat(json).contains("\"9223372036854775807\":2");
    }

    @Test
    void bindingResponse_nullFileSizeStaysNull() throws Exception {
        AttachmentResponse withoutSize = new AttachmentResponse(
                1L, "无大小.txt", "1.txt", "无大小.txt", "text/plain", null,
                "PAGE_UPLOAD", "系统管理员", LocalDateTime.of(2026, 10, 14, 21, 0),
                false, null, null, null, "local", "本地存储"
        );

        String json = objectMapper().writeValueAsString(withoutSize);

        assertThat(json).contains("\"fileSize\":null");
    }
}
