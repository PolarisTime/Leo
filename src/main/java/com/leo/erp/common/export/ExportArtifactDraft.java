package com.leo.erp.common.export;

/**
 * 待写入 {@link ExportArtifactStore} 的导出产物内容。
 *
 * <p>与 {@link ExportArtifact} 的区别：草稿没有 id/时间戳，这些由存储在写入时生成。</p>
 */
public record ExportArtifactDraft(
        String kind,
        String parentType,
        Long parentId,
        Long ownerUserId,
        String fileName,
        String contentType,
        byte[] content
) {
}
