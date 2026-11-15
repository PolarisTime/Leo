package com.leo.erp.common.export.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.json.NumericLongSerializer;

import java.time.Instant;

/**
 * 导出产物资源表示。{@code POST /xxx-export-requests} 的 201 响应体与
 * {@code GET /xxx-export-requests/{id}} 的 200 响应体共用本 DTO。
 *
 * <p>契约要点：</p>
 * <ul>
 *     <li>{@code id} 是雪花 id，经全局 Jackson 配置输出为十进制字符串；</li>
 *     <li>{@code size} 是字节数（远小于 2^53-1），用 {@link NumericLongSerializer}
 *         豁免全局 Long→字符串规则，保持 JSON number；</li>
 *     <li>{@code contentPath} 是内容端点的相对路径（不含 context-path），
 *         例如 {@code /v2.0/module-export-requests/123/content}，客户端拼接 API 基址即可下载。</li>
 * </ul>
 */
public record ExportArtifactResponse(
        Long id,
        String kind,
        String fileName,
        String contentType,
        @JsonSerialize(using = NumericLongSerializer.class) Long size,
        Instant createdAt,
        Instant expiresAt,
        String contentPath
) {

    /**
     * @param collectionPath 资源集合路径（以 {@code /} 开头，可含父资源 id），
     *                       如 {@code /module-export-requests} 或
     *                       {@code /sales-orders/123/xlsx-export-requests}
     */
    public static ExportArtifactResponse of(ExportArtifact artifact, String collectionPath) {
        String id = String.valueOf(artifact.id());
        return new ExportArtifactResponse(
                artifact.id(),
                artifact.kind(),
                artifact.fileName(),
                artifact.contentType(),
                artifact.size(),
                artifact.createdAt(),
                artifact.expiresAt(),
                ApiVersion.PREFIX + collectionPath + "/" + id + "/content"
        );
    }
}
