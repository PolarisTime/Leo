package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PrintTemplateJsonUploadReader 边界测试：空文件、大小上限、
 * 扩展名、UTF-8 严格解码、BOM 剥离与空内容。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintTemplateJsonUploadReaderTest {

    @Mock
    private PrintPdfFormTemplateValidator pdfFormTemplateValidator;

    @InjectMocks
    private PrintTemplateJsonUploadReader reader;

    private MockMultipartFile file(String filename, byte[] bytes) {
        return new MockMultipartFile("file", filename, "application/json", bytes);
    }

    @Test
    void read_shouldRejectNullFile() {
        assertThatThrownBy(() -> reader.read(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传 JSON 文件不能为空");
    }

    @Test
    void read_shouldRejectEmptyFile() {
        assertThatThrownBy(() -> reader.read(file("layout.json", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传 JSON 文件不能为空");
    }

    @Test
    void read_shouldRejectOversizeFile() {
        MultipartFile oversize = new MockMultipartFile(
                "file", "layout.json", "application/json", new byte[0]) {
            @Override
            public long getSize() {
                return 1024L * 1024L + 1;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }
        };

        assertThatThrownBy(() -> reader.read(oversize))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 1MB");
    }

    @Test
    void read_shouldRejectNonJsonExtension() {
        assertThatThrownBy(() -> reader.read(file("layout.txt", "{}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 JSON 文件");
    }

    @Test
    void read_shouldRejectMissingFilename() {
        assertThatThrownBy(() -> reader.read(file(null, "{}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 JSON 文件");
    }

    @Test
    void read_shouldAcceptUppercaseExtensionAndPath() {
        assertThat(reader.read(file("/tmp/upload/LAYOUT.JSON", "{}".getBytes(StandardCharsets.UTF_8))))
                .isEqualTo("{}");
    }

    @Test
    void read_shouldStripUtf8Bom() {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = "{ \"fields\": {} }".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(content, 0, withBom, bom.length, content.length);

        String result = reader.read(file("layout.json", withBom));

        assertThat(result).startsWith("{");
        verify(pdfFormTemplateValidator).validate(anyString());
    }

    @Test
    void read_shouldRejectInvalidUtf8Bytes() {
        byte[] invalid = new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x01};

        assertThatThrownBy(() -> reader.read(file("layout.json", invalid)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("UTF-8 编码");
        verify(pdfFormTemplateValidator, never()).validate(anyString());
    }

    @Test
    void read_shouldRejectBlankContent() {
        assertThatThrownBy(() -> reader.read(file("layout.json", "   ".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传 JSON 文件不能为空");
    }

    @Test
    void read_shouldTrimContentAndDelegateValidation() {
        String result = reader.read(file("layout.json", "  { }  ".getBytes(StandardCharsets.UTF_8)));

        assertThat(result).isEqualTo("{ }");
        verify(pdfFormTemplateValidator).validate("{ }");
    }

    @Test
    void read_shouldRejectBackslashPathWithoutJsonExtension() {
        assertThatThrownBy(() -> reader.read(file("C:\\temp\\layout.pdf", "{}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请上传 JSON 文件");
    }
}
