package com.leo.erp.common.export;

import java.util.Optional;

/**
 * 导出产物存储。
 *
 * <p>导出端点被建模为资源（{@code POST /xxx-export-requests} → {@code 201 + Location}），
 * 之后通过 {@code GET /xxx-export-requests/{id}/content} 回读文件内容，因此生成后的字节
 * 必须在回读窗口内可访问。存储只负责「按种类 + id 存取」，生命周期策略由实现决定
 * （当前默认实现为进程内有界缓存，见 {@link InMemoryExportArtifactStore}）。</p>
 */
public interface ExportArtifactStore {

    /** 写入产物并返回带 id/时间戳的完整表示。 */
    ExportArtifact put(ExportArtifactDraft draft);

    /** 按种类 + id 读取；不存在、已过期或种类不匹配时返回空。 */
    Optional<ExportArtifact> find(String kind, long id);
}
