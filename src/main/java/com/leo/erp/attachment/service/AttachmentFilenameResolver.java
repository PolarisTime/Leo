package com.leo.erp.attachment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class AttachmentFilenameResolver {

    private static final DateTimeFormatter YYYY = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter HHMMSS = DateTimeFormatter.ofPattern("HHmmss");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String buildFileName(String renamePattern, String originalFilename, String contentType, LocalDateTime now) {
        return renderFileName(renamePattern, originalFilename, contentType, now, String.valueOf(System.currentTimeMillis()), UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    public String preview(String renamePattern, String originalFilename) {
        return renderFileName(renamePattern, originalFilename, "application/pdf", LocalDateTime.of(2026, 4, 24, 12, 30, 45), "1777005045000", "preview1");
    }

    private String renderFileName(String renamePattern,
                                  String originalFilename,
                                  String contentType,
                                  LocalDateTime now,
                                  String timestamp,
                                  String random8) {
        FilenameParts parts = parseFilenameParts(originalFilename, contentType);
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("{yyyy}", now.format(YYYY));
        placeholders.put("{yyyyMMdd}", now.format(YYYYMMDD));
        placeholders.put("{HHmmss}", now.format(HHMMSS));
        placeholders.put("{yyyyMMddHHmmss}", now.format(FULL));
        placeholders.put("{timestamp}", timestamp);
        placeholders.put("{random8}", random8);
        placeholders.put("{originName}", parts.baseName());
        placeholders.put("{ext}", parts.extension());

        String rendered = renamePattern;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace(entry.getKey(), entry.getValue());
        }

        String sanitized = sanitizeBaseName(rendered);
        if (sanitized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传命名规则生成的文件名为空");
        }

        if (parts.extension().isBlank()) {
            return sanitized;
        }

        String lowerName = sanitized.toLowerCase(Locale.ROOT);
        String expectedSuffix = "." + parts.extension().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(expectedSuffix)) {
            return sanitized;
        }
        return sanitized + "." + parts.extension();
    }

    public FilenameParts parseFilenameParts(String originalFilename, String contentType) {
        String safeOriginal = originalFilename == null ? "" : originalFilename.trim();
        String baseName = safeOriginal;
        String extension = "";
        int dotIndex = safeOriginal.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < safeOriginal.length() - 1) {
            baseName = safeOriginal.substring(0, dotIndex);
            extension = safeOriginal.substring(dotIndex + 1);
        }

        baseName = sanitizeBaseName(baseName);
        if (baseName.isBlank()) {
            baseName = "clipboard";
        }

        extension = sanitizeExtension(extension);
        if (extension.isBlank()) {
            extension = extensionFromContentType(contentType);
        }
        return new FilenameParts(baseName, extension);
    }

    private String sanitizeBaseName(String value) {
        String sanitized = value == null ? "" : value.trim()
                .replace('\\', '_')
                .replace('/', '_')
                .replace(':', '_')
                .replace('*', '_')
                .replace('?', '_')
                .replace('"', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace('|', '_');
        sanitized = sanitized.replaceAll("\\s+", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^[_\\.]+|[_\\.]+$", "");
        return sanitized;
    }

    private String sanitizeExtension(String extension) {
        String sanitized = extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
        sanitized = sanitized.replaceAll("[^a-z0-9]+", "");
        if (sanitized.length() > 16) {
            sanitized = sanitized.substring(0, 16);
        }
        return sanitized;
    }

    private String extensionFromContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "bin";
        }
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "application/pdf" -> "pdf";
            case "text/plain" -> "txt";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/msword" -> "doc";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.ms-excel" -> "xls";
            default -> "bin";
        };
    }

    public record FilenameParts(String baseName, String extension) {
    }
}
