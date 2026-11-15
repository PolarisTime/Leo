package com.leo.erp.common.export.web;

import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * 导出文件 HTTP 响应构造器（唯一出口）。
 *
 * <p>两类端点的文件表示必须一致，因此集中在本类：</p>
 * <ul>
 *     <li>{@link #content(ExportArtifact)}：新资源端点 {@code GET /{id}/content}，200 + 文件内容；</li>
 *     <li>{@link #download(FileDownloadResponse)}：兼容旧端点的同步返回（200 + 文件内容），
 *         保留旧契约直到调用方迁移完成、旧端点删除。</li>
 * </ul>
 *
 * <p>{@code Content-Type}/{@code Content-Disposition}/{@code Content-Length} 三者必须同时正确：
 * 缺少 {@code Content-Disposition} 时浏览器可能以内联方式打开二进制；缺少 {@code Content-Length}
 * 时下载进度不可知。</p>
 */
public final class ExportArtifactResponses {

    private ExportArtifactResponses() {
    }

    /** 资源内容端点：200 + 文件字节。 */
    public static ResponseEntity<Resource> content(ExportArtifact artifact) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .contentLength(artifact.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(artifact.fileName()))
                .body(new ByteArrayResource(artifact.content()));
    }

    /** 旧端点兼容：200 + 文件字节，行为与资源化前保持一致。 */
    public static ResponseEntity<byte[]> download(FileDownloadResponse file) {
        return ResponseEntity.ok()
                .contentType(file.contentType())
                .contentLength(file.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment(file.filename()))
                .body(file.content());
    }

    private static String attachment(String fileName) {
        return ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
