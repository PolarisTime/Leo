package com.leo.erp.attachment.service;

import com.leo.erp.attachment.api.AttachmentManifestArchive;
import com.leo.erp.attachment.api.AttachmentManifestExporter;
import com.leo.erp.common.export.ExportArtifact;
import com.leo.erp.common.export.ExportArtifactDraft;
import com.leo.erp.common.export.ExportArtifactKinds;
import com.leo.erp.common.export.ExportArtifactStore;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * 附件恢复清单导出产物的统一生成入口（资源化端点与兼容旧端点共用）。
 *
 * <p>清单导出会把 gzip 内容同时写入附件存储，因此它本身是「生成文件 + 返回文件表示」。
 * 资源化后返回的产物是可回读的缓存副本，旧的 {@code POST /attachment-manifest-exports}
 * 仍按原契约同步返回文件。并发闸门与其他导出共用额度。</p>
 */
@Service
public class AttachmentManifestExportArtifactService {

    private static final String GUARD_LABEL = "attachment-manifest-export";

    private final AttachmentManifestExporter attachmentManifestExporter;
    private final ExportConcurrencyGuard exportConcurrencyGuard;
    private final ExportArtifactStore artifactStore;

    public AttachmentManifestExportArtifactService(AttachmentManifestExporter attachmentManifestExporter,
                                                   ExportConcurrencyGuard exportConcurrencyGuard,
                                                   ExportArtifactStore artifactStore) {
        this.attachmentManifestExporter = attachmentManifestExporter;
        this.exportConcurrencyGuard = exportConcurrencyGuard;
        this.artifactStore = artifactStore;
    }

    /** 生成导出文件（不落缓存，供兼容旧端点使用）。 */
    public FileDownloadResponse generate() {
        AttachmentManifestArchive archive = archive();
        return new FileDownloadResponse(
                archive.fileName(),
                MediaType.parseMediaType(archive.contentType()),
                archive.content()
        );
    }

    /** 生成并登记导出产物（供资源端点使用）。 */
    public ExportArtifact create(Long ownerUserId) {
        AttachmentManifestArchive archive = archive();
        return artifactStore.put(new ExportArtifactDraft(
                ExportArtifactKinds.ATTACHMENT_MANIFEST_EXPORT,
                null,
                null,
                ownerUserId,
                archive.fileName(),
                archive.contentType(),
                archive.content()
        ));
    }

    private AttachmentManifestArchive archive() {
        return exportConcurrencyGuard.execute(
                GUARD_LABEL,
                attachmentManifestExporter::exportDailyArchive
        );
    }
}
