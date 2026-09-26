package com.leo.erp.attachment.support;

import java.util.Locale;
import java.util.Map;

/**
 * 附件内容类型(MIME)与文件扩展名之间的双向映射。
 *
 * <p>集中维护以避免上传解析与响应装配两处映射漂移; 未知类型回退为二进制流/{@code bin}。</p>
 */
public final class AttachmentMediaTypes {

    private AttachmentMediaTypes() {
    }

    public static final String OCTET_STREAM = "application/octet-stream";
    public static final String PDF = "application/pdf";
    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";
    public static final String GIF = "image/gif";
    public static final String WEBP = "image/webp";
    public static final String BMP = "image/bmp";
    public static final String TEXT_PLAIN = "text/plain";
    public static final String GZIP = "application/gzip";
    public static final String DOC = "application/msword";
    public static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public static final String XLS = "application/vnd.ms-excel";
    public static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 扩展名 -> 内容类型(小写扩展名)。 */
    private static final Map<String, String> BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", PDF),
            Map.entry("png", PNG),
            Map.entry("jpg", JPEG),
            Map.entry("jpeg", JPEG),
            Map.entry("gif", GIF),
            Map.entry("webp", WEBP),
            Map.entry("bmp", BMP),
            Map.entry("txt", TEXT_PLAIN),
            Map.entry("doc", DOC),
            Map.entry("docx", DOCX),
            Map.entry("xls", XLS),
            Map.entry("xlsx", XLSX)
    );

    /** 内容类型 -> 扩展名(小写内容类型)。 */
    private static final Map<String, String> BY_CONTENT_TYPE = Map.ofEntries(
            Map.entry(PDF, "pdf"),
            Map.entry(PNG, "png"),
            Map.entry(JPEG, "jpg"),
            Map.entry(GIF, "gif"),
            Map.entry(WEBP, "webp"),
            Map.entry(BMP, "bmp"),
            Map.entry(TEXT_PLAIN, "txt"),
            Map.entry(DOC, "doc"),
            Map.entry(DOCX, "docx"),
            Map.entry(XLS, "xls"),
            Map.entry(XLSX, "xlsx")
    );

    /** 按扩展名解析内容类型, 未知回退 {@link #OCTET_STREAM}。 */
    public static String contentTypeOfExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return OCTET_STREAM;
        }
        return BY_EXTENSION.getOrDefault(extension.trim().toLowerCase(Locale.ROOT), OCTET_STREAM);
    }

    /** 按内容类型解析扩展名, 未知回退 {@code bin}。 */
    public static String extensionOfContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "bin";
        }
        return BY_CONTENT_TYPE.getOrDefault(contentType.trim().toLowerCase(Locale.ROOT), "bin");
    }
}
