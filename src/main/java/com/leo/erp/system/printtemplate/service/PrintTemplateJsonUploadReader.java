package com.leo.erp.system.printtemplate.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * PDF_FORM 模板 JSON 上传内容读取与校验：文件存在性、大小上限、
 * 扩展名、UTF-8 严格解码、BOM 剥离与 JSON 结构校验。
 */
@Service
public class PrintTemplateJsonUploadReader {

    private static final long MAX_UPLOAD_JSON_BYTES = 1024L * 1024L;

    private final PrintPdfFormTemplateValidator pdfFormTemplateValidator;

    public PrintTemplateJsonUploadReader(PrintPdfFormTemplateValidator pdfFormTemplateValidator) {
        this.pdfFormTemplateValidator = pdfFormTemplateValidator;
    }

    public String read(MultipartFile file) {
        validateUploadFile(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取上传 JSON 文件失败");
        }
        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件不能为空");
        }
        if (bytes.length > MAX_UPLOAD_JSON_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件不能超过 1MB");
        }

        String content = decodeUtf8(bytes).trim();
        content = stripUtf8Bom(content).trim();
        if (content.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件不能为空");
        }
        pdfFormTemplateValidator.validate(content);
        return content;
    }

    private void validateUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_JSON_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件不能超过 1MB");
        }
        validateJsonFilename(file.getOriginalFilename());
    }

    private void validateJsonFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请上传 JSON 文件");
        }
        String filename = simpleFilename(originalFilename);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请上传 JSON 文件");
        }
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传 JSON 文件必须使用 UTF-8 编码");
        }
    }

    private String stripUtf8Bom(String content) {
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }

    private String simpleFilename(String originalFilename) {
        String normalized = originalFilename.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }
}
